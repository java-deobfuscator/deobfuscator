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

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Type;

public class DelegatingProvider implements Provider {

    private List<Provider> providers = new ArrayList<>();

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        for (Provider provider : providers) {
            if (provider.canInvokeMethod(className, methodName, methodDesc, targetObject, args, context)) {
                return provider.invokeMethod(className, methodName, methodDesc, targetObject, args, context);
            }
        }
        throw new ExecutionException("invokeMethod failed");
    }

    @Override
    public boolean instanceOf(JavaValue target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckInstanceOf(target, type, context)).findFirst().get().instanceOf(target, type, context);
    }

    @Override
    public boolean checkcast(JavaValue target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckcast(target, type, context)).findFirst().get().checkcast(target, type, context);
    }

    @Override
    public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
        return providers.stream().filter(provider -> provider.canCheckEquality(first, second, context)).findFirst().get().checkEquality(first, second, context);
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        for (Provider provider : providers) {
            if (provider.canSetField(className, fieldName, fieldDesc, targetObject, value, context)) {
                provider.setField(className, fieldName, fieldDesc, targetObject, value, context);
                return;
            }
        }
        throw new ExecutionException("setField failed");
    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        for (Provider provider : providers) {
            if (provider.canGetField(className, fieldName, fieldDesc, targetObject, context)) {
                return provider.getField(className, fieldName, fieldDesc, targetObject, context);
            }
        }
        throw new ExecutionException("getField failed");
    }

    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        for (Provider provider : providers) {
            if (provider.canInvokeMethod(className, methodName, methodDesc, targetObject, args, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckInstanceOf(target, type, context)).findFirst().isPresent();
    }

    @Override
    public boolean canCheckcast(JavaValue target, Type type, Context context) {
        return providers.stream().filter(provider -> provider.canCheckcast(target, type, context)).findFirst().isPresent();
    }

    @Override
    public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
        return providers.stream().filter(provider -> provider.canCheckEquality(first, second, context)).findFirst().isPresent();
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        for (Provider provider : providers) {
            if (provider.canSetField(className, fieldName, fieldDesc, targetObject, value, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        for (Provider provider : providers) {
            if (provider.canGetField(className, fieldName, fieldDesc, targetObject, context)) {
                return true;
            }
        }
        return false;
    }

    public DelegatingProvider register(Provider provider) {
        this.providers.add(provider);
        return this;
    }
}
