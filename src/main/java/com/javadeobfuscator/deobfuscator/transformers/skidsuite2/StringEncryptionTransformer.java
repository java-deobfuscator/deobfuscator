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

package com.javadeobfuscator.deobfuscator.transformers.skidsuite2;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.*;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// I can't believe this has to exist
public class StringEncryptionTransformer extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {

        AtomicInteger counter = new AtomicInteger();

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());

        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode);

                Map<AbstractInsnNode, InsnList> replacements = new HashMap<>();

                insns:
                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (!(current instanceof MethodInsnNode))
                        continue;

                    MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                    if (methodInsnNode.getOpcode() != Opcodes.INVOKESTATIC)
                        continue;

                    if (!methodInsnNode.desc.equals("([CLjava/lang/String;I)Ljava/lang/String;"))
                        continue;

                    MethodFrame frame = (MethodFrame) result.getFrames().get(methodInsnNode).get(0);

                    List<JavaValue> args = new ArrayList<>();
                    Map<AbstractInsnNode, InsnList> localReplacements = new HashMap<>();

                    for (Frame arg : frame.getArgs()) {
                        if (arg instanceof LdcFrame) {
                            Object cst = ((LdcFrame) arg).getConstant();
                            if (cst == null) {
                                continue insns;
                            }
                            if (cst instanceof String) {
                                args.add(new JavaObject(cst, "java/lang/String"));
                            } else {
                                args.add(new JavaInteger(((Number) cst).intValue()));
                            }
                        } else if (arg instanceof NewArrayFrame) {
                            NewArrayFrame newArrayFrame = (NewArrayFrame) arg;

                            {
                                InsnList list = new InsnList();
                                list.add(new InsnNode(Opcodes.POP));
                                list.add(new InsnNode(Opcodes.ACONST_NULL));
                                localReplacements.put(result.getMapping().get(newArrayFrame), list);
                            }

                            if (newArrayFrame.getLength() instanceof LdcFrame) {
                                char[] arr = new char[((Number) ((LdcFrame) newArrayFrame.getLength()).getConstant()).intValue()];
                                JavaObject obj = new JavaObject(arr, "[C");
                                for (Frame child0 : arg.getChildren()) {
                                    if (child0 instanceof ArrayStoreFrame) {
                                        ArrayStoreFrame arrayStoreFrame = (ArrayStoreFrame) child0;
                                        if (arrayStoreFrame.getIndex() instanceof LdcFrame && arrayStoreFrame.getObject() instanceof LdcFrame) {
                                            {
                                                InsnList list = new InsnList();
                                                list.add(new InsnNode(Opcodes.POP2));
                                                list.add(new InsnNode(Opcodes.POP));
                                                localReplacements.put(result.getMapping().get(arrayStoreFrame), list);
                                            }
                                            arr[((Number) ((LdcFrame) arrayStoreFrame.getIndex()).getConstant()).intValue()] = (char) ((Number) ((LdcFrame) arrayStoreFrame.getObject()).getConstant()).intValue();
                                        } else {
                                            continue insns;
                                        }
                                    }
                                }
                                args.add(obj);
                            } else {
                                continue insns;
                            }
                        }
                    }

                    if (classes.containsKey(methodInsnNode.owner)) {
                        Context context = new Context(provider);
                        context.push(classNode.name, methodNode.name, getDeobfuscator().getConstantPool(classNode).getSize());
                        ClassNode innerClassNode = classes.get(methodInsnNode.owner);
                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                        try {
                            Object o = MethodExecutor.execute(classNode, decrypterNode, args, null, context);
                            InsnList list = new InsnList();
                            for (int i = 0; i < args.size(); i++) {
                                list.add(new InsnNode(Opcodes.POP));
                            }
                            list.add(new LdcInsnNode(o));
                            methodNode.instructions.insertBefore(methodInsnNode, list);
                            methodNode.instructions.remove(methodInsnNode);
                            replacements.putAll(localReplacements);
                            counter.getAndIncrement();
                        } catch (Throwable t) {
                            System.out.println("Error while decrypting string. " + methodInsnNode.owner + " " + methodInsnNode.name + methodInsnNode.desc);
                            t.printStackTrace(System.out);
                        }
                    }
                }

                replacements.forEach((k, v) -> {
                    methodNode.instructions.insertBefore(k, v);
                    methodNode.instructions.remove(k);
                });
            });
        });

        System.out.println("[SkidSuite2] [StringEncryptionTransformer] Decrypted " + counter + " strings");
        return true;
    }
}
