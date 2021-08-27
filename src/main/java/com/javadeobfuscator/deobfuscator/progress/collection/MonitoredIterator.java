package com.javadeobfuscator.deobfuscator.progress.collection;

import java.util.Iterator;

public class MonitoredIterator<T> implements Iterator<T> {
    private final Runnable monitor;
    private final Iterator<T> wrapped;

    public MonitoredIterator(Runnable monitor, Iterator<T> wrapped) {
        this.monitor = monitor;
        this.wrapped = wrapped;
    }

    @Override
    public boolean hasNext() {
        return wrapped.hasNext();
    }

    @Override
    public T next() {
        T t = wrapped.next();
        monitor.run();
        return t;
    }
}
