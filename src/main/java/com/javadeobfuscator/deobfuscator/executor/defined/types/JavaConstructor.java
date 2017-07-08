package com.javadeobfuscator.deobfuscator.executor.defined.types;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

import java.util.ArrayList;
import java.util.List;

public class JavaConstructor {
    private final JavaClass clazz;
    private final String desc;

    public JavaConstructor(JavaClass clazz, String desc) {
        this.clazz = clazz;
        this.desc = desc;
    }

    public JavaClass getClazz() {
        return clazz;
    }

    public JavaClass[] getParameterTypes() {
        List<JavaClass> params = new ArrayList<>();
        for (Type type : Type.getArgumentTypes(desc)) {
            Class<?> primitive = PrimitiveUtils.getPrimitiveByName(type.getClassName());
            if (primitive != null) {
                params.add(new JavaClass(type.getClassName(), clazz.getContext()));
            } else {
                params.add(new JavaClass(type.getInternalName(), clazz.getContext()));
            }
        }
        return params.toArray(new JavaClass[params.size()]);
    }

    public void setAccessible(boolean accessible) {
    }

    public String getClassName() {
        return clazz.getClassNode().name;
    }

    public String getDesc() {
        return desc;
    }
}
