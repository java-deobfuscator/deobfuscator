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

package com.javadeobfuscator.deobfuscator.utils;

import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.Map;

public class PrimitiveUtils {
    private static final Map<String, Class<?>> nameToPrimitive = new HashMap<>();
    private static final Map<Class<?>, Object> defaultPrimitiveValues = new HashMap<>();

    static {
        defaultPrimitiveValues.put(Integer.TYPE, 0);
        defaultPrimitiveValues.put(Long.TYPE, 0L);
        defaultPrimitiveValues.put(Double.TYPE, 0D);
        defaultPrimitiveValues.put(Float.TYPE, 0F);
        defaultPrimitiveValues.put(Boolean.TYPE, false);
        defaultPrimitiveValues.put(Character.TYPE, '\0');
        defaultPrimitiveValues.put(Byte.TYPE, (byte) 0);
        defaultPrimitiveValues.put(Short.TYPE, (short) 0);
        defaultPrimitiveValues.put(Object.class, null);
        nameToPrimitive.put("int", Integer.TYPE);
        nameToPrimitive.put("long", Long.TYPE);
        nameToPrimitive.put("double", Double.TYPE);
        nameToPrimitive.put("float", Float.TYPE);
        nameToPrimitive.put("boolean", Boolean.TYPE);
        nameToPrimitive.put("char", Character.TYPE);
        nameToPrimitive.put("byte", Byte.TYPE);
        nameToPrimitive.put("short", Short.TYPE);
        nameToPrimitive.put("void", Void.TYPE);
    }

    public static Class<?> getPrimitiveByName(String name) {
        return nameToPrimitive.get(name);
    }

    public static Object getDefaultValue(Class<?> primitive) {
        return defaultPrimitiveValues.get(primitive);
    }

    public static Class<?> getPrimitiveByNewArrayId(int id) {
        switch (id) {
            case Opcodes.T_BOOLEAN:
                return boolean.class;
            case Opcodes.T_CHAR:
                return char.class;
            case Opcodes.T_FLOAT:
                return float.class;
            case Opcodes.T_DOUBLE:
                return double.class;
            case Opcodes.T_BYTE:
                return byte.class;
            case Opcodes.T_SHORT:
                return short.class;
            case Opcodes.T_INT:
                return int.class;
            case Opcodes.T_LONG:
                return long.class;
        }
        throw new IllegalArgumentException("Unknown type " + id);
    }
}
