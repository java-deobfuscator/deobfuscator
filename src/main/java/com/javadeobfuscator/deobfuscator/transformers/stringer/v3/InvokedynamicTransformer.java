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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v3;

import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import com.javadeobfuscator.javavm.mirrors.*;
import com.javadeobfuscator.javavm.nativeimpls.*;
import com.javadeobfuscator.javavm.values.*;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

public class InvokedynamicTransformer extends Transformer<TransformerConfig> implements Opcodes {
    private VirtualMachine vm;
    private AtomicReference<JavaWrapper> capturedMethod = new AtomicReference<>();

    @Override
    public boolean transform() throws Throwable {
        vm = TransformerHelper.newVirtualMachine(this);
        prepareVM();

        JavaClass reflectMethod = JavaClass.forName(vm, "java/lang/reflect/Method");

        int decrypted = 0;

        for (ClassNode classNode : classes.values()) {
            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                InstructionModifier modifier = new InstructionModifier();

                Map<LabelNode, LabelNode> cloneMap = Utils.generateCloneMap(methodNode.instructions);
                int decryptorMethodCount = 0;

                for (AbstractInsnNode insn : TransformerHelper.instructionIterator(methodNode)) {
                    if (!(insn instanceof InvokeDynamicInsnNode)) {
                        continue;
                    }

                    InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) insn;
                    if (!invokeDynamicInsnNode.bsm.getOwner().equals(classNode.name)) {
                        continue;
                    }

                    MethodNode decryptorMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "Decrypt" + decryptorMethodCount++, "()V", null, null);

                    Type[] argTypes = Type.getArgumentTypes(invokeDynamicInsnNode.desc);
                    for (Type type : argTypes) {
                        decryptorMethod.instructions.add(TransformerHelper.zero(type));
                    }
                    decryptorMethod.instructions.add(invokeDynamicInsnNode.clone(cloneMap));
                    decryptorMethod.instructions.add(new InsnNode(RETURN));

                    capturedMethod.set(null);

                    classNode.methods.add(decryptorMethod);
                    try {
                        vm.execute(classNode, decryptorMethod, null, Collections.<JavaWrapper>emptyList());
                    } catch (Throwable e) {
                        // dont care lol
//                        vm.printException(e);
                    }
                    classNode.methods.remove(decryptorMethod);

                    if (capturedMethod.get() == null) {
                        oops("Expected non-null java/lang/reflect/Method");
                        continue;
                    }
                    if (capturedMethod.get().getJavaClass() != reflectMethod) {
                        oops("Expected java/lang/reflect/Method, got " + capturedMethod.get().getJavaClass());
                        continue;
                    }

                    JavaWrapper classObj = capturedMethod.get().asObject().getField("clazz", "Ljava/lang/Class;");
                    JavaClass clazz = java_lang_Class.getJavaClass(classObj);
                    ClassNode indyClass = clazz.getClassNode();
                    MethodNode indyMethod = clazz.getClassNode().methods.get(capturedMethod.get().asObject().getField("slot", "I").asInt());

                    logger.debug("Decrypted {} {}{}", indyClass.name, indyMethod.name, indyMethod.desc);

                    int opcode;
                    if (Modifier.isStatic(indyMethod.access)) {
                        opcode = INVOKESTATIC;
                    } else {
                        if (Modifier.isInterface(indyClass.access)) {
                            opcode = INVOKEINTERFACE;
                        } else {
                            opcode = INVOKEVIRTUAL;
                        }
                    }

                    modifier.replace(invokeDynamicInsnNode, new MethodInsnNode(opcode, indyClass.name, indyMethod.name, indyMethod.desc, opcode == INVOKEINTERFACE));

                    decrypted++;
                }

                modifier.apply(methodNode);
            }
        }

        vm.shutdown();

        logger.info("Decrypted {} invokedynamic instructions", decrypted);
        return true;
    }

    private void prepareVM() {
        // prevent initializing classes
        vm.beforeCallHooks.add(info -> {
            if (!info.is("sun/misc/Unsafe", "ensureClassInitialized", "(Ljava/lang/Class;)V")) {
                return;
            }
            info.setReturnValue(vm.getNull());
        });
        vm.beforeCallHooks.add(info -> {
            if (!info.is("java/lang/Class", "forName0", "(Ljava/lang/String;ZLjava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/Class;")) {
                return;
            }
            List<StackTraceHolder> stacktrace = vm.getStacktrace();
            if (stacktrace.size() < 3) {
                return;
            }
            if (!classes.containsKey(stacktrace.get(2).getClassNode().name)) {
                return;
            }
            info.getParams().set(1, vm.newBoolean(false));
        });

        vm.beforeCallHooks.add(info -> {
            if (!info.is("java/lang/invoke/MethodHandles$Lookup", "unreflect", "(Ljava/lang/reflect/Method;)Ljava/lang/invoke/MethodHandle;")) {
                return;
            }

            capturedMethod.set(info.getParams().get(0));
            throw AbortException.INSTANCE;
        });
    }
}
