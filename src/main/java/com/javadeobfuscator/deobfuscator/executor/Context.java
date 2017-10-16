package com.javadeobfuscator.deobfuscator.executor;

import com.javadeobfuscator.deobfuscator.asm.ConstantPool;
import com.javadeobfuscator.deobfuscator.executor.providers.Provider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

public class Context { //FIXME clinit classes
    private List<StackTraceElement> context = new ArrayList<>();

    public Provider provider;
    public Map<String, ClassNode> dictionary;
    public Map<ClassNode, ConstantPool> constantPools;

    public Set<String> clinit = new HashSet<>();

    public File file;

    public Context(Provider provider) {
        this.provider = provider;
    }

    public StackTraceElement at(int index) {
        return context.get(index);
    }

    public StackTraceElement pop() {
        return context.remove(0);
    }

    public void push(String clazz, String method, int constantPoolSize) {
        clazz = clazz.replace('/', '.');
        context.add(0, new StackTraceElement(clazz, method, "", constantPoolSize));
    }
    
    public void push(String clazz, String method, String sourceFile, int constantPoolSize) {
        clazz = clazz.replace('/', '.');
        context.add(0, new StackTraceElement(clazz, method, sourceFile, constantPoolSize));
    }

    public int size() {
        return context.size();
    }

    public StackTraceElement[] getStackTrace() {
        StackTraceElement[] orig = new StackTraceElement[size()];
        for (int i = 0; i < size(); i++) {
            StackTraceElement e = at(i);
            orig[i] = new StackTraceElement(e.getClassName(), e.getMethodName(), e.getFileName(), -1);
        }
        return orig;
    }

    private Map<AbstractInsnNode, Consumer<BreakpointInfo>> breakpointsBefore = new HashMap<>();
    private Map<AbstractInsnNode, Consumer<BreakpointInfo>> breakpointsAfter = new HashMap<>();

    public void doBreakpoint(AbstractInsnNode now, boolean before, List<JavaValue> stack, List<JavaValue> locals, Object tothrow) {
        if (before && breakpointsBefore.containsKey(now)) {
            breakpointsBefore.get(now).accept(new BreakpointInfo(tothrow, stack, locals));
        } else if (!before && breakpointsAfter.containsKey(now)) {
            breakpointsAfter.get(now).accept(new BreakpointInfo(tothrow, stack, locals));
        }
    }

    public void breakpoint(AbstractInsnNode now, Consumer<BreakpointInfo> before, Consumer<BreakpointInfo> after) {
        if (before != null)
            breakpointsBefore.put(now, before);
        if (after != null)
            breakpointsAfter.put(now, after);
    }

    public static class BreakpointInfo {
        private Object throwable;
        private List<JavaValue> stack;
        private List<JavaValue> locals;

        public BreakpointInfo(Object throwable, List<JavaValue> stack, List<JavaValue> locals) {
            this.throwable = throwable;
            this.stack = stack;
            this.locals = locals;
        }

        public Object getThrowable() {
            return throwable;
        }

        public List<JavaValue> getStack() {
            return stack;
        }

        public List<JavaValue> getLocals() {
            return locals;
        }
    }
}
