/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.allatori;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StringEncryptionTransformer extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        AtomicInteger count = new AtomicInteger();
        Set<MethodNode> decryptor = new HashSet<>();

        System.out.println("[Allatori] [StringEncryptionTransformer] Starting");

        for(ClassNode classNode : classes.values())
            for(MethodNode method : classNode.methods)
            {
            	InstructionModifier modifier = new InstructionModifier();
                Frame<SourceValue>[] frames;
                try 
                {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
                }catch(AnalyzerException e) 
                {
                    oops("unexpected analyzer exception", e);
                    continue;
                }

                for(AbstractInsnNode ain : TransformerHelper.instructionIterator(method))
                	if(ain instanceof MethodInsnNode) 
                	{
                        MethodInsnNode m = (MethodInsnNode)ain;
                        String strCl = m.owner;
                        if(m.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;")
                        	|| m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) 
                        {
                        	Frame<SourceValue> f = frames[method.instructions.indexOf(m)];
                        	if(f.getStack(f.getStackSize() - 1).insns.size() != 1)
                        		continue;
							AbstractInsnNode ldc = f.getStack(f.getStackSize() - 1).insns.iterator().next();
							if(ldc.getOpcode() != Opcodes.LDC || !(((LdcInsnNode)ldc).cst instanceof String))
								continue;
    						Context context = new Context(provider);
                            context.push(classNode.name, method.name, getDeobfuscator().getConstantPool(classNode).getSize());
    						if(classes.containsKey(strCl)) 
    						{
    							ClassNode innerClassNode = classes.get(strCl);
    							MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) 
    								&& mn.desc.equals(m.desc)).findFirst().orElse(null);
    							if(decrypterNode == null || decrypterNode.instructions.getFirst() == null)
    								continue;
    					        Map<Integer, AtomicInteger> insnCount = new HashMap<>();
    					        Map<String, AtomicInteger> invokeCount = new HashMap<>();
    					        for(AbstractInsnNode i = decrypterNode.instructions.getFirst(); i != null; i = i.getNext()) 
    					        {
    					        	int opcode = i.getOpcode();
    					        	insnCount.putIfAbsent(opcode, new AtomicInteger(0));
    					        	insnCount.get(opcode).getAndIncrement();
    					            if(i instanceof MethodInsnNode)
    					            {
    					            	invokeCount.putIfAbsent(((MethodInsnNode)i).name, new AtomicInteger(0));
    					            	invokeCount.get(((MethodInsnNode)i).name).getAndIncrement();
    					            }
    					        }
    							if(decryptor.contains(decrypterNode) || isAllatoriMethod(insnCount, invokeCount))
    							{
    								patchMethod(invokeCount, decrypterNode);
                                    try 
                                    {
                                        ((LdcInsnNode)ldc).cst = MethodExecutor.execute(innerClassNode, decrypterNode, 
                                        	Collections.singletonList(JavaValue.valueOf(((LdcInsnNode)ldc).cst)), null, context);
            							modifier.remove(m);
                                        decryptor.add(decrypterNode);
                                        count.getAndIncrement();
                                    }catch(Throwable t) 
                                    {
                                        System.out.println("Error while decrypting Allatori string.");
                                        System.out.println("Are you sure you're deobfuscating something obfuscated by Allatori?");
                                        System.out.println(classNode.name + " " + method.name + method.desc + " " + m.owner + " " + m.name + m.desc);
                                        t.printStackTrace(System.out);
                                    }
    							}
    						}
                        }
                	}
                modifier.apply(method);
            }
        System.out.println("[Allatori] [StringEncryptionTransformer] Decrypted " + count + " encrypted strings");
        System.out.println("[Allatori] [StringEncryptionTransformer] Removed " + cleanup(decryptor) + " decryption methods");
        System.out.println("[Allatori] [StringEncryptionTransformer] Done");
        return count.get() > 0;
    }

    private boolean isAllatoriMethod(Map<Integer, AtomicInteger> insnCount, Map<String, AtomicInteger> invokeCount) {
        //XXX: Better detector
    	if(insnCount.get(Opcodes.IXOR) == null ||
    		insnCount.get(Opcodes.ISHL) == null ||
    		insnCount.get(Opcodes.NEWARRAY) == null ||
    		invokeCount.get("charAt") == null || invokeCount.get("length") == null)
    			return false;
        return insnCount.get(Opcodes.IXOR).get() >= 3 &&
               insnCount.get(Opcodes.ISHL).get() >= 1 &&
               insnCount.get(Opcodes.NEWARRAY).get() >= 1 &&
               invokeCount.get("charAt").get() >= 2 &&
               invokeCount.get("length").get() >= 1;
    }

    private void patchMethod(Map<String, AtomicInteger> invokeCount, MethodNode method) {
        if (invokeCount.containsKey("getStackTrace") && invokeCount.containsKey("getClassName")) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn.getOpcode() == Opcodes.NEW && (((TypeInsnNode) insn).desc.endsWith("Exception") || ((TypeInsnNode) insn).desc.endsWith("Error"))) {
                    ((TypeInsnNode) insn).desc = "java/lang/RuntimeException";
                } else if (insn instanceof MethodInsnNode && (((MethodInsnNode) insn).owner.endsWith("Exception") || ((MethodInsnNode) insn).owner.endsWith("Error"))) {
                    ((MethodInsnNode) insn).owner = "java/lang/RuntimeException";
                }
            }
        }
    }

    private int cleanup(Set<MethodNode> methods) {
        AtomicInteger count = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
            if (classNode.methods.removeIf(methods::contains)) {
                count.getAndIncrement();
            }
        });
        return count.get();
    }
}
