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

package com.javadeobfuscator.deobfuscator.executor.defined.types;

import java.util.ArrayList;
import java.util.List;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

public class JavaMethod {
    private final JavaClass clazz;
    private final MethodNode method;

    public JavaMethod(JavaClass javaClass, MethodNode methodNode) {
        this.clazz = javaClass;
        this.method = methodNode;
    }

    public String getName() {
        return method.name;
    }

    public JavaClass getReturnType() {
        Class<?> primitive = PrimitiveUtils.getPrimitiveByName(Type.getReturnType(method.desc).getClassName());
        if (primitive != null) {
            return new JavaClass(Type.getReturnType(method.desc).getClassName(), clazz.getContext());
        }
        return new JavaClass(Type.getReturnType(method.desc).getInternalName(), clazz.getContext());
    }

    public JavaClass getDeclaringClass() {
        return this.clazz;
    }

    public JavaClass[] getParameterTypes() {
        List<JavaClass> params = new ArrayList<>();
        for (Type type : Type.getArgumentTypes(method.desc)) {
            Class<?> primitive = PrimitiveUtils.getPrimitiveByName(type.getClassName());
            if (primitive != null) {
                params.add(new JavaClass(type.getClassName(), clazz.getContext()));
            } else {
                params.add(new JavaClass(type.getInternalName(), clazz.getContext()));
            }
        }
        return params.toArray(new JavaClass[params.size()]);
    }
    
    public MethodNode getMethodNode() {
        return this.method;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
        result = prime * result + ((method == null) ? 0 : method.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JavaMethod other = (JavaMethod) obj;
        if (clazz == null) {
            if (other.clazz != null)
                return false;
        } else if (!clazz.equals(other.clazz))
            return false;
        if (method == null) {
            if (other.method != null)
                return false;
        } else if (!method.equals(other.method))
            return false;
        return true;
    }

    public void setAccessible(boolean accessible) {
    }
}
