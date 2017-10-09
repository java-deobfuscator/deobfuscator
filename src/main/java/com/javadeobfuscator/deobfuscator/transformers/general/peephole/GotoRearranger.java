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

package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GotoRearranger extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        classNodes().forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                Set<LabelNode> never = new HashSet<>();
                boolean modified;
                outer:
                do {
                    modified = false;
                    Map<LabelNode, Integer> jumpCount = new HashMap<>();
                    for (int i = 0; i < methodNode.instructions.size(); i++) {
                        AbstractInsnNode node = methodNode.instructions.get(i);
                        if (node instanceof JumpInsnNode) {
                            JumpInsnNode cast = (JumpInsnNode) node;
                            jumpCount.merge(cast.label, 1, Integer::sum);
                        } else if (node instanceof TableSwitchInsnNode) {
                            TableSwitchInsnNode cast = (TableSwitchInsnNode) node;
                            jumpCount.merge(cast.dflt, 1, Integer::sum);
                            cast.labels.forEach(l -> jumpCount.merge(l, 1, Integer::sum));
                        } else if (node instanceof LookupSwitchInsnNode) {
                            LookupSwitchInsnNode cast = (LookupSwitchInsnNode) node;
                            jumpCount.merge(cast.dflt, 1, Integer::sum);
                            cast.labels.forEach(l -> jumpCount.merge(l, 1, Integer::sum));
                        }
                    }
                    if (methodNode.tryCatchBlocks != null) {
                        methodNode.tryCatchBlocks.forEach(tryCatchBlockNode -> {
                            jumpCount.put(tryCatchBlockNode.start, 999);
                            jumpCount.put(tryCatchBlockNode.end, 999);
                            jumpCount.put(tryCatchBlockNode.handler, 999);
                        });
                    }
                    never.forEach(n -> jumpCount.put(n, 999));

                    for (int i = 0; i < methodNode.instructions.size(); i++) {
                        AbstractInsnNode node = methodNode.instructions.get(i);
                        if (node.getOpcode() == Opcodes.GOTO) {
                            JumpInsnNode cast = (JumpInsnNode) node;
                            if (jumpCount.get(cast.label) == 1) {
                                AbstractInsnNode next = cast.label;
                                AbstractInsnNode prev = Utils.getPrevious(next);
                                if (prev != null) {
                                    boolean ok = Utils.isTerminating(prev);
                                    while (next != null) {
                                        if (next == node) {
                                            ok = false;
                                        }
                                        if (methodNode.tryCatchBlocks != null) {
                                            for (TryCatchBlockNode tryCatchBlock : methodNode.tryCatchBlocks) {
                                                int start = methodNode.instructions.indexOf(tryCatchBlock.start);
                                                int mid = methodNode.instructions.indexOf(next);
                                                int end = methodNode.instructions.indexOf(tryCatchBlock.end);
                                                if (start <= mid && mid < end) {
                                                    // it's not ok if we're relocating the basic block outside the try-catch block
                                                    int startIndex = methodNode.instructions.indexOf(node);
                                                    if (startIndex < start || startIndex >= end) {
                                                        ok = false;
                                                    }
                                                }
                                            }
                                        }
                                        if (next != cast.label && jumpCount.getOrDefault(next, 0) > 0) {
                                            ok = false;
                                        }
                                        if (!ok) {
                                            break;
                                        }
                                        if (Utils.isTerminating(next)) {
                                            break;
                                        }
                                        next = next.getNext();
                                    }
                                    next = cast.label.getNext();
                                    if (ok) {
                                        List<AbstractInsnNode> remove = new ArrayList<>();
                                        while (next != null) {
                                            remove.add(next);
                                            if (Utils.isTerminating(next)) {
                                                break;
                                            }
                                            next = next.getNext();
                                        }
                                        InsnList list = new InsnList();
                                        remove.forEach(methodNode.instructions::remove);
                                        remove.forEach(list::add);
                                        methodNode.instructions.insert(node, list);
                                        methodNode.instructions.remove(node);
                                        modified = true;
                                        counter.incrementAndGet();
                                        continue outer;
                                    }
                                }
                            }
                        }
                    }
                } while (modified);
            });
        });
        System.out.println("Rearranged " + counter.get() + " goto blocks");
        return counter.get() > 0;
    }
}
