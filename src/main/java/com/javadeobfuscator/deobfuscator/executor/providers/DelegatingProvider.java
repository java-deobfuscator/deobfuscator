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

import java.util.ArrayList;
import java.util.List;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public class DelegatingProvider implements Provider {

    private List<Provider> providers = new ArrayList<>();

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        return providers.stream().filter(provider -> provider.canInvokeMethod(className, methodName, methodDesc, targetObject, args, context)).findFirst().get().invokeMethod(className, methodName, methodDesc, targetObject, args, context);
    }

    @Override
    public boolean instanceOf(StackObject target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckInstanceOf(target, type, context)).findFirst().get().instanceOf(target, type, context);
    }

    @Override
    public boolean checkcast(StackObject target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckcast(target, type, context)).findFirst().get().checkcast(target, type, context);
    }

    @Override
    public boolean checkEquality(StackObject first, StackObject second, Context context) {
        return providers.stream().filter(provider -> provider.canCheckEquality(first, second, context)).findFirst().get().checkEquality(first, second, context);
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        providers.stream().filter(provider -> provider.canSetField(className, fieldName, fieldDesc, targetObject, value, context)).findFirst().get().setField(className, fieldName, fieldDesc, targetObject, value, context);
    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return providers.stream().filter(provider -> provider.canGetField(className, fieldName, fieldDesc, targetObject, context)).findFirst().get().getField(className, fieldName, fieldDesc, targetObject, context);
    }

    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        return providers.stream().filter(provider -> provider.canInvokeMethod(className, methodName, methodDesc, targetObject, args, context)).findFirst().isPresent();
    }

    @Override
    public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckInstanceOf(target, type, context)).findFirst().isPresent();
    }

    @Override
    public boolean canCheckcast(StackObject target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckcast(target, type, context)).findFirst().isPresent();
    }

    @Override
    public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
        return providers.stream().filter(provider -> provider.canCheckEquality(first, second, context)).findFirst().isPresent();
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        return providers.stream().filter(provider -> provider.canSetField(className, fieldName, fieldDesc, targetObject, value, context)).findFirst().isPresent();
    }

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return providers.stream().filter(provider -> provider.canGetField(className, fieldName, fieldDesc, targetObject, context)).findFirst().isPresent();
    }

    public DelegatingProvider register(Provider provider) {
        this.providers.add(provider);
        return this;
    }
}
