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

package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.*;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes.*;

public class ConstantFolder extends Transformer {

    @Override
    public boolean transform() throws Throwable {
        AtomicInteger folded = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                int start;
                do {
                    start = folded.get();
                    AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode);

                    Map<AbstractInsnNode, InsnList> replacements = new HashMap<>();

                    for (int i = 0; i < methodNode.instructions.size(); i++) {
                        AbstractInsnNode ain = methodNode.instructions.get(i);
                        opcodes:
                        switch (ain.getOpcode()) {
                            case IADD:
                            case ISUB:
                            case IMUL:
                            case IDIV:
                            case IREM:
                            case ISHL:
                            case ISHR:
                            case IUSHR: {
                                List<Frame> frames = result.getFrames().get(ain);
                                if (frames == null) {
                                    break;
                                }
                                Set<Integer> results = new HashSet<>();
                                for (Frame frame0 : frames) {
                                    MathFrame frame = (MathFrame) frame0;
                                    if (frame.getTargets().size() != 2) {
                                        throw new RuntimeException("weird: " + frame);
                                    }
                                    Frame top = frame.getTargets().get(0);
                                    Frame bottom = frame.getTargets().get(1);
                                    if (top instanceof LdcFrame && bottom instanceof LdcFrame) {
                                        if (ain.getOpcode() == IADD) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() + ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == IMUL) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() * ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == IREM) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() % ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == ISUB) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() - ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == IDIV) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() / ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == ISHL) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() << ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == ISHR) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() >> ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        } else if (ain.getOpcode() == IUSHR) {
                                            results.add(((Number) ((LdcFrame) bottom).getConstant()).intValue() >>> ((Number) ((LdcFrame) top).getConstant()).intValue());
                                        }
                                    } else {
                                        break opcodes;
                                    }
                                }
                                if (results.size() == 1) {
                                    InsnList replacement = new InsnList();
                                    replacement.add(new InsnNode(Opcodes.POP2)); // remove existing args from stack
                                    replacement.add(new LdcInsnNode(results.iterator().next()));
                                    replacements.put(ain, replacement);
                                    folded.getAndIncrement();
                                }
                                break;
                            }
                            case TABLESWITCH: {
                                List<Frame> frames = result.getFrames().get(ain);
                                if (frames == null) {
                                    // wat
                                    break;
                                }
                                Set<Integer> results = new HashSet<>();
                                for (Frame frame0 : frames) {
                                    SwitchFrame frame = (SwitchFrame) frame0;
                                    if (frame.getSwitchTarget() instanceof LdcFrame) {
                                        results.add(((Number) ((LdcFrame) frame.getSwitchTarget()).getConstant()).intValue());
                                    } else {
                                        break opcodes;
                                    }
                                }
                                if (results.size() == 1) {
                                    TableSwitchInsnNode tsin = ((TableSwitchInsnNode) ain);
                                    int cst = results.iterator().next();
                                    LabelNode target = (cst < tsin.min || cst > tsin.max) ? tsin.dflt : tsin.labels.get(cst - tsin.min);
                                    InsnList replacement = new InsnList();
                                    replacement.add(new InsnNode(Opcodes.POP)); // remove existing args from stack
                                    replacement.add(new JumpInsnNode(Opcodes.GOTO, target));
                                    replacements.put(ain, replacement);
                                    folded.getAndIncrement();
                                }
                                break;
                            }
                            case IFGE:
                            case IFGT:
                            case IFLE:
                            case IFLT:
                            case IFNE:
                            case IFEQ: {
                                List<Frame> frames = result.getFrames().get(ain);
                                if (frames == null) {
                                    // wat
                                    break;
                                }
                                Set<Boolean> results = new HashSet<>();
                                for (Frame frame0 : frames) {
                                    JumpFrame frame = (JumpFrame) frame0;
                                    if (frame.getComparators().get(0) instanceof LdcFrame) {
                                        if (ain.getOpcode() == IFGE) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() >= 0);
                                        } else if (ain.getOpcode() == IFGT) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() > 0);
                                        } else if (ain.getOpcode() == IFLE) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() <= 0);
                                        } else if (ain.getOpcode() == IFLT) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() < 0);
                                        } else if (ain.getOpcode() == IFNE) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() != 0);
                                        } else if (ain.getOpcode() == IFEQ) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() == 0);
                                        } else {
                                            throw new RuntimeException();
                                        }
                                    } else {
                                        break opcodes;
                                    }
                                }
                                if (results.size() == 1) {
                                    InsnList replacement = new InsnList();
                                    replacement.add(new InsnNode(Opcodes.POP)); // remove existing args from stack
                                    if (results.iterator().next()) {
                                        replacement.add(new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode) ain).label));
                                    }
                                    replacements.put(ain, replacement);
                                    folded.getAndIncrement();
                                }
                                break;
                            }
                            case IF_ICMPNE:
                            case IF_ICMPEQ: {
                                List<Frame> frames = result.getFrames().get(ain);
                                if (frames == null) {
                                    // wat
                                    break;
                                }
                                Set<Boolean> results = new HashSet<>();
                                for (Frame frame0 : frames) {
                                    JumpFrame frame = (JumpFrame) frame0;
                                    if (frame.getComparators().get(0) instanceof LdcFrame && frame.getComparators().get(1) instanceof LdcFrame) {
                                        if (ain.getOpcode() == IF_ICMPNE) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() != ((Number) ((LdcFrame) frame.getComparators().get(1)).getConstant()).intValue());
                                        } else if (ain.getOpcode() == IF_ICMPEQ) {
                                            results.add(((Number) ((LdcFrame) frame.getComparators().get(0)).getConstant()).intValue() == ((Number) ((LdcFrame) frame.getComparators().get(1)).getConstant()).intValue());
                                        } else {
                                            throw new RuntimeException();
                                        }
                                    } else {
                                        break opcodes;
                                    }
                                }
                                if (results.size() == 1) {
                                    InsnList replacement = new InsnList();
                                    replacement.add(new InsnNode(Opcodes.POP2)); // remove existing args from stack
                                    if (results.iterator().next()) {
                                        replacement.add(new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode) ain).label));
                                    }
                                    replacements.put(ain, replacement);
                                    folded.getAndIncrement();
                                }
                                break;
                            }
                            case DUP: {
                                List<Frame> frames = result.getFrames().get(ain);
                                if (frames == null) {
                                    // wat
                                    break;
                                }
                                Set<Object> results = new HashSet<>();
                                for (Frame frame0 : frames) {
                                    DupFrame frame = (DupFrame) frame0;
                                    if (frame.getTargets().get(0) instanceof LdcFrame) {
                                        results.add(((LdcFrame) frame.getTargets().get(0)).getConstant());
                                    } else {
                                        break opcodes;
                                    }
                                }
                                if (results.size() == 1) {
                                    Object val = results.iterator().next();
                                    InsnList replacement = new InsnList();
                                    if (val == null) {
                                        replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                                    } else {
                                        replacement.add(new LdcInsnNode(val));
                                    }
                                    replacements.put(ain, replacement);
                                    folded.getAndIncrement();
                                }
                                break;
                            }
                            case POP:
                            case POP2: {
                                List<Frame> frames = result.getFrames().get(ain);
                                if (frames == null) {
                                    // wat
                                    break;
                                }
                                Set<AbstractInsnNode> remove = new HashSet<>();
                                for (Frame frame0 : frames) {
                                    PopFrame frame = (PopFrame) frame0;
                                    if (frame.getRemoved().get(0) instanceof LdcFrame && (ain.getOpcode() == POP2 ? frame.getRemoved().get(1) instanceof LdcFrame : true)) {
                                        for (Frame deletedFrame : frame.getRemoved()) {
                                            if (deletedFrame.getChildren().size() > 1) {
                                                // ldc -> ldc -> swap -> pop = we can't even
                                                break opcodes;
                                            }
                                            remove.add(result.getMapping().get(deletedFrame));
                                        }
                                    } else {
                                        break opcodes;
                                    }
                                }
                                for (AbstractInsnNode insn : remove) {
                                    replacements.put(insn, new InsnList());
                                    replacements.put(ain, new InsnList());
                                    folded.getAndIncrement();
                                }
                                break;
                            }
                            default:
                                break;
                        }
                    }

                    replacements.forEach((ain, replacement) -> {
                        methodNode.instructions.insertBefore(ain, replacement);
                        methodNode.instructions.remove(ain);
                    });
                } while (start != folded.get());
            });
        });
        System.out.println("Folded " + folded.get() + " constants");

        return folded.get() > 0;
    }
}
