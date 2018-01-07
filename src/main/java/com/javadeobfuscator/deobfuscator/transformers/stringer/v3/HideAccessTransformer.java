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

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.WrongTransformerException;
import com.javadeobfuscator.deobfuscator.matcher.InstructionMatcher;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.Constants;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.Helper;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.HideAccessClassFinder;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.javavm.StackTraceHolder;
import com.javadeobfuscator.javavm.VirtualMachine;
import com.javadeobfuscator.javavm.exceptions.AbortException;
import com.javadeobfuscator.javavm.mirrors.JavaClass;
import com.javadeobfuscator.javavm.nativeimpls.java_lang_Class;
import com.javadeobfuscator.javavm.values.JavaWrapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.Constants.*;
import static com.javadeobfuscator.deobfuscator.utils.TransformerHelper.load;
import static com.javadeobfuscator.deobfuscator.utils.TransformerHelper.size;
import static com.javadeobfuscator.deobfuscator.utils.TransformerHelper.store;

public class HideAccessTransformer extends Transformer<TransformerConfig> implements Opcodes {
    private VirtualMachine vm;
    private AtomicReference<JavaWrapper> captured = new AtomicReference<>();

    public Set<String> getDecryptionClasses() {
        return decryptionClasses;
    }

    private Set<String> decryptionClasses;

