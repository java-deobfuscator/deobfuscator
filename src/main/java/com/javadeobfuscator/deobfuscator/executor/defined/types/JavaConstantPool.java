package com.javadeobfuscator.deobfuscator.executor.defined.types;

public class JavaConstantPool {
    private final JavaClass clazz;

    public JavaConstantPool(JavaClass javaClass) {
        this.clazz = javaClass;
    }
    
    public int getSize() {
        return clazz.getWrappedClassNode().constantPoolSize;
    }
}
