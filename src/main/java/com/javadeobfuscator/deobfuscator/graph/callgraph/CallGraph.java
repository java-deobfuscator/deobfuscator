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

package com.javadeobfuscator.deobfuscator.graph.callgraph;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CallGraph {
    private final Map<Key, CallGraphNode> map;
    private final Set<CallGraphNode> entryPoints;

    CallGraph(Map<Key, CallGraphNode> callGraph, Set<CallGraphNode> entryPoints) {
        this.map = ImmutableMap.copyOf(callGraph);
        this.entryPoints = ImmutableSet.copyOf(entryPoints);
    }

    public final CallGraphNode get(ClassNode owner, MethodNode method) {
        return map.get(getKey(owner, method));
    }

    public final CallGraphNode get(String owner, String name, String desc) {
        return map.get(getKey(owner, name, desc));
    }

    public final Collection<CallGraphNode> values() {
        return map.values();
    }

    /**
     * An entry point being any node with no incoming edges, not necessarily a Java entry point
     */
    public final Set<CallGraphNode> getEntryPoints() {
        return entryPoints;
    }

    static Key getKey(ClassNode classNode, MethodNode methodNode) {
        return new Key(classNode.name, methodNode.name, methodNode.desc);
    }

    static Key getKey(String owner, String name, String desc) {
        return new Key(owner, name, desc);
    }


    static class Key {
        final String owner;
        final String name;
        final String desc;

        public Key(String owner, String name, String desc) {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return Objects.equals(owner, key.owner) &&
                    Objects.equals(name, key.name) &&
                    Objects.equals(desc, key.desc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(owner, name, desc);
        }
    }
}
