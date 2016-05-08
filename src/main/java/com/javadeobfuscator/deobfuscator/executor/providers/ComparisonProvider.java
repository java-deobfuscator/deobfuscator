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

import java.util.List;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
;

public abstract class ComparisonProvider implements Provider {
    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        return false;
    }

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        return false;
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        return false;
    }

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        throw new IllegalStateException("Cannot invoke method on ComparisonProvider");
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        throw new IllegalStateException("Cannot set field on ComparisonProvider");
    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        throw new IllegalStateException("Cannot get field on ComparisonProvider");
    }
}
