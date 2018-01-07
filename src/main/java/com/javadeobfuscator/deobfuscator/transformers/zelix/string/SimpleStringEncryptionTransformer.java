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
import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.matcher.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import com.javadeobfuscator.javavm.mirrors.*;
import com.javadeobfuscator.javavm.utils.*;
import com.javadeobfuscator.javavm.values.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;
import java.util.function.*;

/**
 * This is a transformer for the simplest version of Zelix string encryption. There are a few possible obfuscated outcomes:
 * <p>
 * If:
 * strings outside <clinit>: multiple
 * strings inside <clinit>: none/single/multiple
 * then a {@code private(?) static final(?) java.lang.String[]} will be created
 * <p>
 * If:
 * strings outside <clinit>: single
 * strings inside <clinit>: none/single/multiple
 * then a {@code private(?) static final(?) java.lang.String} will be created
 * <p>
 * If:
 * strings outside <clinit>: none
 * strings inside <clinit>: single/multiple
 * then a local {@code java.lang.String[]} or {@code java.lang.String} will be created. No fields are created
 * <p>
 * A decryption block can be identified by a switch statement which resets a handful of variables before
 * jumping back to the decryption loop (think flattened CFG). Note that sometimes, there may be multiple decryption blocks
 * That is to say, after the case of the first decryption block, there is another decryption block
 * <p>
 * Also note that sometimes local variables may be initialized between decryption. The VM may not be able to handle such cases,
 * so you may have to instruct the VM to ignore such methods through {@link TransformerConfig#setVmModifiers(List)}
 */
@TransformerConfig.ConfigOptions(configClass = SimpleStringEncryptionTransformer.Config.class)
public class SimpleStringEncryptionTransformer extends Transformer<SimpleStringEncryptionTransformer.Config> implements Opcodes {
    private static final InstructionPattern DECRYPT_PATTERN = new InstructionPattern(
            new CapturingStep(new WildcardStep(), "load"),
            new CapturingStep(new LoadIntStep(), "index"),
            new CapturingStep(new OpcodeStep(AALOAD), "aaload")
    );

