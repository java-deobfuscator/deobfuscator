package com.javadeobfuscator.deobfuscator.transformers.stringer;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
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
                                if (m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;") && classes.containsKey(strCl)) {
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
        provider.register(new MethodProvider() {
            public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
                WrappedClassNode wrappedClassNode = classes.get(className);
                if (wrappedClassNode != null) {
                    ClassNode classNode = wrappedClassNode.classNode;
                    MethodNode methodNode = classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElseGet(null);
                    if (methodNode != null) {
                        List<StackObject> argsClone = new ArrayList<>();
                        for (StackObject arg : args) {
                            argsClone.add(arg.copy());
                        }
                        return MethodExecutor.execute(wrappedClassNode, methodNode, argsClone, targetObject == null ? null : targetObject.value, context);
                    }
                }
                throw new IllegalArgumentException("Could not find class");
            }

            public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
                WrappedClassNode wrappedClassNode = classes.get(className);
                return wrappedClassNode != null;
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
                                if (m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;") && classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);
                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                            Context context = new Context(provider);
                                            context.dictionary = classpath;
                                            context.push(classNode.classNode.name.replace('/', '.'), methodNode.name, classNode.constantPoolSize);
                                            Object o = null;
                                            try {
                                                o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Arrays.asList(new StackObject(Object.class, ldc.cst)), null, context);
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
                                                    Object o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Arrays.asList(new StackObject(Object.class, innerLdc.cst)), null, context);
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
