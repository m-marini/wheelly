/*
 *
 * Copyright (c) 2022 Marco Marini, marco.marini@mmarini.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 *    END OF TERMS AND CONDITIONS
 *
 */

package org.mmarini.wheelly.engines.statemachine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class StateMachineContext {
    /**
     * @return
     */
    public static StateMachineContext create() {
        return new StateMachineContext(new HashMap<>());
    }

    private Map<String, Object> values;

    /**
     * @param values
     */
    protected StateMachineContext(Map<String, Object> values) {
        this.values = requireNonNull(values);
    }

    /**
     * @param key
     * @param <T>
     */
    public <T> Optional<T> get(String key) {
        return Optional.ofNullable((T) values.get(key));
    }

    public Optional<Long> getElapsedTime() {
        return getEntryTime().map(entryTime -> System.currentTimeMillis() - entryTime);
    }

    public Optional<Long> getEntryTime() {
        return Optional.ofNullable((Long) values.get(EngineStatus.ENTRY_TIME_KEY));
    }

    public StateMachineContext setEntryTime(long entryTime) {
        values.put(EngineStatus.ENTRY_TIME_KEY, entryTime);
        return this;
    }

    public Optional<String> getStatusName() {
        return Optional.ofNullable((String) values.get(EngineStatus.STATUS_NAME_KEY));
    }

    public StateMachineContext setStatusName(String name) {
        values.put(EngineStatus.STATUS_NAME_KEY, requireNonNull(name));
        return this;
    }

    /**
     * @param key
     * @param value
     * @param <T>
     */
    public <T> StateMachineContext put(String key, T value) {
        values.put(key, value);
        return this;
    }

    /**
     * @param key
     */
    public StateMachineContext remove(String key) {
        values.remove(key);
        return this;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}