package com.javadeobfuscator.deobfuscator.progress.collection;

import java.util.AbstractMap;
import java.util.Set;

public class WrappedMap<K, V> extends AbstractMap<K, V> {
    private final Set<Entry<K, V>> entrySet;

    public WrappedMap(Set<Entry<K, V>> entrySet) {
        this.entrySet = entrySet;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }
}
