/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.general;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.FieldFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// todo WIP implementation of a generic constant propogation transformer
// the idea is, at the end of every INVOKE* call, and before every PUT{STATIC,FIELD} call, check if anything on the stack
// can be represented by constant values
// obviously this is much more complicated than it sounds, but hey. it'll be nice to have
public class ConstantPropogation extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                if (Modifier.isAbstract(methodNode.access) || Modifier.isNative(methodNode.access)) {
                    return;
                }

                // we only care about static methods for now
                if (!Modifier.isStatic(methodNode.access)) {
                    return;
                }

                // also we only care about the init function
                if (!methodNode.name.equals("<clinit>")) {
                    return;
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

                Context context = new Context(provider);

                Map<AbstractInsnNode, InsnList> insertBefore = new HashMap<>();

                AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode);

                for (AbstractInsnNode now = methodNode.instructions.getFirst(); now != null; now = now.getNext()) {
                    if (now.getOpcode() == Opcodes.PUTSTATIC) {
                        boolean needsConstantProp = false;
                        List<Frame> frames = result.getFrames().get(now);
                        for (Frame frame : frames) {
                            FieldFrame fieldFrame = (FieldFrame) frame;
                            if (!(fieldFrame.getObj() instanceof LdcFrame)) {
                                needsConstantProp = true;
                            }
                        }

                        if (needsConstantProp) {
                            AbstractInsnNode tmp = now;
                            context.breakpoint(now, info -> {
                                JavaValue store = info.getStack().get(0);
                                if (store instanceof JavaObject) {
                                    JavaObject object = (JavaObject) store;
                                    if (/*object.type().equals("[Ljava/lang/String;")*/ ((FieldInsnNode) tmp).desc.equals("[Ljava/lang/String;")) { // this executor doesn't do types properly. bleh
                                        boolean actuallyNeedsInsert = false; // only insert if array has non-null element;

                                        Object[] arr = object.as(Object[].class);
                                        InsnList insert = new InsnList();
                                        insert.add(new InsnNode(Opcodes.POP));
                                        insert.add(new LdcInsnNode(arr.length));
                                        insert.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
                                        for (int i = 0; i < arr.length; i++) {
                                            insert.add(new InsnNode(Opcodes.DUP));
                                            insert.add(new LdcInsnNode(i));
                                            Object o = arr[i];
                                            if (o == null) {
                                                insert.add(new InsnNode(Opcodes.ACONST_NULL));
                                            } else {
                                                actuallyNeedsInsert = true;
                                                insert.add(new LdcInsnNode(o));
                                            }
                                            insert.add(new InsnNode(Opcodes.AASTORE));
                                        }

                                        if (actuallyNeedsInsert)
                                            insertBefore.put(tmp, insert);
                                    }
                                }
                            }, null);
                        }
                    } else if (now.getOpcode() == Opcodes.INVOKESTATIC) {
                        context.breakpoint(now, null, info -> {
                        });
                    }
                }

                try {
                    MethodExecutor.execute(classes.get(classNode.name), methodNode, Collections.emptyList(), null, context);
                } catch (Throwable t) {
                    t.printStackTrace(System.out);
                }

                insertBefore.forEach((k, v) -> methodNode.instructions.insertBefore(k, v));
            });
        });

        return true;
    }
}
