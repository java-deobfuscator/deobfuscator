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
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.InsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.JumpInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LabelNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class GotoUnconditionalJumpRemover extends Transformer {
    public GotoUnconditionalJumpRemover(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                boolean modified = false;
                do {
                    modified = false;
                    Map<LabelNode, LabelNode> mapping = new HashMap<>();
                    methodNode.instructions.iterator().forEachRemaining(ain -> {
                        if (ain instanceof LabelNode) {
                            mapping.put((LabelNode) ain, (LabelNode) ain);
                        }
                    });
                    for (int i = 0; i < methodNode.instructions.size(); i++) {
                        AbstractInsnNode node = methodNode.instructions.get(i);
                        if (node.getOpcode() == Opcodes.GOTO) {
                            AbstractInsnNode target = Utils.getNext(((JumpInsnNode) node).label);
                            if (target != null) {
                                switch (target.getOpcode()) {
                                    case Opcodes.RETURN:
                                    case Opcodes.IRETURN:
                                    case Opcodes.FRETURN:
                                    case Opcodes.DRETURN:
                                    case Opcodes.LRETURN:
                                    case Opcodes.ARETURN:
                                    case Opcodes.ATHROW:
                                    case Opcodes.GOTO:
                                    case Opcodes.TABLESWITCH:
                                    case Opcodes.LOOKUPSWITCH:
                                        methodNode.instructions.insertBefore(node, target.clone(mapping));
                                        methodNode.instructions.remove(node);
                                        counter.incrementAndGet();
                                        modified = true;
                                        break;
                                }
                            }
                        }
                    }
                } while (modified);
            });
        });
        System.out.println("Removed " + counter.get() + " goto unconditional jumps");
    }
}
