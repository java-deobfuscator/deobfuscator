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

import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.executor.values.*;
import com.javadeobfuscator.deobfuscator.matcher.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import com.javadeobfuscator.javavm.mirrors.*;
import com.javadeobfuscator.javavm.values.*;
import com.javadeobfuscator.javavm.values.JavaArray;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;
import java.util.stream.*;

/**
 * This is a transformer for the enhanced version of Zelix string encryption
 */
public class EnhancedStringEncryptionTransformer extends Transformer<TransformerConfig> implements Opcodes {
    private static final InstructionPattern DECRYPT_PATTERN = new InstructionPattern(
            new LoadIntStep(),
            new LoadIntStep(),
            new InvocationStep(INVOKESTATIC, null, null, "(II)Ljava/lang/String;", false)
    );

    @Override
    public boolean transform() throws Throwable, WrongTransformerException {
        VirtualMachine vm = TransformerHelper.newVirtualMachine(this);

        for (ClassNode classNode : classes.values()) {
            // Just YOLO through the initialization process
            try {
                vm.initialize(JavaClass.forName(vm, classNode.name));
            } catch (VMException e) {
                logger.debug("Exception while initializing {}, should be fine", classNode.name);
                logger.debug(vm.exceptionToString(e));
            } catch (Throwable e) {
                logger.debug("(Severe) Exception while initializing {}, should be fine", classNode.name, e);
            }
            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                InstructionModifier modifier = new InstructionModifier();

                for (AbstractInsnNode insnNode : TransformerHelper.instructionIterator(methodNode)) {
                    InstructionMatcher matcher = DECRYPT_PATTERN.matcher(insnNode);
                    if (!matcher.find()) continue;

                    MethodNode decryptNode = new MethodNode(ASM6, ACC_PUBLIC | ACC_STATIC, "Decryptor", "()Ljava/lang/String;", null, null);
                    InsnList decryptInsns = new InsnList();
                    for (AbstractInsnNode matched : matcher.getCapturedInstructions("all")) {
                        decryptInsns.add(matched.clone(null));
                    }
                    decryptInsns.add(new InsnNode(ARETURN));
                    decryptNode.instructions = decryptInsns;

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

                    modifier.removeAll(matcher.getCapturedInstructions("all"));
                    modifier.replace(matcher.getCapturedInstructions("all").get(0), new LdcInsnNode(decrypted));
                }

                modifier.apply(methodNode);
            }
        }

        vm.shutdown();
        return false;
    }
}
