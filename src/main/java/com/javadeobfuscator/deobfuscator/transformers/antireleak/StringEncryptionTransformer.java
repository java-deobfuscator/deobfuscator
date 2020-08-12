/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.antireleak;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class StringEncryptionTransformer extends Transformer<TransformerConfig>
{
	/**
	 * Contains a map of patched objects and their objects to null
	 */
	private Map<ClassNode, FieldNode> patched = new HashMap<>();
	
	@Override
	public boolean transform()
	{
		System.out
			.println("[AntiReleak] [StringEncryptionTransformer] Starting");
		
		int total = decrypt();
		System.out
			.println("[AntiReleak] [StringEncryptionTransformer] Decrypted "
				+ total + " strings");
		
		System.out.println("[AntiReleak] [StringEncryptionTransformer] Done");
		return total > 0;
	}
	
	private int decrypt()
	{
		DelegatingProvider provider = new DelegatingProvider();
		provider.register(new PrimitiveFieldProvider());
		provider.register(new MappedFieldProvider());
		provider.register(new JVMMethodProvider());
		provider.register(new MappedMethodProvider(classes));
		provider.register(new ComparisonProvider()
		{
			@Override
			public boolean instanceOf(JavaValue target, Type type,
				Context context)
			{
				return true;
			}
			
			@Override
			public boolean checkcast(JavaValue target, Type type,
				Context context)
			{
				if(type.getDescriptor().equals("[C"))
					if(!(target.value() instanceof char[]))
						return false;
				return true;
			}
			
			@Override
			public boolean checkEquality(JavaValue first, JavaValue second,
				Context context)
			{
				return true;
			}
			
			@Override
			public boolean canCheckInstanceOf(JavaValue target, Type type,
				Context context)
			{
				return true;
			}
			
			@Override
			public boolean canCheckcast(JavaValue target, Type type,
				Context context)
			{
				return true;
			}
			
			@Override
			public boolean canCheckEquality(JavaValue first, JavaValue second,
				Context context)
			{
				return false;
			}
		});
		
		AtomicInteger total = new AtomicInteger(0);
		//Note: It isn't possible to remove the string encryption method because some aload methods use it
		classNodes().forEach(classNode -> {
			classNode.methods.forEach(methodNode -> {
				AnalyzerResult analyzerResult = null;
				InsnList methodInsns = methodNode.instructions;
				for(int insnIndex = 0; insnIndex < methodInsns
					.size(); insnIndex++)
				{
					AbstractInsnNode ain = methodInsns.get(insnIndex);
					if(ain.getOpcode() == Opcodes.INVOKESTATIC)
					{
						MethodInsnNode min = (MethodInsnNode)ain;
						AbstractInsnNode previous = ain.getPrevious();
						if(min.owner.equals(classNode.name) 
							&& min.desc
							.equals("(Ljava/lang/String;)Ljava/lang/String;")
							&& methodNode.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
								+ "Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;")
							&& previous != null
							&& previous.getOpcode() == Opcodes.LDC)
						{
							LdcInsnNode ldc = (LdcInsnNode)previous;
							if(ldc.cst instanceof String)
							{
								Context context = new Context(provider);
								MethodNode decryptMethod =
									classNode.methods.stream()
										.filter(mn -> mn.name.equals(min.name)
											&& mn.desc.equals(min.desc))
										.findFirst().orElse(null);
								if(decryptMethod == null)
									throw new RuntimeException("Decrypt method cannot be found");
								String result =
									MethodExecutor.execute(
										classes.get(min.owner), decryptMethod,
										Arrays.asList(new JavaObject(ldc.cst,
											"java/lang/String")),
										null, context);
								methodNode.instructions
									.remove(ain.getPrevious());
								methodNode.instructions.set(ain,
									new LdcInsnNode(result));
								total.getAndIncrement();
							}
						}else if(min.desc.equals(methodNode.desc)
							&& min.name.equals(methodNode.name)
							&& min.owner.equals(classNode.name)
							&& methodNode.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
								+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
								+ "Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
						{
							if(!patched.containsKey(classNode))	
							{
								FieldNode toNull = null;
								for(int i = 0; i < methodNode.instructions.size(); i++)
								{
									AbstractInsnNode ain1 =
										methodNode.instructions.get(i);
									if(ain1.getOpcode() == Opcodes.INVOKESTATIC)
									{
										MethodInsnNode methodInsn = (MethodInsnNode)ain1;
										if(methodInsn.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
											&& methodInsn.name.equals("getProperty")
											&& methodInsn.owner.equals("java/lang/System"))
										{
											methodNode.instructions.remove(methodInsn.getPrevious());
											methodNode.instructions.set(methodInsn, new LdcInsnNode("1.8.0_144"));
										}
									}else if(ain1.getOpcode() == Opcodes.AASTORE
										&& ain1.getPrevious().getOpcode() == Opcodes.INVOKESTATIC
										&& ain1.getPrevious().getPrevious().getOpcode() == Opcodes.LDC
										&& ain1.getNext() != null && ain1.getNext().getOpcode() == Opcodes.AASTORE
										&& ain1.getNext().getNext() != null
										&& ain1.getNext().getNext().getOpcode() == Opcodes.GETSTATIC)
									{
										AbstractInsnNode next = ain1.getNext().getNext().getNext().getNext().getNext().getNext();
										while(next != null)
										{
											if(next.getOpcode() == -1
												&& next.getNext() != null
												&& next.getNext().getOpcode() == Opcodes.ALOAD
												&& next.getNext().getNext() != null
												&& next.getNext().getNext().getOpcode() == Opcodes.INSTANCEOF)
											{
												methodNode.instructions.insert(
													ain1.getNext().getNext().getNext().getNext().getNext(),
													new JumpInsnNode(Opcodes.GOTO, (LabelNode)next));
												methodNode.instructions.remove(next.getNext().getNext());
												methodNode.instructions.set(next.getNext(), new InsnNode(Opcodes.ICONST_1));
												break;
											}
											next = next.getNext();
										}
									}
									if(toNull == null && ain1.getOpcode() == Opcodes.GETSTATIC)
									{
										FieldInsnNode fieldInsn = (FieldInsnNode)ain1;
										if(fieldInsn.owner.equals(classNode.name))
											toNull = classNode.fields.stream().filter(f -> f.desc.equals(fieldInsn.desc)
												&& f.name.equals(fieldInsn.name)).findFirst().orElse(null);
									}
								}
								patched.put(classNode, toNull);
							}
							Context context = new Context(provider);
							context.dictionary = classpath;
							context.file = getDeobfuscator().getConfig().getInput();
							List<JavaValue> args = new ArrayList<>();
							provider.setField(classNode.name, patched.get(classNode).name, 
								patched.get(classNode).desc, null, null, context);
							if(analyzerResult == null)
								analyzerResult = MethodAnalyzer.analyze(classNode, methodNode);
							List<Frame> toRemove = ((MethodFrame)analyzerResult.getFrames().get(min).get(0)).getArgs();
							for(Frame f : toRemove)
								if(f.getOpcode() == Opcodes.ACONST_NULL)
									args.add(JavaValue.valueOf(null));	
								else if(f.getOpcode() == Opcodes.LDC)
									args.add(JavaValue.valueOf(((LdcFrame)f).getConstant()));
								else
								{
									toRemove = new ArrayList<>();
									args.clear();
									break;
								}
							for(Frame f : toRemove)
								methodNode.instructions.remove(analyzerResult.getInsnNode(f));
							if(args.size() != 0)
							{
								String result = MethodExecutor.execute(classNode, methodNode, args, null, context);
								methodNode.instructions.set(min, new LdcInsnNode(result));
								total.getAndIncrement();
							}
						}else if(min.desc.equals(methodNode.desc)
							&& min.name.equals(methodNode.name)
							&& min.owner.equals(classNode.name)
							&& methodNode.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
								+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
								+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
						{
							Context context = new Context(provider);
							context.dictionary = classpath;
							context.file = getDeobfuscator().getConfig().getInput();
							context.constantPools = getDeobfuscator().getConstantPools();
							List<JavaValue> args = new ArrayList<>();
							if(analyzerResult == null)
								analyzerResult = MethodAnalyzer.analyze(classNode, methodNode);
							List<Frame> toRemove = ((MethodFrame)analyzerResult.getFrames().get(min).get(0)).getArgs();
							for(Frame f : toRemove)
								if(f.getOpcode() == Opcodes.ACONST_NULL)
									args.add(JavaValue.valueOf(null));	
								else if(f.getOpcode() == Opcodes.LDC)
									args.add(JavaValue.valueOf(((LdcFrame)f).getConstant()));
								else
								{
									toRemove = new ArrayList<>();
									args.clear();
									break;
								}
							for(Frame f : toRemove)
								methodNode.instructions.remove(analyzerResult.getInsnNode(f));
							if(args.size() != 0)
							{
								String result = MethodExecutor.execute(classNode, methodNode, args, null, context);
								methodNode.instructions.set(min, new LdcInsnNode(result));
								total.getAndIncrement();
							}
						}
					}
				}
			});
		});
		return total.get();
	}
}
