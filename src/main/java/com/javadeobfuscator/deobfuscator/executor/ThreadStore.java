package com.javadeobfuscator.deobfuscator.executor;

import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaThread;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ThreadStore {
    private static Map<Long, JavaThread> threads = Collections.synchronizedMap(new HashMap<>());

    public static void addThread(long threadId, JavaThread thread) {
        threads.put(threadId, thread);
    }

    public static JavaThread retrieveThread(long threadId) {
        JavaThread thread = threads.get(threadId);

        if (thread == null) { // main thread
            thread = new JavaThread(null, Thread.currentThread());
            addThread(threadId, thread);
        }

        return thread;
    }
}
