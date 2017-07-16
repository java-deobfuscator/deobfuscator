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

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;

public class PrimitiveFieldProvider extends FieldProvider {
    public Object getField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        if (!fieldName.equals("TYPE")) {
            throw new IllegalStateException();
        }
        switch (className) {
        case "java/lang/Integer":
            return new JavaClass("int", context);
        case "java/lang/Byte":
            return new JavaClass("byte", context);
        case "java/lang/Short":
            return new JavaClass("short", context);
        case "java/lang/Float":
            return new JavaClass("float", context);
        case "java/lang/Boolean":
            return new JavaClass("boolean", context);
        case "java/lang/Character":
            return new JavaClass("char", context);
        case "java/lang/Double":
            return new JavaClass("double", context);
        case "java/lang/Long":
            return new JavaClass("long", context);
        case "java/lang/Void":
            return new JavaClass("void", context);
        default:
            throw new IllegalStateException();
        }
    }

    public void setField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        throw new IllegalStateException();
    }

    public boolean canGetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        if (!fieldName.equals("TYPE")) {
            return false;
        }
        switch (className) {
        case "java/lang/Integer":
        case "java/lang/Byte":
        case "java/lang/Short":
        case "java/lang/Float":
        case "java/lang/Boolean":
        case "java/lang/Character":
        case "java/lang/Double":
        case "java/lang/Long":
        case "java/lang/Void":
            return true;
        default:
            return false;
        }
    }

    public boolean canSetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        return false;
    }
}
