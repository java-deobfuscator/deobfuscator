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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils;

import com.google.common.collect.ImmutableMap;
import com.javadeobfuscator.deobfuscator.matcher.*;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.HideAccessTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class Constants implements Opcodes {
    public static final String HIDE_ACCESS_DECRYPT_FIELD_SIG = "(I)Ljava/lang/reflect/Field;";
    public static final String HIDE_ACCESS_DECRYPT_METHOD_SIG = "(I)Ljava/lang/reflect/Method;";

    public static final InstructionPattern DECRYPT_PATTERN = new InstructionPattern(
            new OpcodeStep(LDC),
            new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;)Ljava/lang/String;", false)
    );

    public static final InstructionPattern HIDE_ACCESS_GETFIELD = new InstructionPattern(
            new CapturingStep(new LoadIntStep(), "cst"),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;I)Ljava/lang/Object;", false), "call")
    );
    public static final InstructionPattern HIDE_ACCESS_PUTFIELD = new InstructionPattern(
            new CapturingStep(new LoadIntStep(), "cst"),
            new OpcodeStep(SWAP),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;ILjava/lang/Object;)V", false), "call")
    );
    public static final InstructionPattern HIDE_ACCESS_GETSTATIC = new InstructionPattern(
            new CapturingStep(new LoadIntStep(), "cst"),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(I)Ljava/lang/Object;", false), "call")
    );
    public static final InstructionPattern HIDE_ACCESS_PUTSTATIC = new InstructionPattern(
            new CapturingStep(new LoadIntStep(), "cst"),
            new OpcodeStep(SWAP),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(ILjava/lang/Object;)V", false), "call")
    );

    private static final Step PREPARE_REFLECTION_ARRAY = new MultiStep(
            new LoadIntStep(),
            new ANewArrayStep("java/lang/Object", false),
            new RepeatingStep(
                    new MultiStep(
                            new OpcodeStep(DUP_X1),
                            new OpcodeStep(SWAP),
                            new LoadIntStep(),
                            new OpcodeStep(SWAP),
                            new OptionalStep(new InvocationStep(INVOKESTATIC, null, "valueOf", null, false)),
                            new OpcodeStep(AASTORE)
                    ), -1, -1
            )
    );

    public static final InstructionPattern HIDE_ACCESS_INVOKESTATIC_INVOKESPECIAL = new InstructionPattern(
            PREPARE_REFLECTION_ARRAY,
            new CapturingStep(new LoadIntStep(), "cst"),
            new OpcodeStep(SWAP),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(I[Ljava/lang/Object;)Ljava/lang/Object;", false), "call")
    );
    public static final InstructionPattern HIDE_ACCESS_INVOKEVIRTUAL = new InstructionPattern(
            PREPARE_REFLECTION_ARRAY,
            new CapturingStep(new LoadIntStep(), "cst"),
            new OpcodeStep(SWAP),
            new CapturingStep(new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", false), "call")
    );

    public static final Map<InstructionPattern, BiFunction<HideAccessTransformer, InstructionMatcher, InsnList>> HIDE_ACCESS_HANDLERS;

    static {
        Map<InstructionPattern, BiFunction<HideAccessTransformer, InstructionMatcher, InsnList>> handlers = new HashMap<>();
        handlers.put(HIDE_ACCESS_GETFIELD, (transformer, matcher) -> {
            LdcInsnNode cst = (LdcInsnNode) matcher.getCapturedInstruction("cst");
            MethodInsnNode call = (MethodInsnNode) matcher.getCapturedInstruction("call");
            if (transformer.getDecryptionClasses().contains(call.owner)) {
                InsnList result = new InsnList();
                result.add(new InsnNode(ACONST_NULL));
                result.add(cst.clone(null));
                result.add(call.clone(null));
                result.add(new InsnNode(RETURN));
                return result;
            }
            return null;
        });
        handlers.put(HIDE_ACCESS_PUTFIELD, (transformer, matcher) -> {
            LdcInsnNode cst = (LdcInsnNode) matcher.getCapturedInstruction("cst");
            MethodInsnNode call = (MethodInsnNode) matcher.getCapturedInstruction("call");
            if (transformer.getDecryptionClasses().contains(call.owner)) {
                InsnList result = new InsnList();
                result.add(new InsnNode(ACONST_NULL));
                result.add(cst.clone(null));
                result.add(new InsnNode(ACONST_NULL));
                result.add(call.clone(null));
                result.add(new InsnNode(RETURN));
                return result;
            }
            return null;
        });
        handlers.put(HIDE_ACCESS_GETSTATIC, (transformer, matcher) -> {
            LdcInsnNode cst = (LdcInsnNode) matcher.getCapturedInstruction("cst");
            MethodInsnNode call = (MethodInsnNode) matcher.getCapturedInstruction("call");
            if (transformer.getDecryptionClasses().contains(call.owner)) {
                InsnList result = new InsnList();
                result.add(cst.clone(null));
                result.add(call.clone(null));
                result.add(new InsnNode(RETURN));
                return result;
            }
            return null;
        });
        handlers.put(HIDE_ACCESS_PUTSTATIC, (transformer, matcher) -> {
            LdcInsnNode cst = (LdcInsnNode) matcher.getCapturedInstruction("cst");
            MethodInsnNode call = (MethodInsnNode) matcher.getCapturedInstruction("call");
            if (transformer.getDecryptionClasses().contains(call.owner)) {
                InsnList result = new InsnList();
                result.add(cst.clone(null));
                result.add(new InsnNode(ACONST_NULL));
                result.add(call.clone(null));
                result.add(new InsnNode(RETURN));
                return result;
            }
            return null;
        });
        handlers.put(HIDE_ACCESS_INVOKESTATIC_INVOKESPECIAL, handlers.get(HIDE_ACCESS_PUTSTATIC));
        handlers.put(HIDE_ACCESS_INVOKEVIRTUAL, handlers.get(HIDE_ACCESS_PUTFIELD));
        HIDE_ACCESS_HANDLERS = ImmutableMap.copyOf(handlers);
    }
}
