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

package com.javadeobfuscator.deobfuscator.transformers.allatori.string;

import com.javadeobfuscator.deobfuscator.asm.source.*;
import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import com.javadeobfuscator.javavm.mirrors.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;

public class StringEncryptionTransformer extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws WrongTransformerException {
        VirtualMachine vm = TransformerHelper.newVirtualMachine(this);

        // We don't need to initialize for Allatori
        for (ClassNode classNode : classes.values()) {
            JavaClass.forName(vm, classNode.name).setInitializationState(JavaClass.InitializationState.INITIALIZED, null);
        }

        int decrypted = 0;

        for (ClassNode classNode : classes.values()) {
            for (MethodNode methodNode : classNode.methods) {
                InstructionModifier modifier = new InstructionModifier();

                Frame<SourceValue>[] frames;
                try {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
                } catch (AnalyzerException e) {
                    oops("unexpected analyzer exception", e);
                    continue;
                }

                for (AbstractInsnNode abstractInsnNode : TransformerHelper.instructionIterator(methodNode)) {
                    if (abstractInsnNode.getOpcode() != INVOKESTATIC) continue;

                    MethodInsnNode methodInsnNode = (MethodInsnNode) abstractInsnNode;
                    if (!methodInsnNode.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) continue;

                    Frame<SourceValue> currentFrame = frames[methodNode.instructions.indexOf(methodInsnNode)];

                    MethodNode decryptorMethod = new MethodNode(ACC_STATIC | ACC_PUBLIC, "decrypt" + decrypted, "()Ljava/lang/String;", null, null);
                    Optional<Object> consensus = SourceFinder.findSource(methodNode, frames, new ArrayList<>(), new ConstantPropagatingSourceFinder(), methodInsnNode, currentFrame.getStack(currentFrame.getStackSize() - 1)).consensus();
                    if (!consensus.isPresent()) continue;

                    decryptorMethod.instructions.add(new LdcInsnNode(consensus.get()));
                    decryptorMethod.instructions.add(methodInsnNode.clone(null));
                    decryptorMethod.instructions.add(new InsnNode(ARETURN));

                    MethodExecution execution;

                    ClassNode decryptorNode = new ClassNode();
                    decryptorNode.visit(49, ACC_PUBLIC, "decryptor" + decrypted, null, "java/lang/Object", null);
                    decryptorNode.methods.add(decryptorMethod);
                    try {
                        execution = vm.execute(decryptorNode, decryptorMethod);
                    } catch (VMException e) {
                        oops("unexpected vm exception", e);
                        TransformerHelper.dumpMethod(decryptorMethod);
                        continue;
                    } catch (Throwable e) {
                        oops("unexpected severe vm exception", e);
                        TransformerHelper.dumpMethod(decryptorMethod);
                        continue;
                    }

                    String value = vm.convertJavaObjectToString(execution.getReturnValue());
                    if (value == null) continue;

                    logger.info("Decrypted string in {} {}{}: {}", classNode.name, methodNode.name, methodNode.desc, value);

                    modifier.replace(methodInsnNode, new InsnNode(POP), new LdcInsnNode(value));
                    decrypted++;
                }

                modifier.apply(methodNode);
            }
        }

        vm.shutdown();

        logger.info("Decrypted {} strings", decrypted);
        return decrypted > 0;
    }
}
