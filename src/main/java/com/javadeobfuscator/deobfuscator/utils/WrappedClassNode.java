package com.javadeobfuscator.deobfuscator.utils;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;

public class WrappedClassNode {
    public ClassNode classNode;
    public int constantPoolSize;
    
    public WrappedClassNode(ClassNode classNode, int constantPoolSize) {
        this.classNode = classNode;
        this.constantPoolSize = constantPoolSize;
    }
    
    public ClassNode getClassNode() {
        return this.classNode;
    }
}
