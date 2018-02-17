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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v9;

import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.matcher.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v9.utils.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public class StringEncryptionTransformer extends Transformer<TransformerConfig> implements Opcodes {
    @Override
    public boolean transform() throws Throwable {
        VirtualMachine vm = TransformerHelper.newVirtualMachine(this);

//        vm.beforeCallHooks.add(info -> {
//            if (!info.is("java/lang/Class", "forName0", "(Ljava/lang/String;ZLjava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/Class;")) {
//                return;
//            }
//            List<StackTraceHolder> stacktrace = vm.getStacktrace();
//            if (stacktrace.size() < 3) {
//                return;
//            }
//            if (!classes.containsKey(stacktrace.get(2).getClassNode().name)) {
//                return;
//            }
//            info.getParams().set(1, vm.newBoolean(false));
//        });

        int decrypted = 0;

        for (ClassNode classNode : classes.values()) {
            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                InstructionModifier modifier = new InstructionModifier();

                Map<LabelNode, LabelNode> cloneMap = Utils.generateCloneMap(methodNode.instructions);

                for (AbstractInsnNode insn : TransformerHelper.instructionIterator(methodNode)) {
                    InstructionMatcher matcher = Constants.DECRYPT_PATTERN.matcher(insn);
                    if (!matcher.find()) {
                        continue;
                    }

                    MethodNode decryptorMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodNode.name, "()Ljava/lang/String;", null, null);
                    for (AbstractInsnNode matched : matcher.getCapturedInstructions("all")) {
                        decryptorMethod.instructions.add(matched.clone(cloneMap));
                    }
                    decryptorMethod.instructions.add(new InsnNode(ARETURN));

                    classNode.methods.add(decryptorMethod);
                    try {
                        MethodExecution result = vm.execute(classNode, decryptorMethod);
                        classNode.methods.remove(decryptorMethod);

                        String decryptedStr = vm.convertJavaObjectToString(result.getReturnValue());

                        logger.debug("Decrypted {} {}{}, {}", classNode.name, methodNode.name, methodNode.desc, decryptedStr);
                        decrypted++;

                        modifier.removeAll(matcher.getCapturedInstructions("all"));
                        modifier.replace(matcher.getEnd(), new LdcInsnNode(decryptedStr));
                    } catch (VMException e) {
                        TransformerHelper.dumpMethod(decryptorMethod);
                        vm.convertException(e).printStackTrace();
                        return false;
                    } catch (Throwable t) {
                        TransformerHelper.dumpMethod(decryptorMethod);
                        t.printStackTrace();
                        return false;
                    }
                }

                modifier.apply(methodNode);
            }
        }

        vm.shutdown();

        logger.info("Decrypted {} strings", decrypted);
        return true;
    }
}
