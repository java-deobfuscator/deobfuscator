/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.allatori;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class StringEncryptionTransformer extends Transformer {

    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));

        AtomicInteger count = new AtomicInteger();

        System.out.println("[Allatori] [StringEncryptionTransformer] Starting");

        classNodes().forEach(wrappedClassNode -> {
            wrappedClassNode.classNode.methods.forEach(methodNode -> {
                AnalyzerResult result = MethodAnalyzer.analyze(wrappedClassNode.classNode, methodNode);
                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) current;
                        MethodFrame frame = (MethodFrame) result.getFrames().get(m).get(0);
                        String strCl = m.owner;
                        if (m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                            if (frame.getArgs().get(0) instanceof LdcFrame) {
                                LdcFrame ldcFrame = (LdcFrame) frame.getArgs().get(0);
                                if (!(result.getMapping().get(ldcFrame) instanceof LdcInsnNode))
                                {
                                    continue;
                                }
                                LdcInsnNode insn = (LdcInsnNode) result.getMapping().get(ldcFrame);
                                Context context = new Context(provider);
                                context.push(wrappedClassNode.classNode.name, methodNode.name, wrappedClassNode.constantPoolSize);
                                if (classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
                                    if (isAllatoriMethod(decrypterNode)) {
                                        try {
                                            insn.cst = MethodExecutor.execute(wrappedClassNode, decrypterNode, Collections.singletonList(JavaValue.valueOf(insn.cst)), null, context);
                                            methodNode.instructions.remove(current);
                                            count.getAndIncrement();
                                        } catch (Throwable t) {
                                            System.out.println("Error while decrypting Allatori string.");
                                            System.out.println("Are you sure you're deobfuscating something obfuscated by Allatori?");
                                            System.out.println(wrappedClassNode.classNode.name + " " + methodNode.name + methodNode.desc + " " + m.owner + " " + m.name + m.desc);
                                            t.printStackTrace(System.out);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        });
        System.out.println("[Allatori] [StringEncryptionTransformer] Decrypted " + count + " encrypted strings");
    }

    //XXX: Better detector
    private boolean isAllatoriMethod(MethodNode method) {
        Map<Integer, AtomicInteger> insnCount   = new HashMap<>();
        Map<String, AtomicInteger>  invokeCount = new HashMap<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            insnCount.putIfAbsent(opcode, new AtomicInteger(0));
            insnCount.get(opcode).getAndIncrement();

            if (insn instanceof MethodInsnNode) {
                invokeCount.putIfAbsent(((MethodInsnNode) insn).name, new AtomicInteger(0));
                invokeCount.get(((MethodInsnNode) insn).name).getAndIncrement();
            }
        }

        if (((int) ((insnCount.get(Opcodes.IXOR).get() * 100.0f) / 11)) >= 50 &&
                ((int) ((insnCount.get(Opcodes.ISHL).get() * 100.0f) / 4)) >= 50 &&
                ((int) ((insnCount.get(Opcodes.NEWARRAY).get() * 100.0f) / 1)) >= 100 &&
                ((int) ((invokeCount.get("charAt").get() * 100.0f) / 4)) >= 50 &&
                ((int) ((invokeCount.get("length").get() * 100.0f) / 2)) >= 50) {
            if (invokeCount.containsKey("getStackTrace") && invokeCount.containsKey("getClassName")) {
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode insn = method.instructions.get(i);
                    if (insn.getOpcode() == Opcodes.NEW && ((TypeInsnNode)insn).desc.endsWith("Exception")) {
                        method.instructions.set(insn, new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException"));
                    } else if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.endsWith("Exception")) {
                        method.instructions.set(insn, new MethodInsnNode(insn.getOpcode(), "java/lang/RuntimeException", ((MethodInsnNode) insn).name, ((MethodInsnNode) insn).desc, ((MethodInsnNode) insn).itf));
                    }
                }
            }
            return true;
        }
        return false;
    }
}