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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;

import com.javadeobfuscator.deobfuscator.executor.defined.DisabledFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class InvokedynamicTransformer extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() {
        System.out.println("[Stringer] [InvokedynamicTransformer] Starting");
        System.out.println("[Stringer] [InvokedynamicTransformer] Finding invokedynamic instructions");
        int amount = findInvokeDynamic();
        System.out.println("[Stringer] [InvokedynamicTransformer] Found " + amount + " invokedynamic instructions");
        int inlined = 0;
        if (amount > 0) {
            System.out.println("[Stringer] [InvokedynamicTransformer] Inlining invokedynamic");
            long start = System.currentTimeMillis();
            inlined = inlineInvokeDynamic(amount);
            long end = System.currentTimeMillis();
            System.out.println("[Stringer] [InvokedynamicTransformer] Removed " + inlined + " invokedynamic instructions, took " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
            System.out.println("[Stringer] [InvokedynamicTransformer] Cleaning up bootstrap methods");
            int cleanedup = cleanup();
            System.out.println("[Stringer] [InvokedynamicTransformer] Removed " + cleanedup + " bootstrap methods");
        }
        System.out.println("[Stringer] [InvokedynamicTransformer] Done");
        return inlined > 0;
    }

    private int findInvokeDynamic() {
        AtomicInteger total = new AtomicInteger();
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof InvokeDynamicInsnNode) {
                        InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) abstractInsnNode;
                        if (dyn.bsmArgs.length > 0 && dyn.bsmArgs[0] instanceof String) {
                            total.incrementAndGet();
                        }
                    }
                }
            });
        });
        return total.get();
    }

    private int inlineInvokeDynamic(int expected) {
        AtomicInteger total = new AtomicInteger();
        final boolean[] alerted = new boolean[100];

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new DisabledFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                if (type.getDescriptor().equals("[C")) {
                    if (!(target.value() instanceof char[])) {
                        return false;
                    }
                }
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

        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof InvokeDynamicInsnNode) {
                        InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) abstractInsnNode;
                        if (dyn.bsmArgs.length == 1 && dyn.bsmArgs[0] instanceof String) {
                            Handle bootstrap = dyn.bsm;
                            ClassNode bootstrapClassNode = classes.get(bootstrap.getOwner());
                            MethodNode bootstrapMethodNode = bootstrapClassNode.methods.stream().filter(mn -> mn.name.equals(bootstrap.getName()) && mn.desc.equals(bootstrap.getDesc())).findFirst().orElse(null);
                            List<JavaValue> args = new ArrayList<>();
                            args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
                            args.add(JavaValue.valueOf(dyn.name)); //dyn method name
                            args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
                            for (Object o : dyn.bsmArgs) {
                                args.add(JavaValue.valueOf(o));
                            }
                            try {
                                Context context = new Context(provider);
                                context.dictionary = this.classpath;

                                JavaMethodHandle result = MethodExecutor.execute(bootstrapClassNode, bootstrapMethodNode, args, null, context);
                                String clazz = result.clazz.replace('.', '/');
                                MethodInsnNode replacement = null;
                                switch (result.type) {
                                    case "virtual":
                                        replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ? 
                                        	 Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, result.name, result.desc,
                                        	 (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
                                        break;
                                    case "static":
                                        replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, result.name, result.desc, false);
                                        break;
                                }
                                methodNode.instructions.insert(abstractInsnNode, replacement);
                                methodNode.instructions.remove(abstractInsnNode);
                                total.incrementAndGet();
                                int x = (int) ((total.get() * 1.0d / expected) * 100);
                                if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                    System.out.println("[Stringer] [InvokedynamicTransformer] Done " + x + "%");
                                    alerted[x - 1] = true;
                                }
                            } catch (ExecutionException ex) {
                                if (ex.getCause() != null) {
                                    ex.getCause().printStackTrace(System.out);
                                }
                                throw ex;
                            } catch (Throwable t) {
                                System.out.println(classNode.name);
                                throw t;
                            }
                        }
                    }
                }
            });
        });
        return total.get();
    }

    private int cleanup() {
        AtomicInteger total = new AtomicInteger();
        classNodes().forEach(classNode -> {
            Iterator<MethodNode> it = classNode.methods.iterator();
            while (it.hasNext()) {
                MethodNode node = it.next();
                if (node.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/Object;")) {
                    it.remove();
                    total.incrementAndGet();
                }
            }
        });
        return total.get();
    }
}
