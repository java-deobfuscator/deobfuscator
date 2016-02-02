package com.javadeobfuscator.deobfuscator.transformers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public abstract class Transformer {
    
    protected final Map<String, WrappedClassNode> classes;
    protected final Map<String, WrappedClassNode> classpath;
    protected final Map<MethodNode, List<Entry<WrappedClassNode, MethodNode>>> callers;
    
    public Transformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        this.classes = classes;
        this.classpath = classpath;
        this.callers = null;
    }
    
    public Collection<WrappedClassNode> classNodes() {
        return this.classes.values();
    }
    
    public abstract void transform() throws Throwable;
}
