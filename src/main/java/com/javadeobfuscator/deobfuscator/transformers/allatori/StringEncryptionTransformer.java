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

package com.javadeobfuscator.deobfuscator.transformers.allatori;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public class StringEncryptionTransformer extends Transformer<StringEncryptionTransformer.Config> {

    public static class Config extends TransformerConfig {
        private boolean cleanup = true;

        public Config() {
            super(StringEncryptionTransformer.class);
        }

        public boolean isCleanup() {
            return cleanup;
        }

        public void setCleanup(boolean cleanup) {
            this.cleanup = cleanup;
        }
    }

    @Override
    public boolean transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
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

        AtomicInteger count = new AtomicInteger();
        Set<MethodNode> decryptor = new HashSet<>();

        System.out.println("[Allatori] [StringEncryptionTransformer] Starting");

        for (ClassNode classNode : classes.values()) {
            for (MethodNode method : classNode.methods) {
                InstructionModifier modifier = new InstructionModifier();
                Frame<SourceValue>[] frames;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
                } catch (AnalyzerException e) {
                    oops("unexpected analyzer exception", e);
                    continue;
                }

                for (AbstractInsnNode ain : TransformerHelper.instructionIterator(method)) {
                    if (ain.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode m = (MethodInsnNode) ain;
                    String targetClass = m.owner;
                    if (!m.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;") && !m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                        continue;
                    }
                    Frame<SourceValue> f = frames[method.instructions.indexOf(m)];
                    if (f.getStack(f.getStackSize() - 1).insns.size() != 1) {
                        continue;
                    }
                    AbstractInsnNode insn = f.getStack(f.getStackSize() - 1).insns.iterator().next();
                    if (insn.getOpcode() != Opcodes.LDC) {
                        continue;
                    }
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (!(ldc.cst instanceof String)) {
                        continue;
                    }

                    Context context = new Context(provider);
                    context.push(classNode.name, method.name, getDeobfuscator().getConstantPool(classNode).getSize());

                    ClassNode targetClassNode = classes.get(targetClass);
                    if (targetClassNode == null) {
                        continue;
                    }

                    MethodNode decrypterNode = targetClassNode.methods.stream()
                            .filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc))
                            .findFirst().orElse(null);
                    if (decrypterNode == null || decrypterNode.instructions.getFirst() == null) {
                        continue;
                    }

                    if (decryptor.contains(decrypterNode) || isAllatoriMethod(decrypterNode)) {
                        patchMethod(decrypterNode);
                        try {
                            ldc.cst = MethodExecutor.execute(targetClassNode, decrypterNode,
                                    Collections.singletonList(JavaValue.valueOf(ldc.cst)), null, context);
                            modifier.remove(m);
                            decryptor.add(decrypterNode);
                            count.getAndIncrement();
                        } catch (Throwable t) {
                            System.out.println("Error while decrypting Allatori string.");
                            System.out.println("Are you sure you're deobfuscating something obfuscated by Allatori?");
                            System.out.println(classNode.name + " " + method.name + method.desc + " " + m.owner + " " + m.name + m.desc);
                            t.printStackTrace(System.out);
                        }
                    }
                }
                modifier.apply(method);
            }
        }
        System.out.println("[Allatori] [StringEncryptionTransformer] Decrypted " + count + " encrypted strings");
        if (getConfig().isCleanup()) {
            System.out.println("[Allatori] [StringEncryptionTransformer] Removed " + cleanup(decryptor) + " decryption methods");
        }
        System.out.println("[Allatori] [StringEncryptionTransformer] Done");
        return count.get() > 0;
    }

    public static boolean isAllatoriMethod(MethodNode decryptorNode) {
        return isAllatoriMethod1(decryptorNode) || isAllatoriMethod2(decryptorNode);
    }

    private static boolean isAllatoriMethod1(MethodNode decryptorNode) {
        boolean isAllatori = true;
        isAllatori = isAllatori && TransformerHelper.containsInvokeVirtual(decryptorNode, "java/lang/String", "charAt", "(I)C");
        isAllatori = isAllatori && TransformerHelper.containsInvokeVirtual(decryptorNode, "java/lang/String", "length", "()I");
        isAllatori = isAllatori && TransformerHelper.containsInvokeSpecial(decryptorNode, "java/lang/String", "<init>", null);
        isAllatori = isAllatori && TransformerHelper.countOccurencesOf(decryptorNode, IXOR) > 2;
        isAllatori = isAllatori && TransformerHelper.countOccurencesOf(decryptorNode, NEWARRAY) > 0;
        return isAllatori;
    }

    private static boolean isAllatoriMethod2(MethodNode decryptorNode) {
        Map<Integer, AtomicInteger> map = TransformerHelper.calcOpcodeOccurenceMap(decryptorNode);
        AtomicInteger zero = new AtomicInteger();
        boolean isAllatori = true;
        isAllatori = isAllatori && map.getOrDefault(CASTORE, zero).get() > 0;
        isAllatori = isAllatori && map.getOrDefault(NEW, zero).get() > 0;
        isAllatori = isAllatori && map.getOrDefault(IINC, zero).get() > 0;
        isAllatori = isAllatori && map.getOrDefault(IXOR, zero).get() > 0;
        isAllatori = isAllatori && map.getOrDefault(I2C, zero).get() > 0;
        isAllatori = isAllatori && map.getOrDefault(ARETURN, zero).get() > 0;
        isAllatori = isAllatori && map.getOrDefault(NEWARRAY, zero).get() == 1;
        isAllatori = isAllatori && TransformerHelper.containsInvokeVirtual(decryptorNode, "java/lang/String", "charAt", "(I)C");
        isAllatori = isAllatori && TransformerHelper.containsInvokeVirtual(decryptorNode, "java/lang/String", "length", "()I");
        isAllatori = isAllatori && TransformerHelper.containsInvokeSpecial(decryptorNode, "java/lang/String", "<init>", null);
        return isAllatori;
    }

    private void patchMethod(MethodNode method) {
        boolean getStackTrace = false;
        boolean getClassName = false;
        for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof MethodInsnNode)) {
                continue;
            }
            String name = ((MethodInsnNode) i).name;
            if (!getStackTrace && name.equals("getStackTrace")) {
                getStackTrace = true;
                if (getClassName) {
                    break;
                }
            } else if (!getClassName && name.equals("getClassName")) {
                getClassName = true;
                if (getStackTrace) {
                    break;
                }
            }
        }
        if (!getClassName || !getStackTrace) {
            return;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.NEW) {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                if (typeInsn.desc.endsWith("Exception") || typeInsn.desc.endsWith("Error")) {
                    typeInsn.desc = "java/lang/RuntimeException";
                }
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (methodInsn.owner.endsWith("Exception") || methodInsn.owner.endsWith("Error")) {
                    methodInsn.owner = "java/lang/RuntimeException";
                }
            }
        }
    }

    private int cleanup(Set<MethodNode> toRemove) {
        //remove methods from removal set which are still called
        classNodes().forEach(node -> node.methods.forEach(methodNode -> {
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn.getOpcode() != INVOKESTATIC) {
                    continue;
                }
                MethodInsnNode m = (MethodInsnNode) insn;
                ClassNode owner = classes.get(m.owner);
                if (owner == null) {
                    continue;
                }
                MethodNode mNode = owner.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
                toRemove.remove(mNode);
            }
        }));
        AtomicInteger count = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
            for (Iterator<MethodNode> it = classNode.methods.iterator(); it.hasNext(); ) {
                MethodNode methodNode = it.next();
                if (toRemove.remove(methodNode)) {
                    it.remove();
                    count.getAndIncrement();
                }
            }
        });
        return count.get();
    }
}