    @Override
    public boolean transform() throws Throwable {
        decryptionClasses = new HideAccessClassFinder().findNames(classes.values());
        logger.info("Detected the following hide access decryption classes: {}", decryptionClasses);

        vm = TransformerHelper.newVirtualMachine(this);
        prepareVM();

        JavaClass reflectField = JavaClass.forName(vm, "java/lang/reflect/Field");

        int decrypted = 0;

        for (ClassNode classNode : classes.values()) {
            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                InstructionModifier modifier = new InstructionModifier();

                for (AbstractInsnNode insn : TransformerHelper.instructionIterator(methodNode)) {
                    InstructionMatcher matcher = Helper.findMatch(insn,
                            HIDE_ACCESS_GETFIELD,
                            HIDE_ACCESS_GETSTATIC,
                            HIDE_ACCESS_PUTFIELD,
                            HIDE_ACCESS_PUTSTATIC,
                            HIDE_ACCESS_INVOKEVIRTUAL,
                            HIDE_ACCESS_INVOKESTATIC_INVOKESPECIAL
                    );

                    if (matcher == null) {
                        continue;
                    }

                    InsnList instructions = Constants.HIDE_ACCESS_HANDLERS.get(matcher.getPattern()).apply(this, matcher);
                    if (instructions == null) {
                        continue;
                    }

                    MethodNode decryptorMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodNode.name, "()V", null, null);
                    decryptorMethod.instructions.add(instructions);

                    captured.set(null);
                    classNode.methods.add(decryptorMethod);
                    vm.execute(classNode, decryptorMethod, null, Collections.<JavaWrapper>emptyList());
                    classNode.methods.remove(decryptorMethod);
                    if (captured.get() == null) {
                        throw new WrongTransformerException("Expected non-null java/lang/reflect/*");
                    }
                    logger.debug("Decrypted {} in {} {}{}", captured.get().asObject().javaToString(), classNode.name, methodNode.name, methodNode.desc);

                    modifier.removeAll(matcher.getCapturedInstructions("all"));

                    JavaWrapper classObj = captured.get().asObject().getField("clazz", "Ljava/lang/Class;");
                    ClassNode reflClass = java_lang_Class.getJavaClass(classObj).getClassNode();
                    if (captured.get().getJavaClass() == reflectField) {
                        FieldNode reflField = reflClass.fields.get(captured.get().asObject().getField("slot", "I").asInt());

                        int opcode;
                        if (Modifier.isStatic(reflField.access)) {
                            opcode = matcher.getPattern() == HIDE_ACCESS_GETSTATIC ? GETSTATIC : PUTSTATIC;
                        } else {
                            opcode = matcher.getPattern() == HIDE_ACCESS_GETFIELD ? GETFIELD : PUTFIELD;
                        }

                        InsnList replace = new InsnList();
                        if (opcode == PUTSTATIC || opcode == PUTFIELD) {
                            replace.add(TransformerHelper.unbox(Type.getType(reflField.desc)));
                        }
                        replace.add(new FieldInsnNode(opcode, reflClass.name, reflField.name, reflField.desc));
                        if (opcode == GETSTATIC || opcode == GETFIELD) {
                            replace.add(TransformerHelper.box(Type.getType(reflField.desc)));
                        }

                        modifier.replace(matcher.getEnd(), replace);
                    } else {
                        modifier.replace(matcher.getEnd(), convertMethodInvocation(methodNode, matcher.getCapturedInstruction("call"), captured.get()));
                    }

                    decrypted++;
                }

                modifier.apply(methodNode);
            }
        }

        vm.shutdown();

        logger.info("Decrypted {} hide access instruction", decrypted);
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

        // For retrieving java/lang/reflect/Method and java/lang/reflect/Field instances
        vm.afterCallHooks.add(info -> {
            boolean isInterested = false;
            for (String decryptionClass : decryptionClasses) {
                if (info.getClassNode().name.equals(decryptionClass) &&
                        (info.getMethodNode().desc.equals(HIDE_ACCESS_DECRYPT_FIELD_SIG) ||
                                info.getMethodNode().desc.equals(HIDE_ACCESS_DECRYPT_METHOD_SIG))) {
                    isInterested = true;
                }
            }

            if (!isInterested) {
                return;
            }

            captured.set(info.getReturnValue());
            throw AbortException.INSTANCE;
        });
        vm.beforeCallHooks.add(info -> {
            if (!info.is("java/lang/reflect/Constructor", "newInstance", "([Ljava/lang/Object;)Ljava/lang/Object;")) {
                return;
            }
            List<StackTraceHolder> stacktrace = vm.getStacktrace();
            if (stacktrace.size() < 2) {
                return;
            }
            if (!classes.containsKey(stacktrace.get(1).getClassNode().name)) {
                return;
            }
            captured.set(info.getInstance());
            throw AbortException.INSTANCE;
        });
    }

    private static InsnList convertMethodInvocation(MethodNode destNode, AbstractInsnNode invocationInstruction, JavaWrapper methodInstance) {
        JavaWrapper classObj = methodInstance.asObject().getField("clazz", "Ljava/lang/Class;");
        ClassNode reflClass = java_lang_Class.getJavaClass(classObj).getClassNode();
        MethodNode reflMethod = reflClass.methods.get(methodInstance.asObject().getField("slot", "I").asInt());

        int opcode;
        if (reflMethod.name.equals("<init>")) {
            opcode = INVOKESPECIAL;
        } else if (Modifier.isStatic(reflMethod.access)) {
            opcode = INVOKESTATIC;
        } else if (Modifier.isInterface(reflClass.access)) {
            opcode = INVOKEINTERFACE;
        } else {
            opcode = INVOKEVIRTUAL;
        }

        InsnList replace = new InsnList();
        if (opcode == INVOKESPECIAL) {
            Analyzer<BasicValue> analyzer = new Analyzer<>(new BasicInterpreter());
            Frame<BasicValue>[] result;
            try {
                result = analyzer.analyze("java/lang/Object", destNode);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e);
            }
            Frame<BasicValue> index = result[destNode.instructions.indexOf(invocationInstruction)];

            Type[] argumentTypes = Type.getArgumentTypes(reflMethod.desc);
            Collections.reverse(Arrays.asList(argumentTypes));
            InsnList storeInsns = store(argumentTypes, index.getLocals());
            Collections.reverse(Arrays.asList(argumentTypes));
            InsnList loadInsns = load(argumentTypes, index.getLocals() + size(argumentTypes) - 1, false);

            replace.add(storeInsns);
            replace.add(new TypeInsnNode(NEW, reflClass.name));
            replace.add(new InsnNode(DUP));
            replace.add(loadInsns);
            replace.add(new MethodInsnNode(INVOKESPECIAL, reflClass.name, reflMethod.name, reflMethod.desc, false));

            destNode.maxLocals += size(argumentTypes);
        } else {
            replace.add(new MethodInsnNode(opcode, reflClass.name, reflMethod.name, reflMethod.desc, opcode == INVOKEINTERFACE));
            replace.add(TransformerHelper.box(Type.getReturnType(reflMethod.desc)));
        }
        return replace;
    }
}
