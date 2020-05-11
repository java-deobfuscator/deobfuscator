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

package com.javadeobfuscator.deobfuscator.asm.source;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public class SourceFinder implements Opcodes {

    public static SourceResult findSource(MethodNode methodNode, Frame<SourceValue>[] frames, List<AbstractInsnNode> instructions, SourceFinderConsumer sfc, AbstractInsnNode now, SourceValue want) {
        // Remember, Frame stores the stack/locals *before* the instruction is executed

        List<Object> foundResults = new ArrayList<>();
        List<ExceptionHolder> foundExceptions = new ArrayList<>();

        for (AbstractInsnNode sourceInsn : want.insns) {
        	instructions.add(sourceInsn);
//            System.out.println(TransformerHelper.insnToString(now) + " -> " + TransformerHelper.insnToString(sourceInsn));
            switch (sourceInsn.getOpcode()) {
                // Explicit push
                case ACONST_NULL: {
                    foundResults.add(null);
                    break;
                }
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5: {
                    foundResults.add(sourceInsn.getOpcode() - ICONST_0);
                    break;
                }
                case LCONST_0:
                case LCONST_1: {
                    foundResults.add((long) (sourceInsn.getOpcode() - LCONST_0));
                    break;
                }
                case FCONST_0:
                case FCONST_1:
                case FCONST_2: {
                    foundResults.add((float) (sourceInsn.getOpcode() - FCONST_0));
                    break;
                }
                case DCONST_0:
                case DCONST_1: {
                    foundResults.add((double) (sourceInsn.getOpcode() - DCONST_0));
                    break;
                }
                case BIPUSH:
                case SIPUSH: {
                    foundResults.add(((IntInsnNode) sourceInsn).operand);
                    break;
                }
                case LDC: {
                    foundResults.add(((LdcInsnNode) sourceInsn).cst);
                    break;
                }
                default: {
                    if (sfc == null) {
                        return SourceResult.unknown();
                    }
                    SourceResult found = sfc.findSource(methodNode, frames, instructions, now, want, sourceInsn);
                    if (found.isUnknown()) {
                        return SourceResult.unknown();
                    }
                    foundResults.addAll(found.getValues());
                    foundExceptions.addAll(found.getExceptions());
                    break;
                }
            }
        }

        return new SourceResult(foundResults, foundExceptions);
    }
}
