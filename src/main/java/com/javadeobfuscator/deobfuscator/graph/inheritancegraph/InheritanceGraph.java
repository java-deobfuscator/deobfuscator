/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.graph.inheritancegraph;

import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class InheritanceGraph {
    private final Map<Key, InheritanceGraphNode> map;

    public InheritanceGraph(Map<Key, InheritanceGraphNode> map) {
        this.map = ImmutableMap.copyOf(map);
    }

    public final InheritanceGraphNode get(String owner) {
        return map.get(getKey(owner));
    }

    public final Collection<InheritanceGraphNode> values() {
        return map.values();
    }

    static Key getKey(ClassNode classNode) {
        return new Key(classNode.name);
    }

    static Key getKey(String owner) {
        return new Key(owner);
    }

    static class Key {
        final String owner;

        public Key(String owner) {
            this.owner = owner;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return Objects.equals(owner, key.owner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner);
        }
    }
}
