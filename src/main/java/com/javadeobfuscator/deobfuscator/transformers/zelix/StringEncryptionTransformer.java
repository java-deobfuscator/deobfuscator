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

package com.javadeobfuscator.deobfuscator.transformers.zelix;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.general.peephole.PeepholeOptimizer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StringEncryptionTransformer extends Transformer {

    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        Map<ClassNode, List<MethodNode>> remove = new HashMap<>();
        ClassNode decryptorClassNode = new ClassNode();
        decryptorClassNode.visit(49, Opcodes.ACC_PUBLIC, "Decryptor", null, "java/lang/Object", null);
        WrappedClassNode wrapped = new WrappedClassNode(decryptorClassNode, 0);
        classes.put(decryptorClassNode.name, wrapped);
        classpath.put(decryptorClassNode.name, wrapped);
        {
            AtomicInteger current = new AtomicInteger(0);
            classNodes().stream().map(n -> n.classNode).forEach(classNode -> {
                MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                if (clinit != null) {
                    Map<LabelNode, LabelNode> mapping = new HashMap<>();
                    InsnList insns = clinit.instructions;

                    for (int i = 0; i < insns.size(); i++) {
                        AbstractInsnNode node = insns.get(i);
                        if (node instanceof LabelNode) {
                            mapping.put((LabelNode) node, new LabelNode());
                        }
                    }
                    for (int i = 0; i < insns.size(); i++) {
                        AbstractInsnNode node = insns.get(i);
                        if (node instanceof MethodInsnNode) {
                            MethodInsnNode cast = (MethodInsnNode) node;
                            if (cast.owner.equals("java/lang/String") && cast.name.equals("toCharArray")) {
                                List<AbstractInsnNode> everything = new ArrayList<>();
                                everything.add(cast);
                                AbstractInsnNode next = cast.getNext();
                                while (true) {
                                    everything.add(next);
                                    if (next instanceof MethodInsnNode) {
                                        MethodInsnNode cast1 = (MethodInsnNode) next;
                                        if (cast1.name.equals("intern")) {
                                            break;
                                        }
                                    }
                                    next = next.getNext();
                                }
                                insns.remove(next.getNext());
                                insns.remove(next.getNext());
                                MethodNode decryptorNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "DECRYPTOR_METHOD_" + current.getAndIncrement(), "(Ljava/lang/String;)Ljava/lang/String;", null, null);
                                clinit.instructions.insertBefore(cast, new MethodInsnNode(Opcodes.INVOKESTATIC, decryptorClassNode.name, decryptorNode.name, decryptorNode.desc, false));
                                decryptorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                for (AbstractInsnNode everyth : everything) {
                                    decryptorNode.instructions.add(everyth.clone(mapping));
                                    clinit.instructions.remove(everyth);
                                }
                                decryptorNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                                decryptorClassNode.methods.add(decryptorNode);
                            } else if (cast.desc.equals("(Ljava/lang/String;)[C")) {
                                if (cast.getNext() instanceof MethodInsnNode) {
                                    MethodInsnNode castnext = (MethodInsnNode) cast.getNext();
                                    if (castnext.desc.equals("([C)Ljava/lang/String;")) {
                                        if (cast.owner.equals(classNode.name) && castnext.owner.equals(classNode.name)) {
                                            MethodNode decryptorNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "DECRYPTOR_METHOD_" + current.getAndIncrement(), "(Ljava/lang/String;)Ljava/lang/String;", null, null);
                                            decryptorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                            decryptorNode.instructions.add(cast.clone(null));
                                            decryptorNode.instructions.add(castnext.clone(null));
                                            decryptorNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                                            MethodNode mn = classpath.get(cast.owner).classNode.methods.stream().filter(dsds -> dsds.name.equals(cast.name) && dsds.desc.equals(cast.desc)).findFirst().orElse(null);
                                            MethodNode mn1 = classpath.get(castnext.owner).classNode.methods.stream().filter(dsds -> dsds.name.equals(castnext.name) && dsds.desc.equals(castnext.desc)).findFirst().orElse(null);
                                            mn.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                                            mn1.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                                            decryptorClassNode.methods.add(decryptorNode);
                                            {
                                                List<MethodNode> r = remove.get(classpath.get(cast.owner).classNode);
                                                if (r == null) {
                                                    r = new ArrayList<>();
                                                    remove.put(classpath.get(cast.owner).classNode, r);
                                                }
                                                r.add(mn);
                                            }
                                            {
                                                List<MethodNode> r = remove.get(classpath.get(castnext.owner).classNode);
                                                if (r == null) {
                                                    r = new ArrayList<>();
                                                    remove.put(classpath.get(castnext.owner).classNode, r);
                                                }
                                                r.add(mn1);
                                            }
                                            insns.insert(castnext, new MethodInsnNode(Opcodes.INVOKESTATIC, decryptorClassNode.name, decryptorNode.name, decryptorNode.desc, false));
                                            insns.remove(castnext);
                                            insns.remove(cast);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            });
        }
        for (int i = 0; i < 3; i++) {
            new PeepholeOptimizer(classes, classpath).transform();
        }

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));

        classNodes().forEach(wrappedClassNode -> {
            MethodNode clinit = wrappedClassNode.classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                boolean modified = false;
                do {
                    modified = false;
                    for (int index = 0; index < clinit.instructions.size(); index++) {
                        AbstractInsnNode current = clinit.instructions.get(index);
                        if (current instanceof LdcInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) current;
                            if (ldc.cst instanceof String) {
                                AbstractInsnNode next = Utils.getNext(ldc);
                                if (next instanceof MethodInsnNode) {
                                    MethodInsnNode m = (MethodInsnNode) next;
                                    String strCl = m.owner;
                                    if (m.owner.equals(decryptorClassNode.name)) {
                                        Context context = new Context(provider);
                                        context.push(wrappedClassNode.classNode.name, clinit.name, wrappedClassNode.constantPoolSize);
                                        ClassNode innerClassNode = classes.get(strCl).classNode;
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
                                        String o = MethodExecutor.execute(wrappedClassNode, decrypterNode, Arrays.asList(new StackObject(Object.class, ldc.cst)), null, context);
                                        ldc.cst = o;
                                        clinit.instructions.remove(m);
                                        modified = true;
                                    }
                                }
                            }
                        }
                    }
                } while (modified);
            }
        });
        for (int i = 0; i < 3; i++) {
            new PeepholeOptimizer(classes, classpath).transform();
        }
        classpath.remove(decryptorClassNode.name);
        classes.remove(decryptorClassNode.name);
        remove.forEach((cn, mn) -> {
            cn.methods.removeAll(mn);
        });
    }
}
