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

import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;

public abstract class JavaValue {

    public int intValue() {
        throw new ExecutionException(new UnsupportedOperationException());
    }

    public double doubleValue() {
        throw new ExecutionException(new UnsupportedOperationException());
    }

    public float floatValue() {
        throw new ExecutionException(new UnsupportedOperationException());
    }

    public long longValue() {
        throw new ExecutionException(new UnsupportedOperationException());
    }

    public Object value() {
        return MethodExecutor.value(this);
    }

    public <T> T as(Class<T> clazz) {
        if (Primitives.unwrap(clazz) != clazz) {
            throw new ExecutionException("Cannot call as(Class<T> clazz) with a primitive class");
        }
        if (value() instanceof Character && clazz == char.class) {
            return (T) value();
        }
        if (value() instanceof Integer && clazz == boolean.class) {
            return (T) Boolean.valueOf(intValue() != 0 ? true : false);
        }
        return clazz.cast(value());
    }

    public abstract JavaValue copy();

    public void initialize(Object value) {
        throw new ExecutionException(new UnsupportedOperationException());
    }

    public String type() {
        throw new ExecutionException(new UnsupportedOperationException());
    }

    public static JavaValue valueOf(Object cst) {
        return new JavaObject(cst, "java/lang/Object"); //fixme type
    }

    public static JavaValue forPrimitive(Class<?> prim) {
        JavaValue result;
        switch (prim.getCanonicalName()) {
            case "byte":
                result = new JavaByte((byte) 0);
                break;
            case "char":
                result = new JavaCharacter('\u0000');
                break;
            case "double":
                result = new JavaDouble(0.0d);
                break;
            case "float":
                result = new JavaFloat(0.0f);
                break;
            case "int":
                result = new JavaInteger(0);
                break;
            case "long":
                result = new JavaLong(0l);
                break;
            case "short":
                result = new JavaShort((short) 0);
                break;
            default:
                result = new JavaObject(null, "java/lang/Object");
                break;
        }
        return result;
    }
}