    @Override
    public boolean transform() throws WrongTransformerException {
        VirtualMachine vm = TransformerHelper.newVirtualMachine(this);

        // In this mode of string encryption, class hierarchy doesn't matter. Manually flag all classes as initialized
        for (ClassNode classNode : classes.values()) {
            JavaClass.forName(vm, classNode.name).setInitializationState(JavaClass.InitializationState.INITIALIZED, null);
        }

        for (ClassNode classNode : classes.values()) {
            MethodNode clinit = TransformerHelper.findClinit(classNode);
            if (clinit == null) {
                // No static initializer = no encrypted strings
                continue;
            }
            logger.debug("Decrypting {}", classNode.name);

            Frame<SourceValue>[] analysis;
            try {
                analysis = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, clinit);
            } catch (AnalyzerException e) {
                oops("unexpected analyzer exception", e);
                continue;
            }

            // Locate all the local strings within <clinit>
            Set<InstructionMatcher> clinitLocalStrings = new HashSet<>();
            Set<Integer> registers = new HashSet<>();
            for (AbstractInsnNode insnNode : TransformerHelper.instructionIterator(clinit)) {
                InstructionMatcher matcher = DECRYPT_PATTERN.matcher(insnNode);
                if (!matcher.find()) continue;

                AbstractInsnNode source = getSource(clinit, analysis, matcher.getCapturedInstruction("load"), ANEWARRAY);
                if (source == null) continue;
                source = getSource(clinit, analysis, matcher.getCapturedInstruction("load"), ALOAD);
                if (source == null) continue;

                registers.add(((VarInsnNode) source).var);
                clinitLocalStrings.add(matcher);
            }
            if (!clinitLocalStrings.isEmpty() && registers.size() != 1) {
                oops("expected one register, found {}", registers);
                continue;
            }

            // Locate all possible decryption fields which have a PUTSTATIC
            Map<FieldNode, List<AbstractInsnNode>> allPuts = new HashMap<>();
            Set<FieldNode> decryptedFields = new HashSet<>();
            Map<FieldNode, String> decryptedSingularStrings = new HashMap<>();
            for (AbstractInsnNode insn : TransformerHelper.instructionIterator(clinit)) {
                if (insn.getOpcode() != PUTSTATIC) continue;

                FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
                if (!fieldInsnNode.owner.equals(classNode.name)) continue;

                FieldNode targetField = ASMHelper.findField(classNode, fieldInsnNode.name, fieldInsnNode.desc);
                if (targetField == null) continue;

                if (!fieldInsnNode.desc.equals("[Ljava/lang/String;") && !fieldInsnNode.desc.equals("Ljava/lang/String;"))
                    continue;

                if (fieldInsnNode.desc.equals("Ljava/lang/String;")) {
                    AbstractInsnNode source = getSource(clinit, analysis, fieldInsnNode, AALOAD, INVOKEVIRTUAL);
                    if (source != null && source.getOpcode() == INVOKEVIRTUAL) {
                        // The only INVOKEVIRTUAL source allowed is String.intern()
                        MethodInsnNode methodInsnNode = (MethodInsnNode) source;
                        if (!methodInsnNode.owner.equals("java/lang/String") || !methodInsnNode.name.equals("intern") || !methodInsnNode.desc.equals("()Ljava/lang/String;")) {
                            source = null;
                        }
                    }
                    if (source == null) {
                        continue;
                    }
                }
                allPuts.computeIfAbsent(targetField, k -> new ArrayList<>()).add(insn);
            }
            // There should only be one PUTFIELD to the decrypted string(s), since it's final
            allPuts.forEach((key, value) -> {
                if (value.size() > 1) {
                    oops("didn't expect more than one PUTSTATIC for {} {} {}", classNode.name, key.name, key.desc);
                }
            });
            allPuts.entrySet().removeIf(e -> e.getValue().size() != 1);

            if (allPuts.isEmpty() && clinitLocalStrings.isEmpty()) {
                // no encrypted strings
                continue;
            }

            InstructionModifier clinitModifier = new InstructionModifier();

            Function<Map.Entry<String, String>, InsnList> parseDecrypted = field -> {
                InsnList insertBefore = new InsnList();
                insertBefore.add(new InsnNode(Opcodes.POP));

                FieldNode targetField = ASMHelper.findField(classNode, field.getKey(), field.getValue());
                if (targetField == null) {
                    oops("how is {} {} null?", field.getKey(), field.getValue());
                    return null;
                }
                JavaWrapper storedValue = JavaClass.forName(vm, classNode.name).getStaticField(field.getKey(), field.getValue());
                if (storedValue.is(JavaValueType.NULL)) {
                    return null;
                }
                if (storedValue.getJavaClass() == vm.getSystemDictionary().getJavaLangString()) {
                    String value = vm.convertJavaObjectToString(storedValue);
                    decryptedSingularStrings.put(targetField, value);
                    targetField.value = value;
                    insertBefore.add(new LdcInsnNode(value));
                } else {
                    JavaArray stringArray = storedValue.asArray();
                    insertBefore.add(new LdcInsnNode(stringArray.length()));
                    insertBefore.add(new TypeInsnNode(ANEWARRAY, "java/lang/String"));
                    for (int i = 0; i < stringArray.length(); i++) {
                        String value = vm.convertJavaObjectToString(stringArray.get(i));
                        if (value == null) {
                            return null;
                        }
                        insertBefore.add(new InsnNode(DUP));
                        insertBefore.add(new LdcInsnNode(i));
                        insertBefore.add(new LdcInsnNode(value));
                        insertBefore.add(new InsnNode(AASTORE));
                    }
                }

                return insertBefore;
            };

            Runnable tryDecryptFields = () -> {
                for (Map.Entry<FieldNode, List<AbstractInsnNode>> entry : allPuts.entrySet()) {
                    if (decryptedFields.contains(entry.getKey())) continue;

                    InsnList insertBefore = parseDecrypted.apply(new AbstractMap.SimpleEntry<>(entry.getKey().name, entry.getKey().desc));
                    if (insertBefore != null) {
                        // register the modification in <clinit>
                        clinitModifier.prepend(entry.getValue().get(0), insertBefore);
                        decryptedFields.add(entry.getKey());
                    }
                }

                if (!allPuts.isEmpty() && allPuts.size() == decryptedFields.size()) {
                    // also abort if we've finished decrypting all fields
                    throw AbortException.INSTANCE;
                }
            };

            Consumer<ExecutionOptions.BreakpointInfo> parseLocalStrings = info -> {
                for (InstructionMatcher clinitLocalString : clinitLocalStrings) {
                    if (info.getNow() == clinitLocalString.getCapturedInstruction("aaload")) {
                        JavaArray decryptedArray = info.getLocals().get(registers.iterator().next()).asArray();

                        for (InstructionMatcher decrypt : clinitLocalStrings) {
                            int index = Utils.getIntValue(decrypt.getCapturedInstruction("index"));
                            String decrypted = vm.convertJavaObjectToString(decryptedArray.get(index));
                            logger.info("Decrypted local string in {}: {}", classNode.name, decrypted);

                            clinitModifier.removeAll(decrypt.getCapturedInstructions("all"));
                            clinitModifier.replace(decrypt.getCapturedInstructions("all").get(0), new LdcInsnNode(decrypted));
                        }

                        if (allPuts.isEmpty()) {
                            // abort if there were no decryption fields, since local strings takes one pass
                            throw AbortException.INSTANCE;
                        }
                    }
                }
            };

            Object breakpointToken = vm.addBreakpoint(info -> {
                parseLocalStrings.accept(info);
                tryDecryptFields.run();
            });

            try {
                vm.execute(classNode, clinit);
                // try decrypting it one last time
                tryDecryptFields.run();
                for (Map.Entry<FieldNode, List<AbstractInsnNode>> entry : allPuts.entrySet()) {
                    if (!decryptedFields.contains(entry.getKey())) {
                        oops("couldn't decrypt field {} {} {}", classNode.name, entry.getKey().name, entry.getKey().desc);
                    }
                }
            } catch (AbortException ignored) {
            } catch (VMException e) {
                logger.debug("Exception while initializing {}, should be fine", classNode.name);
                logger.debug(vm.exceptionToString(e));
            } catch (Throwable e) {
                logger.debug("(Severe) Exception while initializing {}, should be fine", classNode.name, e);
            } finally {
                vm.breakpoints.remove(breakpointToken);
            }

            clinitModifier.apply(clinit);

            if (getConfig().isPropogateStrings()) {
                propogateStrings(classNode, vm, decryptedSingularStrings);
            }
        }

