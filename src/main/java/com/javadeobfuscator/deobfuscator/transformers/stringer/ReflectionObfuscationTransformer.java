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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.DictionaryMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.FieldNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class ReflectionObfuscationTransformer extends Transformer {
    private Set<ClassNode> remove = new HashSet<>();
    
    public ReflectionObfuscationTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() {
        System.out.println("[Stringer] [ReflectionObfuscationTransformer] Starting");
        int count = count();
        System.out.println("[Stringer] [ReflectionObfuscationTransformer] Found " + count + " reflection obfuscation calls");
        if (count > 0) {
            int decrypted = decrypt(count);
            System.out.println("[Stringer] [ReflectionObfuscationTransformer] Deobfuscated " + decrypted + " reflection obfuscation calls");
            int cleanedup = cleanup();
            System.out.println("[Stringer] [ReflectionObfuscationTransformer] Removed " + cleanedup + " reflection obfuscation classes");
        }
        System.out.println("[Stringer] [ReflectionObfuscationTransformer] Done");
    }

    private int count() {
        AtomicInteger total = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                        WrappedClassNode wrappedTarget = classpath.get(methodInsnNode.owner);
                        if (wrappedTarget != null) {
                            ClassNode target = wrappedTarget.getClassNode();
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

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new PrimitiveFieldProvider());
        provider.register(new MappedFieldProvider());
        provider.register(new DictionaryMethodProvider(this.classes));
        provider.register(new JVMMethodProvider());
        provider.register(new MethodProvider() {
            public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
                Object val = targetObject != null && targetObject.value instanceof JavaMethod ? targetObject.value : null;
                if (val != null) {
                    myMethod.set((JavaMethod) val);
                }
                return val;
            }

            public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
                return true;
            }
        });

        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(StackObject target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(StackObject target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean checkEquality(StackObject first, StackObject second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean canCheckcast(StackObject target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
                return false;
            }
        });

        Set<ClassNode> initted = new HashSet<>();

        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                        WrappedClassNode wrappedTarget = classpath.get(methodInsnNode.owner);
                        if (wrappedTarget != null) {
                            ClassNode target = wrappedTarget.getClassNode();
                            MethodNode method = target.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            if (method != null) {
                                if (isValidTarget(target, method)) {
                                    if (initted.add(target)) {
                                        Context context = new Context(provider);
                                        context.dictionary = this.classpath;
                                        MethodNode clinit = target.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                        MethodExecutor.execute(wrappedTarget, clinit, new ArrayList<>(), null, context);
                                    }
                                    remove.add(target);
                                    List<StackObject> args = new ArrayList<>();
                                    for (Type t : Type.getArgumentTypes(method.desc)) {
                                        Class<?> prim = PrimitiveUtils.getPrimitiveByName(t.getClassName());
                                        if (prim != null) {
                                            args.add(StackObject.forPrimitive(prim));
                                        } else {
                                            args.add(StackObject.forPrimitive(Object.class));
                                        }
                                    }
                                    Context context = new Context(provider);
                                    context.dictionary = this.classpath;
                                    MethodExecutor.execute(wrappedTarget, method, args, null, context);
                                    JavaMethod result = myMethod.get();
                                    
                                    String partDesc = Utils.descFromTypes(Type.getArgumentTypes(result.getMethodNode().desc));
                                    
                                    
                                    methodInsnNode.owner = result.getDeclaringClass().getName().replace('.', '/');
                                    methodInsnNode.name = result.getName();
                                    ClassNode cn = result.getDeclaringClass().getClassNode();
                                    MethodNode mn = cn.methods.stream().filter(m -> m.name.equals(result.getName()) && m.desc.startsWith(partDesc)).findFirst().orElse(null);
                                    methodInsnNode.desc = mn.desc;
                                    methodInsnNode.setOpcode(Modifier.isStatic(mn.access) ? Opcodes.INVOKESTATIC : Opcodes.INVOKEVIRTUAL);
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
}
