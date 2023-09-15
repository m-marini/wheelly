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

package org.mmarini.rl.agents;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import org.mmarini.rl.envs.Environment;
import org.mmarini.rl.envs.Signal;
import org.mmarini.rl.envs.WithSignalsSpec;
import org.mmarini.yaml.Utils;
import org.mmarini.yaml.schema.Locator;
import org.mmarini.yaml.schema.Validator;
import org.nd4j.linalg.api.ndarray.INDArray;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mmarini.yaml.schema.Validator.*;

/**
 * The agent interface
 */
public interface Agent extends Closeable, WithSignalsSpec {
    Validator BASE_CONFIG = objectPropertiesRequired(Map.of(
            "version", string(values("0.4")),
            "active", string(),
            "configurations", object()
    ), List.of("version", "active", "configurations"));

    static Agent fromConfig(String file, WithSignalsSpec env) {
        try {
            JsonNode config = Utils.fromFile(file);
            BASE_CONFIG.apply(Locator.root()).accept(config);
            String active = Locator.locate("active").getNode(config).asText();
            Locator baseLocator = Locator.locate("configurations").path(active);
            return Utils.createObject(config, baseLocator, new Object[]{env}, new Class[]{WithSignalsSpec.class});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the action generated by agent for the given state
     *
     * @param state the state
     */
    Map<String, Signal> act(Map<String, Signal> state);

    /**
     * Returns the agent specification json node
     */
    JsonNode getJson();

    /**
     * Observes the execution result training the agent
     *
     * @param result the execution result
     */
    void observe(Environment.ExecutionResult result);

    /**
     * Returns the flowable of kpis
     */
    Flowable<Map<String, INDArray>> readKpis();

    /**
     * Save the model
     *
     * @param path the path
     * @throws IOException in case of error
     */
    void save(File path) throws IOException;
}
