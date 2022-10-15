/*
 * MIT License
 *
 * Copyright (c) 2022 Marco Marini
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.mmarini.wheelly.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.mmarini.wheelly.envs.Signal;
import org.mmarini.wheelly.envs.SignalSpec;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.mmarini.yaml.schema.Validator.*;

/**
 * The processor processes the signals to produce new coding signals
 */
public class InputProcessor implements UnaryOperator<Map<String, Signal>> {
    public static final Validator PROCESSOR_LIST = array(arrayItems(Utils.DYNAMIC_OBJECT),
            minItems(1));

    public static InputProcessor concat(List<InputProcessor> list) {
        requireNonNull(list);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("list must not be empty");
        }
        UnaryOperator<Map<String, Signal>> encode1 = x -> {
            Map<String, Signal> in = x;
            for (InputProcessor e : list) {
                in = e.apply(in);
            }
            return in;
        };
        ArrayNode json = Utils.objectMapper.createArrayNode();
        for (InputProcessor processor : list) {
            json.add(processor.getJson());
        }
        Map<String, SignalSpec> spec1 = list.get(list.size() - 1).getSpec();

        return new InputProcessor(encode1, spec1, json);
    }

    /**
     * Returns the processor that concatenate the processor list from specification
     *
     * @param root    the root document
     * @param locator the processor array locator
     * @param spec    the input specification
     */
    public static InputProcessor create(JsonNode root, Locator locator, Map<String, SignalSpec> spec) {
        PROCESSOR_LIST.apply(locator).accept(root);
        List<InputProcessor> processors = new ArrayList<>();
        Class<?>[] classes = new Class[]{Map.class};
        List<Locator> processorLocators = locator.elements(root).collect(Collectors.toList());
        for (Locator loc : processorLocators) {
            InputProcessor processor = Utils.createObject(root, loc, new Object[]{spec}, classes);
            spec = processor.getSpec();
            processors.add(processor);
        }
        return InputProcessor.concat(processors);
    }

    private final Map<String, SignalSpec> spec;
    private final UnaryOperator<Map<String, Signal>> encode;
    private final JsonNode json;

    /**
     * Creates the tile processor
     *
     * @param encode the encode function
     * @param spec   the output spec
     * @param json   the json node
     */
    public InputProcessor(UnaryOperator<Map<String, Signal>> encode, Map<String, SignalSpec> spec, JsonNode json) {
        this.spec = requireNonNull(spec);
        this.encode = requireNonNull(encode);
        this.json = json;
    }

    @Override
    public Map<String, Signal> apply(Map<String, Signal> signals) {
        return encode.apply(signals);
    }

    /**
     * Returns the json node
     */
    public JsonNode getJson() {
        return json;
    }

    /**
     * Returns the signal spec of the processed signal
     */
    public Map<String, SignalSpec> getSpec() {
        return spec;
    }
}
