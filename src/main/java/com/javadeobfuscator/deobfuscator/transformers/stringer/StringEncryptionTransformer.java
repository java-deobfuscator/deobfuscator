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

package com.javadeobfuscator.deobfuscator.transformers.stringer;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;

import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.FieldNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.InsnList;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LdcInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class StringEncryptionTransformer extends Transformer {
    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() {
        System.out.println("[Stringer] [StringEncryptionTransformer] Starting");
        int count = count();
        System.out.println("[Stringer] [StringEncryptionTransformer] Found " + count + " encrypted strings");
        if (count > 0) {
            int decrypted = decrypt(count);
            System.out.println("[Stringer] [StringEncryptionTransformer] Decrypted " + decrypted + " encrypted strings");
            int cleanedup = cleanup();
            System.out.println("[Stringer] [StringEncryptionTransformer] Removed " + cleanedup + " decryption classes");
        }
        System.out.println("[Stringer] [StringEncryptionTransformer] Done");
    }

    private int cleanup() {
        AtomicInteger total = new AtomicInteger();
        Set<String> remove = new HashSet<>();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            boolean method = false;
            boolean field = false;
            Iterator<MethodNode> it = classNode.methods.iterator();
            while (it.hasNext()) {
                MethodNode node = it.next();
                if (node.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                    method = true;
                } else if (node.desc.equals("(Ljava/io/InputStream;)V")) { //Don't delete resource decryptors yet
                    method = false;
                    break;
                }
            }
            Iterator<FieldNode> fieldIt = classNode.fields.iterator();
            while (fieldIt.hasNext()) {
                FieldNode node = fieldIt.next();
                if (node.desc.equals("[Ljava/lang/Object;")) {
                    field = true;
                }
            }
            if (method && field) {
                remove.add(classNode.name);
            }
        });
        remove.forEach(str -> {
            total.incrementAndGet();
            classes.remove(str);
            classpath.remove(str);
        });
        return total.get();
    }

    private int count() {
        AtomicInteger count = new AtomicInteger(0);
        for (WrappedClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                        if (currentInsn instanceof LdcInsnNode && currentInsn.getNext() instanceof MethodInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) currentInsn;
                            MethodInsnNode m = (MethodInsnNode) ldc.getNext();
                            if (ldc.cst instanceof String) {
                                String strCl = m.owner;
                                Type type = Type.getType(m.desc);
                                if (type.getArgumentTypes().length == 1 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);
                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                            count.getAndIncrement();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return count.get();
    }

    private int decrypt(int expected) {
        AtomicInteger total = new AtomicInteger();
        final boolean[] alerted = new boolean[100];

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new MappedFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));

        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                return true;
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
                return true;
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        Map<AbstractInsnNode, String> enhanced = new HashMap<>();

        for (WrappedClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                        if (currentInsn instanceof LdcInsnNode && currentInsn.getNext() instanceof MethodInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) currentInsn;
                            MethodInsnNode m = (MethodInsnNode) ldc.getNext();
                            if (ldc.cst instanceof String) {
                                String strCl = m.owner;
                                Type type = Type.getType(m.desc);
                                if (type.getArgumentTypes().length == 1 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);
                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                            Context context = new Context(provider);
                                            context.dictionary = classpath;
                                            context.push(classNode.classNode.name.replace('/', '.'), methodNode.name, classNode.constantPoolSize);
                                            context.file = deobfuscator.getFile();
                                            Object o = null;
                                            try {
                                                o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Collections.singletonList(new JavaObject(ldc.cst, "java/lang/String")), null, context);
                                            } catch (ArrayIndexOutOfBoundsException e) {
                                                enhanced.put(ldc, classNode.classNode.name + " " + methodNode.name);
                                            }
                                            if (o != null) {
                                                ldc.cst = (String) o;
                                                methodNode.instructions.remove(ldc.getNext());
                                                total.incrementAndGet();
                                                int x = (int) ((total.get() * 1.0d / expected) * 100);
                                                if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                                    System.out.println("[Stringer] [StringEncryptionTransformer] Done " + x + "%");
                                                    alerted[x - 1] = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (WrappedClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) currentInsn;
                        WrappedClassNode targetClassNode = classes.get(m.owner);
                        if (targetClassNode != null) {
                            MethodNode targetMethodNode = null;
                            for (MethodNode tempMethodNode : targetClassNode.classNode.methods) {
                                if (tempMethodNode.name.equals(m.name) && tempMethodNode.desc.equals(m.desc)) {
                                    targetMethodNode = tempMethodNode;
                                    break;
                                }
                            }
                            if (targetMethodNode != null) {
                                InsnList innerMethodInsns = targetMethodNode.instructions;
                                for (int innerInsnIndex = 0; innerInsnIndex < innerMethodInsns.size(); innerInsnIndex++) {
                                    AbstractInsnNode innerCurrentInsn = innerMethodInsns.get(innerInsnIndex);
                                    if (innerCurrentInsn instanceof LdcInsnNode && innerCurrentInsn.getNext() instanceof MethodInsnNode) {
                                        LdcInsnNode innerLdc = (LdcInsnNode) innerCurrentInsn;
                                        MethodInsnNode innerMethod = (MethodInsnNode) innerLdc.getNext();
                                        if (innerLdc.cst instanceof String) {
                                            String strCl = innerMethod.owner;
                                            if (innerMethod.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                                                if (enhanced.remove(innerLdc) != null) {
                                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(innerMethod.name) && mn.desc.equals(innerMethod.desc)).findFirst().orElse(null);
                                                    Context context = new Context(provider);
                                                    context.push(classNode.classNode.name.replace('/', '.'), methodNode.name, classNode.constantPoolSize);
                                                    context.push(targetClassNode.classNode.name.replace('/', '.'), targetMethodNode.name, targetClassNode.constantPoolSize);
                                                    context.dictionary = classpath;
                                                    Object o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Arrays.asList(new JavaObject(innerLdc.cst, "java/lang/String")), null, context);
                                                    innerLdc.cst = o;
                                                    targetMethodNode.instructions.remove(innerLdc.getNext());
                                                    total.incrementAndGet();
                                                    int x = (int) ((total.get() * 1.0d / expected) * 100);
                                                    if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                                        System.out.println("[Stringer] [StringEncryptionTransformer] Done " + x + "%");
                                                        alerted[x - 1] = true;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return total.get();
    }
}
