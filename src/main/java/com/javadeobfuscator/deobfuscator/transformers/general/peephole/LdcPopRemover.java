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

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class LdcPopRemover extends Transformer {
    public LdcPopRemover(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    private boolean willTakeTwoSlots(int opcode) {
        switch (opcode) {
            case Opcodes.DLOAD:
            case Opcodes.LLOAD:
            case Opcodes.DCONST_0:
            case Opcodes.DCONST_1:
            case Opcodes.LCONST_0:
            case Opcodes.LCONST_1:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            classNode.methods.stream().filter(Utils::notAbstractOrNative).forEach(methodNode -> {
//                AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode);
//                Map<AbstractInsnNode, List<Frame>> frames = result.getFrames();
//                Map<Frame, AbstractInsnNode> reverse = result.getMapping();
//                for (Map.Entry<AbstractInsnNode, List<Frame>> entry : frames.entrySet()) {
//                    boolean takesTwo = willTakeTwoSlots(entry.getKey().getOpcode());
//                    if (entry.getValue() != null) {
//                        boolean allValid = true;
//                        for (Frame frame : entry.getValue()) {
//                            if (!(frame instanceof LdcFrame)) {
//                                allValid = false;
//                                break;
//                            }
//                            for (Frame child : frame.getChildren()) {
//                                if (child instanceof PopFrame) {
//                                    if (!takesTwo && child.getOpcode() == Opcodes.POP2) {
//                                        allValid = false;
//                                        break;
//                                    } else if (takesTwo && child.getOpcode() != Opcodes.POP2) {
//                                        allValid = false;
//                                        break;
//                                    }
//                                } else {
//                                    allValid = false;
//                                    break;
//                                }
//                            }
//                        }
//                        if (allValid) {
//                            for (Frame frame : entry.getValue()) {
//                                System.out.println(classNode.name + " " + methodNode.name + methodNode.desc);
//                                System.out.println(frame);
//                                System.out.println(frame.getChildren());
//                                for (Frame child : frame.getChildren()) {
//                                    methodNode.instructions.remove(reverse.get(child));
//                                }
//                                System.out.println();
//                            }
//                            methodNode.instructions.remove(entry.getKey());
//                            counter.incrementAndGet();
//                        }
//                    }
//                }
                boolean modified = false;
                do {
                    modified = false;
                    for (int i = 0; i < methodNode.instructions.size(); i++) {
                        AbstractInsnNode node = methodNode.instructions.get(i);
                        if (Utils.willPushToStack(node.getOpcode())) {
                            AbstractInsnNode next = node.getNext();
                            if (next.getOpcode() == Opcodes.POP) {
                                methodNode.instructions.remove(next);
                                methodNode.instructions.remove(node);
                                counter.incrementAndGet();
                                modified = true;
                            }else if(node.getPrevious() != null
                            	&& Utils.willPushToStack(node.getPrevious().getOpcode())
                            	&& next.getOpcode() == Opcodes.POP2)
                            {
                            	methodNode.instructions.remove(next);
                            	methodNode.instructions.remove(node.getPrevious());
                                methodNode.instructions.remove(node);
                                counter.incrementAndGet();
                                modified = true;
                            }
                        }
                    }
                } while (modified);
            });
        });
        System.out.println("Removed " + counter.get() + " ldc-pop patterns");
    }
}
