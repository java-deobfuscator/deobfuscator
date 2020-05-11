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

import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public class ConstantPropagatingSourceFinder extends SourceFinderConsumer {
    public ConstantPropagatingSourceFinder(SourceFinderConsumer parent) {
        super(parent);
    }

    public ConstantPropagatingSourceFinder() {
        super(null);
    }

    @Override
    public SourceResult findSource(MethodNode methodNode, Frame<SourceValue>[] frames, List<AbstractInsnNode> instructions, AbstractInsnNode source, SourceValue want, AbstractInsnNode now) {
        Frame<SourceValue> sourceFrame = frames[methodNode.instructions.indexOf(source)];
        Frame<SourceValue> curFrame = frames[methodNode.instructions.indexOf(now)];

        switch (now.getOpcode()) {
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IREM:
            case IAND:
            case IOR:
            case IXOR:
            case ISHL:
            case ISHR:
            case IUSHR: {
            	instructions.add(now);
                SourceResult bres = SourceFinder.findSource(methodNode, frames, instructions, this, now, curFrame.getStack(curFrame.getStackSize() - 2));
                SourceResult ares = SourceFinder.findSource(methodNode, frames, instructions, this, now, curFrame.getStack(curFrame.getStackSize() - 1));
                if (bres.isUnknown() || ares.isUnknown()) return SourceResult.unknown();
                if (!bres.getExceptions().isEmpty() || !ares.getExceptions().isEmpty())
                    return SourceResult.unknown(); // todo maybe we should merge this?
                if (bres.getValues().size() != ares.getValues().size())
                    throw new RuntimeException("unexpected different size of values " + bres + " " + ares);

//                System.out.println("got " + TransformerHelper.insnToString(now) + " " + bres + " " + ares);
                List<Object> values = new ArrayList<>();
                List<ExceptionHolder> exceptions = new ArrayList<>();
                for (int i = 0; i < ares.getValues().size(); i++) {
                    int b = (int) bres.getValues().get(i);
                    int a = (int) ares.getValues().get(i);
//                    System.out.println("got " + TransformerHelper.insnToString(now) + " " + b + " " + a);
                    switch (now.getOpcode()) {
                        case IADD: {
                            values.add(b + a);
                            break;
                        }
                        case ISUB: {
                            values.add(b - a);
                            break;
                        }
                        case IMUL: {
                            values.add(b * a);
                            break;
                        }
                        case IDIV: {
                            if (a == 0) exceptions.add(new ExceptionHolder("java/lang/ArithmeticException"));
                            else values.add(b / a);
                            break;
                        }
                        case IREM: {
                            if (a == 0) exceptions.add(new ExceptionHolder("java/lang/ArithmeticException"));
                            else values.add(b % a);
                            break;
                        }
                        case IAND: {
                            values.add(b & a);
                            break;
                        }
                        case IOR: {
                            values.add(b | a);
                            break;
                        }
                        case IXOR: {
                            values.add(b ^ a);
                            break;
                        }
                        case ISHL: {
                            values.add(b << a);
                            break;
                        }
                        case ISHR: {
                            values.add(b >> a);
                            break;
                        }
                        case IUSHR: {
                            values.add(b >>> a);
                            break;
                        }
                        default: {
                            throw new RuntimeException();
                        }
                    }
                }
                return new SourceResult(values, exceptions);
            }
            case DUP2: 
            case SWAP: {
            	instructions.add(now);
                if (getStackOffset(sourceFrame, want) == sourceFrame.getStackSize() - 1) {
                    return SourceFinder.findSource(methodNode, frames, instructions, this, now, curFrame.getStack(curFrame.getStackSize() - 2));
                } else if (getStackOffset(sourceFrame, want) == sourceFrame.getStackSize() - 2) {
                    return SourceFinder.findSource(methodNode, frames, instructions, this, now, curFrame.getStack(curFrame.getStackSize() - 1));
                }
                throw new RuntimeException(String.valueOf(getStackOffset(sourceFrame, want)));
            }
            case DUP:
            case DUP_X1: {
            	instructions.add(now);
                return SourceFinder.findSource(methodNode, frames, instructions, this, now, curFrame.getStack(curFrame.getStackSize() - 1));
            }
        }

        return parent == null ? SourceResult.unknown() : parent.findSource(methodNode, frames, instructions, source, want, now);
    }

    private static int getStackOffset(Frame<SourceValue> frame, SourceValue want) {
        for (int i = 0; i < frame.getStackSize(); i++) {
            if (frame.getStack(i) == want) {
                return i;
            }
        }

        return -1;
    }
}
