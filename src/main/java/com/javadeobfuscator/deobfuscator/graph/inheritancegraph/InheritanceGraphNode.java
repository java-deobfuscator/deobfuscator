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

import com.google.common.collect.ImmutableSet;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class InheritanceGraphNode {
    private final String owner;

    private Set<InheritanceGraphNode> directChildren = new HashSet<>();
    private Set<InheritanceGraphNode> directParents = new HashSet<>();
    private Set<InheritanceGraphNode> children = new HashSet<>();
    private Set<InheritanceGraphNode> parents = new HashSet<>();

    private Object userData;

    InheritanceGraphNode(InheritanceGraph.Key key) {
        this.owner = key.owner;
    }

    void addChild(InheritanceGraphNode other) {
        directChildren.add(other);
    }

    void addParent(InheritanceGraphNode other) {
        directParents.add(other);
    }

    void computeIndirectRelationships() {
        ArrayDeque<InheritanceGraphNode> toVisit = new ArrayDeque<>();

        toVisit.addAll(directChildren);
        while (!toVisit.isEmpty()) {
            InheritanceGraphNode child = toVisit.pop();
            children.add(child);
            toVisit.addAll(child.directChildren);
        }

        toVisit.addAll(directParents);
        while (!toVisit.isEmpty()) {
            InheritanceGraphNode parent = toVisit.pop();
            parents.add(parent);
            toVisit.addAll(parent.directParents);
        }
    }

    void freeze() {
        directChildren = ImmutableSet.copyOf(directChildren);
        directParents = ImmutableSet.copyOf(directParents);
    }

    public String getOwner() {
        return owner;
    }

    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }

    public Set<InheritanceGraphNode> getDirectChildren() {
        return directChildren;
    }

    public Set<InheritanceGraphNode> getDirectParents() {
        return directParents;
    }

    public Set<InheritanceGraphNode> getChildren() {
        return children;
    }

    public Set<InheritanceGraphNode> getParents() {
        return parents;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InheritanceGraphNode that = (InheritanceGraphNode) o;
        return Objects.equals(owner, that.owner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner);
    }
}
