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

package com.javadeobfuscator.deobfuscator.transformers.dasho;

import com.javadeobfuscator.deobfuscator.asm.source.*;
import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class StringEncryptionTransformer extends Transformer<TransformerConfig> {
    private static final Type STRING_TYPE = Type.getObjectType("java/lang/String");

    @Override
    public boolean transform() throws Exception {
    	DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));

        AtomicInteger count = new AtomicInteger();
        Set<MethodNode> decryptor = new HashSet<>();

        System.out.println("[DashO] [StringEncryptionTransformer] Starting");

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

                insns:
                for(AbstractInsnNode ain : TransformerHelper.instructionIterator(method))
                	if(ain.getOpcode() == Opcodes.INVOKESTATIC) 
                	{
                        MethodInsnNode m = (MethodInsnNode)ain;
                        if(!Type.getReturnType(m.desc).equals(STRING_TYPE)) 
                        	continue;

                        Type[] argTypes = Type.getArgumentTypes(m.desc);
                        if (!TransformerHelper.hasArgumentTypes(argTypes, Type.INT_TYPE, STRING_TYPE)
                        	|| TransformerHelper.hasArgumentTypesOtherThan(argTypes, Type.INT_TYPE, STRING_TYPE))
                            continue;
                        String strCl = m.owner;
                        Frame<SourceValue> currentFrame = frames[method.instructions.indexOf(m)];
                        List<JavaValue> args = new ArrayList<>();
                        List<AbstractInsnNode> instructions = new ArrayList<>();
                        
                        for(int i = 0, stackOffset = currentFrame.getStackSize() - argTypes.length; i < argTypes.length; i++) 
                        {
                            Optional<Object> consensus = SourceFinder.findSource(method, frames, instructions, new ConstantPropagatingSourceFinder(), 
                            	m, currentFrame.getStack(stackOffset)).consensus();
                            if(!consensus.isPresent())
                            	continue insns;

                            Object o = consensus.get();
                            if(o instanceof Integer)
                            	args.add(new JavaInteger((int)o));
                            else
                            	args.add(new JavaObject(o, "java/lang/String"));
                            stackOffset++;
                        }
                        instructions = new ArrayList<>(new HashSet<>(instructions));
                        Context context = new Context(provider);
                        context.dictionary = classpath;
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
                        	if(decryptor.contains(decrypterNode) || isDashOMethod(insnCount, invokeCount))
                        	{
                        		try
                        		{
                        			modifier.replace(m, new LdcInsnNode(MethodExecutor.execute(innerClassNode, decrypterNode, 
                        				args, null, context)));
                        			modifier.removeAll(instructions);
                        			decryptor.add(decrypterNode);
                        			count.getAndIncrement();
                        		}catch(Throwable t) 
                        		{
                        			System.out.println("Error while decrypting DashO string.");
                        			System.out.println("Are you sure you're deobfuscating something obfuscated by DashO?");
                        			System.out.println(classNode.name + " " + method.name + method.desc + " " + m.owner + " " + m.name + m.desc);
                        			t.printStackTrace(System.out);
                        		}
                        	}
                        }
                	}
                modifier.apply(method);
            }
        System.out.println("[DashO] [StringEncryptionTransformer] Decrypted " + count + " encrypted strings");
        System.out.println("[DashO] [StringEncryptionTransformer] Removed " + cleanup(decryptor) + " decryption methods");
        System.out.println("[DashO] [StringEncryptionTransformer] Done");
        return count.get() > 0;
    }
    
    private boolean isDashOMethod(Map<Integer, AtomicInteger> insnCount, Map<String, AtomicInteger> invokeCount) {
    	if(insnCount.get(Opcodes.IXOR) == null ||
    		insnCount.get(Opcodes.ISHL) == null ||
    		invokeCount.get("toCharArray") == null)
    			return false;
        return insnCount.get(Opcodes.IXOR).get() >= 1 &&
               insnCount.get(Opcodes.ISHL).get() >= 1 &&
               invokeCount.get("toCharArray").get() >= 1;
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
