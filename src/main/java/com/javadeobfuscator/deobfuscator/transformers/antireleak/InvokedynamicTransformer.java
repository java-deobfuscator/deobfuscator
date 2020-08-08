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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class InvokedynamicTransformer extends Transformer<TransformerConfig>
{
	private Map<ClassNode, InsnList> bootstrapMap = new HashMap<>();
	
	@Override
	public boolean transform()
	{
		System.out.println("[AntiReleak] [InvokedynamicTransformer] Starting");
		System.out.println(
			"[AntiReleak] [InvokedynamicTransformer] Finding invokedynamic instructions");
		int amount = findInvokeDynamic();
		System.out.println("[AntiReleak] [InvokedynamicTransformer] Found "
			+ amount + " invokedynamic instructions");
		if(amount > 0)
		{
			System.out.println(
				"[AntiReleak] [InvokedynamicTransformer] Inlining invokedynamic");
			long start = System.currentTimeMillis();
			int inlined = inlineInvokeDynamic(amount);
			long end = System.currentTimeMillis();
			System.out.println("[AntiReleak] [InvokedynamicTransformer] Removed "
				+ inlined + " invokedynamic instructions, took "
				+ TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
			System.out.println(
				"[AntiReleak] [InvokedynamicTransformer] Cleaning up bootstrap methods");
			cleanup();
		}
		System.out.println("[AntiReleak] [InvokedynamicTransformer] Done");
		return amount > 0;
	}
	
	private int findInvokeDynamic()
	{
		AtomicInteger total = new AtomicInteger();
		classNodes().forEach(classNode -> {
			classNode.methods.forEach(methodNode -> {
				for(int i = 0; i < methodNode.instructions.size(); i++)
				{
					AbstractInsnNode abstractInsnNode =
						methodNode.instructions.get(i);
					if(abstractInsnNode instanceof InvokeDynamicInsnNode)
					{
						InvokeDynamicInsnNode dyn =
							(InvokeDynamicInsnNode)abstractInsnNode;
						if(dyn.bsmArgs.length == 3 && 
							(dyn.bsmArgs[2].equals(182) || dyn.bsmArgs[2].equals(184)))
							total.incrementAndGet();
						else if(dyn.bsmArgs.length == 8 && dyn.bsmArgs[0] instanceof Integer)
							total.incrementAndGet();
						else if(dyn.bsmArgs.length == 9 && dyn.bsmArgs[0] instanceof String
							 && isInteger((String)dyn.bsmArgs[0]))
							total.incrementAndGet();
						else if(dyn.bsmArgs.length == 7 && dyn.bsmArgs[0].equals("1")
							 && dyn.bsmArgs[1].equals("8"))
							total.incrementAndGet();
					}
				}
			});
		});
		return total.get();
	}
	
	private int inlineInvokeDynamic(int expected)
	{
		AtomicInteger total = new AtomicInteger();
		final boolean[] alerted = new boolean[100];
		
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
				if(type.getDescriptor().equals("Ljava/lang/String;"))
					if(!(target.value() instanceof String))
						return false;
				if(type.getDescriptor().equals("Ljava/lang/Integer;"))
					if(!(target.value() instanceof Integer))
						return false;
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
		classNodes().forEach(classNode -> {
			patchIndyTransformer(classNode);
			classNode.methods.forEach(methodNode -> {
				for(int i = 0; i < methodNode.instructions.size(); i++)
				{
					AbstractInsnNode abstractInsnNode =
						methodNode.instructions.get(i);
					if(abstractInsnNode instanceof InvokeDynamicInsnNode)
					{
						InvokeDynamicInsnNode dyn =
							(InvokeDynamicInsnNode)abstractInsnNode;
						if((dyn.bsmArgs.length == 3 && 
							(dyn.bsmArgs[2].equals(182) || dyn.bsmArgs[2].equals(184)))
							|| (dyn.bsmArgs.length == 9 && dyn.bsmArgs[0] instanceof String && isInteger((String)dyn.bsmArgs[0]))
							|| (dyn.bsmArgs.length == 8 && dyn.bsmArgs[0] instanceof Integer)
							|| (dyn.bsmArgs.length == 7 && dyn.bsmArgs[0].equals("1") && dyn.bsmArgs[1].equals("8")))
						{
							Handle bootstrap = dyn.bsm;
							ClassNode bootstrapClassNode =
								classes.get(bootstrap.getOwner());
							MethodNode bootstrapMethodNode =
								bootstrapClassNode.methods.stream()
									.filter(mn -> mn.name
										.equals(bootstrap.getName())
										&& mn.desc
											.equals(bootstrap.getDesc()))
									.findFirst().orElse(null);
							List<JavaValue> args = new ArrayList<>();
							args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); // Lookup
							args.add(JavaValue.valueOf(dyn.name)); // dyn
																	// method
																	// name
							args.add(new JavaObject(null, "java/lang/invoke/MethodType")); // dyn method
																// type
							Context context = new Context(provider);
							context.dictionary = classpath;
							context.constantPools = getDeobfuscator().getConstantPools();
							context.file = getDeobfuscator().getConfig().getInput();
							for(int i1 = 0; i1 < dyn.bsmArgs.length; i1++)
							{
								Object o = dyn.bsmArgs[i1];
								if(o.getClass() == Type.class)
								{
									Type type = (Type)o;
									args.add(JavaValue.valueOf(new JavaClass(
										type.getInternalName().replace('/', '.'), context)));
								}else if(o.getClass() == Integer.class && Type.getArgumentTypes(bootstrapMethodNode.desc)[i1 + 3]
									.getSort() == Type.INT)
									args.add(new JavaInteger((int)o));
								else
									args.add(JavaValue.valueOf(o));
							}
							try
							{
								JavaMethodHandle result =
									MethodExecutor.execute(bootstrapClassNode,
										bootstrapMethodNode, args, null,
										context);
								String clazz =
									result.clazz.replace('.', '/');
								MethodInsnNode replacement = null;
								switch(result.type)
								{
									case "virtual":
										replacement = new MethodInsnNode(
											(classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ? Opcodes.INVOKEINTERFACE 
												: Opcodes.INVOKEVIRTUAL, clazz,
											result.name, result.desc,
											(classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
										break;
									case "static":
										replacement = new MethodInsnNode(
											Opcodes.INVOKESTATIC, clazz,
											result.name, result.desc,
											false);
										break;
								}
								methodNode.instructions.set(abstractInsnNode, replacement);
								//Removes extra casts (should be ok?)
								if(replacement.getNext() != null
									&& replacement.getNext().getOpcode() == Opcodes.CHECKCAST
									&& Type.getReturnType(replacement.desc).getDescriptor().equals(((TypeInsnNode)replacement.getNext()).desc))
									methodNode.instructions.remove(replacement.getNext());
								total.incrementAndGet();
								int x = (int)(total.get() * 1.0d / expected
									* 100);
								if(x != 0 && x % 10 == 0 && !alerted[x - 1])
								{
									System.out.println(
										"[AntiReleak] [InvokedynamicTransformer] Done "
											+ x + "%");
									alerted[x - 1] = true;
								}
							}catch(ExecutionException ex)
							{
								if(ex.getCause() != null)
									ex.getCause()
										.printStackTrace(System.out);
								throw ex;
							}catch(Throwable t)
							{
								System.out.println(classNode.name);
								throw t;
							}
						}
					}
				}
			});
		});
		return total.get();
	}
	
	/**
	 * Patch the invokedynamic bootstrap method, jumping to the ConstantCallSite section.
	 * @param classNode The class
	 */
	private void patchIndyTransformer(ClassNode classNode)
	{
		Iterator<MethodNode> it = classNode.methods.iterator();
		while(it.hasNext())
		{
			MethodNode node = it.next();
			if(node.desc.equals(
				"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;"))
			{
				InsnList list = Utils.cloneInsnList(node.instructions);
				boolean patched = false;
				for(int i = 0; i < node.instructions.size(); i++)
				{
					AbstractInsnNode abstractInsnNode =
						node.instructions.get(i);
					if(abstractInsnNode.getType() == AbstractInsnNode.LABEL)
					{
						LabelNode labelNode = (LabelNode)abstractInsnNode;
						AbstractInsnNode after3 = labelNode.getNext().getNext().getNext();
						if(labelNode.getNext() != null && labelNode.getNext().getOpcode() == Opcodes.ASTORE
							&& labelNode.getNext().getNext() != null && labelNode.getNext().getNext().getOpcode() ==
							Opcodes.ALOAD && after3 != null && after3.getOpcode() == Opcodes.INVOKESTATIC)
						{
							//Start at label 80 by removing everything before it
							while(labelNode.getPrevious() != null)
								node.instructions.remove(labelNode.getPrevious());
							//Delete "astore10" from Label 80
							node.instructions.remove(labelNode.getNext());
							//5 nexts from after3 if the beginning of label 101
							AbstractInsnNode after8 = after3.getNext().getNext().getNext().getNext().getNext();
							if(after8 != null 
								&& after8.getType() == AbstractInsnNode.LABEL)
								if(after8.getNext() != null 
								&& after8.getNext().getOpcode() == Opcodes.ILOAD 
								&& after8.getNext().getNext() != null 
								&& after8.getNext().getNext().getOpcode() == Opcodes.IFNE)
								{
									//Label 101 (after 80) is a trap, delete it (Removing getNext twice)
									node.instructions.remove(after8.getNext());
									node.instructions.remove(after8.getNext());
									patched = true;
								}
						}
					}	
				}
				if(patched)
					bootstrapMap.put(classNode, list);
			}else if(node.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
				+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
				+ "Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
			{
				InsnList list = Utils.cloneInsnList(node.instructions);
				boolean patched = false;
				for(int i = 0; i < node.instructions.size(); i++)
				{
					AbstractInsnNode ain =
						node.instructions.get(i);
					if(ain.getOpcode() == Opcodes.INVOKESTATIC)
					{
						MethodInsnNode methodInsn = (MethodInsnNode)ain;
						if(methodInsn.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
							&& methodInsn.name.equals("getProperty")
							&& methodInsn.owner.equals("java/lang/System"))
						{
							node.instructions.remove(methodInsn.getPrevious());
							node.instructions.set(methodInsn, new LdcInsnNode("1.8.0_144"));
							patched = true;
						}
					}
				}
				if(patched)
				{
					for(int i = 0; i < node.instructions.size(); i++)
					{
						AbstractInsnNode ain =
							node.instructions.get(i);
						if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
							&& ((MethodInsnNode)ain).owner.equals("java/lang/Object")
							&& ((MethodInsnNode)ain).name.equals("equals"))
							node.instructions.remove(ain.getNext());
						else if(ain.getOpcode() == Opcodes.LDC
							&& ((LdcInsnNode)ain).cst instanceof Type
							&& ((Type)((LdcInsnNode)ain).cst).getInternalName().equals("java/net/URL")
							&& ain.getPrevious() != null && ain.getPrevious().getOpcode() == Opcodes.IFNONNULL
							&& ain.getPrevious().getPrevious() != null
							&& ain.getPrevious().getPrevious().getOpcode() == Opcodes.ALOAD)
						{
							LabelNode toJump = null;
							boolean first = false;
							AbstractInsnNode next = ain.getNext();
							while(next != null)
							{
								if(next.getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)next).owner.equals("java/lang/String")
									&& ((MethodInsnNode)next).name.equals("equals"))
								{
									if(!first)
									{
										first = true;
										next = next.getNext();
										continue;
									}
									toJump = (LabelNode)next.getNext();
									break;
								}
								next = next.getNext();
							}
							node.instructions.set(ain.getPrevious(), new JumpInsnNode(Opcodes.GOTO, toJump));
							while(next != null)
							{
								if(next.getOpcode() == Opcodes.IFEQ)
								{
									node.instructions.remove(next);
									break;
								}
								next = next.getNext();
							}
						}else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
							&& ((MethodInsnNode)ain).owner.equals("java/lang/String")
							&& ((MethodInsnNode)ain).name.equals("length")
							&& ain.getNext().getOpcode() == Opcodes.ICONST_5
							&& ain.getNext().getNext().getOpcode() == Opcodes.IF_ICMPLE)
						{
							AbstractInsnNode next = ain.getNext();
							boolean foundEquals = false;
							while(next != null)
							{
								if(next.getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)next).owner.equals("java/lang/Object")
									&& ((MethodInsnNode)next).name.equals("equals"))
									foundEquals = true;
								if(foundEquals && next.getOpcode() == -1)
								{
									node.instructions.insert(ain.getNext().getNext(), 
										new JumpInsnNode(Opcodes.GOTO, (LabelNode)next));
									break;
								}
								next = next.getNext();
							}
						}else if(ain.getOpcode() == Opcodes.AASTORE
							&& ain.getPrevious().getOpcode() == Opcodes.INVOKESTATIC
							&& ain.getPrevious().getPrevious().getOpcode() == Opcodes.LDC
							&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.AASTORE
							&& ain.getNext().getNext() != null
							&& ain.getNext().getNext().getOpcode() == Opcodes.GETSTATIC)
						{
							AbstractInsnNode next = ain.getNext().getNext().getNext().getNext().getNext().getNext();
							if(next.getOpcode() != Opcodes.ALOAD)
								while(next != null)
								{
									if(next.getOpcode() == -1
										&& next.getNext() != null
										&& next.getNext().getOpcode() == Opcodes.ALOAD
										&& next.getNext().getNext() != null
										&& next.getNext().getNext().getOpcode() == Opcodes.INSTANCEOF)
									{
										node.instructions.insert(
											ain.getNext().getNext().getNext().getNext().getNext(),
											new JumpInsnNode(Opcodes.GOTO, (LabelNode)next));
										node.instructions.remove(next.getNext().getNext());
										node.instructions.set(next.getNext(), new InsnNode(Opcodes.ICONST_1));
										break;
									}
									next = next.getNext();
								}
						}else if(ain.getOpcode() == Opcodes.POP
							&& ((ain.getPrevious() != null
							&& ain.getPrevious() instanceof LabelNode
							&& ain.getPrevious().getPrevious() != null
							&& ain.getPrevious().getPrevious().getOpcode() == Opcodes.INVOKEVIRTUAL
							&& ((MethodInsnNode)ain.getPrevious().getPrevious()).name.equals("invoke")
							&& ((MethodInsnNode)ain.getPrevious().getPrevious()).owner.equals("java/lang/reflect/Method")
							&& ain.getPrevious().getPrevious().getPrevious() != null
							&& ain.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.AASTORE
							&& ain.getPrevious().getPrevious().getPrevious().getPrevious() != null
							&& ain.getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.ALOAD
							&& ain.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious() != null
							&& ain.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.ICONST_0)
								|| (ain.getPrevious() != null
								&& ain.getPrevious().getOpcode() == Opcodes.INVOKEVIRTUAL
								&& ((MethodInsnNode)ain.getPrevious()).name.equals("invoke")
								&& ((MethodInsnNode)ain.getPrevious()).owner.equals("java/lang/reflect/Method")
								&& ain.getPrevious().getPrevious() != null
								&& ain.getPrevious().getPrevious().getOpcode() == Opcodes.AASTORE
								&& ain.getPrevious().getPrevious().getPrevious() != null
								&& ain.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.ALOAD
								&& ain.getPrevious().getPrevious().getPrevious().getPrevious() != null
								&& ain.getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.ICONST_0)))
						{
							//ProxySelector.setDefault()
							while(ain.getPrevious().getOpcode() != Opcodes.CHECKCAST)
							{
								node.instructions.remove(ain.getPrevious());
								i--;
							}
							node.instructions.insert(ain.getPrevious(), new InsnNode(Opcodes.POP));
						}else if(ain.getOpcode() == Opcodes.BIPUSH
							&& ((IntInsnNode)ain).operand == 20
							&& ain.getNext() != null
							&& ain.getNext().getOpcode() == Opcodes.AALOAD
							&& ain.getNext().getNext() != null
							&& ain.getNext().getNext().getOpcode() == Opcodes.AASTORE)
						{
							//ProxySelector.getDefault()
							AbstractInsnNode next = ain;
							boolean notFound = false;
							while(next != null)
							{
								if(next.getOpcode() == Opcodes.INVOKESTATIC
									&& ((MethodInsnNode)next).name.equals("forName")
									&& ((MethodInsnNode)next).owner.equals("java/lang/Class"))
									break;
								if(next.getOpcode() == Opcodes.LDC
								&& ((LdcInsnNode)next).cst instanceof Type)
								{
									notFound = true;
									break;
								}
								next = next.getNext();
							}
							InsnNode popNode = null;
							if(!notFound)
							{
								while(next.getNext() != null)
								{
									if(next.getNext().getOpcode() == Opcodes.LDC
										&& ((LdcInsnNode)next.getNext()).cst instanceof Type)
									{
										node.instructions.set(next, popNode = new InsnNode(Opcodes.POP));
										break;
									}
									node.instructions.remove(next.getNext());
								}
								LabelNode toJump = null;
								boolean first = false;
								//Fix because this code screws up an older patch
								AbstractInsnNode next1 = popNode.getNext().getNext();
								while(next1 != null)
								{
									if(next1.getOpcode() == Opcodes.INVOKEVIRTUAL
										&& ((MethodInsnNode)next1).owner.equals("java/lang/String")
										&& ((MethodInsnNode)next1).name.equals("equals"))
									{
										if(!first)
										{
											first = true;
											next1 = next1.getNext();
											continue;
										}
										toJump = (LabelNode)next1.getNext();
										break;
									}
									next1 = next1.getNext();
								}
								//Lookup ifnonnull
								AbstractInsnNode ifnonnull = ain;
								while(ifnonnull != null)
								{
									if(ifnonnull.getOpcode() == Opcodes.IFNONNULL)
										break;
									ifnonnull = ifnonnull.getPrevious();
								}
								node.instructions.set(ifnonnull, new JumpInsnNode(Opcodes.GOTO, toJump));
								while(next1 != null)
								{
									if(next1.getOpcode() == Opcodes.IFEQ)
									{
										node.instructions.remove(next1);
										break;
									}
									next1 = next1.getNext();
								}
							}
						}
					}
					bootstrapMap.put(classNode, list);
				}
			}else if(node.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
				+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
				+ "Ljava/lang/Object;)Ljava/lang/Object;"))
			{
				InsnList list = Utils.cloneInsnList(node.instructions);
				boolean patched = false;
				for(int i = 0; i < node.instructions.size(); i++)
				{
					AbstractInsnNode ain =
						node.instructions.get(i);
					if(ain.getOpcode() == Opcodes.CHECKCAST
						&& ((TypeInsnNode)ain).desc.equals("java/lang/invoke/MethodHandles$Lookup")
						&& Utils.getPrevious(ain) != null
						&& Utils.getPrevious(ain).getOpcode() == Opcodes.ALOAD)
					{
						AbstractInsnNode prev = Utils.getPrevious(ain).getPrevious();
		    			while(!Utils.isInstruction(prev) && !(prev instanceof LabelNode))
		    				prev = prev.getPrevious();
		    			if(prev instanceof LabelNode)
		    			{
		    				node.instructions.insert(new JumpInsnNode(Opcodes.GOTO, (LabelNode)prev));
		    				patched = true;
		    				break;
		    			}
					}	
				}
				if(patched)
					bootstrapMap.put(classNode, list);
			}else if(node.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
				+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
				+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
			{
				InsnList list = Utils.cloneInsnList(node.instructions);
				boolean patched = false;
				for(int i = 0; i < node.instructions.size(); i++)
				{
					AbstractInsnNode ain =
						node.instructions.get(i);
					if(ain.getOpcode() == Opcodes.NEW
						&& ((TypeInsnNode)ain).desc.equals("java/util/zip/ZipFile"))
					{
						AbstractInsnNode next = ain;
						LabelNode lbl = null;
		    			while(true)
		    			{
		    				if(next instanceof LabelNode)
		    					lbl = (LabelNode)next;
		    				if(next.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)next).var == 8)
		    					break;
		    				next = next.getNext();
		    			}
		    			node.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, (LabelNode)lbl));
		    			patched = true;
					}else if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).owner.equals(classNode.name)
						&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.IFNE)
					{
						LabelNode jump = ((JumpInsnNode)ain.getNext()).label;
						node.instructions.remove(ain.getNext());
						node.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, jump));
					}
				}
				if(patched)
					bootstrapMap.put(classNode, list);
			}
		}
	}

	/**
	 * Removes the invokedynamic decryption method, string decryption method,
	 * and removes the extra fields.
	 * Also removes signatures and attributes.
	 */
	private void cleanup()
	{
		classNodes().forEach(classNode -> {
			if(bootstrapMap.containsKey(classNode))
			{
				boolean hasIndyNode = false;
				//Special string decrypt method remover
				for(int i = 0; i < classNode.methods.size(); i++) 
				{
					MethodNode method = classNode.methods.get(i);
					if((method.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
						+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
						+ "Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
						|| method.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;)Ljava/lang/Object;"))
						&& i > 0 && classNode.methods.get(i - 1).desc.equals("(Ljava/lang/String;)Ljava/lang/String;"))
					{
						classNode.methods.remove(i - 1);
						hasIndyNode = true;
						break;
					}
				}
				Iterator<MethodNode> it = classNode.methods.iterator();
				List<MethodInsnNode> bootstrapReferences = new ArrayList<>();
				List<FieldInsnNode> bootstrapFieldReferences = new ArrayList<>();
				while(it.hasNext())
				{
					MethodNode node = it.next();
					if(node.desc.equals(
						"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
						+ "Ljava/lang/Class;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;"))
					{
						//Check invokes
						for(AbstractInsnNode ain : bootstrapMap.get(classNode).toArray())
							if(ain.getOpcode() == Opcodes.INVOKESTATIC)
							{
								MethodInsnNode methodNode = (MethodInsnNode)ain;
								if(methodNode.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
									&& methodNode.owner.equals(classNode.name))
									bootstrapReferences.add(methodNode);
							}else if(ain.getOpcode() == Opcodes.PUTSTATIC || ain.getOpcode() == Opcodes.GETSTATIC)
							{	
								FieldInsnNode fieldNode = (FieldInsnNode)ain;
								if(fieldNode.owner.equals(classNode.name))
									bootstrapFieldReferences.add(fieldNode);
							}
						it.remove();
						hasIndyNode = true;
					}else if(node.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;)Ljava/lang/Object;")
						|| node.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
						|| node.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
							+ "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
					{
						//Check putstatic
						for(AbstractInsnNode ain : bootstrapMap.get(classNode).toArray())
							if(ain.getOpcode() == Opcodes.PUTSTATIC || ain.getOpcode() == Opcodes.GETSTATIC)
							{	
								FieldInsnNode fieldNode = (FieldInsnNode)ain;
								if(fieldNode.owner.equals(classNode.name))
									bootstrapFieldReferences.add(fieldNode);
							}
						it.remove();
						hasIndyNode = true;
					}
				}
				for(MethodInsnNode insnNode : bootstrapReferences)
				{
					MethodNode method = classNode.methods.stream().filter(
						m -> m.name.equals(insnNode.name) && m.desc.equals(insnNode.desc)).findFirst().orElse(null);
					if(method != null && method.desc
						.equals("(Ljava/lang/String;)Ljava/lang/String;"))
					{
						classNode.methods.remove(method);
						hasIndyNode = true;
					}
				}
				if(hasIndyNode)
				{
					//Signature and attributes
					classNode.signature = null;
					if(classNode.attrs != null) 
					{
						Iterator<Attribute> attributeIterator = classNode.attrs.iterator();
						while(attributeIterator.hasNext()) 
						{
		                    Attribute attribute = attributeIterator.next();
		                    if(attribute.type.equalsIgnoreCase("PluginVersion"))
		                        attributeIterator.remove();
		                    if(attribute.type.equalsIgnoreCase("CompileVersion"))
		                        attributeIterator.remove();
		                }
					}
					for(FieldInsnNode fieldInsn : bootstrapFieldReferences)
					{
						FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(fieldInsn.name)
							&& f.desc.equals(fieldInsn.desc)).findFirst().orElse(null);
						if(field != null)
						{
							if(classNode.fields.indexOf(field) < classNode.fields.size() - 1)
							{
								FieldNode next = classNode.fields.get(classNode.fields.indexOf(field) + 1);
								try
								{
									Integer.parseInt(next.name);
									classNode.fields.remove(next);
								}catch(NumberFormatException e)
								{
									
								}
							}
							if(classNode.fields.indexOf(field) > 0)
							{
								FieldNode next = classNode.fields.get(classNode.fields.indexOf(field) - 1);
								try
								{
									Integer.parseInt(next.name);
									classNode.fields.remove(next);
								}catch(NumberFormatException e)
								{
									
								}
							}
							classNode.fields.remove(field);
						}
					}
				}
			}
		});
	}
	
	private boolean isInteger(String str)
	{
		try
		{
			Integer.parseInt(str);
		}catch(NumberFormatException e)
		{
			return false;
		}
		return true;
	}
}
