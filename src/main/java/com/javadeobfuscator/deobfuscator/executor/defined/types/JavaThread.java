package com.javadeobfuscator.deobfuscator.executor.defined.types;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collections;

public class JavaThread {
    private Thread thread;
    private Context context;
    private JavaObject instance;

    private boolean started;

    public JavaThread(Context context, JavaObject instance) {
        this.context = context;
        this.instance = instance;
    }

    public JavaThread(Context context, Thread thread) {
        this.context = context;
        this.thread = thread;
    }

    public Thread getThread() {
        return thread;
    }

    public Context getContext() {
        return context;
    }

    public JavaObject getInstance() {
        return instance;
    }

    public void start() {
        if (!started) {
            started = true;
            ClassNode classNode = context.dictionary.get(instance.type());
            if (classNode != null) {
                MethodNode method = classNode.methods.stream().filter(mn -> mn.name.equals("run") && mn.desc.equals("()V")).findFirst().orElse(null);
                if (method != null) {
                    Context threadContext = context.copyForNewThread();

                    thread = new Thread(() -> MethodExecutor.execute(classNode, method, Collections.emptyList(), instance, threadContext));
                    context.threadStore.addThread(thread.getId(), this);
                    this.context = threadContext;

                    thread.start();
                    return;
                }
                throw new IllegalArgumentException("Could not find run() method on " + classNode.name);
            }
            throw new IllegalArgumentException("Could not find class " + instance.type());
        } else {
            throw new IllegalStateException("Thread already started");
        }
    }
}
