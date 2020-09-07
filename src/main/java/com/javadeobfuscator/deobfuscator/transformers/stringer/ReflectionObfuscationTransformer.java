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

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.DictionaryMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ReflectionObfuscationTransformer extends Transformer<TransformerConfig> {
    private Set<ClassNode> remove = new HashSet<>();

    @Override
    public boolean transform() {
        System.out.println("[Stringer] [ReflectionObfuscationTransformer] Starting");
        int count = count();
        System.out.println("[Stringer] [ReflectionObfuscationTransformer] Found " + count + " reflection obfuscation calls");
        int decrypted = 0;
        if (count > 0) {
            decrypted = decrypt(count);
            System.out.println("[Stringer] [ReflectionObfuscationTransformer] Deobfuscated " + decrypted + " reflection obfuscation calls");
            int cleanedup = cleanup();
            System.out.println("[Stringer] [ReflectionObfuscationTransformer] Removed " + cleanedup + " reflection obfuscation classes");
        }
        System.out.println("[Stringer] [ReflectionObfuscationTransformer] Done");
        return decrypted > 0;
    }

    private int count() {
        AtomicInteger total = new AtomicInteger();
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                        ClassNode target = classpath.get(methodInsnNode.owner);
                        if (target != null) {
                            MethodNode method = target.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            if (method != null) {
                                if (isValidTarget(target, method)) {
                                    total.incrementAndGet();
                                }
                            }
                        }
                    }
                }
            });
        });
        return total.get();
    }

    private boolean isValidTarget(ClassNode classNode, MethodNode methodNode) {
        if (Modifier.isStatic(methodNode.access) && classNode.fields.size() >= 2) {
            FieldNode objArray = classNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;") && Modifier.isStatic(fn.access)).findFirst().orElse(null);
            FieldNode classArray = classNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Class;") && Modifier.isStatic(fn.access)).findFirst().orElse(null);
            return objArray != null && classArray != null;
        }
        return false;
    }

    private int decrypt(int expected) {
        AtomicInteger total = new AtomicInteger();
        final boolean[] alerted = new boolean[100];

        AtomicReference<JavaMethod> myMethod = new AtomicReference<>();

        Set<ClassNode> initted = new HashSet<>();

        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                        ClassNode target = classpath.get(methodInsnNode.owner);
                        if (target != null) {
                            MethodNode method = target.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            if (method != null) {
                                if (isValidTarget(target, method)) {
                                    DelegatingProvider provider = new DelegatingProvider();
                                    provider.register(new MethodProvider() {
                                        public Object invokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
                                            Object val = targetObject != null && targetObject.value() instanceof JavaMethod ? targetObject.value() : null;
                                            if (val != null) {
                                                myMethod.set((JavaMethod) val);
                                                throw new StopExecution();
                                            }
                                            return val;
                                        }

                                        public boolean canInvokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
                                            return className.equals("java/lang/reflect/Method") && (methodName.equals("setAccessible") || methodName.equals("invoke"));
                                        }
                                    });
                                    provider.register(new PrimitiveFieldProvider());
                                    provider.register(new MappedFieldProvider());
                                    provider.register(new DictionaryMethodProvider(this.classes));
                                    provider.register(new JVMMethodProvider());

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

                                    if (initted.add(target) || true) {
                                        Context context = new Context(provider);
                                        context.dictionary = this.classpath;
                                        context.constantPools = getDeobfuscator().getConstantPools();
                                        context.file = getDeobfuscator().getConfig().getInput();
                                        MethodNode clinit = target.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                        MethodExecutor.execute(target, clinit, new ArrayList<>(), null, context);
                                    }
                                    remove.add(target);
                                    List<JavaValue> args = new ArrayList<>();
                                    for (Type t : Type.getArgumentTypes(method.desc)) {
                                        Class<?> prim = PrimitiveUtils.getPrimitiveByName(t.getClassName());
                                        if (prim != null) {
                                            args.add(JavaValue.forPrimitive(prim));
                                        } else {
                                            args.add(new JavaObject(null, "java/lang/Object"));
                                        }
                                    }
                                    Context context = new Context(provider);
                                    context.dictionary = this.classpath;
                                    context.file = getDeobfuscator().getConfig().getInput();
                                    try {
                                        MethodExecutor.execute(target, method, args, null, context);
                                    } catch (StopExecution ex) {
                                    }
                                    JavaMethod result = myMethod.get();

                                    if (result != null) {

                                        String partDesc = Utils.descFromTypes(Type.getArgumentTypes(result.getMethodNode().desc));


                                        methodInsnNode.owner = result.getDeclaringClass().getName().replace('.', '/');
                                        methodInsnNode.name = result.getName();
                                        ClassNode cn = result.getDeclaringClass().getClassNode();
                                        MethodNode mn = cn.methods.stream().filter(m -> m.name.equals(result.getName()) && m.desc.startsWith(partDesc)).findFirst().orElse(null);
                                        methodInsnNode.desc = mn.desc;
                                        methodInsnNode.setOpcode(Modifier.isStatic(mn.access) ? Opcodes.INVOKESTATIC : 
                                        	(cn.access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL);
                                        methodInsnNode.itf = methodInsnNode.getOpcode() == Opcodes.INVOKEINTERFACE;
                                        total.incrementAndGet();
                                        int x = (int) ((total.get() * 1.0d / expected) * 100);
                                        if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                            System.out.println("[Stringer] [ReflectionObfuscationTransformer] Done " + x + "%");
                                            alerted[x - 1] = true;
                                        }
                                    }
                                }
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
        remove.forEach(str -> {
            total.incrementAndGet();
            classes.remove(str.name);
            classpath.remove(str.name);
        });
        return total.get();
    }

    private class StopExecution extends ExecutionException {
        public StopExecution() {
            super("");
        }
    }
}
