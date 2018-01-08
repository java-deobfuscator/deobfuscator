/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
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

package com.javadeobfuscator.deobfuscator.transformers.zelix.string;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.*;
import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.matcher.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import com.javadeobfuscator.javavm.mirrors.*;
import com.javadeobfuscator.javavm.values.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;
import java.util.function.Supplier;

/**
 * This is a transformer for the enhanced version of Zelix string encryption
 */
@TransformerConfig.ConfigOptions(configClass = EnhancedStringEncryptionTransformer.Config.class)
public class EnhancedStringEncryptionTransformer extends Transformer<EnhancedStringEncryptionTransformer.Config> implements Opcodes {
    private static final InstructionPattern DECRYPT_PATTERN = new InstructionPattern(
            new LoadIntStep(),
            new LoadIntStep(),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(II)Ljava/lang/String;", false), "invoke")
    );

    @Override
    public boolean transform() throws Throwable, WrongTransformerException {
        VirtualMachine vm = TransformerHelper.newVirtualMachine(this);

        for (ClassNode classNode : classes.values()) {
            // Just YOLO through the initialization process
            try {
                vm.initialize(JavaClass.forName(vm, classNode.name));
            } catch (VMException e) {
                JavaClass.forName(vm, classNode.name).setInitializationState(JavaClass.InitializationState.INITIALIZED, null); // of course we initialized it
                logger.debug("Exception while initializing {}, should be fine", classNode.name);
                logger.debug(vm.exceptionToString(e));
            } catch (Throwable e) {
                JavaClass.forName(vm, classNode.name).setInitializationState(JavaClass.InitializationState.INITIALIZED, null); // of course we initialized it
                logger.debug("(Severe) Exception while initializing {}, should be fine", classNode.name, e);
            }

            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                InstructionModifier modifier = new InstructionModifier();

                // If we want the slow version, memoize the analysis
                Supplier<Frame<SourceValue>[]> framesSupplier = Suppliers.memoize(() -> {
                    try {
                        return new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
                    } catch (AnalyzerException e) {
                        oops("unexpected analyzer exception", e);
                        return null;
                    }
                })::get;

                for (AbstractInsnNode insnNode : TransformerHelper.instructionIterator(methodNode)) {
                    AbstractInsnNode invocation;
                    MethodNode decryptNode;

                    if (!getConfig().isSlowlyDetermineMagicNumbers()) {
                        InstructionMatcher matcher = DECRYPT_PATTERN.matcher(insnNode);
                        if (!matcher.find()) continue;

                        decryptNode = new MethodNode(ASM6, ACC_PUBLIC | ACC_STATIC, "Decryptor", "()Ljava/lang/String;", null, null);
                        InsnList decryptInsns = new InsnList();
                        for (AbstractInsnNode matched : matcher.getCapturedInstructions("all")) {
                            decryptInsns.add(matched.clone(null));
                        }
                        decryptInsns.add(new InsnNode(ARETURN));
                        decryptNode.instructions = decryptInsns;
                        invocation = matcher.getCapturedInstruction("invoke");
                    } else {
                        if (insnNode.getOpcode() != INVOKESTATIC) continue;

                        MethodInsnNode methodInsnNode = (MethodInsnNode) insnNode;
                        if (!methodInsnNode.desc.equals("(II)Ljava/lang/String;")) continue;

                        Frame<SourceValue>[] frames = framesSupplier.get();
                        if (frames == null) continue;

                        Frame<SourceValue> frame = frames[methodNode.instructions.indexOf(insnNode)];
                        if (frame == null) continue;

                        SourceValue cst1 = frame.getStack(frame.getStackSize() - 2);
                        SourceValue cst2 = frame.getStack(frame.getStackSize() - 1);
                        if (cst1.insns.size() != 1) continue;
                        if (cst2.insns.size() != 1) continue;

                        AbstractInsnNode insn1 = cst1.insns.iterator().next();
                        AbstractInsnNode insn2 = cst2.insns.iterator().next();
                        if (insn1.getOpcode() != SIPUSH) continue;
                        if (insn2.getOpcode() != SIPUSH) continue;

                        decryptNode = new MethodNode(ASM6, ACC_PUBLIC | ACC_STATIC, "Decryptor", "()Ljava/lang/String;", null, null);
                        InsnList decryptInsns = new InsnList();
                        decryptInsns.add(insn1.clone(null));
                        decryptInsns.add(insn2.clone(null));
                        decryptInsns.add(methodInsnNode.clone(null));
                        decryptInsns.add(new InsnNode(ARETURN));
                        decryptNode.instructions = decryptInsns;
                        invocation = methodInsnNode;
                    }

                    JavaWrapper result;

                    classNode.methods.add(decryptNode);
                    try {
                        result = vm.execute(classNode, decryptNode).getReturnValue();
                    } catch (VMException e) {
                        logger.debug("Exception while decrypting a string in {} {}{}", classNode.name, methodNode.name, methodNode.desc);
                        logger.debug(vm.exceptionToString(e));
                        continue;
                    } finally {
                        classNode.methods.remove(decryptNode);
                    }

                    if (result == null) {
                        logger.info("Warning: decrypted null string in {} {}{}", classNode.name, methodNode.name, methodNode.desc);
                        continue;
                    }

                    String decrypted = vm.convertJavaObjectToString(result);
                    logger.info("Decrypted string in {} {}{}: {}", classNode.name, methodNode.name, methodNode.desc, decrypted);

                    modifier.replace(invocation, new InsnNode(POP2), new LdcInsnNode(decrypted));
                }

                modifier.apply(methodNode);
            }
        }

        vm.shutdown();
        return false;
    }

    public static class Config extends TransformerConfig {

        @JsonProperty("slowly-determine-magic-numbers")
        private boolean slowlyDetermineMagicNumbers;

        public Config() {
            super(EnhancedStringEncryptionTransformer.class);
        }

        public boolean isSlowlyDetermineMagicNumbers() {
            return slowlyDetermineMagicNumbers;
        }

        public void setSlowlyDetermineMagicNumbers(boolean slowlyDetermineMagicNumbers) {
            this.slowlyDetermineMagicNumbers = slowlyDetermineMagicNumbers;
        }
    }
}
