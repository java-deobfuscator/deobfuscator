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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class RedundantTrapRemover extends Transformer<TransformerConfig> {

    private Boolean doesTrapCatch(TryCatchBlockNode node, String... exceptions) {
        if (node.type == null) {
            return true;
        }
        if (node.type.equals("java/lang/Throwable")) {
            return true;
        }
        for (String exception : exceptions) {
            try {
                if (getDeobfuscator().isSubclass(node.type, exception)) {
                    return true;
                }
            } catch (NoClassInPathException ex) {
                logger.warn("Could not find class " + ex.getMessage());
                return null;
            }
        }
        return false;
    }

    @Override
    public boolean transform() throws Throwable {
        AtomicInteger redudantTraps = new AtomicInteger();
        classNodes().forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                if (methodNode.tryCatchBlocks != null && !methodNode.tryCatchBlocks.isEmpty()) {
                    {
                        List<TryCatchBlockNode> remove = new ArrayList<>();
//                        List<TryCatchBlockNode> add = new ArrayList<>();
                        tcbnFor:
                        for (TryCatchBlockNode tryCatchBlockNode : methodNode.tryCatchBlocks) {
                            boolean containsThrowableInstructions = false;
                            boolean previousInsnThrows = false;
                            boolean currentInsnThrows;
                            // todo static analysis on stuff like IDIV, and check if the throwable is ever used. if not, optimize away
                            boolean guaranteedThrow = false;
                            AbstractInsnNode firstThrowable = null;
                            AbstractInsnNode latestThrowable = null;
                            AbstractInsnNode guaranteedThrowable = null;
                            for (AbstractInsnNode cur = tryCatchBlockNode.start; ; cur = cur.getNext()) {
                                if (cur.getType() != AbstractInsnNode.LABEL && cur.getType() != AbstractInsnNode.FRAME && cur.getType() != AbstractInsnNode.LINE) {
                                    currentInsnThrows = false;
                                    switch (cur.getOpcode()) {
                                        case IALOAD:
                                        case DALOAD:
                                        case FALOAD:
                                        case LALOAD:
                                        case SALOAD:
                                        case AALOAD: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode,
                                                    "java/lang/NullPointerException",
                                                    "java/lang/ArrayIndexOutOfBoundsException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case IASTORE:
                                        case DASTORE:
                                        case FASTORE:
                                        case LASTORE:
                                        case SASTORE:
                                        case AASTORE: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode,
                                                    "java/lang/NullPointerException",
                                                    "java/lang/ArrayIndexOutOfBoundsException",
                                                    "java/lang/ArrayStoreException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case NEWARRAY:
                                        case ANEWARRAY:
                                        case MULTIANEWARRAY: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/NegativeArraySizeException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case RETURN:
                                        case IRETURN:
                                        case DRETURN:
                                        case FRETURN:
                                        case LRETURN:
                                        case ARETURN: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/IllegalMonitorStateException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case ARRAYLENGTH: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/NullPointerException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case ATHROW: {
                                            containsThrowableInstructions = true;
                                            currentInsnThrows = true;
                                            guaranteedThrow = true;
                                            break;
                                        }
                                        case CHECKCAST: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/ClassCastException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case GETFIELD:
                                        case PUTFIELD: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/NullPointerException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case GETSTATIC:
                                        case PUTSTATIC:
                                        case NEW: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/Error");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case IDIV:
                                        case IREM:
                                        case LDIV:
                                        case LREM: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/ArithmeticException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case INVOKEDYNAMIC:
                                        case INVOKEINTERFACE:
                                        case INVOKESPECIAL:
                                        case INVOKESTATIC:
                                        case INVOKEVIRTUAL: {
                                            containsThrowableInstructions = true;
                                            currentInsnThrows = true;
                                            break;
                                        }
                                        case MONITORENTER: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode, "java/lang/NullPointerException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                        case MONITOREXIT: {
                                            Boolean b = doesTrapCatch(tryCatchBlockNode,
                                                    "java/lang/NullPointerException", 
                                                    "java/lang/IllegalMonitorStateException");
                                            if (b == null) {
                                                continue tcbnFor;
                                            }
                                            if (b) {
                                                containsThrowableInstructions = true;
                                                currentInsnThrows = true;
                                            }
                                            break;
                                        }
                                    }

                                    // any instruction can throw this, but is this necessary? can people really trigger stackoverflow/oom/internalerror on demand?
//                                    if (deobfuscator.isSubclass(tryCatchBlockNode.type, "java/lang/VirtualMachineError")) {
//                                        containsThrowableInstructions = true;
//                                        currentInsnThrows = true;
//                                    }

                                    if (containsThrowableInstructions) {
                                        if (firstThrowable == null) {
                                            firstThrowable = cur;
                                        }
                                        latestThrowable = cur;
                                    }
//                                    if (guaranteedThrow) {
//                                        if (guaranteedThrowable == null) {
//                                            guaranteedThrowable = cur;
//                                        }
//                                    }

//                                    if (!currentInsnThrows) {
//                                        if (previousInsnThrows) {
//                                            TryCatchBlockNode tcbn = new TryCatchBlockNode(new LabelNode(), new LabelNode(), tryCatchBlockNode.handler, 
//                                            tryCatchBlockNode.type);
//                                            methodNode.instructions.insertBefore(firstThrowable, tcbn.start);
//                                            methodNode.instructions.insert(latestThrowable, tcbn.end);
//                                            firstThrowable = null;
//                                            latestThrowable = null;
//                                            currentInsnThrows = false;
////                                        containsThrowableInstructions = false;
////                                        guaranteedThrow = false;
//                                            add.add(tcbn);
//                                        }
//                                    }

//                                    previousInsnThrows = currentInsnThrows;
                                }

                                if (cur == tryCatchBlockNode.end) {
                                    break;
                                }
                            }

                            if (!containsThrowableInstructions) {
                                remove.add(tryCatchBlockNode);
                                redudantTraps.incrementAndGet();
                            } else {
                                LabelNode start = new LabelNode();
                                LabelNode end = new LabelNode();
                                methodNode.instructions.insertBefore(firstThrowable, start);
                                methodNode.instructions.insert(latestThrowable, end);
                                tryCatchBlockNode.start = start;
                                tryCatchBlockNode.end = end;
                            }
                        }

                        methodNode.tryCatchBlocks.removeAll(remove);
//                        methodNode.tryCatchBlocks.addAll(add);
                    }

                    // Now remove duplicates
                    {
                        Map<Map.Entry<String, List<AbstractInsnNode>>, List<TryCatchBlockNode>> duplicates = new HashMap<>();
                        for (TryCatchBlockNode tryCatchBlockNode : methodNode.tryCatchBlocks) {
                            duplicates.computeIfAbsent(
                                    new AbstractMap.SimpleEntry<>(
                                            tryCatchBlockNode.type,
                                            Arrays.asList(Utils.getNext(tryCatchBlockNode.start),
                                                    Utils.getNext(tryCatchBlockNode.end),
                                                    Utils.getNext(tryCatchBlockNode.handler))
                                    ), key -> new ArrayList<>()).add(tryCatchBlockNode);
                        }

                        duplicates.forEach((ent, list) -> {
                            if (list.size() > 1) {
                                for (int i = 1; i < list.size(); i++) {
                                    methodNode.tryCatchBlocks.remove(list.get(i));
                                }
                            }
                        });
                    }
                }
            });
        });
        System.out.println("Removed " + redudantTraps.get() + " redundant traps");
        return redudantTraps.get() > 0;
    }
}
