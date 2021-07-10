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

package com.javadeobfuscator.deobfuscator.rules.allatori;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.allatori.string.StringEncryptionTransformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public class RuleStringDecryptor implements Rule {

    @Override
    public String getDescription() {
        return "Allatori's string decryption is very simple, accepting an encrypted string and outputting a decrypted string";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                Frame<SourceValue>[] frames = null;
                for (AbstractInsnNode ain : TransformerHelper.instructionIterator(methodNode)) {
                    if (ain.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode m = (MethodInsnNode) ain;
                    String strCl = m.owner;
                    if (!m.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;") && !m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                        continue;
                    }
                    if (!deobfuscator.getClasses().containsKey(strCl)) {
                        continue;
                    }
                    ClassNode innerClassNode = deobfuscator.getClasses().get(strCl);
                    MethodNode decryptorNode = innerClassNode.methods.stream()
                            .filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc))
                            .findFirst().orElse(null);
                    if (decryptorNode == null || decryptorNode.instructions == null) {
                        continue;
                    }

                    if (!com.javadeobfuscator.deobfuscator.transformers.allatori.StringEncryptionTransformer.isAllatoriMethod(decryptorNode)) {
                        continue;
                    }

                    if (frames == null) {
                        try {
                            frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
                        } catch (Exception e) {
                            if (deobfuscator.getConfig().isDebugRulesAnalyzer()) {
                                e.printStackTrace();
                            }
                            continue;
                        }
                    }
                    Frame<SourceValue> f = frames[methodNode.instructions.indexOf(m)];
                    Set<AbstractInsnNode> insns = f.getStack(f.getStackSize() - 1).insns;
                    if (insns.size() != 1 || insns.iterator().next().getOpcode() != Opcodes.LDC) {
                        continue;
                    }
                    return "Found possible string decryption class " + innerClassNode.name;
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Arrays.asList(com.javadeobfuscator.deobfuscator.transformers.allatori.StringEncryptionTransformer.class, StringEncryptionTransformer.class);
    }
}
