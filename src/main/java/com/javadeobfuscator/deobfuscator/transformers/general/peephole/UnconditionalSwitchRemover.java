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
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class UnconditionalSwitchRemover extends Transformer<TransformerConfig> {
	
    @Override
    public boolean transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        classNodes().forEach(classNode -> {
            MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                Map<LabelNode, LabelNode> mapping = new HashMap<>();
                InsnList insns = clinit.instructions;
                for (int i = 0; i < insns.size(); i++) {
                    AbstractInsnNode node = insns.get(i);
                    if (node instanceof LabelNode) {
                        mapping.put((LabelNode) node, (LabelNode) node);
                    }
                }
                for (int i = 0; i < insns.size(); i++) {
                    AbstractInsnNode node = insns.get(i);
                    int prev = Utils.iconstToInt(node.getOpcode());
                    if (prev == Integer.MIN_VALUE) {
                        if (node.getOpcode() == Opcodes.BIPUSH || node.getOpcode() == Opcodes.SIPUSH) {
                            prev = ((IntInsnNode) node).operand;
                        }
                    }
                    if (prev == Integer.MIN_VALUE) {
                        if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Integer) {
                            prev = (Integer) ((LdcInsnNode) node).cst;
                        }
                    }
                    if (prev != Integer.MIN_VALUE) {
                        AbstractInsnNode next = Utils.getNextFollowGoto(node);
                        if (next instanceof TableSwitchInsnNode) {
                            TableSwitchInsnNode cast = (TableSwitchInsnNode) next;
                            int index = prev - cast.min;
                            LabelNode go = null;
                            if (index >= 0 && index < cast.labels.size()) {
                                go = cast.labels.get(index);
                            } else {
                                go = cast.dflt;
                            }
                            InsnList replace = new InsnList();
                            replace.add(new JumpInsnNode(Opcodes.GOTO, go));
                            insns.insertBefore(node, replace);
                            insns.remove(node);
                            counter.incrementAndGet();
                        }
                    }
                }
            }
        });
        classNodes().forEach(classNode -> {
            MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                Map<LabelNode, LabelNode> mapping = new HashMap<>();
                InsnList insns = clinit.instructions;
                for (int i = 0; i < insns.size(); i++) {
                    AbstractInsnNode node = insns.get(i);
                    if (node instanceof LabelNode) {
                        mapping.put((LabelNode) node, (LabelNode) node);
                    }
                }
                for (int i = 0; i < insns.size(); i++) {
                    AbstractInsnNode node = insns.get(i);
                    int prev = Utils.iconstToInt(node.getOpcode());
                    if (prev == Integer.MIN_VALUE) {
                        if (node.getOpcode() == Opcodes.BIPUSH || node.getOpcode() == Opcodes.SIPUSH) {
                            prev = ((IntInsnNode) node).operand;
                        }
                    }
                    if (prev == Integer.MIN_VALUE) {
                        if (node instanceof LdcInsnNode && ((LdcInsnNode) node).cst instanceof Integer) {
                            prev = (Integer) ((LdcInsnNode) node).cst;
                        }
                    }
                    if (prev != Integer.MIN_VALUE) {
                        AbstractInsnNode next = Utils.getNextFollowGoto(node);
                        if (next.getOpcode() == Opcodes.SWAP) {
                            next = Utils.getNextFollowGoto(next);
                            if (next.getOpcode() == Opcodes.INVOKESTATIC) {
                                AbstractInsnNode methodNode = next;
                                next = Utils.getNextFollowGoto(next);
                                if (next.getOpcode() == Opcodes.SWAP) {
                                    next = Utils.getNextFollowGoto(next);
                                    if (next instanceof TableSwitchInsnNode) {
                                        TableSwitchInsnNode cast = (TableSwitchInsnNode) next;
                                        int index = prev - cast.min;
                                        LabelNode go = null;
                                        if (index >= 0 && index < cast.labels.size()) {
                                            go = cast.labels.get(index);
                                        } else {
                                            go = cast.dflt;
                                        }
                                        InsnList replace = new InsnList();
                                        replace.add(methodNode.clone(null));
                                        replace.add(new JumpInsnNode(Opcodes.GOTO, go));
                                        insns.insertBefore(node, replace);
                                        insns.remove(node);
                                        counter.incrementAndGet();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        System.out.println("Removed " + counter.get() + " unconditional switches");
        return counter.get() > 0;
    }
}
