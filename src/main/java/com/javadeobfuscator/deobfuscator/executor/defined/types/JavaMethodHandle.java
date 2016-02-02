package com.javadeobfuscator.deobfuscator.executor.defined.types;

public class JavaMethodHandle {
    public final String clazz;
    public final String name;
    public final String desc;
    public final String type;

    public JavaMethodHandle(String clazz, String name, String desc, String type) {
        this.clazz = clazz;
        this.name = name;
        this.desc = desc;
        this.type = type;
    }
}
