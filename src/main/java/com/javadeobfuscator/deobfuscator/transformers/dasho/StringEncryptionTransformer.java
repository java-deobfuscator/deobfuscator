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

import java.util.Arrays;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;

import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.commons.Method;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.IntInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LdcInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class StringEncryptionTransformer extends Transformer {

    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());

        classNodes().forEach(wrappedClassNode -> {
            wrappedClassNode.classNode.methods.forEach(methodNode -> {

                AnalyzerResult result = MethodAnalyzer.analyze(wrappedClassNode.classNode, methodNode);

                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (!(current instanceof MethodInsnNode))
                        continue;

                    MethodInsnNode methodInsnNode = (MethodInsnNode) current;

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
//
//                    if (current instanceof LdcInsnNode) {
//                        LdcInsnNode ldc = (LdcInsnNode) current;
//                        if (ldc.cst instanceof String) {
//                            if (Utils.getNext(Utils.getNext(ldc)) instanceof MethodInsnNode) {
//                                MethodInsnNode m = (MethodInsnNode) Utils.getNext(Utils.getNext(ldc));
//                                String strCl = m.owner;
//                                if (m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) {
//                                    Context context = new Context(provider);
//                                    context.push(wrappedClassNode.classNode.name, methodNode.name, wrappedClassNode.constantPoolSize);
//                                    ClassNode innerClassNode = classes.get(strCl).classNode;
//                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
//
//                                    int intConstant = Integer.MIN_VALUE;
//                                    if (Utils.getNext(ldc) instanceof IntInsnNode) {
//                                        intConstant = ((IntInsnNode) Utils.getNext(ldc)).operand;
//                                    } else {
//                                        intConstant = Utils.iconstToInt(Utils.getNext(ldc).getOpcode());
//                                    }
//                                    if (intConstant != Integer.MAX_VALUE) {
//                                        try {
//                                            Object o = MethodExecutor.execute(wrappedClassNode, decrypterNode, Arrays.asList(new JavaObject(ldc.cst, "java/lang/String"), new JavaInteger(intConstant)), null, context);
//                                            ldc.cst = o;
//                                            methodNode.instructions.remove(Utils.getNext(ldc));
//                                            methodNode.instructions.remove(Utils.getNext(ldc));
//                                        } catch (Throwable t) {
//                                            System.out.println("Error while decrypting DashO string.");
//                                            System.out.println("Are you sure you're deobfuscating something obfuscated by DashO?");
//                                            t.printStackTrace(System.out);
//                                        }
//                                    }
//
//                                }
//                            }
//                        }
//                    }
                }
            });
        });
    }
}
