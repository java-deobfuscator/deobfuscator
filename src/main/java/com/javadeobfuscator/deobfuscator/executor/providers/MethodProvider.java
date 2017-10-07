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

package com.javadeobfuscator.deobfuscator.executor.providers;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Type;

public abstract class MethodProvider implements Provider {

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        return false;
    }

    @Override
    public boolean canCheckcast(JavaValue target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        return false;
    }

    @Override
    public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
        return false;
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        throw new IllegalStateException("Cannot set field on MethodProvider");
    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        throw new IllegalStateException("Cannot get field on MethodProvider");
    }

    @Override
    public boolean instanceOf(JavaValue target, Type type, Context context) {
        throw new IllegalStateException("Cannot check instanceof on MethodProvider");
    }

    @Override
    public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
        throw new IllegalStateException("Cannot check equality on MethodProvider");
    }

    @Override
    public boolean checkcast(JavaValue target, Type type, Context context) {
        throw new IllegalStateException("Cannot check cast on MethodProvider");
    }
}
