/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.asm.source;

import java.util.*;

public class SourceResult {

    private final List<Object> values = new ArrayList<>();
    private final Set<Object> valuesDeduped = new HashSet<>();
    private final List<ExceptionHolder> exceptions = new ArrayList<>();
    private final Set<ExceptionHolder> exceptionsDeduped = new HashSet<>();

    public SourceResult(List<Object> values, List<ExceptionHolder> exceptions) {
        this.values.addAll(values);
        this.exceptions.addAll(exceptions);
        this.valuesDeduped.addAll(values);
        this.exceptionsDeduped.addAll(exceptions);
    }

    public static SourceResult unknown() {
        return new SourceResult(Collections.emptyList(), Collections.emptyList());
    }

    public static SourceResult values(List<Object> values) {
        return new SourceResult(values, Collections.emptyList());
    }

    public static SourceResult exceptions(List<ExceptionHolder> exceptions) {
        return new SourceResult(Collections.emptyList(), exceptions);
    }

    public static SourceResult values(Object... values) {
        return new SourceResult(Arrays.asList(values), Collections.emptyList());
    }

    public static SourceResult exceptions(ExceptionHolder... exceptions) {
        return new SourceResult(Collections.emptyList(), Arrays.asList(exceptions));
    }

    public Set<Object> getValuesDeduped() {
        return valuesDeduped;
    }

    public Optional<Object> consensus() {
        return valuesDeduped.size() == 1 ?
                Optional.of(valuesDeduped.iterator().next()) : Optional.empty();
    }

    public OptionalInt intConsensus() {
        return valuesDeduped.size() == 1 && valuesDeduped.iterator().next() instanceof Integer ?
                OptionalInt.of((Integer) valuesDeduped.iterator().next()) : OptionalInt.empty();
    }

    public Set<ExceptionHolder> getExceptionsDeduped() {
        return exceptionsDeduped;
    }

    public boolean isUnknown() {
        return values.size() == 0 && exceptions.size() == 0;
    }

    @Override
    public String toString() {
        return "SourceResult{" +
                "values=" + values +
                ", exceptions=" + exceptions +
                '}';
    }

    public List<Object> getValues() {
        return values;
    }

    public List<ExceptionHolder> getExceptions() {
        return exceptions;
    }
}
