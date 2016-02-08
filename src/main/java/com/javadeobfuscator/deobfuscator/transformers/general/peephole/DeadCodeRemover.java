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

import static com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes.*;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DeadCodeRemover extends Transformer {
    public DeadCodeRemover(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        AtomicInteger deadInstructions = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                if (methodNode.localVariables != null) {
                    methodNode.localVariables.clear();
                }
                List<AbstractInsnNode> protectedNodes = new ArrayList<>();

                if (methodNode.tryCatchBlocks != null) {
                    methodNode.tryCatchBlocks.forEach(tryCatchBlockNode -> {
                        protectedNodes.add(tryCatchBlockNode.start);
                        protectedNodes.add(tryCatchBlockNode.end);
                        walk(tryCatchBlockNode.handler, protectedNodes, new HashSet<>());
                    });
                }

                walk(methodNode.instructions.getFirst(), protectedNodes, new HashSet<>());

                Iterator<AbstractInsnNode> it = methodNode.instructions.iterator();
                while (it.hasNext()) {
                    if (!protectedNodes.contains(it.next())) {
                        it.remove();
                        deadInstructions.getAndIncrement();
                    }
                }
            });
        });
        System.out.println("Removed " + deadInstructions.get() + " dead instructions");
    }

    private void walk(AbstractInsnNode now, List<AbstractInsnNode> notDead, Set<AbstractInsnNode> jumped) {
        while (now != null) {
            notDead.add(now);
            if (now instanceof JumpInsnNode) {
                JumpInsnNode cast = (JumpInsnNode) now;
                if (jumped.add(cast.label)) {
                    walk(cast.label, notDead, jumped);
                }
            } else if (now instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode cast = (TableSwitchInsnNode) now;
                for (LabelNode label : cast.labels) {
                    if (jumped.add(label))
                        walk(label, notDead, jumped);
                }
                if (jumped.add(cast.dflt)) {
                    walk(cast.dflt, notDead, jumped);
                }
            } else if (now instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode cast = (LookupSwitchInsnNode) now;
                for (LabelNode label : cast.labels) {
                    if (jumped.add(label))
                        walk(label, notDead, jumped);
                }
                if (jumped.add(cast.dflt)) {
                    walk(cast.dflt, notDead, jumped);
                }
            }
            switch (now.getOpcode()) {
                case JSR:
                case RET:
                    throw new IllegalArgumentException();
                case GOTO:
                case TABLESWITCH:
                case LOOKUPSWITCH:
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN:
                case ATHROW:
                    return;
                default:
                    break;
            }
            now = now.getNext();
        }
    }
}
