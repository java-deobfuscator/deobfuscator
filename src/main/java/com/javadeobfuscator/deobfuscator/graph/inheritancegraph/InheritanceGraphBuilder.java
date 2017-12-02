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

import org.objectweb.asm.tree.ClassNode;

import java.util.*;

import static com.javadeobfuscator.deobfuscator.graph.GraphHelper.validateUniqueClasses;

public class InheritanceGraphBuilder {
    private List<ClassNode> nodes = new ArrayList<>();

    private InheritanceGraphBuilder() {
    }

    public static InheritanceGraphBuilder newBuilder() {
        return new InheritanceGraphBuilder();
    }

    public final InheritanceGraphBuilder withNode(ClassNode node) {
        nodes.add(node);
        return this;
    }

    public final InheritanceGraphBuilder withNodes(Collection<ClassNode> nodes) {
        this.nodes.addAll(nodes);
        return this;
    }

    public final InheritanceGraph build() {
        validateUniqueClasses(nodes);

        Map<InheritanceGraph.Key, InheritanceGraphNode> inheritanceGraph = new HashMap<>();

        // First, compute the inheritance graph
        for (ClassNode classNode : nodes) {
            InheritanceGraphNode thisNode = inheritanceGraph.computeIfAbsent(InheritanceGraph.getKey(classNode), InheritanceGraphNode::new);
            {
                if (classNode.superName != null) { // In case we reached java/lang/Object
                    InheritanceGraphNode superClass = inheritanceGraph.computeIfAbsent(InheritanceGraph.getKey(classNode.superName), InheritanceGraphNode::new);

                    thisNode.addParent(superClass);
                    superClass.addChild(thisNode);
                }
            }
            {
                if (classNode.interfaces != null) {
                    for (String intf : classNode.interfaces) {
                        InheritanceGraphNode intfClass = inheritanceGraph.computeIfAbsent(InheritanceGraph.getKey(intf), InheritanceGraphNode::new);

                        thisNode.addParent(intfClass);
                        intfClass.addChild(thisNode);
                    }
                }
            }
        }

        // Now, aggregate results to form an "all children" and "all parents" view
        inheritanceGraph.values().forEach(InheritanceGraphNode::computeIndirectRelationships);

        // Freeze it!
        inheritanceGraph.values().forEach(InheritanceGraphNode::freeze);

        return new InheritanceGraph(inheritanceGraph);
    }
}
