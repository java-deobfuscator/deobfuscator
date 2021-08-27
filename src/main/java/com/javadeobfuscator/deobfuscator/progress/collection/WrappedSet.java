package com.javadeobfuscator.deobfuscator.progress.collection;

import java.util.AbstractSet;
import java.util.Iterator;

public class WrappedSet<T> extends AbstractSet<T> {
    private final Iterator<T> iterator;
    private final int size;

    public WrappedSet(Iterator<T> iterator, int size) {
        this.iterator = iterator;
        this.size = size;
    }

    @Override
    public Iterator<T> iterator() {
        return iterator;
    }

    @Override
    public int size() {
        return size;
    }
}
