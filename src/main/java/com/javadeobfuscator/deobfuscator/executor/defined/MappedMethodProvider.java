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

package com.javadeobfuscator.deobfuscator.executor.defined;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class MappedMethodProvider extends MethodProvider {
    private Map<String, ClassNode> classpath = new HashMap<>();

    public MappedMethodProvider(Map<String, ClassNode> classpath) {
        this.classpath = classpath;
    }

    public boolean canInvokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        ClassNode classNode = classpath.get(className);
        if(classNode == null)
        	return false;
        MethodNode methodNode = classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElse(null);
        return methodNode != null;
    }

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        ClassNode classNode = classpath.get(className);
        if (classNode != null) {
            MethodNode methodNode = classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElse(null);
            if (methodNode != null) {
                List<JavaValue> argsClone = new ArrayList<>();
                for (JavaValue arg : args) {
                    argsClone.add(arg.copy());
                }
                return MethodExecutor.execute(classNode, methodNode, argsClone, targetObject == null ? new JavaObject(null, "java/lang/Object") : targetObject, context);
            }
            throw new IllegalArgumentException("Could not find method " + methodName + methodDesc);
        }
        throw new IllegalArgumentException("Could not find class " + className);
    }
}
