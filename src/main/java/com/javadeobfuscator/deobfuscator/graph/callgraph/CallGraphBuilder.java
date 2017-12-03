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

import com.javadeobfuscator.deobfuscator.graph.inheritancegraph.InheritanceGraph;
import com.javadeobfuscator.deobfuscator.graph.inheritancegraph.InheritanceGraphNode;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

import static com.javadeobfuscator.deobfuscator.graph.GraphHelper.validateUniqueClasses;

public class CallGraphBuilder {
    private List<ClassNode> nodes = new ArrayList<>();
    private InheritanceGraph inheritanceGraph;

    private CallGraphBuilder() {
    }

    public static CallGraphBuilder newBuilder() {
        return new CallGraphBuilder();
    }

    public final CallGraphBuilder withNode(ClassNode node) {
        nodes.add(node);
        return this;
    }

    public final CallGraphBuilder withNodes(Collection<ClassNode> nodes) {
        this.nodes.addAll(nodes);
        return this;
    }

    public final CallGraphBuilder withInheritanceGraph(InheritanceGraph inheritanceGraph) {
        this.inheritanceGraph = inheritanceGraph;
        return this;
    }

    public final CallGraph build() {
        Map<String, ClassNode> classMap = validateUniqueClasses(nodes);

        Map<CallGraph.Key, CallGraphNode> callGraph = new HashMap<>();

        // First, compute the call graph
        for (ClassNode classNode : nodes) {
            for (MethodNode methodNode : classNode.methods) {
                CallGraphNode thisNode = callGraph.computeIfAbsent(CallGraph.getKey(classNode, methodNode), CallGraphNode::new);

                if (methodNode.instructions == null) {
                    continue;
                }

                for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
                    AbstractInsnNode insn = it.next();

                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
                        CallGraph.Key otherKey = CallGraph.getKey(methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                        CallGraphNode otherNode = callGraph.computeIfAbsent(otherKey, CallGraphNode::new);

                        thisNode.addXrefTo(otherNode);
                        otherNode.addXrefFrom(thisNode);

                        // Handle inheritance
                        if (inheritanceGraph != null) {
                            InheritanceGraphNode inheritanceGraphNode = inheritanceGraph.get(methodInsnNode.owner);
                            if (inheritanceGraphNode != null) {
                                for (InheritanceGraphNode child : inheritanceGraphNode.getChildren()) {
                                    ClassNode childNode = classMap.get(child.getOwner());
                                    if (childNode == null) {
                                        continue;
                                    }
                                    MethodNode method = TransformerHelper.findMethodNode(childNode, methodInsnNode.name, methodInsnNode.desc);
                                    if (method == null) {
                                        continue;
                                    }
                                    otherKey = CallGraph.getKey(child.getOwner(), methodInsnNode.name, methodInsnNode.desc);
                                    otherNode = callGraph.computeIfAbsent(otherKey, CallGraphNode::new);

                                    thisNode.addXrefTo(otherNode);
                                    otherNode.addXrefFrom(thisNode);
                                }

                                for (InheritanceGraphNode parent : inheritanceGraphNode.getParents()) {
                                    ClassNode parentNode = classMap.get(parent.getOwner());
                                    if (parentNode == null) {
                                        continue;
                                    }
                                    MethodNode method = TransformerHelper.findMethodNode(parentNode, methodInsnNode.name, methodInsnNode.desc);
                                    if (method == null) {
                                        continue;
                                    }
                                    otherKey = CallGraph.getKey(parent.getOwner(), methodInsnNode.name, methodInsnNode.desc);
                                    otherNode = callGraph.computeIfAbsent(otherKey, CallGraphNode::new);

                                    thisNode.addXrefTo(otherNode);
                                    otherNode.addXrefFrom(thisNode);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Freeze it!
        callGraph.values().forEach(CallGraphNode::freeze);

        // Next, compute the entry points
        Set<CallGraphNode> entryPoints = new HashSet<>();
        for (CallGraphNode node : callGraph.values()) {
            if (!classMap.containsKey(node.getOwner())) {
                continue;
            }

            if (node.getXrefsFrom().isEmpty()) {
                entryPoints.add(node);
            }
        }

        return new CallGraph(callGraph, entryPoints);
    }
}
