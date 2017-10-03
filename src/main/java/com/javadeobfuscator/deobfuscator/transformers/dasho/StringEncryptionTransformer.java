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

package com.javadeobfuscator.deobfuscator.transformers.dasho;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

//BUG: redo
public class StringEncryptionTransformer extends Transformer {
    @Override
    public boolean transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());

        classNodes().forEach(wrappedClassNode -> {
            wrappedClassNode.classNode.methods.forEach(methodNode -> {
                AnalyzerResult result = MethodAnalyzer.analyze(wrappedClassNode.classNode, methodNode);

                insns:
                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (!(current instanceof MethodInsnNode))
                        continue;

                    MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                    if (methodInsnNode.getOpcode() != Opcodes.INVOKESTATIC) // only invokestatic is supported right now
                        continue;

                    Type[] argTypes = Type.getArgumentTypes(methodInsnNode.desc);

                    boolean illegalType = false;
                    boolean hasString = false;

                    // (IILjava/lang/String;)Ljava/lang/String;
                    // (Ljava/lang/String;I)Ljava/lang/String;
                    // (Ljava/lang/String;II)Ljava/lang/String;
                    for (Type type : argTypes) {
                        if (type.getSort() == Type.INT)
                            continue;
                        if (type.getSort() == Type.OBJECT && type.getDescriptor().equals("Ljava/lang/String;")) {
                            hasString = true;
                            continue;
                        }
                        illegalType = true;
                    }

                    if (illegalType || !hasString)
                        continue;

                    MethodFrame frame = (MethodFrame) result.getFrames().get(methodInsnNode).get(0);

                    List<JavaValue> args = new ArrayList<>();

                    for (Frame arg : frame.getArgs()) {
                        if (!(arg instanceof LdcFrame)) {
                            continue insns;
                        }
                        Object cst = ((LdcFrame) arg).getConstant();
                        if (cst == null) {
                            continue insns;
                        }
                        if (cst instanceof String) {
                            args.add(new JavaObject(cst, "java/lang/String"));
                        } else {
                            args.add(new JavaInteger(((Number) cst).intValue()));
                        }
                    }
                    if (classes.containsKey(methodInsnNode.owner)) {
                        Context context = new Context(provider);
                        context.push(wrappedClassNode.classNode.name, methodNode.name, wrappedClassNode.constantPoolSize);
                        ClassNode innerClassNode = classes.get(methodInsnNode.owner).classNode;
                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                        try {
                            Object o = MethodExecutor.execute(wrappedClassNode, decrypterNode, args, null, context);
                            InsnList list = new InsnList();
                            for (int i = 0; i < args.size(); i++) {
                                list.add(new InsnNode(Opcodes.POP));
                            }
                            list.add(new LdcInsnNode(o));
                            methodNode.instructions.insertBefore(methodInsnNode, list);
                            methodNode.instructions.remove(methodInsnNode);
                            counter.getAndIncrement();
                        } catch (Throwable t) {
                            System.out.println("Error while decrypting DashO string. " + methodInsnNode.owner + " " + methodInsnNode.name + methodInsnNode.desc);
                            System.out.println("Are you sure you're deobfuscating something obfuscated by DashO?");
                            t.printStackTrace(System.out);
                        }
                    }
                }
            });
        });

        System.out.println("[DashO] [StringEncryptionTransformer] Decrypted " + counter + " strings");
        return true;
    }
}
