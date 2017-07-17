/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.executor.defined;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;

public class MappedFieldProvider extends FieldProvider {
    private final Map<String, Object> staticFields = Collections.synchronizedMap(new HashMap<>());
    private final Map<Object, Map<String, Object>> instanceFields = new ConcurrentHashMap<>();

    public Object getField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        if (targetObject == null) {
            return staticFields.get(className + fieldName + fieldDesc);
        } else {
            synchronized (instanceFields) {
                Object field = instanceFields.get(targetObject.value());
                if (field != null) {
                    return ((Map<String, Object>) field).get(className + fieldName + fieldDesc);
                }
            }

            return null;
        }
    }

    public void setField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        if (targetObject == null) {
            staticFields.put(className + fieldName + fieldDesc, value);
        } else {
            synchronized (instanceFields) {
                Object field = instanceFields.get(targetObject.value());
                if (field == null) {
                    field = new HashMap<>();
                }
                ((Map<String, Object>) field).put(className + fieldName + fieldDesc, value);
                instanceFields.put(targetObject.value(), (Map<String, Object>) field);
            }
        }
    }

    public boolean canGetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        return true;
    }

    public boolean canSetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        return true;
    }
}
