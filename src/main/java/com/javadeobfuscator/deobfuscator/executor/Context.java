package com.javadeobfuscator.deobfuscator.executor;

import com.javadeobfuscator.deobfuscator.asm.ConstantPool;
import com.javadeobfuscator.deobfuscator.executor.providers.Provider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class Context { //FIXME clinit classes
    private final List<StackTraceElement> stackTrace = new ArrayList<>();

    public Provider provider;
    public Map<String, ClassNode> dictionary;
    public Map<ClassNode, ConstantPool> constantPools;
    public Map<AbstractInsnNode, BiFunction<List<JavaValue>, Context, JavaValue>> customMethodFunc = new HashMap<>();
    public ThreadStore threadStore = new ThreadStore();
    public Monitor monitor = new Monitor();

    public Set<String> clinit = Collections.synchronizedSet(new HashSet<>());

    public File file;

    public Context(Provider provider) {
        this.provider = provider;
    }

    public Context copyForNewThread() {
        Context threadContext = new Context(provider);
        threadContext.dictionary = dictionary;
        threadContext.constantPools = constantPools;
        threadContext.customMethodFunc = customMethodFunc;
        threadContext.threadStore = threadStore;
        threadContext.monitor = monitor;
        threadContext.clinit = clinit;
        threadContext.file = file;
        threadContext.breakpointsBefore = breakpointsBefore;
        threadContext.breakpointsAfter = breakpointsAfter;
        return threadContext;
    }

    public StackTraceElement at(int index) {
        return stackTrace.get(index);
    }

    public StackTraceElement pop() {
        return stackTrace.remove(0);
    }

    public void push(String clazz, String method, int constantPoolSize) {
        clazz = clazz.replace('/', '.');
        stackTrace.add(0, new StackTraceElement(clazz, method, "", constantPoolSize));
    }
    
    public void push(String clazz, String method, String sourceFile, int constantPoolSize) {
        clazz = clazz.replace('/', '.');
        stackTrace.add(0, new StackTraceElement(clazz, method, sourceFile, constantPoolSize));
    }

    public int size() {
        return stackTrace.size();
    }

    public StackTraceElement[] getStackTrace() {
        StackTraceElement[] orig = new StackTraceElement[size()];
        for (int i = 0; i < size(); i++) {
            StackTraceElement e = at(i);
            orig[i] = new StackTraceElement(e.getClassName(), e.getMethodName(), e.getFileName(), -1);
        }
        return orig;
    }

    public void clearStackTrace() {
        stackTrace.clear();
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
        private final Object throwable;
        private final List<JavaValue> stack;
        private final List<JavaValue> locals;

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
