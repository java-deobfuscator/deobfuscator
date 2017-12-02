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

import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CallGraphNode {
    private final String owner;
    private final String name;
    private final String desc;

    private Set<CallGraphNode> xrefsTo = new HashSet<>();
    private Set<CallGraphNode> xrefsFrom = new HashSet<>();

    private Object userData;

    CallGraphNode(CallGraph.Key key) {
        this.owner = key.owner;
        this.name = key.name;
        this.desc = key.desc;
    }


    void addXrefTo(CallGraphNode other) {
        xrefsTo.add(other);
    }

    void addXrefFrom(CallGraphNode other) {
        xrefsFrom.add(other);
    }

    void freeze() {
        xrefsTo = ImmutableSet.copyOf(xrefsTo);
        xrefsFrom = ImmutableSet.copyOf(xrefsFrom);
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public Set<CallGraphNode> getXrefsTo() {
        return xrefsTo;
    }

    public Set<CallGraphNode> getXrefsFrom() {
        return xrefsFrom;
    }

    public Object getUserData() {
        return userData;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallGraphNode that = (CallGraphNode) o;
        return Objects.equals(owner, that.owner) &&
                Objects.equals(name, that.name) &&
                Objects.equals(desc, that.desc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, name, desc);
    }
}
