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

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.objectweb.asm.Opcodes.*;

public class TrapHandlerMerger extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        AtomicInteger redudantTraps = new AtomicInteger();
        classNodes().forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                if (methodNode.tryCatchBlocks != null && !methodNode.tryCatchBlocks.isEmpty()) {
                    Map<List<String>, List<TryCatchBlockNode>> merge = new HashMap<>();
                    Set<LabelNode> handled = new HashSet<>();
                    outer:
                    for (TryCatchBlockNode tryCatchBlockNode : methodNode.tryCatchBlocks) {
                        if (!handled.add(tryCatchBlockNode.handler)) {
                            continue;
                        }
                        List<String> insns = new ArrayList<>(); // yes I know it's string because asm doesn't do equals
                        loop:
                        for (AbstractInsnNode now = tryCatchBlockNode.handler; ; ) {
                            if (now.getType() != AbstractInsnNode.LABEL && now.getType() != AbstractInsnNode.FRAME && now.getType() != AbstractInsnNode.LINE) {
                                // todo need some way of comparing insns
//                                int oldindex = now.index;
//                                now.index = 0;
//                                insns.add(Utils.prettyprint(now));
//                                now.index = oldindex;
                                switch (now.getOpcode()) {
                                    case RETURN:
                                    case ARETURN:
                                    case IRETURN:
                                    case FRETURN:
                                    case DRETURN:
                                    case LRETURN:
                                    case ATHROW:
                                        // done!
                                        break loop;
                                }
                                if (Utils.isTerminating(now) || now instanceof JumpInsnNode) {
                                    // not gonna worry about branching handlers for now
                                    continue outer;
                                }
                            }
                            now = now.getNext();
                        }

                        merge.computeIfAbsent(insns, key -> new ArrayList<>()).add(tryCatchBlockNode);
                    }

                    merge.forEach((insns, handlers) -> {
                        if (handlers.size() > 1) {
                            for (TryCatchBlockNode t : handlers) {
                                t.handler = handlers.get(0).handler;
                                redudantTraps.incrementAndGet();
                            }
                        }
                    });
                }
            });
        });
        System.out.println("Removed " + redudantTraps.get() + " duplicate handlers");
        return redudantTraps.get() > 0;
    }
}
