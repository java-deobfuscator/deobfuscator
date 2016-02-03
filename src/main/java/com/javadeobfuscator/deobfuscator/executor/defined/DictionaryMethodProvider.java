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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class DictionaryMethodProvider extends MethodProvider {
    private final Map<String, WrappedClassNode> classes;
    
    public DictionaryMethodProvider(Map<String, WrappedClassNode> classes) {
        this.classes = classes;
    }
    
    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        WrappedClassNode wrappedClassNode = classes.get(className);
        if (wrappedClassNode != null) {
            ClassNode classNode = wrappedClassNode.classNode;
            MethodNode methodNode = classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElseGet(null);
            if (methodNode != null) {
                List<StackObject> argsClone = new ArrayList<>();
                for (StackObject arg : args) {
                    argsClone.add(arg.copy());
                }
                return MethodExecutor.execute(wrappedClassNode, methodNode, argsClone, targetObject == null ? null : targetObject.value, context);
            }
        }
        throw new IllegalStateException();
    }

    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        WrappedClassNode wrappedClassNode = classes.get(className);
        if (wrappedClassNode != null) {
            MethodNode methodNode = wrappedClassNode.classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElseGet(null);
            return methodNode != null;
        }
        return false;
    }
}
