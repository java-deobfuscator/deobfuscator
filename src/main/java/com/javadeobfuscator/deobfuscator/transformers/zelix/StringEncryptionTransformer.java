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

import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;

import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.exceptions.NoSuchHandlerException;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaShort;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.general.peephole.PeepholeOptimizer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StringEncryptionTransformer extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        Map<ClassNode, List<MethodNode>> remove = new HashMap<>();
        ClassNode decryptorClassNode = new ClassNode();
        decryptorClassNode.visit(49, Opcodes.ACC_PUBLIC, "Decryptor", null, "java/lang/Object", null);
        classes.put(decryptorClassNode.name, decryptorClassNode);
        classpath.put(decryptorClassNode.name, decryptorClassNode);
        AtomicInteger currentDecryptorId = new AtomicInteger(0);
        {
            classNodes().forEach(classNode -> {
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
                            if (cast.owner.equals("java/lang/String") && cast.name.equals("toCharArray")) { //FIXME check if it's valid
                                boolean foundIntern = false;
                                List<AbstractInsnNode> everything = new ArrayList<>();
                                everything.add(cast);
                                AbstractInsnNode next = cast.getNext();
                                while (true) {
                                    if (next == null) {
                                        break;
                                    }
                                    everything.add(next);
                                    if (next instanceof MethodInsnNode) {
                                        MethodInsnNode cast1 = (MethodInsnNode) next;
                                        if (cast1.name.equals("intern")) {
                                            foundIntern = true;
                                            break;
                                        }
                                    }
                                    next = next.getNext();
                                }
                                if (foundIntern) {
                                    insns.remove(next.getNext());
                                    insns.remove(next.getNext());
                                    MethodNode decryptorNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "DECRYPTOR_METHOD_" + currentDecryptorId.getAndIncrement(), "(Ljava/lang/String;)Ljava/lang/String;", null, null);
                                    clinit.instructions.insertBefore(cast, new MethodInsnNode(Opcodes.INVOKESTATIC, decryptorClassNode.name, decryptorNode.name, decryptorNode.desc, false));
                                    decryptorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    for (AbstractInsnNode everyth : everything) {
                                        decryptorNode.instructions.add(everyth.clone(mapping));
                                        clinit.instructions.remove(everyth);
                                    }
                                    decryptorNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                                    decryptorClassNode.methods.add(decryptorNode);
                                }
                            } else if (cast.desc.equals("(Ljava/lang/String;)[C")) {
                                if (cast.getNext() instanceof MethodInsnNode) {
                                    MethodInsnNode castnext = (MethodInsnNode) cast.getNext();
                                    if (castnext.desc.equals("([C)Ljava/lang/String;")) {
                                        if (cast.owner.equals(classNode.name) && castnext.owner.equals(classNode.name)) {
                                            MethodNode decryptorNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "DECRYPTOR_METHOD_" + currentDecryptorId.getAndIncrement(), "(Ljava/lang/String;)Ljava/lang/String;", null, null);
                                            decryptorNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                            decryptorNode.instructions.add(cast.clone(null));
                                            decryptorNode.instructions.add(castnext.clone(null));
                                            decryptorNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                                            MethodNode mn = classpath.get(cast.owner).methods.stream().filter(dsds -> dsds.name.equals(cast.name) && dsds.desc.equals(cast.desc)).findFirst().orElse(null);
                                            MethodNode mn1 = classpath.get(castnext.owner).methods.stream().filter(dsds -> dsds.name.equals(castnext.name) && dsds.desc.equals(castnext.desc)).findFirst().orElse(null);
                                            mn.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                                            mn1.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
                                            decryptorClassNode.methods.add(decryptorNode);
                                            {
                                                remove.computeIfAbsent(classpath.get(cast.owner), k -> new ArrayList<>()).add(mn);
                                            }
                                            {
                                                remove.computeIfAbsent(classpath.get(castnext.owner), k -> new ArrayList<>()).add(mn1);
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
            try {
                getDeobfuscator().runFromConfig(TransformerConfig.configFor(PeepholeOptimizer.class));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new MappedFieldProvider());
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                return target.value() instanceof char[];
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type, Context context) {
                return type.toString().equals("[C");
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        classNodes().forEach(classNode -> {
            MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                boolean modified = false;
                outer:
                do {
                    Map<AbstractInsnNode, List<Frame>> analysis = MethodAnalyzer.analyze(classNode, clinit).getFrames();
                    Map<Frame, AbstractInsnNode> reverseMapping = new HashMap<>();
                    analysis.entrySet().forEach(ent -> ent.getValue().forEach(frame -> reverseMapping.put(frame, ent.getKey())));
                    modified = false;
                    for (int index = 0; index < clinit.instructions.size(); index++) {
                        AbstractInsnNode current = clinit.instructions.get(index);
                        if (current instanceof MethodInsnNode) {
                            MethodInsnNode cast = (MethodInsnNode) current;
                            if (cast.owner.equals(decryptorClassNode.name)) {
                                List<Frame> frames = analysis.get(cast);
                                if (frames != null) {
                                    Map<LdcInsnNode, Frame> interestedFrames = new HashMap<>(); //To sort out dupes - should be fixme
                                    for (Frame frame : frames) {
                                        MethodFrame methodFrame = (MethodFrame) frame;
                                        if (methodFrame.getArgs().size() != 1) {
                                            throw new IllegalArgumentException("What?");
                                        }
                                        Frame potentialLdcFrame = methodFrame.getArgs().get(0);
                                        if (potentialLdcFrame instanceof LdcFrame) {
                                            interestedFrames.put((LdcInsnNode) reverseMapping.get(potentialLdcFrame), potentialLdcFrame);
                                        }
                                    }
                                    for (Map.Entry<LdcInsnNode, Frame> ent : interestedFrames.entrySet()) {
                                        if (ent.getValue() instanceof LdcFrame) {
                                            LdcFrame ldc = (LdcFrame) ent.getValue();
                                            Context context = new Context(provider);
                                            context.push(classNode.name, clinit.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                            ClassNode innerClassNode = classes.get(cast.owner);
                                            MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(cast.name) && mn.desc.equals(cast.desc)).findFirst().orElse(null);
                                            String o = MethodExecutor.execute(classNode, decrypterNode, Collections.singletonList(new JavaObject(ent.getKey().cst, "java/lang/String")), null, context);
                                            ent.getKey().cst = o;
                                        }
                                    }
                                    clinit.instructions.remove(cast);
                                    modified = true;
                                    continue outer;
                                }
                            }
                        }
                    }
                } while (modified);
                {
                    try {
                        Context context = new Context(provider);
                        context.push(classNode.name, clinit.name, getDeobfuscator().getConstantPool(classNode).getSize());
                        context.dictionary = classpath;
                        MethodExecutor.execute(classNode, clinit, new ArrayList<>(), null, context);
                    } catch (NoSuchHandlerException e) {
                    } catch (Throwable t) {
                        System.out.println("Error while fully initializing " + classNode.name);
                        t.printStackTrace();
                    }
                }
            }

            classNode.methods.forEach(methodNode -> {
                boolean modified = false;
                do {
                    modified = false;
                    for (int index = 0; index < methodNode.instructions.size(); index++) {
                        AbstractInsnNode current = methodNode.instructions.get(index);
                        if (current.getOpcode() == Opcodes.SIPUSH) {
                            IntInsnNode sipush1 = (IntInsnNode) current;
                            AbstractInsnNode next = Utils.getNext(sipush1);
                            if (next.getOpcode() == Opcodes.SIPUSH) {
                                IntInsnNode sipush2 = (IntInsnNode) next;
                                next = Utils.getNext(sipush2);
                                if (next instanceof MethodInsnNode) {
                                    MethodInsnNode m = (MethodInsnNode) next;
                                    String strCl = m.owner;
                                    if (m.desc.equals("(II)Ljava/lang/String;") && m.owner.equals(classNode.name)) {
                                        Context context = new Context(provider);
                                        context.push(classNode.name, methodNode.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                        ClassNode innerClassNode = classes.get(strCl);
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
                                        List<JavaValue> stack = new ArrayList<>();
                                        stack.add(new JavaShort((short) sipush1.operand));
                                        stack.add(new JavaShort((short) sipush2.operand));
                                        Object o = MethodExecutor.execute(classNode, decrypterNode, stack, null, context);
                                        InsnList replace = new InsnList();
                                        replace.add(new LdcInsnNode(o));
                                        methodNode.instructions.insert(m, replace);
                                        methodNode.instructions.remove(m);
                                        methodNode.instructions.remove(sipush2);
                                        methodNode.instructions.remove(sipush1);
                                        {
                                            remove.computeIfAbsent(classpath.get(m.owner), k -> new ArrayList<>()).add(decrypterNode);
                                        }
                                        modified = true;
                                    }
                                }
                            }
                        }
                    }
                } while (modified);
            });
        });

        for (int i = 0; i < 3; i++) {
            try {
                getDeobfuscator().runFromConfig(TransformerConfig.configFor(PeepholeOptimizer.class));
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        /**
         * Cleanup the ZKM 8 decryption code
         */

        classNodes().forEach(classNode -> {
            MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                AbstractInsnNode last = null;
                for (int i = 0; i < clinit.instructions.size(); i++) { //Remove each block of string decryption
                    AbstractInsnNode now = clinit.instructions.get(i);
                    if (now.getOpcode() == Opcodes.LDC) {
                        if (now.getNext().getOpcode() == Opcodes.DUP) {
                            if (now.getNext().getNext().getOpcode() == Opcodes.ASTORE) {
                                if (now.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL) { //1337
                                    List<AbstractInsnNode> block = new ArrayList<>();
                                    while (true) {
                                        block.add(now);
                                        if (now.getOpcode() == Opcodes.GOTO) {
                                            while (now.getNext() instanceof LabelNode) {
                                                block.add(now.getNext());
                                                now = now.getNext();
                                            }
                                            break;
                                        }
                                        now = now.getNext();
                                    }
                                    if (block.size() < 45) {
                                        i = clinit.instructions.indexOf(block.get(block.size() - 1));
                                        last = block.get(block.size() - 1);
                                    }
                                }
                            }
                        }
                    }
                }
                if (last != null) {
                    while (last.getOpcode() != Opcodes.ALOAD) {
                        last = last.getNext();
                    }
                    Map<LabelNode, LabelNode> mapping = new HashMap<>();
                    InsnList insns = clinit.instructions;
                    for (int i = 0; i < insns.size(); i++) {
                        AbstractInsnNode node = insns.get(i);
                        if (node instanceof LabelNode) {
                            mapping.put((LabelNode) node, new LabelNode());
                        }
                    }
                    List<AbstractInsnNode> cloneList = new ArrayList<>();
                    AbstractInsnNode node = insns.get(0);
                    while (true) {
                        if (node == null) {
                            break;
                        }
                        cloneList.add(node);
                        if (node == last) {
                            break;
                        }
                        node = node.getNext();
                    }
                    MethodNode decryptorNode = new MethodNode(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, "DECRYPTOR_METHOD_" + currentDecryptorId.getAndIncrement(), "()[Ljava/lang/String;", null, null);
                    clinit.instructions.insert(clinit.instructions.getFirst(), new MethodInsnNode(Opcodes.INVOKESTATIC, decryptorClassNode.name, decryptorNode.name, decryptorNode.desc, false));
                    for (AbstractInsnNode everyth : cloneList) {
                        decryptorNode.instructions.add(everyth.clone(mapping));
                        clinit.instructions.remove(everyth);
                    }
                    decryptorNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                    decryptorClassNode.methods.add(decryptorNode);
                }
            }
        });


        classNodes().forEach(classNode -> {
            MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                boolean modified = false;
                do {
                    modified = false;
                    for (int index = 0; index < clinit.instructions.size(); index++) {
                        AbstractInsnNode current = clinit.instructions.get(index);
                        if (current instanceof MethodInsnNode) {
                            MethodInsnNode m = (MethodInsnNode) current;
                            String strCl = m.owner;
                            if (m.desc.equals("()[Ljava/lang/String;")) {
                                if (classes.containsKey(strCl)) {
                                    Context context = new Context(provider);
                                    context.push(classNode.name, clinit.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                    ClassNode innerClassNode = classes.get(strCl);
                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
                                    Object[] o = MethodExecutor.execute(classNode, decrypterNode, Arrays.asList(), null, context);
                                    InsnList insert = new InsnList();
                                    insert.add(new LdcInsnNode(o.length));
                                    insert.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
                                    for (int i = 0; i < o.length; i++) {
                                        insert.add(new InsnNode(Opcodes.DUP));
                                        insert.add(new LdcInsnNode(i));
                                        if (o[i] == null) {
                                            insert.add(new InsnNode(Opcodes.ACONST_NULL));
                                        } else {
                                            insert.add(new LdcInsnNode(o[i]));
                                        }
                                        insert.add(new InsnNode(Opcodes.AASTORE));
                                    }
                                    if (m.getNext().getOpcode() != Opcodes.PUTSTATIC) {
                                        clinit.instructions.insert(m, insert);
                                        clinit.instructions.remove(m);
                                    } else {
                                        AbstractInsnNode a = m;
                                        List<AbstractInsnNode> delete = new ArrayList<>();
                                        while (true) {
                                            if (a == null) break;
                                            delete.add(a);
                                            if (a.getOpcode() == Opcodes.ANEWARRAY) {
                                                delete.add(a.getNext());
                                                break;
                                            }
                                            a = a.getNext();
                                        }
                                        delete.forEach(clinit.instructions::remove);
                                    }
                                    modified = true;
                                }
                            }
                        }
                    }
                } while (modified);
            }
        });

//        classpath.remove(decryptorClassNode.name);
//        classes.remove(decryptorClassNode.name);
//        remove.forEach((cn, mn) -> {
//            cn.methods.removeAll(mn);
//        });
        return true;
    }
}
