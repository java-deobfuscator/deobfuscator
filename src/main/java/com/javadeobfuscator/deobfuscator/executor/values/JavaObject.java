/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.executor.values;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class JavaObject extends JavaValue {
    private Object value;
    private String type;
    public static final Map<String, Function<Object, Object>> patchClasses = new HashMap<>();

    public JavaObject(Object value, String type) {
        this.value = value;
        this.type = type;
    }

    public JavaObject(String type) {
        this.type = type;
    }

    @Override
    public Object value() {
        return this.value;
    }

    public JavaObject copy() {
        return this;
    }

    public void initialize(Object value) {
    	if(patchClasses.containsKey(type))
    		this.value = patchClasses.get(type).apply(value);
    	else
    		this.value = value;
    }

    public String type() {
        return this.type;
    }
    public String toString() {
        return "JavaObject@" + Integer.toHexString(System.identityHashCode(this)) + "(value=" + value + ", type=" + type + ")";
    }
}
