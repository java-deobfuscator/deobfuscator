package com.javadeobfuscator.deobfuscator.executor.defined.types;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.FieldNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

public class JavaField {
    private final JavaClass clazz;
    private final FieldNode field;

    public JavaField(JavaClass javaClass, FieldNode methodNode) {
        this.clazz = javaClass;
        this.field = methodNode;
    }

    public String getName() {
        return field.name;
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
