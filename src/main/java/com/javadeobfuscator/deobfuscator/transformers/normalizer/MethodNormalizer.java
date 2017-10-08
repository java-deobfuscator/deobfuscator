/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@TransformerConfig.ConfigOptions(configClass = MethodNormalizer.Config.class)
public class MethodNormalizer extends AbstractNormalizer<MethodNormalizer.Config> {

    @Override
    public void remap(CustomRemapper remapper) {

        AtomicInteger id = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
            Set<String> allClasses = new HashSet<>();
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTry = new LinkedList<>();
            toTry.add(tree.thisClass);
            while (!toTry.isEmpty()) {
                String t = toTry.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassNode cn = this.getDeobfuscator().assureLoaded(t);
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.parentClasses);
                    toTry.addAll(ct.parentClasses);
                    allClasses.addAll(ct.subClasses);
                    toTry.addAll(ct.subClasses);
                }
            }
            allClasses.remove(tree.thisClass);

            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                if (methodNode.name.startsWith("<"))
                    continue;
                if (methodNode.name.equals("main"))
                    continue;
                final Map<Map.Entry<ClassNode, MethodNode>, Boolean> allMethodNodes = new HashMap<>();
                final Type methodType = Type.getReturnType(methodNode.desc);
                final AtomicBoolean isLibrary = new AtomicBoolean(false);
                if (methodType.getSort() != Type.OBJECT && methodType.getSort() != Type.ARRAY) {
                    if (methodType.getSort() == Type.METHOD) {
                        throw new IllegalArgumentException("Did not expect method");
                    }
                    allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
                        boolean foundSimilar = false;
                        boolean equals = false;
                        MethodNode equalsMethod = null;
                        for (MethodNode method : node.methods) {
                            Type thisType = Type.getMethodType(methodNode.desc);
                            Type otherType = Type.getMethodType(method.desc);
                            if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
                                foundSimilar = true;
                                if (thisType.getReturnType().getSort() == otherType.getReturnType().getSort()) {
                                    equals = true;
                                    equalsMethod = method;
                                }
                            }
                        }
                        if (foundSimilar) {
                            if (equals) {
                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
                            }
                        } else {
                            allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                        }
                    });
                } else if (methodType.getSort() == Type.ARRAY) {
                    Type elementType = methodType.getElementType();
                    if (elementType.getSort() == Type.OBJECT) {
                        String parent = elementType.getInternalName();
                        allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
                            boolean foundSimilar = false;
                            boolean equals = false;
                            MethodNode equalsMethod = null;
                            for (MethodNode method : node.methods) {
                                Type thisType = Type.getMethodType(methodNode.desc);
                                Type otherType = Type.getMethodType(method.desc);
                                if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
                                    if (otherType.getReturnType().getSort() == Type.OBJECT) {
                                        foundSimilar = true;
                                        String child = otherType.getReturnType().getInternalName();
                                        this.getDeobfuscator().assureLoaded(parent);
                                        this.getDeobfuscator().assureLoaded(child);
                                        if (this.getDeobfuscator().isSubclass(parent, child) || this.getDeobfuscator().isSubclass(child, parent)) {
                                            equals = true;
                                            equalsMethod = method;
                                        }
                                    }
                                }
                            }
                            if (foundSimilar) {
                                if (equals) {
                                    allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
                                }
                            } else {
                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                            }
                        });
                    } else {
                        allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
                            boolean foundSimilar = false;
                            boolean equals = false;
                            MethodNode equalsMethod = null;
                            for (MethodNode method : node.methods) {
                                Type thisType = Type.getMethodType(methodNode.desc);
                                Type otherType = Type.getMethodType(method.desc);
                                if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
                                    foundSimilar = true;
                                    if (thisType.getReturnType().getSort() == otherType.getReturnType().getSort()) {
                                        equals = true;
                                        equalsMethod = method;
                                    }
                                }
                            }
                            if (foundSimilar) {
                                if (equals) {
                                    allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
                                }
                            } else {
                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                            }
                        });
                    }
                } else if (methodType.getSort() == Type.OBJECT) {
                    String parent = methodType.getInternalName();
                    allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
                        boolean foundSimilar = false;
                        boolean equals = false;
                        MethodNode equalsMethod = null;
                        for (MethodNode method : node.methods) {
                            Type thisType = Type.getMethodType(methodNode.desc);
                            Type otherType = Type.getMethodType(method.desc);
                            if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
                                if (otherType.getReturnType().getSort() == Type.OBJECT) {
                                    foundSimilar = true;
                                    String child = otherType.getReturnType().getInternalName();
                                    this.getDeobfuscator().assureLoaded(parent);
                                    this.getDeobfuscator().assureLoaded(child);
                                    if (this.getDeobfuscator().isSubclass(parent, child) || this.getDeobfuscator().isSubclass(child, parent)) {
                                        equals = true;
                                        equalsMethod = method;
                                    }
                                }
                            }
                        }
                        if (foundSimilar) {
                            if (equals) {
                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
                            } else {
                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                            }
                        } else {
                            allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                        }
                    });
                }

                allMethodNodes.forEach((key, value) -> {
                    if (getDeobfuscator().isLibrary(key.getKey()) && value) {
                        isLibrary.set(true);
                    }
                });

                if (!isLibrary.get()) {
                    if (!remapper.methodMappingExists(classNode.name, methodNode.name, methodNode.desc)) {
                        while (true) {
                            String name = "Method" + id.getAndIncrement();
                            if (remapper.mapMethodName(classNode.name, methodNode.name, methodNode.desc, name, false)) {
                                allMethodNodes.keySet().forEach(ent -> {
                                    remapper.mapMethodName(ent.getKey().name, ent.getValue().name, ent.getValue().desc, name, true);
                                });
                                break;
                            }
                        }
                    }
                }
            }
        });
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(MethodNormalizer.class);
        }
    }
}
