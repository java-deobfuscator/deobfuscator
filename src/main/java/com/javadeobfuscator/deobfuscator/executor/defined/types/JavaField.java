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

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

public class JavaField {
    private final JavaClass clazz;
    private final FieldNode field;

    public JavaField(JavaClass javaClass, FieldNode methodNode) {
        this.clazz = javaClass;
        this.field = methodNode;
    }

    public String getClassName() {
        return getDeclaringClass().getName().replace(".", "/");
    }

    public String getName() {
        return field.name;
    }

    public String getDesc() {
        return field.desc;
    }

    public JavaClass getDeclaringClass() {
        return this.clazz;
    }

    public JavaClass getType() {
        Type type = Type.getType(field.desc);
        Class<?> primitive = PrimitiveUtils.getPrimitiveByName(type.getClassName());
        if (primitive != null) {
            return new JavaClass(type.getClassName(), clazz.getContext());
        } else {
            return new JavaClass(type.getInternalName(), clazz.getContext());
        }
    }

    public void setAccessible(boolean accessible) {
    }

    public int getModifiers() {
        return this.field.access;
    }

    public void setInt(Object obj, int i) {
        this.field.value = i;
    }

    public void set(Object instance, Object obj) {
        this.field.value = obj;
    }

    public Object get(Object obj) {
        return this.field.value;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((clazz == null) ? 0 : clazz.hashCode());
        result = prime * result + ((field == null) ? 0 : field.hashCode());
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
        JavaField other = (JavaField) obj;
        if (clazz == null) {
            if (other.clazz != null)
                return false;
        } else if (!clazz.equals(other.clazz))
            return false;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        return true;
    }
}
