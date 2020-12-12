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

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.allatori.string.StringEncryptionTransformer;
import com.javadeobfuscator.deobfuscator.utils.*;

import org.assertj.core.internal.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.*;

public class RuleStringDecryptor implements Rule {
    @Override
    public String getDescription() {
        return "Allatori's string decryption is very simple, accepting an encrypted string and outputting a decrypted string";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
            	Frame<SourceValue>[] frames;
                try 
                {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
                }catch(AnalyzerException e) 
                {
                    continue;
                }
            	for(AbstractInsnNode ain : TransformerHelper.instructionIterator(methodNode))
                	if(ain instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode)ain;
                        String strCl = m.owner;
                        if((m.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;")
                        	|| m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;"))
                        	&& deobfuscator.getClasses().containsKey(strCl)) {
                            Frame<SourceValue> f = frames[methodNode.instructions.indexOf(m)];
                        	if(f.getStack(f.getStackSize() - 1).insns.size() != 1
                        		|| f.getStack(f.getStackSize() - 1).insns.iterator().next().getOpcode() != Opcodes.LDC)
                        		continue;
                        	ClassNode innerClassNode = deobfuscator.getClasses().get(strCl);
                        	MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) 
                        		&& mn.desc.equals(m.desc)).findFirst().orElse(null);
                        	boolean isAllatori = true;

                            isAllatori = isAllatori && TransformerHelper.containsInvokeVirtual(decrypterNode, "java/lang/String", "charAt", "(I)C");
                            isAllatori = isAllatori && TransformerHelper.containsInvokeVirtual(decrypterNode, "java/lang/String", "length", "()I");
                            isAllatori = isAllatori && TransformerHelper.countOccurencesOf(decrypterNode, IXOR) > 2;
                            isAllatori = isAllatori && TransformerHelper.countOccurencesOf(decrypterNode, NEWARRAY) > 0;
                            
                            if (!isAllatori) {
                                continue;
                            }

                            return "Found possible string decryption class " + innerClassNode.name;
                        }
                	}
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Arrays.asList(com.javadeobfuscator.deobfuscator.transformers.allatori.StringEncryptionTransformer.class,
        	StringEncryptionTransformer.class);
    }
}
