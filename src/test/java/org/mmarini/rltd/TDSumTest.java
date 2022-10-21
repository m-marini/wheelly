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

package org.mmarini.rltd;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.rng.Random;
import org.nd4j.linalg.factory.Nd4j;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.mmarini.ArgumentsGenerator.createArgumentGenerator;
import static org.mmarini.ArgumentsGenerator.createStream;
import static org.mmarini.wheelly.engines.deepl.TestFunctions.matrixCloseTo;

class TDSumTest {

    public static final long SEED = 1234L;
    private static final double EPSILON = 1e-6;

    static Stream<Arguments> cases() {
        Random random = Nd4j.getRandom();
        random.setSeed(SEED);
        return createStream(SEED,
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // inputs0
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)), // inputs1
                createArgumentGenerator((ignored) -> Nd4j.randn(random, 1, 2)) // grad
        );
    }

    @ParameterizedTest
    @MethodSource("cases")
    void forward(INDArray input0,
                 INDArray input1,
                 INDArray grad) {
        TDSum layer = new TDSum("name");
        float in00 = input0.getFloat(0, 0);
        float in01 = input0.getFloat(0, 1);
        float in10 = input1.getFloat(0, 0);
        float in11 = input1.getFloat(0, 1);
        INDArray[] in = {input0, input1};
        INDArray out = layer.forward(in, null);
        assertThat(out, matrixCloseTo(new float[][]{{
                in00 + in10, in01 + in11
        }}, EPSILON));
    }

    @Test
    void spec() {
        TDSum layer = new TDSum("name");
        JsonNode node = layer.getSpec();
        assertThat(node.path("name").asText(), equalTo("name"));
        assertThat(node.path("type").asText(), equalTo("sum"));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void train(INDArray input0,
               INDArray input1,
               INDArray grad) {
        float grad0 = grad.getFloat(0, 0);
        float grad1 = grad.getFloat(0, 1);
        INDArray[] in = {input0, input1};

        TDSum layer = new TDSum("name");
        INDArray out = layer.forward(in, null);
        INDArray[] post_grads = layer.train(in, out, grad, Nd4j.zeros(1), 0, null);

        assertThat(post_grads, arrayWithSize(2));
        assertThat(post_grads[0], matrixCloseTo(new float[][]{{
                grad0, grad1
        }}, EPSILON));
        assertThat(post_grads[1], matrixCloseTo(new float[][]{{
                grad0, grad1
        }}, EPSILON));
    }
}
