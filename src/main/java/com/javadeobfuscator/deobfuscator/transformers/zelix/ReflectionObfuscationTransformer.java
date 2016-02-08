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
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaField;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ReflectionObfuscationTransformer extends Transformer {
    static Map<String, String> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put("boolean", "java/lang/Boolean");
        PRIMITIVES.put("byte", "java/lang/Byte");
        PRIMITIVES.put("char", "java/lang/Character");
        PRIMITIVES.put("short", "java/lang/Short");
        PRIMITIVES.put("int", "java/lang/Integer");
        PRIMITIVES.put("float", "java/lang/Float");
        PRIMITIVES.put("double", "java/lang/Double");
        PRIMITIVES.put("long", "java/lang/Long");
    }

    public ReflectionObfuscationTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Starting");
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Finding reflection obfuscation");
        int count = findReflectionObfuscation();
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Found " + count + " reflection obfuscation instructions");
        if (count > 0) {
            int amount = inlineReflection(count);
            System.out.println("[Zelix] [ReflectionObfuscationTransformer] Inlined " + amount + " reflection obfuscation instructions");
        }
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Done");
    }

    public int inlineReflection(int expected) throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        final boolean[] alerted = new boolean[100];

        Map<String, Object> fields = new HashMap<>();

        DelegatingProvider provider = new DelegatingProvider();

        provider.register(new FieldProvider() {
            @Override
            public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
                fields.put(className + fieldName + fieldDesc, value);
            }

            @Override
            public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
                if (className.startsWith("java/lang/") && fieldName.equals("TYPE")) {
                    try {
                        Class<?> clazz = (Class<?>) Class.forName(className.replace('/', '.')).getField("TYPE").get(null);
                        return clazz.getCanonicalName();
                    } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException | ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                return fields.get(className + fieldName + fieldDesc);
            }

            @Override
            public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
                return true;
            }

            @Override
            public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
                return true;
            }
        });
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(StackObject target, Type type, Context context) {
                return type.getDescriptor().equals("java/lang/String") && target.value instanceof String;
            }

            @Override
            public boolean checkEquality(StackObject first, StackObject second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
                return false;
            }
        });

        Set<ClassNode> initted = new HashSet<>();
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                if (methodNode.tryCatchBlocks != null) {
                    Iterator<TryCatchBlockNode> tryCatchBlocks = methodNode.tryCatchBlocks.iterator();
                    while (tryCatchBlocks.hasNext()) {
                        TryCatchBlockNode next = tryCatchBlocks.next();
                        if (next.type != null && next.type.contains("java/lang/reflect")) {
                            AbstractInsnNode nextInsn = next.handler;
                            while (nextInsn.getType() == AbstractInsnNode.LABEL || nextInsn.getType() == AbstractInsnNode.LINE) {
                                nextInsn = nextInsn.getNext();
                            }
                            if (nextInsn.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) nextInsn).name.equals("getTargetException")) {
                                nextInsn = nextInsn.getNext();
                                while (nextInsn.getType() == AbstractInsnNode.LABEL || nextInsn.getType() == AbstractInsnNode.LINE) {
                                    nextInsn = nextInsn.getNext();
                                }
                                if (nextInsn.getOpcode() == Opcodes.ATHROW) {
                                    methodNode.instructions.remove(nextInsn.getPrevious());
                                    methodNode.instructions.remove(nextInsn);
                                    tryCatchBlocks.remove();
                                }
                            }
                        }
                    }
                }
                AbstractInsnNode current = methodNode.instructions.getFirst();
                int i = 0;
                while (i < methodNode.instructions.size()) {
                    current = methodNode.instructions.get(i++);
                    if (current == null) {
                        continue;
                    }
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                        if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Method;")) {
                            long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                            String strCl = methodInsnNode.owner;
                            ClassNode innerClassNode = classpath.get(strCl).classNode;
                            if (initted.add(innerClassNode)) {
                                MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                Context context = new Context(provider);
                                context.dictionary = this.classpath;
                                MethodExecutor.execute(classpath.get(innerClassNode.name), decrypterNode, Arrays.asList(new StackObject(long.class, ldc)), null, context);
                            }

                            MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            Context ctx = new Context(provider);
                            ctx.dictionary = classpath;
                            JavaMethod javaMethod = MethodExecutor.execute(classpath.get(classNode.name), decrypterNode, Arrays.asList(new StackObject(long.class, ldc)), null, ctx);

                            InsnList replacement = new InsnList();
                            String str = javaMethod.getDeclaringClass().getName().replace('.', '/');

                            Type t = Type.getObjectType(str);
                            replacement.add(new LdcInsnNode(t));
                            replacement.add(new LdcInsnNode(javaMethod.getName()));
                            replacement.add(new LdcInsnNode(javaMethod.getParameterTypes().length));
                            replacement.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
                            for (int x = 0; x < javaMethod.getParameterTypes().length; x++) {
                                JavaClass param = javaMethod.getParameterTypes()[x];
                                replacement.add(new InsnNode(Opcodes.DUP));
                                replacement.add(new LdcInsnNode(x));
                                if (param.isPrimitive()) {
                                    replacement.add(new FieldInsnNode(Opcodes.GETSTATIC, PRIMITIVES.get(param.getName()), "TYPE", "Ljava/lang/Class;"));
                                } else {
                                    String pp = param.getName().replace('.', '/');
                                    Type t1 = Type.getObjectType(pp);
                                    replacement.add(new LdcInsnNode(t1));
                                }
                                replacement.add(new InsnNode(Opcodes.AASTORE));
                            }
                            replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false));
                            methodNode.instructions.insert(current.getPrevious().getPrevious(), replacement);
                            methodNode.instructions.remove(current.getPrevious());
                            methodNode.instructions.remove(current);
                            count.incrementAndGet();
                            int x = (int) ((count.get() * 1.0d / expected) * 100);
                            if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                alerted[x - 1] = true;
                            }
                        } else if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Field;")) {
                            long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                            String strCl = methodInsnNode.owner;
                            ClassNode innerClassNode = classpath.get(strCl).classNode;
                            if (initted.add(innerClassNode)) {
                                MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                MethodExecutor.execute(classpath.get(innerClassNode.name), decrypterNode, Arrays.asList(new StackObject(long.class, ldc)), null, new Context(provider));
                            }
                            MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            Context ctx = new Context(provider);
                            ctx.dictionary = classpath;
                            JavaField javaField = MethodExecutor.execute(classpath.get(classNode.name), decrypterNode, Arrays.asList(new StackObject(long.class, ldc)), null, ctx);
                            InsnList replacement = new InsnList();
                            Type t = Type.getObjectType(javaField.getDeclaringClass().getName().replace('.', '/'));
                            replacement.add(new LdcInsnNode(t));
                            replacement.add(new LdcInsnNode(javaField.getName()));
                            replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false));
                            methodNode.instructions.insert(current.getPrevious().getPrevious(), replacement);
                            methodNode.instructions.remove(current.getPrevious());
                            methodNode.instructions.remove(current);
                            count.incrementAndGet();
                            int x = (int) ((count.get() * 1.0d / expected) * 100);
                            if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                alerted[x - 1] = true;
                            }
                        }
                    }
                }
            });
        });
        Iterator<WrappedClassNode> iterator = classNodes().iterator();
        while (iterator.hasNext()) {
            WrappedClassNode node = iterator.next();
            if (initted.contains(node.classNode)) {
                node.classNode.fields.add(0, new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "REFLECTION_OBFUSCATION_CLASS", "Z", null, true));
            }
        }
        return count.get();
    }

    public int findReflectionObfuscation() throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode current = methodNode.instructions.get(i);
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                        if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Method;") || methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Field;")) {
                            count.incrementAndGet();
                        }
                    }
                }
            });
        });
        return count.get();
    }
}
