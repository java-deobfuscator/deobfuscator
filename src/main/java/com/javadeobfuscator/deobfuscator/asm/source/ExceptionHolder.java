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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Since {@link java.lang.Throwable} doesn't override {@link Object#equals(Object)}, we use an ExceptionHolder to represent
 * arbitrary exceptions
 */
public class ExceptionHolder {
    private final String exceptionType;
    private final Map<String, Object> exceptionProperties;

    public ExceptionHolder(String exceptionType) {
        this(exceptionType, Collections.emptyMap());
    }

    public ExceptionHolder(String exceptionType, Map<String, Object> exceptionProperties) {
        this.exceptionType = exceptionType;
        this.exceptionProperties = new HashMap<>();
        this.exceptionProperties.putAll(exceptionProperties);
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public Map<String, Object> getExceptionProperties() {
        return exceptionProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExceptionHolder that = (ExceptionHolder) o;
        return Objects.equals(exceptionType, that.exceptionType) &&
                Objects.equals(exceptionProperties, that.exceptionProperties);
    }

    @Override
    public int hashCode() {

        return Objects.hash(exceptionType, exceptionProperties);
    }

    @Override
    public String toString() {
        return "ExceptionHolder{" +
                "exceptionType='" + exceptionType + '\'' +
                ", exceptionProperties=" + exceptionProperties +
                '}';
    }
}