        vm.shutdown();

        return false;
    }

    private void propogateStrings(ClassNode classNode, VirtualMachine vm, Map<FieldNode, String> decryptedSingularStrings) {
        for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
            logger.debug("Decrypting {} {}{}", classNode.name, methodNode.name, methodNode.desc);
            Frame<SourceValue>[] analysis;
            try {
                analysis = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
            } catch (AnalyzerException e) {
                oops("unexpected analyzer exception", e);
                continue;
            }

            InstructionModifier modifier = new InstructionModifier();

            // Replace all GETSTATIC of single strings
            for (AbstractInsnNode insnNode : TransformerHelper.instructionIterator(methodNode)) {
                if (insnNode.getOpcode() != GETSTATIC) continue;

                FieldInsnNode fieldInsnNode = (FieldInsnNode) insnNode;
                if (!fieldInsnNode.owner.equals(classNode.name)) continue;

                String value = decryptedSingularStrings.get(ASMHelper.findField(classNode, fieldInsnNode.name, fieldInsnNode.desc));
                if (value == null) continue;

                logger.info("Decrypted string in {} {}{}: {}", classNode.name, methodNode.name, methodNode.desc, value);
                modifier.replace(insnNode, new LdcInsnNode(value));
            }

            // Replace all (GETSTATIC|ALOAD),LDC [intvalue],AALOAD with strings
            for (AbstractInsnNode insnNode : TransformerHelper.instructionIterator(methodNode)) {
                InstructionMatcher matcher = DECRYPT_PATTERN.matcher(insnNode);
                if (!matcher.find()) continue;

                AbstractInsnNode source = getSource(methodNode, analysis, matcher.getCapturedInstruction("load"), GETSTATIC);
                if (source == null) continue;

                FieldInsnNode getstatic = (FieldInsnNode) source;
                if (!getstatic.desc.equals("[Ljava/lang/String;")) continue;

                JavaWrapper field = JavaClass.forName(vm, classNode.name).getStaticField(getstatic.name, getstatic.desc);
                if (field.is(JavaValueType.NULL)) {
                    oops("Expected non-null field {} {}{}", getstatic.owner, getstatic.name, getstatic.desc);
                    continue;
                }
                JavaArray decryptedArray = field.asArray();

                int index = Utils.getIntValue(matcher.getCapturedInstruction("index"));

                String decrypted = vm.convertJavaObjectToString(decryptedArray.get(index));
                logger.info("Decrypted string in {} {}{}: {}", classNode.name, methodNode.name, methodNode.desc, decrypted);

                modifier.removeAll(matcher.getCapturedInstructions("all"));
                modifier.replace(matcher.getCapturedInstructions("all").get(0), new LdcInsnNode(decrypted));
            }

            modifier.apply(methodNode);
        }
    }

    private AbstractInsnNode getSource(MethodNode methodNode, Frame<SourceValue>[] frames, AbstractInsnNode now, int... wants) {
        Frame<SourceValue> currentFrame = frames[methodNode.instructions.indexOf(now)];
        SourceValue currentValue;

        for (int want : wants)
            if (want == now.getOpcode()) return now;
        switch (now.getOpcode()) {
            case ALOAD: {
                currentValue = currentFrame.getLocal(((VarInsnNode) now).var);
                break;
            }
            case ASTORE: {
                currentValue = currentFrame.getStack(currentFrame.getStackSize() - 1);
                break;
            }
            case DUP: {
                currentValue = currentFrame.getStack(currentFrame.getStackSize() - 1);
                break;
            }
            case PUTSTATIC: {
                currentValue = currentFrame.getStack(currentFrame.getStackSize() - 1);
                break;
            }
            case SWAP: {
                currentValue = currentFrame.getStack(currentFrame.getStackSize() - 1);
                break;
            }
            default: {
                oops("Unexpected opcode {}", now.getOpcode());
                return null;
            }
        }

        if (currentValue.insns.size() != 1) {
            oops("Expected 1 source insn, found {}", TransformerHelper.insnsToString(currentValue.insns));
            return null;
        }

        return getSource(methodNode, frames, currentValue.insns.iterator().next(), wants);
    }

    public static class Config extends TransformerConfig {

        @JsonProperty("propogate-strings")
        private boolean propogateStrings = true;

        public Config() {
            super(SimpleStringEncryptionTransformer.class);
        }

        public boolean isPropogateStrings() {
            return propogateStrings;
        }

        public void setPropogateStrings(boolean propogateStrings) {
            this.propogateStrings = propogateStrings;
        }
    }
}
