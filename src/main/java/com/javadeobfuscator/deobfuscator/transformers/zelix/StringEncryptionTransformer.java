package com.javadeobfuscator.deobfuscator.transformers.zelix;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.*;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class StringEncryptionTransformer extends Transformer<TransformerConfig>
{
	public static List<String> includeOnly;
	
	@Override
	public boolean transform() throws Throwable
	{
		DelegatingProvider provider = new DelegatingProvider();
		provider.register(new MappedFieldProvider());
		provider.register(new JVMMethodProvider());
		provider.register(new MappedMethodProvider(classes));
		provider.register(new ComparisonProvider()
		{
			@Override
			public boolean instanceOf(JavaValue target, Type type,
				Context context)
			{
				if(target.value() instanceof JavaObject
					&& type.getInternalName().equals(((JavaObject)target.value()).type()))
					return true;
				return false;
			}

			@Override
			public boolean checkcast(JavaValue target, Type type,
				Context context)
			{
				return true;
			}

			@Override
			public boolean checkEquality(JavaValue first, JavaValue second,
				Context context)
			{
				return false;
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
		Context context = new Context(provider);
		context.dictionary = classes;
		AtomicInteger encClasses = new AtomicInteger();
		AtomicInteger encStrings = new AtomicInteger();
		System.out
			.println("[Zelix] [StringEncryptionTransformer] Starting");
		classNodes().forEach(classNode -> {
			MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
			if(clinit != null && (includeOnly == null || includeOnly.contains(classNode.name)))
			{
				//Multiple layers of ZKM
				List<Integer> ran = new ArrayList<>();
				boolean modified = false;
				boolean runOnce = false;
				Map<ClassNode, List<MethodNode>> toRemove = new HashMap<>();
				do
				{
					//Fix flow obf methods
					int firstLabel = clinit.instructions.size() - 1;
					for(AbstractInsnNode a : clinit.instructions.toArray())
						if(a.getOpcode() == -1)
						{
							firstLabel = clinit.instructions.indexOf(a);
							break;
						}
					AbstractInsnNode invoke = null;
					for(int i1 = 0; i1 < clinit.instructions.size(); i1++)
					{
						if(i1 > firstLabel)
							break;
						AbstractInsnNode a = clinit.instructions.get(i1);
						if(a.getOpcode() == Opcodes.INVOKESTATIC
							&& ((MethodInsnNode)a).owner.equals(classNode.name)
							&& Type.getArgumentTypes(((MethodInsnNode)a).desc).length == 1
							&& Type.getReturnType(((MethodInsnNode)a).desc).getSort() == Type.VOID)
						{
							MethodNode method = classNode.methods.stream().filter(m -> 
							m.name.equals(((MethodInsnNode)a).name) && m.desc.equals(((MethodInsnNode)a).desc)).
								findFirst().orElse(null);
							if(method != null && Modifier.isStatic(method.access))
							{
								List<AbstractInsnNode> instrs = new ArrayList<>();
								for(AbstractInsnNode ain : method.instructions.toArray())
									if(Utils.isInstruction(ain))
										instrs.add(ain);
								if(instrs.size() == 3
									&& instrs.get(0).getOpcode() >= Opcodes.ILOAD
									&& instrs.get(0).getOpcode() <= Opcodes.ALOAD
									&& instrs.get(1).getOpcode() == Opcodes.PUTSTATIC
									&& ((FieldInsnNode)instrs.get(1)).owner.equals(classNode.name)
									&& instrs.get(2).getOpcode() == Opcodes.RETURN)
									invoke = a;
							}
						}
					}
					AbstractInsnNode first = null;
					if(invoke != null)
					{
						for(int i1 = 0; i1 < firstLabel; i1++)
						{
							AbstractInsnNode a = clinit.instructions.get(i1);
							if(a.getOpcode() == Opcodes.INVOKEINTERFACE
								&& ((MethodInsnNode)a).desc.equals("(I)I")
								&& a.getPrevious().getOpcode() == Opcodes.LDC
								&& a.getPrevious().getPrevious().getOpcode() == Opcodes.INVOKESTATIC
								&& ((MethodInsnNode)a.getPrevious().getPrevious()).desc.startsWith("(II)")
								&& a.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.LDC
								&& a.getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.LDC)
							{
								first = a.getPrevious().getPrevious().getPrevious().getPrevious();
								break;
							}else if(a.getOpcode() == Opcodes.ISTORE
								&& a.getPrevious().getOpcode() == Opcodes.IXOR
								&& (a.getPrevious().getPrevious().getOpcode() == Opcodes.SIPUSH
								|| a.getPrevious().getPrevious().getOpcode() == Opcodes.LDC)
								&& (a.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.GETSTATIC
								|| a.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.LDC))
							{
								first = a.getPrevious().getPrevious().getPrevious();
								break;
							}else if(((a.getOpcode() == Opcodes.ASTORE
								&& a.getPrevious().getOpcode() == Opcodes.ANEWARRAY
								&& ((TypeInsnNode)a.getPrevious()).desc.equals("java/lang/String"))
								|| (a.getOpcode() == Opcodes.PUTSTATIC
								&& a.getPrevious().getOpcode() == Opcodes.ANEWARRAY
								&& ((TypeInsnNode)a.getPrevious()).desc.equals("java/lang/Object")))
								&& Utils.isInteger(a.getPrevious().getPrevious()))
							{
								first = a.getPrevious().getPrevious();
								break;
							}
						}
						if(first != null && clinit.instructions.indexOf(invoke) > clinit.instructions.indexOf(first)
							&& Type.getArgumentTypes(((MethodInsnNode)invoke).desc).length == 1)
						{
							org.objectweb.asm.tree.analysis.Frame<SourceValue>[] frames;
							try 
							{
								frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, clinit);
							}catch(AnalyzerException e)
							{
					         	throw new RuntimeException(e);
							}
							org.objectweb.asm.tree.analysis.Frame<SourceValue> methodFrame = frames[clinit.instructions.indexOf(invoke)];
							SourceValue arg = methodFrame.getStack(methodFrame.getStackSize() - 1);
							if(arg.insns.size() == 1)
							{
								AbstractInsnNode top = arg.insns.iterator().next();
								if(top.getOpcode() == Opcodes.NEWARRAY || top.getOpcode() == Opcodes.ANEWARRAY)
								{
									org.objectweb.asm.tree.analysis.Frame<SourceValue> arrArgs = frames[clinit.instructions.indexOf(top)];
									if(arrArgs.getStack(arrArgs.getStackSize() - 1).insns.size() == 1)
									{
										AbstractInsnNode length = arrArgs.getStack(arrArgs.getStackSize() - 1).insns.iterator().next();
										clinit.instructions.remove(invoke);
	    								clinit.instructions.remove(top);
	    								clinit.instructions.remove(length);
	    								clinit.instructions.insertBefore(first, length);
	    								clinit.instructions.insertBefore(first, top);
	    								clinit.instructions.insertBefore(first, invoke);
									}
								}else if((top.getOpcode() == Opcodes.LDC
									&& !(((LdcInsnNode)top).cst instanceof Double)
									&& !(((LdcInsnNode)top).cst instanceof Long)) || Utils.isInteger(top)
									|| top.getOpcode() == Opcodes.ACONST_NULL)
								{
									clinit.instructions.remove(invoke);
    								clinit.instructions.remove(top);
    								clinit.instructions.insertBefore(first, top);
    								clinit.instructions.insertBefore(first, invoke);
								}
							}
						}
					}
					modified = false;
					//Find out the start of the ZKM instructions (1st occurrence)
					int mode = -1;
					int zkm8ArraySize = Integer.MIN_VALUE;
					int singleStrDecryptNumb = Integer.MIN_VALUE;
					AbstractInsnNode firstZKMInstr = null;
					AbstractInsnNode firstZKMDecrypt = null;
					AbstractInsnNode beforeJump = null;
					MethodInsnNode clinit1 = null;
					MethodInsnNode clinit2 = null;
					for(AbstractInsnNode ain : clinit.instructions.toArray())
					{
						//ZKM 8 (includes special case where string decrypt in diff method)
						if(Utils.isInteger(ain) && ain.getNext() != null
							&& ain.getNext().getOpcode() == Opcodes.ANEWARRAY
							&& ((TypeInsnNode)ain.getNext()).desc.equals("java/lang/String")
							&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.ASTORE
							&& ain.getNext().getNext().getNext() != null 
							&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.ICONST_0
							&& ain.getNext().getNext().getNext().getNext() != null 
							&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE)
						{
							zkm8ArraySize = Utils.getIntValue(ain);
							AbstractInsnNode next = ain;
							while(next != null)
							{
								if(next.getOpcode() == Opcodes.GOTO)
									break;
								next = next.getNext();
							}
							if(next != null && next.getOpcode() == Opcodes.GOTO)
							{
								LabelNode jump = ((JumpInsnNode)next).label;
								if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext())
									&& jump.getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
								{
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext();
									AbstractInsnNode preJump = jump.getNext().getNext().getNext().getNext().getNext();
									AbstractInsnNode label = ((JumpInsnNode)preJump.getNext()).label;
									next = label.getNext();
									while(next != null)
									{
										if(next instanceof LabelNode)
											throw new RuntimeException("Failed to find if_icmpge in ZKM 8 string enc at " + classNode.name);
										if(next.getOpcode() == Opcodes.IF_ICMPGE)
										{
											beforeJump = next.getPrevious();
											break;
										}
										next = next.getNext();
									}
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.DUP_X2
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
								{
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext().getNext();
									AbstractInsnNode preJump = jump.getNext().getNext().getNext().getNext().getNext().getNext();
									AbstractInsnNode label = ((JumpInsnNode)preJump.getNext()).label;
									next = label.getNext();
									while(next != null)
									{
										if(next instanceof LabelNode)
											throw new RuntimeException("Failed to find if_icmpge in ZKM 8 string enc at " + classNode.name);
										if(next.getOpcode() == Opcodes.IF_ICMPGE)
										{
											beforeJump = next.getPrevious();
											break;
										}
										next = next.getNext();
									}
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext())
									&& jump.getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.TABLESWITCH)
								{
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext();
									AbstractInsnNode preJump = jump.getNext().getNext().getNext().getNext();
									AbstractInsnNode label = ((TableSwitchInsnNode)preJump.getNext()).labels.get(
										((TableSwitchInsnNode)preJump.getNext()).labels.size() - 1);
									next = label.getNext();
									while(next != null)
									{
										if(next instanceof LabelNode)
											throw new RuntimeException("Failed to find if_icmpge in ZKM 8 string enc at " + classNode.name);
										if(next.getOpcode() == Opcodes.IF_ICMPGE)
										{
											beforeJump = next.getPrevious();
											break;
										}
										next = next.getNext();
									}
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.DUP_X2
									&& jump.getNext().getNext() != null
									&& jump.getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.TABLESWITCH)
								{
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext().getNext();
									AbstractInsnNode preJump = jump.getNext().getNext().getNext().getNext().getNext();
									AbstractInsnNode label = ((TableSwitchInsnNode)preJump.getNext()).labels.get(
										((TableSwitchInsnNode)preJump.getNext()).labels.size() - 1);
									next = label.getNext();
									while(next != null)
									{
										if(next instanceof LabelNode)
											throw new RuntimeException("Failed to find if_icmpge in ZKM 8 string enc at " + classNode.name);
										if(next.getOpcode() == Opcodes.IF_ICMPGE)
										{
											beforeJump = next.getPrevious();
											break;
										}
										next = next.getNext();
									}
								}
							}
							firstZKMDecrypt = ain;
							firstZKMInstr = ain;
							mode = 0;
							break;
						}
						//ZKM singleton string and ZKM 5
						if(ain.getOpcode() == Opcodes.LDC)
						{
							if(ain.getNext() != null 
								&& ((Utils.isInteger(ain.getNext()) && Utils.getIntValue(ain.getNext()) == -1)
									|| ain.getNext().getOpcode() == Opcodes.ACONST_NULL)
								&& ain.getPrevious() != null && Utils.isInteger(ain.getPrevious())
								&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.GOTO)
							{
								//number - ldc - number or number - ldc - null (JSR)
								LabelNode jump = ((JumpInsnNode)ain.getNext().getNext()).label;
								if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.ASTORE
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)jump.getNext().getNext()).name.equals("toCharArray")
									&& ((MethodInsnNode)jump.getNext().getNext()).owner.equals("java/lang/String")
									&& jump.getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.DUP_X1)
								{
									//number - ldc - null
									firstZKMDecrypt = jump;
									firstZKMInstr = ain.getPrevious();
									mode = 1;
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)jump.getNext().getNext()).name.equals("toCharArray")
									&& ((MethodInsnNode)jump.getNext().getNext()).owner.equals("java/lang/String")
									&& jump.getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.DUP_X1)
								{
									//number - ldc - number
									firstZKMDecrypt = jump;
									firstZKMInstr = ain.getPrevious();
									mode = 1;
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.DUP_X2
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)jump.getNext().getNext().getNext()).name.equals("toCharArray")
									&& ((MethodInsnNode)jump.getNext().getNext().getNext()).owner.equals("java/lang/String")
									&& jump.getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.DUP_X1)
								{
									//number - ldc - -1 (ZKM 8)
									firstZKMDecrypt = jump;
									firstZKMInstr = ain.getPrevious();
									mode = 1;
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.DUP_X2
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
								{
									//number - ldc - -1 (method outside)
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext().getNext();
									firstZKMInstr = ain.getPrevious();
									mode = 1;
									beforeJump = jump.getNext().getNext().getNext().getNext().getNext().getNext();
									break;
								}
							}
							if(ain.getNext() != null && ((Utils.isInteger(ain.getNext()) 
								&& Utils.getIntValue(ain.getNext()) >= -1) || ain.getNext().getOpcode() == Opcodes.ACONST_NULL)
								&& ain.getNext().getNext() != null
								&& ain.getNext().getNext().getOpcode() == Opcodes.GOTO)
							{
								//ldc - (> -1) or ldc - null (JSR)
								singleStrDecryptNumb = Utils.getIntValue(ain.getNext());
								LabelNode jump = ((JumpInsnNode)ain.getNext().getNext()).label;
								if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)jump.getNext().getNext()).name.equals("toCharArray")
									&& ((MethodInsnNode)jump.getNext().getNext()).owner.equals("java/lang/String")
									&& jump.getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.DUP)
								{
									//ldc - num
									firstZKMDecrypt = jump;
									firstZKMInstr = ain;
									mode = 2;
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.ASTORE
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)jump.getNext().getNext()).name.equals("toCharArray")
									&& ((MethodInsnNode)jump.getNext().getNext()).owner.equals("java/lang/String")
									&& jump.getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.DUP)
								{
									//ldc - null
									firstZKMDecrypt = jump;
									firstZKMInstr = ain;
									mode = 2;
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext())
									&& jump.getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
								{
									//ldc - num (no switch)
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext();
									firstZKMInstr = ain;
									mode = 2;
									beforeJump = jump.getNext().getNext().getNext().getNext().getNext();
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext())
									&& jump.getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
									&& jump.getNext().getNext().getNext().getNext().getNext() != null 
									&& jump.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.TABLESWITCH)
								{
									//ldc - num (switch)
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext();
									firstZKMInstr = ain;
									mode = 2;
									beforeJump = jump.getNext().getNext().getNext().getNext();
									break;
								}else if(jump.getNext() != null && jump.getNext().getOpcode() == Opcodes.ASTORE
									&& jump.getNext().getNext() != null 
									&& jump.getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod1(classNode, (MethodInsnNode)jump.getNext().getNext())
									&& jump.getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
									&& isClinitMethod2(classNode, (MethodInsnNode)jump.getNext().getNext().getNext())
									&& jump.getNext().getNext().getNext().getNext() != null
									&& jump.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
								{
									//ldc - null
									firstZKMDecrypt = jump;
									clinit1 = (MethodInsnNode)jump.getNext().getNext();
									clinit2 = (MethodInsnNode)jump.getNext().getNext().getNext();
									firstZKMInstr = ain;
									mode = 2;
									beforeJump = jump.getNext().getNext().getNext();
									break;
								}
							}
							if(ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INVOKESTATIC
								&& isClinitMethod1(classNode, (MethodInsnNode)ain.getNext())
								&& ain.getNext().getNext() != null
								&& ain.getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
								&& isClinitMethod2(classNode, (MethodInsnNode)ain.getNext().getNext()))
							{
								//ldc - invoke - invoke
								firstZKMInstr = null;
								mode = 3;
								MethodNode clinitMethod1 = classNode.methods.stream().filter(m -> 
								m.desc.equals(((MethodInsnNode)ain.getNext()).desc) 
									&& m.name.equals(((MethodInsnNode)ain.getNext()).name)).findFirst().orElse(null);
								MethodNode clinitMethod2 = classNode.methods.stream().filter(m -> 
								m.desc.equals(((MethodInsnNode)ain.getNext().getNext()).desc) 
									&& m.name.equals(((MethodInsnNode)ain.getNext().getNext()).name)).findFirst().orElse(null);
								char[] res1 = MethodExecutor.execute(classNode, clinitMethod1, Arrays.asList(
									new JavaObject(((LdcInsnNode)ain).cst, "java/lang/String")), null, context);
								String res2 = MethodExecutor.execute(classNode, clinitMethod2, Arrays.asList(
									new JavaObject(res1, "java/lang/Object")), null, context);
								((LdcInsnNode)ain).cst = res2;
								encStrings.incrementAndGet();
								clinit.instructions.remove(ain.getNext().getNext());
								clinit.instructions.remove(ain.getNext());
								boolean found = false;
								loop:
								for(AbstractInsnNode a : clinit.instructions.toArray())
									if(a.getOpcode() == Opcodes.INVOKESTATIC
										&& (isClinitMethod1(classNode, (MethodInsnNode)a)
											|| isClinitMethod2(classNode, (MethodInsnNode)a)))
									{
										found = true;
										break loop;
									}
								if(!found)
								{
									classNode.methods.remove(clinitMethod1);
									classNode.methods.remove(clinitMethod2);
								}
								modified = true;
							}
						}
					}
					if(firstZKMInstr != null)
					{
						//Find "before jump" (if it doesn't have intern, it should be found already)
						if(beforeJump == null)
						{
							AbstractInsnNode preJump = null;
							AbstractInsnNode next = firstZKMDecrypt;
							while(next != null)
							{
								if(next.getOpcode() == Opcodes.INVOKEVIRTUAL
									&& ((MethodInsnNode)next).owner.equals("java/lang/String")
									&& ((MethodInsnNode)next).name.equals("intern"))
								{
									//GOTO and TABLESWITCH are BOTH candidates
									if(next.getNext() != null && next.getNext().getOpcode() == Opcodes.SWAP
										&& next.getNext().getNext() != null 
										&& next.getNext().getNext().getOpcode() == Opcodes.POP
										&& next.getNext().getNext().getNext() != null 
										&& next.getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
										&& next.getNext().getNext().getNext().getNext() != null 
										&& next.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.TABLESWITCH)
										preJump = next.getNext().getNext().getNext();
									else if(next.getNext() != null && next.getNext().getOpcode() == Opcodes.SWAP
										&& next.getNext().getNext() != null 
										&& next.getNext().getNext().getOpcode() == Opcodes.POP
										&& next.getNext().getNext().getNext() != null 
										&& next.getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
										&& next.getNext().getNext().getNext().getNext() != null 
										&& next.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
										&& next.getNext().getNext().getNext().getNext().getNext() != null 
										&& next.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
										preJump = next.getNext().getNext().getNext().getNext();
									else if(next.getNext() != null && next.getNext().getOpcode() == Opcodes.SWAP
										&& next.getNext().getNext() != null 
										&& next.getNext().getNext().getOpcode() == Opcodes.POP
										&& next.getNext().getNext().getNext() != null 
										&& next.getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
										preJump = next.getNext().getNext();
									break;
								}
								next = next.getNext();
							}
							if(mode != 0)
								beforeJump = preJump;
							else if(preJump != null)
							{
								AbstractInsnNode label = null;
								if(preJump.getNext().getOpcode() == Opcodes.TABLESWITCH)
									label = ((TableSwitchInsnNode)preJump.getNext()).labels.get(
										((TableSwitchInsnNode)preJump.getNext()).labels.size() - 1);
								else if(preJump.getNext().getOpcode() == Opcodes.GOTO)
									label = ((JumpInsnNode)preJump.getNext()).label;
								else
									throw new IllegalStateException(classNode.name + " Unexpected opcode: "
										+ preJump.getNext().getOpcode());
								next = label.getNext();
								while(next != null)
								{
									if(next instanceof LabelNode)
										throw new RuntimeException("Failed to find if_icmpge in ZKM 8 string enc at " + classNode.name);
									if(next.getOpcode() == Opcodes.IF_ICMPGE)
									{
										beforeJump = next.getPrevious();
										break;
									}
									next = next.getNext();
								}
							}
						}
						if(beforeJump != null)
						{
							LabelNode start = null;
							if(beforeJump.getNext().getOpcode() == Opcodes.GOTO)
								start = ((JumpInsnNode)beforeJump.getNext()).label;
							else if(beforeJump.getNext().getOpcode() == Opcodes.TABLESWITCH)
							{
								if(mode != 0)
								{
									if(singleStrDecryptNumb < 0)
										start = ((TableSwitchInsnNode)beforeJump.getNext()).dflt;
									else
										start = ((TableSwitchInsnNode)beforeJump.getNext()).labels.get(singleStrDecryptNumb);
								}else
									start = ((TableSwitchInsnNode)beforeJump.getNext()).labels.get(
										((TableSwitchInsnNode)beforeJump.getNext()).labels.size() - 1);
							}else if(beforeJump.getNext().getOpcode() == Opcodes.IF_ICMPGE)
								start = ((JumpInsnNode)beforeJump.getNext()).label;
							AbstractInsnNode insertedReturn = null;
							LabelNode insertedLabel = null;
							AbstractInsnNode insertedGoto = null;
							AbstractInsnNode startSpecialE = null;
							AbstractInsnNode endSpecialE = null;
							boolean done = false;
							do
							{
								switch(start.getNext().getOpcode())
								{
									case Opcodes.ALOAD:
										//ZKM 8
										if(mode == 0)
										{
											clinit.instructions.insert(start.getNext(), insertedReturn = new InsnNode(Opcodes.ARETURN));
											done = true;
											break;
										}
									default:
										if(mode != 0)
										{
											clinit.instructions.insert(start, insertedReturn = new InsnNode(Opcodes.ARETURN));
											done = true;
										}else if(mode == 0 && start.getNext().getOpcode() == Opcodes.LDC
											&& start.getNext().getNext().getOpcode() == Opcodes.DUP
											&& start.getNext().getNext().getNext().getOpcode() == Opcodes.ASTORE
											&& start.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL)
										{
											AbstractInsnNode next = start;
											while(next != null)
											{
												if(next.getOpcode() == Opcodes.GOTO)
													break;
												next = next.getNext();
											}
											startSpecialE = ((JumpInsnNode)next).label;
											//Now lets find intern
											AbstractInsnNode jumped = null;
											AbstractInsnNode afterInsertedLabel = startSpecialE;
											while(afterInsertedLabel != null)
											{
												if(afterInsertedLabel.getOpcode() == Opcodes.INVOKEVIRTUAL
													&& ((MethodInsnNode)afterInsertedLabel).owner.equals("java/lang/String")
													&& ((MethodInsnNode)afterInsertedLabel).name.equals("intern")
													&& afterInsertedLabel.getNext() != null
													&& afterInsertedLabel.getNext().getOpcode() == Opcodes.SWAP
													&& afterInsertedLabel.getNext().getNext() != null
													&& afterInsertedLabel.getNext().getNext().getOpcode() == Opcodes.POP
													&& afterInsertedLabel.getNext().getNext().getNext() != null
													&& afterInsertedLabel.getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
												{
													jumped = ((JumpInsnNode)afterInsertedLabel.getNext().getNext().getNext()).label;
													endSpecialE = afterInsertedLabel.getNext().getNext().getNext();
													break;
												}
												afterInsertedLabel = afterInsertedLabel.getNext();
											}
											while(jumped != null)
											{
												if(jumped.getOpcode() == Opcodes.IF_ICMPGE)
												{
													jumped = ((JumpInsnNode)jumped).label;
													break;
												}
												jumped = jumped.getNext();
											}
											start = (LabelNode)jumped;
										}else
											throw new RuntimeException("Unexpected opcode! Mode " + mode + ", opcode " 
												+ start.getNext().getOpcode() + " at " + classNode.name);
										break;
								}
							}while(!done);
							clinit.instructions.insertBefore(firstZKMInstr, insertedLabel = new LabelNode(new Label()));
							clinit.instructions.insert(insertedGoto = new JumpInsnNode(Opcodes.GOTO, insertedLabel));
							Object res = MethodExecutor.execute(classNode, clinit, Arrays.asList(), null, context);
							clinit.instructions.remove(insertedGoto);
							clinit.instructions.remove(insertedLabel);
							clinit.instructions.remove(insertedReturn);
							if(mode == 0)
							{
								if(start.getNext().getOpcode() == Opcodes.ALOAD
									&& start.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
									&& start.getNext().getNext().getNext() != null
									&& Utils.getIntValue(start.getNext().getNext().getNext()) == zkm8ArraySize
									&& start.getNext().getNext().getNext().getNext() != null				
									&& start.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ANEWARRAY
									&& ((TypeInsnNode)start.getNext().getNext().getNext().getNext()).desc.equals("java/lang/String")
									&& start.getNext().getNext().getNext().getNext().getNext() != null
									&& start.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC)
								{
									FieldInsnNode encryptedInsn = (FieldInsnNode)start.getNext().getNext();
									FieldInsnNode decryptedInsn = (FieldInsnNode)start.getNext().getNext().getNext().getNext().getNext();
									FieldNode encrypted = classNode.fields.stream().filter(m -> m.name.equals(encryptedInsn.name)
										&& m.desc.equals(encryptedInsn.desc)).findFirst().orElse(null);
									FieldNode decrypted = classNode.fields.stream().filter(m -> m.name.equals(decryptedInsn.name)
										&& m.desc.equals(decryptedInsn.desc)).findFirst().orElse(null);
									//Enhanced
									context.provider.setField(classNode.name, 
										encryptedInsn.name, 
										encryptedInsn.desc, 
										null, res, context);
									context.provider.setField(classNode.name, 
										decryptedInsn.name, 
										decryptedInsn.desc, 
										null, new String[zkm8ArraySize], context);
									List<MethodNode> decryptorMethods = new ArrayList<>();
									MethodNode decryptMethod = null;
									for(MethodNode method : classNode.methods)
										// We search, run, and fix all the methods that have
										// obfuscated strings.
										// Additionally, we also check to see if the method is a ZKM
										// decrypt method.
										// After a method is determined to be added by ZKM, it is
										// put
										// into a list.
										for(AbstractInsnNode ain : method.instructions.toArray())
											if(ain.getOpcode() == Opcodes.INVOKESTATIC)
											{
												MethodInsnNode min = (MethodInsnNode)ain;
												if(min.desc.equals("(II)Ljava/lang/String;")
													&& min.owner
														.equals(classNode.name))
												{
													if(ain.getPrevious() != null
														&& ain.getPrevious().getPrevious() != null)
													{
															decryptMethod =
																classNode.methods
																	.stream()
																	.filter(mn -> mn.name
																		.equals(min.name)
																		&& mn.desc.equals(min.desc))
																	.findFirst().orElse(null);
															boolean isZKMMethod1 = decryptorMethods
																.contains(decryptMethod);
															if(isZKMMethod1
																|| (decryptMethod != null
																&& isZKMEnhancedMethod(decryptMethod, decrypted)))
															{
																if(!decryptorMethods
																	.contains(decryptMethod))
																	decryptorMethods
																		.add(decryptMethod);
																if(Utils.isInteger(ain.getPrevious())
																	&& Utils.isInteger(ain.getPrevious().getPrevious()))
																{
																	int args1 = Utils.getIntValue(ain
																		.getPrevious().getPrevious());
																	int args2 =
																		Utils.getIntValue(ain.getPrevious());
																	List<JavaValue> args =
																		new ArrayList<>();
																	args.add(new JavaInteger(args1));
																	args.add(new JavaInteger(args2));
																	String result = MethodExecutor
																		.execute(classNode,
																			decryptMethod, args, null,
																			context);
																	if(result != null)
																	{
																		method.instructions
																			.remove(ain.getPrevious()
																				.getPrevious());
																		method.instructions
																			.remove(ain.getPrevious());
																		method.instructions.set(ain,
																			new LdcInsnNode(result));
																		encStrings.incrementAndGet();
																	}
																}else
																{
																	//Oh no, a ZKM method was identified, but the previous 2 values are not numbers!
																	//Reverse arg lookup (in order so first on list is first argument)
																	org.objectweb.asm.tree.analysis.Frame<SourceValue>[] frames;
																	try
																	{
																		frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
																	}catch(AnalyzerException e)
																	{
																		throw new RuntimeException(e);
																	}
																	org.objectweb.asm.tree.analysis.Frame<SourceValue> f = frames[method.instructions.indexOf(ain)];
																	AbstractInsnNode a1 = f.getStack(f.getStackSize() - 2).insns.iterator().next();
																	AbstractInsnNode a2 = f.getStack(f.getStackSize() - 1).insns.iterator().next();
																	int args1 = Utils.getIntValue(a1);
																	int args2 = Utils.getIntValue(a2);
																	List<JavaValue> args =
																		new ArrayList<>();
																	args.add(new JavaInteger(args1));
																	args.add(new JavaInteger(args2));
																	String result = MethodExecutor
																		.execute(classNode,
																			decryptMethod, args, null,
																			context);
																	if(result != null)
																	{
																		method.instructions.remove(a1);
																		method.instructions.remove(a2);
																		method.instructions.set(ain,
																			new LdcInsnNode(result));
																		encStrings.incrementAndGet();
																	}
																}
															}
													}
												}else if(min.desc.equals("(III)Ljava/lang/String;")
													&& min.owner.equals(classNode.name))
												{
													if(ain.getPrevious() != null
														&& ain.getPrevious().getPrevious() != null
														&& ain.getPrevious().getPrevious().getPrevious() != null)
													{
															decryptMethod =
																classNode.methods
																	.stream()
																	.filter(mn -> mn.name
																		.equals(min.name)
																		&& mn.desc.equals(min.desc))
																	.findFirst().orElse(null);
															boolean isZKMMethod1 = decryptorMethods
																.contains(decryptMethod);
															if(isZKMMethod1
																|| (decryptMethod != null
																&& isZKM9EnhancedMethod(decryptMethod, decrypted)))
															{
																if(!decryptorMethods
																	.contains(decryptMethod))
																	decryptorMethods
																		.add(decryptMethod);
																if(Utils.isInteger(ain.getPrevious())
																	&& Utils.isInteger(ain.getPrevious().getPrevious())
																	&& Utils.isInteger(ain.getPrevious().getPrevious().getPrevious()))
																{
																	int args1 = Utils.getIntValue(ain
																		.getPrevious().getPrevious().getPrevious());
																	int args2 = Utils.getIntValue(ain
																		.getPrevious().getPrevious());
																	int args3 =
																		Utils.getIntValue(ain.getPrevious());
																	List<JavaValue> args =
																		new ArrayList<>();
																	args.add(new JavaInteger(args1));
																	args.add(new JavaInteger(args2));
																	args.add(new JavaInteger(args3));
																	String result = MethodExecutor
																		.execute(classNode,
																			decryptMethod, args, null,
																			context);
																	if(result != null)
																	{
																		method.instructions
																		.remove(ain.getPrevious()
																			.getPrevious().getPrevious());
																		method.instructions
																			.remove(ain.getPrevious()
																				.getPrevious());
																		method.instructions
																			.remove(ain.getPrevious());
																		method.instructions.set(ain,
																			new LdcInsnNode(result));
																		encStrings.incrementAndGet();
																	}
																}else
																{
																	//Oh no, a ZKM method was identified, but the previous 3 values are not numbers!
																	//Reverse arg lookup (in order so first on list is first argument)
																	org.objectweb.asm.tree.analysis.Frame<SourceValue>[] frames;
																	try
																	{
																		frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
																	}catch(AnalyzerException e)
																	{
																		throw new RuntimeException(e);
																	}
																	org.objectweb.asm.tree.analysis.Frame<SourceValue> f = frames[method.instructions.indexOf(ain)];
																	AbstractInsnNode a1 = f.getStack(f.getStackSize() - 3).insns.iterator().next();
																	AbstractInsnNode a2 = f.getStack(f.getStackSize() - 2).insns.iterator().next();
																	AbstractInsnNode a3 = f.getStack(f.getStackSize() - 1).insns.iterator().next();
									        						if(!Utils.isInteger(a3))
									        						{
									        							System.out
									        							.println("[Zelix] [StringEncryptionTransformer] Warning: Unable to decrypt string"
									        								+ " at method " + method.name + method.desc + " from class " + classNode.name
									        								+ ". Other arguments: " + Utils.getIntValue(a1) + " " + Utils.getIntValue(a2));
									        							continue;
									        						}
																	int args1 = Utils.getIntValue(a1);
																	int args2 =
																		Utils.getIntValue(a2);
																	int args3 =
																		Utils.getIntValue(a3);
																	List<JavaValue> args =
																		new ArrayList<>();
																	args.add(new JavaInteger(args1));
																	args.add(new JavaInteger(args2));
																	args.add(new JavaInteger(args3));
																	String result = MethodExecutor
																		.execute(classNode,
																			decryptMethod, args, null,
																			context);
																	if(result != null)
																	{
																		method.instructions.remove(a1);
																		method.instructions.remove(a2);
																		method.instructions.remove(a3);
																		method.instructions.set(ain,
																			new LdcInsnNode(result));
																		encStrings.incrementAndGet();
																	}
																}
															}
													}
												}
											}
									// Remove the string encryption method
									Iterator<MethodNode> it =
										classNode.methods.iterator();
									while(it.hasNext())
									{
										MethodNode node = it.next();
										for(MethodNode decrypt : decryptorMethods)
											if(node.equals(decrypt))
												it.remove();
									}
									AbstractInsnNode last = start.getNext().getNext().getNext().getNext().getNext();
									while(firstZKMInstr.getNext() != last)
										clinit.instructions.remove(firstZKMInstr.getNext());
									clinit.instructions.remove(firstZKMInstr);
									AbstractInsnNode gotoNode = last.getNext();
									while(gotoNode != null)
									{
										if(gotoNode instanceof LabelNode)
										{
											gotoNode = null;
											break;
										}
										if(gotoNode.getOpcode() == Opcodes.GOTO)
											break;
										gotoNode = gotoNode.getNext();
									}
									if(gotoNode == null)
									{
										AbstractInsnNode returnNode = last;
										while(returnNode != null)
										{
											if(returnNode.getOpcode() == Opcodes.RETURN)
												break;
											returnNode = returnNode.getNext();
										}
										while(returnNode.getNext() != null)
											clinit.instructions.remove(returnNode.getNext());
									}else
									{
										LabelNode jumpSite = ((JumpInsnNode)gotoNode).label;
										while(jumpSite.getPrevious() != null && jumpSite.getPrevious() != gotoNode)
											clinit.instructions.remove(jumpSite.getPrevious());
										//Remove jumpsite if unused
										boolean used = false;
										for(TryCatchBlockNode trycatch : clinit.tryCatchBlocks)
											if(trycatch.start == jumpSite)
												used = true;
										for(AbstractInsnNode ain : clinit.instructions.toArray())
											if(ain instanceof JumpInsnNode && ain != gotoNode 
											&& ((JumpInsnNode)ain).label == jumpSite)
												used = true;
										if(!used)
											clinit.instructions.remove(jumpSite);
										clinit.instructions.remove(gotoNode);
									}
									clinit.instructions.remove(last);
									classNode.fields.remove(encrypted);
									classNode.fields.remove(decrypted);
									if(startSpecialE != null && clinit.instructions.contains(startSpecialE))
									{
										while(startSpecialE.getNext() != endSpecialE)
											clinit.instructions.remove(startSpecialE.getNext());
										clinit.instructions.remove(startSpecialE);
										clinit.instructions.remove(endSpecialE);
									}
									modified = true;
								}else if(start.getNext().getOpcode() == Opcodes.ALOAD
									&& start.getNext().getNext().getOpcode() == Opcodes.ASTORE)
								{
									int storeNum = ((VarInsnNode)start.getNext().getNext()).var;
									//Analyzer the block to sort out dups and pops
									AbstractInsnNode next = start.getNext().getNext();
									while(next != null && next.getOpcode() != Opcodes.GOTO)
										next = next.getNext();
									JumpInsnNode inserted;
									InsnNode returnNode;
									VarInsnNode removedAload = (VarInsnNode)start.getNext();
									TypeInsnNode newarray;
									LdcInsnNode size;
									clinit.instructions.insertBefore(next, returnNode = new InsnNode(Opcodes.RETURN));
									clinit.instructions.remove(removedAload);
									clinit.instructions.insert(inserted = new JumpInsnNode(Opcodes.GOTO, start));
									clinit.instructions.insert(start, newarray = new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
									clinit.instructions.insert(start, size = new LdcInsnNode(zkm8ArraySize));
									AnalyzerResult result = MethodAnalyzer.analyze(classNode, clinit);
									//Scan first for dups
									for(Entry<AbstractInsnNode, List<Frame>> entry : result.getFrames().entrySet())
										if(entry.getKey().getOpcode() == Opcodes.DUP)
										{
											DupFrame dup = (DupFrame)entry.getValue().get(0);
											if(dup.getTargets().size() == 1 && dup.getTargets().get(0) instanceof LocalFrame)
											{
												LocalFrame loc = (LocalFrame)dup.getTargets().get(0);
												if(loc.getLocal() == storeNum && loc.getOpcode() == Opcodes.ASTORE)
													clinit.instructions.set(entry.getKey(), new VarInsnNode(Opcodes.ALOAD, storeNum));
											}
										}
									//Convert all aloads to getstatics
									Map<AbstractInsnNode, AbstractInsnNode> conversions = new HashMap<>();
									for(AbstractInsnNode ain : clinit.instructions.toArray())
										if(ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)ain).var == storeNum)
										{
											FieldInsnNode field;
											clinit.instructions.set(ain, field = new FieldInsnNode(Opcodes.GETSTATIC, "debug", "debug", "Ljava/lang/Object;"));
											conversions.put(ain, field);
										}
									//And then pops
									AnalyzerResult result1 = MethodAnalyzer.analyze(classNode, clinit);
									for(Entry<AbstractInsnNode, List<Frame>> entry : result1.getFrames().entrySet())
										if(entry.getKey().getOpcode() == Opcodes.POP)
										{
											PopFrame pop = (PopFrame)entry.getValue().get(0);
											if(pop.getRemoved().size() == 1 && pop.getRemoved().get(0) instanceof FieldFrame)
											{
												FieldFrame field = (FieldFrame)pop.getRemoved().get(0);
												clinit.instructions.remove(result1.getMapping().get(field));
												clinit.instructions.remove(result1.getMapping().get(pop));
											}
										}
									//Now convert them back
									for(Entry<AbstractInsnNode, AbstractInsnNode> entry : conversions.entrySet())
										if(clinit.instructions.contains(entry.getValue()))
											clinit.instructions.set(entry.getValue(), entry.getKey());
									clinit.instructions.remove(newarray);
									clinit.instructions.remove(size);
									clinit.instructions.insert(start, removedAload);
									clinit.instructions.remove(inserted);
									clinit.instructions.remove(returnNode);
									//Time to fix all of them
									Object[] resultStrings = (Object[])res;
									for(AbstractInsnNode ain : clinit.instructions.toArray())
										if(ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)ain).var == storeNum
											&& Utils.isInteger(ain.getNext()) && ain.getNext().getNext().getOpcode() == Opcodes.AALOAD)
										{
											int value = Utils.getIntValue(ain.getNext());
											clinit.instructions.remove(ain.getNext().getNext());
											clinit.instructions.remove(ain.getNext());
											clinit.instructions.set(ain, new LdcInsnNode(resultStrings[value]));
											encStrings.incrementAndGet();
										}
									AbstractInsnNode last = start.getNext().getNext();
									while(firstZKMInstr.getNext() != last)
										clinit.instructions.remove(firstZKMInstr.getNext());
									clinit.instructions.remove(firstZKMInstr);
									AbstractInsnNode gotoNode = last.getNext();
									while(gotoNode != null)
									{
										if(gotoNode instanceof LabelNode)
										{
											gotoNode = null;
											break;
										}
										if(gotoNode.getOpcode() == Opcodes.GOTO)
											break;
										gotoNode = gotoNode.getNext();
									}
									if(gotoNode == null)
									{
										AbstractInsnNode returnNode1 = last;
										while(returnNode1 != null)
										{
											if(returnNode1.getOpcode() == Opcodes.RETURN)
												break;
											returnNode1 = returnNode1.getNext();
										}
										while(returnNode1.getNext() != null)
											clinit.instructions.remove(returnNode1.getNext());
									}else
									{
										LabelNode jumpSite = ((JumpInsnNode)gotoNode).label;
										while(jumpSite.getPrevious() != null && jumpSite.getPrevious() != gotoNode)
											clinit.instructions.remove(jumpSite.getPrevious());
										//Remove jumpsite if unused
										boolean used = false;
										for(TryCatchBlockNode trycatch : clinit.tryCatchBlocks)
											if(trycatch.start == jumpSite)
												used = true;
										for(AbstractInsnNode ain : clinit.instructions.toArray())
											if(ain instanceof JumpInsnNode && ain != gotoNode 
											&& ((JumpInsnNode)ain).label == jumpSite)
												used = true;
										if(!used)
											clinit.instructions.remove(jumpSite);
										clinit.instructions.remove(gotoNode);
									}
									clinit.instructions.remove(last);
									if(clinit1 != null && clinit2 != null)
									{
										final MethodInsnNode clinit1F = clinit1;
										MethodNode clinit1Method = classNode.methods.stream().filter(m -> 
										m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null);
										final MethodInsnNode clinit2F = clinit2;
										MethodNode clinit2Method = classNode.methods.stream().filter(m -> 
										m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null);
										classNode.methods.remove(clinit1Method);
										classNode.methods.remove(clinit2Method);
									}
									modified = true;
								}else if(start.getNext().getOpcode() == Opcodes.ALOAD 
									&& start.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC)
								{
									FieldInsnNode putStatic = (FieldInsnNode)start.getNext().getNext();
									FieldNode decryptedStrings = classNode.fields.stream().filter(f -> 
										f.name.equals(putStatic.name) && f.desc.equals(putStatic.desc)).findFirst().orElse(null);
									//Analyzer the block to sort out dups and pops
									AbstractInsnNode next = start.getNext().getNext();
									while(next != null && next.getOpcode() != Opcodes.GOTO)
										next = next.getNext();
									LabelNode addedLabel;
									InsnNode addedReturn;
									JumpInsnNode addedJump;
									clinit.instructions.insert(start.getNext().getNext(), addedLabel = new LabelNode(new Label()));
									clinit.instructions.insert(addedJump = new JumpInsnNode(Opcodes.GOTO, addedLabel));
									clinit.instructions.insertBefore(next, addedReturn = new InsnNode(Opcodes.RETURN));
									AnalyzerResult result = MethodAnalyzer.analyze(classNode, clinit);
									//Scan first for dups
									for(Entry<AbstractInsnNode, List<Frame>> entry : result.getFrames().entrySet())
										if(entry.getKey().getOpcode() == Opcodes.DUP)
										{
											DupFrame dup = (DupFrame)entry.getValue().get(0);
											if(dup.getTargets().size() == 1 && dup.getTargets().get(0) instanceof FieldFrame)
											{
												FieldFrame field = (FieldFrame)dup.getTargets().get(0);
												if(putStatic.name.equals(field.getName()) && putStatic.desc.equals(field.getDesc())
													&& putStatic.owner.equals(field.getOwner()))
													clinit.instructions.set(entry.getKey(), 
														new FieldInsnNode(Opcodes.GETSTATIC, field.getOwner(), field.getName(), 
															field.getDesc()));
											}
										}
									//And then pops
									AnalyzerResult result1 = MethodAnalyzer.analyze(classNode, clinit);
									for(Entry<AbstractInsnNode, List<Frame>> entry : result1.getFrames().entrySet())
										if(entry.getKey().getOpcode() == Opcodes.POP)
										{
											PopFrame pop = (PopFrame)entry.getValue().get(0);
											if(pop.getRemoved().size() == 1 && pop.getRemoved().get(0) instanceof FieldFrame)
											{
												FieldFrame field = (FieldFrame)pop.getRemoved().get(0);
												clinit.instructions.remove(result1.getMapping().get(field));
												clinit.instructions.remove(result1.getMapping().get(pop));
											}
										}
									clinit.instructions.remove(addedReturn);
									clinit.instructions.remove(addedJump);
									clinit.instructions.remove(addedLabel);
									//Time to fix all of them
									Object[] resultStrings = (Object[])res;
									for(MethodNode method : classNode.methods)
									{
										int varStored = -1;
										for(AbstractInsnNode ain : method.instructions.toArray())
										{
											if(ain.getOpcode() == Opcodes.GETSTATIC
												&& ((FieldInsnNode)ain).desc.equals(decryptedStrings.desc)
												&& ((FieldInsnNode)ain).name.equals(decryptedStrings.name)
												&& ((FieldInsnNode)ain).owner.equals(classNode.name))
											{
												if(ain.getNext() != null 
													&& Utils.isInteger(ain.getNext())
													&& ain.getNext().getNext() != null
													&& ain.getNext().getNext().getOpcode() == Opcodes.AALOAD) 
												{
													int index = Utils.getIntValue(ain.getNext());
													method.instructions.remove(ain.getNext().getNext());
													method.instructions.remove(ain.getNext());
													method.instructions.set(ain, new LdcInsnNode(resultStrings[index]));
													encStrings.incrementAndGet();
												}else if(ain.getNext() != null
													&& ain.getNext().getOpcode() == Opcodes.ASTORE)
												{									
													varStored = ((VarInsnNode)ain.getNext()).var;
													method.instructions.remove(ain.getNext());
													method.instructions.remove(ain);
												}else
													throw new RuntimeException("Unknown action for encrypted strings array");
											}else if(varStored != -1 && ain.getOpcode() == Opcodes.ALOAD
												&& ((VarInsnNode)ain).var == varStored)
											{
												if(!Utils.isInteger(ain.getNext()))
													throw new RuntimeException("Unknown action for encrypted strings array");
												int index = Utils.getIntValue(ain.getNext());
												method.instructions.remove(ain.getNext().getNext());
												method.instructions.remove(ain.getNext());
												method.instructions.set(ain, new LdcInsnNode(resultStrings[index]));				
												encStrings.incrementAndGet();
											}
										}
									}
									AbstractInsnNode last = start.getNext().getNext();
									while(firstZKMInstr.getNext() != last)
										clinit.instructions.remove(firstZKMInstr.getNext());
									clinit.instructions.remove(firstZKMInstr);
									AbstractInsnNode gotoNode = last.getNext();
									while(gotoNode != null)
									{
										if(gotoNode instanceof LabelNode)
										{
											gotoNode = null;
											break;
										}
										if(gotoNode.getOpcode() == Opcodes.GOTO)
											break;
										gotoNode = gotoNode.getNext();
									}
									if(gotoNode == null)
									{
										AbstractInsnNode returnNode = last;
										while(returnNode != null)
										{
											if(returnNode.getOpcode() == Opcodes.RETURN)
												break;
											returnNode = returnNode.getNext();
										}
										while(returnNode.getNext() != null)
											clinit.instructions.remove(returnNode.getNext());
									}else
									{
										LabelNode jumpSite = ((JumpInsnNode)gotoNode).label;
										while(jumpSite.getPrevious() != null && jumpSite.getPrevious() != gotoNode)
											clinit.instructions.remove(jumpSite.getPrevious());
										//Remove jumpsite if unused
										boolean used = false;
										for(TryCatchBlockNode trycatch : clinit.tryCatchBlocks)
											if(trycatch.start == jumpSite)
												used = true;
										for(AbstractInsnNode ain : clinit.instructions.toArray())
											if(ain instanceof JumpInsnNode && ain != gotoNode 
											&& ((JumpInsnNode)ain).label == jumpSite)
												used = true;
										if(!used)
											clinit.instructions.remove(jumpSite);
										clinit.instructions.remove(gotoNode);
									}
									clinit.instructions.remove(last);
									classNode.fields.remove(decryptedStrings);
									if(clinit1 != null && clinit2 != null)
									{
										final MethodInsnNode clinit1F = clinit1;
										MethodNode clinit1Method = classNode.methods.stream().filter(m -> 
										m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null);
										final MethodInsnNode clinit2F = clinit2;
										MethodNode clinit2Method = classNode.methods.stream().filter(m -> 
										m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null);
										classNode.methods.remove(clinit1Method);
										classNode.methods.remove(clinit2Method);
									}
									modified = true;
								}
							}else
							{
								if(((firstZKMInstr.getOpcode() == Opcodes.LDC
									&& Utils.isInteger(firstZKMInstr.getNext()) && mode == 2)
									|| (Utils.isInteger(firstZKMInstr) && firstZKMInstr.getNext().getOpcode() == Opcodes.LDC
									&& Utils.isInteger(firstZKMInstr.getNext().getNext())
									&& mode == 1)) && beforeJump.getNext().getOpcode() == Opcodes.GOTO)
								{
									//ZKM 8 - Single String
									if(start.getNext() != null
										&& start.getNext().getOpcode() == Opcodes.ASTORE)
									{
										int storeNum = ((VarInsnNode)start.getNext()).var;
										//Analyzer the block to sort out dups and pops
										AbstractInsnNode next = start.getNext().getNext();
										while(next != null && next.getOpcode() != Opcodes.GOTO)
											next = next.getNext();
										JumpInsnNode inserted;
										InsnNode returnNode;
										InsnNode nullNode;
										clinit.instructions.insertBefore(next, returnNode = new InsnNode(Opcodes.RETURN));
										clinit.instructions.insert(inserted = new JumpInsnNode(Opcodes.GOTO, start));
										clinit.instructions.insert(start, nullNode = new InsnNode(Opcodes.ACONST_NULL));
										AnalyzerResult result = MethodAnalyzer.analyze(classNode, clinit);
										//Scan first for dups
										for(Entry<AbstractInsnNode, List<Frame>> entry : result.getFrames().entrySet())
											if(entry.getKey().getOpcode() == Opcodes.DUP)
											{
												DupFrame dup = (DupFrame)entry.getValue().get(0);
												if(dup.getTargets().size() == 1 && dup.getTargets().get(0) instanceof LocalFrame)
												{
													LocalFrame loc = (LocalFrame)dup.getTargets().get(0);
													if(loc.getLocal() == storeNum && loc.getOpcode() == Opcodes.ASTORE)
														clinit.instructions.set(entry.getKey(), new VarInsnNode(Opcodes.ALOAD, storeNum));
												}
											}
										//Convert all aloads to getstatics
										Map<AbstractInsnNode, AbstractInsnNode> conversions = new HashMap<>();
										for(AbstractInsnNode ain : clinit.instructions.toArray())
											if(ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)ain).var == storeNum)
											{
												FieldInsnNode field;
												clinit.instructions.set(ain, field = new FieldInsnNode(Opcodes.GETSTATIC, "0", "0", "0"));
												conversions.put(ain, field);
											}
										//And then pops
										AnalyzerResult result1 = MethodAnalyzer.analyze(classNode, clinit);
										for(Entry<AbstractInsnNode, List<Frame>> entry : result1.getFrames().entrySet())
											if(entry.getKey().getOpcode() == Opcodes.POP)
											{
												PopFrame pop = (PopFrame)entry.getValue().get(0);
												if(pop.getRemoved().size() == 1 && pop.getRemoved().get(0) instanceof FieldFrame)
												{
													FieldFrame field = (FieldFrame)pop.getRemoved().get(0);
													clinit.instructions.remove(result1.getMapping().get(field));
													clinit.instructions.remove(result1.getMapping().get(pop));
												}
											}
										//Now convert them back
										for(Entry<AbstractInsnNode, AbstractInsnNode> entry : conversions.entrySet())
											if(clinit.instructions.contains(entry.getValue()))
												clinit.instructions.set(entry.getValue(), entry.getKey());
										clinit.instructions.remove(nullNode);
										clinit.instructions.remove(inserted);
										clinit.instructions.remove(returnNode);
										//Time to fix them
										for(AbstractInsnNode ain : clinit.instructions.toArray())
											if(ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)ain).var == storeNum)
											{
												clinit.instructions.set(ain, new LdcInsnNode(res));
												encStrings.incrementAndGet();
											}
										AbstractInsnNode last = start.getNext();
										while(firstZKMInstr.getNext() != last)
											clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.remove(firstZKMInstr);
										AbstractInsnNode gotoNode = last.getNext();
										while(gotoNode != null)
										{
											if(gotoNode instanceof LabelNode)
											{
												gotoNode = null;
												break;
											}
											if(gotoNode.getOpcode() == Opcodes.GOTO)
												break;
											gotoNode = gotoNode.getNext();
										}
										if(gotoNode == null)
										{
											AbstractInsnNode returnNode1 = last;
											while(returnNode1 != null)
											{
												if(returnNode1.getOpcode() == Opcodes.RETURN)
													break;
												returnNode1 = returnNode1.getNext();
											}
											while(returnNode1.getNext() != null)
												clinit.instructions.remove(returnNode1.getNext());
										}else
										{
											LabelNode jumpSite = ((JumpInsnNode)gotoNode).label;
											while(jumpSite.getPrevious() != null && jumpSite.getPrevious() != gotoNode)
												clinit.instructions.remove(jumpSite.getPrevious());
											//Remove jumpsite if unused
											boolean used = false;
											for(TryCatchBlockNode trycatch : clinit.tryCatchBlocks)
												if(trycatch.start == jumpSite)
													used = true;
											for(AbstractInsnNode ain : clinit.instructions.toArray())
												if(ain instanceof JumpInsnNode && ain != gotoNode 
												&& ((JumpInsnNode)ain).label == jumpSite)
													used = true;
											if(!used)
												clinit.instructions.remove(jumpSite);
											clinit.instructions.remove(gotoNode);
										}
										clinit.instructions.remove(last);
										if(clinit1 != null && clinit2 != null)
										{
											final MethodInsnNode clinit1F = clinit1;
											MethodNode clinit1Method = classNode.methods.stream().filter(m -> 
											m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null);
											final MethodInsnNode clinit2F = clinit2;
											MethodNode clinit2Method = classNode.methods.stream().filter(m -> 
											m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null);
											classNode.methods.remove(clinit1Method);
											classNode.methods.remove(clinit2Method);
										}
										modified = true;
									}else if(start.getNext() != null
										&& start.getNext().getOpcode() == Opcodes.PUTSTATIC)
									{
										FieldInsnNode putStatic = (FieldInsnNode)start.getNext();
										//Analyzer the block to sort out dups and pops
										AbstractInsnNode next = start.getNext().getNext();
										while(next != null && next.getOpcode() != Opcodes.GOTO)
										{
											if(next.getOpcode() == Opcodes.RETURN)
												break;
											next = next.getNext();
										}
										LabelNode addedLabel;
										InsnNode addedReturn = null;
										JumpInsnNode addedJump;
										clinit.instructions.insert(start.getNext(), addedLabel = new LabelNode(new Label()));
										clinit.instructions.insert(addedJump = new JumpInsnNode(Opcodes.GOTO, addedLabel));
										if(next.getOpcode() != Opcodes.RETURN)
											clinit.instructions.insertBefore(next, addedReturn = new InsnNode(Opcodes.RETURN));
										AnalyzerResult result = MethodAnalyzer.analyze(classNode, clinit);
										//Scan first for dups
										for(Entry<AbstractInsnNode, List<Frame>> entry : result.getFrames().entrySet())
											if(entry.getKey().getOpcode() == Opcodes.DUP)
											{
												DupFrame dup = (DupFrame)entry.getValue().get(0);
												if(dup.getTargets().size() == 1 && dup.getTargets().get(0) instanceof FieldFrame)
												{
													FieldFrame field = (FieldFrame)dup.getTargets().get(0);
													if(putStatic.name.equals(field.getName()) && putStatic.desc.equals(field.getDesc())
														&& putStatic.owner.equals(field.getOwner()))
														clinit.instructions.set(entry.getKey(), 
															new FieldInsnNode(Opcodes.GETSTATIC, field.getOwner(), field.getName(), 
																field.getDesc()));
												}
											}
										//And then pops
										AnalyzerResult result1 = MethodAnalyzer.analyze(classNode, clinit);
										for(Entry<AbstractInsnNode, List<Frame>> entry : result1.getFrames().entrySet())
											if(entry.getKey().getOpcode() == Opcodes.POP)
											{
												PopFrame pop = (PopFrame)entry.getValue().get(0);
												if(pop.getRemoved().size() == 1 && pop.getRemoved().get(0) instanceof FieldFrame)
												{
													FieldFrame field = (FieldFrame)pop.getRemoved().get(0);
													clinit.instructions.remove(result1.getMapping().get(field));
													clinit.instructions.remove(result1.getMapping().get(pop));
												}
											}
										if(next.getOpcode() != Opcodes.RETURN)
											clinit.instructions.remove(addedReturn);
										clinit.instructions.remove(addedJump);
										clinit.instructions.remove(addedLabel);
										//Fix string
										clinit.instructions.insertBefore(start.getNext(), new LdcInsnNode(res));
										encStrings.incrementAndGet();
										AbstractInsnNode last = start;
										while(firstZKMInstr.getNext() != last)
											clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.remove(firstZKMInstr);
										AbstractInsnNode gotoNode = last.getNext();
										while(gotoNode != null)
										{
											if(gotoNode instanceof LabelNode)
											{
												gotoNode = null;
												break;
											}
											if(gotoNode.getOpcode() == Opcodes.GOTO)
												break;
											gotoNode = gotoNode.getNext();
										}
										if(gotoNode == null)
										{
											AbstractInsnNode returnNode = last;
											while(returnNode != null)
											{
												if(returnNode.getOpcode() == Opcodes.RETURN)
													break;
												returnNode = returnNode.getNext();
											}
											while(returnNode.getNext() != null)
												clinit.instructions.remove(returnNode.getNext());
										}else
										{
											LabelNode jumpSite = ((JumpInsnNode)gotoNode).label;
											while(jumpSite.getPrevious() != null && jumpSite.getPrevious() != gotoNode)
												clinit.instructions.remove(jumpSite.getPrevious());
											//Remove jumpsite if unused
											boolean used = false;
											for(TryCatchBlockNode trycatch : clinit.tryCatchBlocks)
												if(trycatch.start == jumpSite)
													used = true;
											for(AbstractInsnNode ain : clinit.instructions.toArray())
												if(ain instanceof JumpInsnNode && ain != gotoNode 
												&& ((JumpInsnNode)ain).label == jumpSite)
													used = true;
											if(!used)
												clinit.instructions.remove(jumpSite);
											clinit.instructions.remove(gotoNode);
										}
										clinit.instructions.remove(last);
										if(clinit1 != null && clinit2 != null)
										{
											final MethodInsnNode clinit1F = clinit1;
											MethodNode clinit1Method = classNode.methods.stream().filter(m -> 
											m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null);
											final MethodInsnNode clinit2F = clinit2;
											MethodNode clinit2Method = classNode.methods.stream().filter(m -> 
											m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null);
											classNode.methods.remove(clinit1Method);
											classNode.methods.remove(clinit2Method);
										}
										modified = true;
									}else
									{
										//ZKM 5 special case
										clinit.instructions.insertBefore(start.getNext(), new LdcInsnNode(res));
										encStrings.incrementAndGet();
										while(firstZKMInstr.getNext().getOpcode() != Opcodes.GOTO)
											clinit.instructions.remove(firstZKMInstr.getNext());
										LabelNode jump = ((JumpInsnNode)firstZKMInstr.getNext()).label;
										while(jump.getNext() != beforeJump)
											clinit.instructions.remove(jump.getNext());
										clinit.instructions.remove(beforeJump.getNext());
										clinit.instructions.remove(beforeJump);
										clinit.instructions.remove(jump);
										clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.remove(firstZKMInstr);
										if(clinit1 != null && clinit2 != null)
										{
											final MethodInsnNode clinit1F = clinit1;
											MethodNode clinit1Method = classNode.methods.stream().filter(m -> 
											m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null);
											final MethodInsnNode clinit2F = clinit2;
											MethodNode clinit2Method = classNode.methods.stream().filter(m -> 
											m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null);
											classNode.methods.remove(clinit1Method);
											classNode.methods.remove(clinit2Method);
										}
										modified = true;
									}
								}else if((firstZKMInstr.getOpcode() == Opcodes.LDC
									&& firstZKMInstr.getNext().getOpcode() == Opcodes.ACONST_NULL && mode == 2)
									|| (Utils.isInteger(firstZKMInstr) && firstZKMInstr.getNext().getOpcode() == Opcodes.LDC
									&& firstZKMInstr.getNext().getNext().getOpcode() == Opcodes.ACONST_NULL
									&& mode == 1))
								{
									//ZKM 5 version 1 (JSR)
									JumpInsnNode gotoNode = (JumpInsnNode)beforeJump.getNext();
									if(firstZKMDecrypt.getPrevious() != null 
										&& firstZKMDecrypt.getPrevious().getOpcode() == Opcodes.GOTO
										&& ((JumpInsnNode)firstZKMDecrypt.getPrevious()).label == firstZKMInstr.getPrevious())
										clinit.instructions.remove(firstZKMDecrypt.getPrevious());
									while(firstZKMDecrypt.getNext() != gotoNode)
										clinit.instructions.remove(firstZKMDecrypt.getNext());
									clinit.instructions.remove(gotoNode);
									clinit.instructions.remove(firstZKMDecrypt);
									if(mode == 2)
									{
										if(gotoNode.label == firstZKMInstr.getNext().getNext().getNext())
										{
											clinit.instructions.remove(firstZKMInstr.getNext().getNext().getNext());
											clinit.instructions.remove(firstZKMInstr.getNext().getNext());
										}else
											clinit.instructions.set(firstZKMInstr.getNext().getNext(), new JumpInsnNode(
												Opcodes.GOTO, gotoNode.label));
										clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.set(firstZKMInstr, new LdcInsnNode(res));
									}else
									{
										if(gotoNode.label == firstZKMInstr.getNext().getNext().getNext().getNext())
										{
											clinit.instructions.remove(firstZKMInstr.getNext().getNext().getNext().getNext());
											clinit.instructions.remove(firstZKMInstr.getNext().getNext().getNext());
										}else
											clinit.instructions.set(firstZKMInstr.getNext().getNext().getNext(), new JumpInsnNode(
												Opcodes.GOTO, gotoNode.label));
										clinit.instructions.remove(firstZKMInstr.getNext().getNext());
										clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.set(firstZKMInstr, new LdcInsnNode(res));
									}
									encStrings.incrementAndGet();
									modified = true;
									//Can't remove clinit1 and clinit2 yet
									if(clinit1 != null && clinit2 != null && toRemove.get(classNode) == null)
									{
										toRemove.put(classNode, new ArrayList<>());
										final MethodInsnNode clinit1F = clinit1;
										toRemove.get(classNode).add(classNode.methods.stream().filter(m -> 
										m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null));
										final MethodInsnNode clinit2F = clinit2;
										toRemove.get(classNode).add(classNode.methods.stream().filter(m -> 
										m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null));
									}
									modified = true;
								}else if((firstZKMInstr.getOpcode() == Opcodes.LDC
									&& Utils.isInteger(firstZKMInstr.getNext()) && mode == 2)
									|| (Utils.isInteger(firstZKMInstr) && firstZKMInstr.getNext().getOpcode() == Opcodes.LDC
									&& Utils.isInteger(firstZKMInstr.getNext().getNext())
									&& mode == 1))
								{
									//ZKM version 2
									if(firstZKMDecrypt.getPrevious() != null 
										&& firstZKMDecrypt.getPrevious().getOpcode() == Opcodes.GOTO
										&& ((JumpInsnNode)firstZKMDecrypt.getPrevious()).label == firstZKMInstr.getPrevious())
										clinit.instructions.remove(firstZKMDecrypt.getPrevious());
									LabelNode jumpTo;
									if(beforeJump.getNext().getOpcode() == Opcodes.GOTO)
									{
										JumpInsnNode gotoNode = (JumpInsnNode)beforeJump.getNext();
										jumpTo = gotoNode.label;
										while(firstZKMDecrypt.getNext() != gotoNode)
											clinit.instructions.remove(firstZKMDecrypt.getNext());
										clinit.instructions.remove(gotoNode);
										clinit.instructions.remove(firstZKMDecrypt);
									}else
									{
										TableSwitchInsnNode switchNode = (TableSwitchInsnNode)beforeJump.getNext();
										if(singleStrDecryptNumb < 0)
										{
											jumpTo = switchNode.dflt;
											switchNode.dflt = null;
										}else
										{
											jumpTo = switchNode.labels.get(singleStrDecryptNumb);
											switchNode.labels.set(singleStrDecryptNumb, null);
										}
										boolean allNull = true;
										for(LabelNode n : switchNode.labels)
											if(n != null)
												allNull = false;
										if(allNull && switchNode.dflt == null)
										{
											while(firstZKMDecrypt.getNext() != switchNode)
												clinit.instructions.remove(firstZKMDecrypt.getNext());
											clinit.instructions.remove(switchNode);
											clinit.instructions.remove(firstZKMDecrypt);
										}
									}
									if(mode == 2)
									{
										if(jumpTo == firstZKMInstr.getNext().getNext().getNext())
										{
											clinit.instructions.remove(firstZKMInstr.getNext().getNext().getNext());
											clinit.instructions.remove(firstZKMInstr.getNext().getNext());
										}else
											clinit.instructions.set(firstZKMInstr.getNext().getNext(), new JumpInsnNode(
												Opcodes.GOTO, jumpTo));
										clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.set(firstZKMInstr, new LdcInsnNode(res));
									}else
									{
										if(jumpTo == firstZKMInstr.getNext().getNext().getNext().getNext())
										{
											clinit.instructions.remove(firstZKMInstr.getNext().getNext().getNext().getNext());
											clinit.instructions.remove(firstZKMInstr.getNext().getNext().getNext());
										}else
											clinit.instructions.set(firstZKMInstr.getNext().getNext().getNext(), new JumpInsnNode(
												Opcodes.GOTO, jumpTo));
										clinit.instructions.remove(firstZKMInstr.getNext().getNext());
										clinit.instructions.remove(firstZKMInstr.getNext());
										clinit.instructions.set(firstZKMInstr, new LdcInsnNode(res));
									}
									//Can't remove clinit1 and clinit2 yet
									if(clinit1 != null && clinit2 != null && toRemove.get(classNode) == null)
									{
										toRemove.put(classNode, new ArrayList<>());
										final MethodInsnNode clinit1F = clinit1;
										toRemove.get(classNode).add(classNode.methods.stream().filter(m -> 
										m.name.equals(clinit1F.name) && m.desc.equals(clinit1F.desc)).findFirst().orElse(null));
										final MethodInsnNode clinit2F = clinit2;
										toRemove.get(classNode).add(classNode.methods.stream().filter(m -> 
										m.name.equals(clinit2F.name) && m.desc.equals(clinit2F.desc)).findFirst().orElse(null));
									}
									modified = true;
									encStrings.incrementAndGet();
								}
							}			
						}
					}
					if(!runOnce && modified)
						encClasses.incrementAndGet();
					runOnce = true;
					if(modified)
						ran.add(mode);
				}while(modified);
				for(Entry<ClassNode, List<MethodNode>> entry : toRemove.entrySet())
					for(MethodNode m : entry.getValue())
						entry.getKey().methods.remove(m);
				//Inline
				boolean inline = false;
				int total = Integer.MIN_VALUE;
				Object[] decrypted = null;
				int done = 0;
				FieldInsnNode field = null;
				AbstractInsnNode start = null;
				AbstractInsnNode end = null;
				boolean specialStatic = false;
				List<AbstractInsnNode> exempt = new ArrayList<>();
				for(int i = 0; i < clinit.instructions.size(); i++)
				{
					AbstractInsnNode ain = clinit.instructions.get(i);
					if(total == Integer.MIN_VALUE && Utils.isInteger(ain) && ain.getNext() != null 
						&& ain.getNext().getOpcode() == Opcodes.ANEWARRAY
						&& ((TypeInsnNode)ain.getNext()).desc.equals("java/lang/String"))
					{
						start = ain;
						total = Utils.getIntValue(ain);
						decrypted = new String[total];
						i++;
					}else if(total != -1 && ain.getOpcode() == Opcodes.DUP
						&& ain.getNext() != null && Utils.isInteger(ain.getNext()) && ain.getNext().getNext() != null
						&& ain.getNext().getNext().getOpcode() == Opcodes.LDC
						&& ain.getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.AASTORE)
					{
						decrypted[done] = ((LdcInsnNode)ain.getNext().getNext()).cst;
						done++;
						i += 3;
					}else if(done == total && ain.getOpcode() == Opcodes.PUTSTATIC
						&& ((FieldInsnNode)ain).owner.equals(classNode.name))
					{
						end = ain;
						field = (FieldInsnNode)ain;
						inline = true;
						break;
					}else if(!specialStatic && total != -1 && ain.getOpcode() == Opcodes.DUP
						&& ain.getNext() != null && Utils.isInteger(ain.getNext())
						&& ain.getNext().getNext() != null
						&& ain.getNext().getNext().getOpcode() == Opcodes.LDC
						&& ain.getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC)
					{
						exempt.add(ain.getNext().getNext());
						exempt.add(ain.getNext().getNext().getNext());
						i += 2;
						AbstractInsnNode next = ain.getNext().getNext().getNext().getNext();
						while(next.getOpcode() == Opcodes.LDC && next.getNext() != null
							&& next.getNext().getOpcode() == Opcodes.PUTSTATIC)
						{
							exempt.add(next);
							exempt.add(next.getNext());
							i += 2;
							next = next.getNext().getNext();
						}
						if(next.getOpcode() == Opcodes.LDC && next.getNext() != null
							&& next.getNext().getOpcode() == Opcodes.AASTORE)
						{
							decrypted[done] = ((LdcInsnNode)next).cst;
							done++;
							i += 3;
							specialStatic = true;
						}else
							break;
					}else
						break;
				}
				if(inline)
				{
					boolean[] refs = new boolean[total];
					for(MethodNode method : classNode.methods)
						for(AbstractInsnNode ain : method.instructions.toArray())
							if(ain.getOpcode() == Opcodes.GETSTATIC
								&& ((FieldInsnNode)ain).desc.equals(field.desc)
								&& ((FieldInsnNode)ain).name.equals(field.name)
								&& ((FieldInsnNode)ain).owner.equals(field.owner)
								&& ain.getNext() != null && Utils.isInteger(ain.getNext())
								&& ain.getNext().getNext() != null
								&& ain.getNext().getNext().getOpcode() == Opcodes.AALOAD)
								refs[Utils.getIntValue(ain.getNext())] = true;
					boolean allUsed = true;
					for(boolean b : refs)
						if(!b)
							allUsed = false;
					if(allUsed)
					{
						for(MethodNode method : classNode.methods)
							for(AbstractInsnNode ain : method.instructions.toArray())
								if(ain.getOpcode() == Opcodes.GETSTATIC
									&& ((FieldInsnNode)ain).desc.equals(field.desc)
									&& ((FieldInsnNode)ain).name.equals(field.name)
									&& ((FieldInsnNode)ain).owner.equals(field.owner)
									&& ain.getNext() != null && Utils.isInteger(ain.getNext())
									&& ain.getNext().getNext() != null
									&& ain.getNext().getNext().getOpcode() == Opcodes.AALOAD)
								{
									int index = Utils.getIntValue(ain.getNext());
									method.instructions.remove(ain.getNext().getNext());
									method.instructions.remove(ain.getNext());
									method.instructions.set(ain, new LdcInsnNode(decrypted[index]));
								}
						final FieldInsnNode fieldF = field;
						FieldNode decryptedField = classNode.fields.stream().filter(f -> f.name.equals(fieldF.name)
							&& f.desc.equals(fieldF.desc)).findFirst().orElse(null);
						classNode.fields.remove(decryptedField);
						AbstractInsnNode start2 = start;
						while(start.getNext() != end)
							if(!exempt.contains(start.getNext()))
								clinit.instructions.remove(start.getNext());
							else
								start = start.getNext();
						clinit.instructions.remove(start2);
						clinit.instructions.remove(end);
					}
				}else if(ran.size() > 0 && (ran.get(ran.size() - 1) == 1 || ran.get(ran.size() - 1) == 2))
				{
					//Inline private static final Strings
					int counter = 0;
					int lastIndex = -1;
					for(int i = 0; i < clinit.instructions.size(); i++)
					{
						AbstractInsnNode ain = clinit.instructions.get(i);
						if(ain.getOpcode() == Opcodes.LDC
							&& ain.getNext().getOpcode() == Opcodes.PUTSTATIC)
						{
							lastIndex = i;
							i++;
							counter++;
						}
					}
					if(counter == 1 && ((FieldInsnNode)clinit.instructions.get(lastIndex + 1)).owner.equals(classNode.name))
					{
						FieldInsnNode fieldInsn = (FieldInsnNode)clinit.instructions.get(lastIndex + 1);
						FieldNode decryptedString = classNode.fields.stream().filter(f -> f.name.equals(fieldInsn.name)
							&& f.desc.equals(fieldInsn.desc)).findFirst().orElse(null);
						if(Modifier.isPrivate(decryptedString.access) && Modifier.isStatic(decryptedString.access)
							&& Modifier.isFinal(decryptedString.access))
						{
							Object cst = ((LdcInsnNode)clinit.instructions.get(lastIndex)).cst;
							int calls = 0;
							for(MethodNode methodNode : classNode.methods)
								for(AbstractInsnNode ain : methodNode.instructions.toArray())
									if(ain.getOpcode() == Opcodes.GETSTATIC
										&& ((FieldInsnNode)ain).owner.equals(fieldInsn.owner)
										&& ((FieldInsnNode)ain).name.equals(fieldInsn.name)
										&& ((FieldInsnNode)ain).desc.equals(fieldInsn.desc))
										calls++;
							if(calls > 0)
							{
								for(MethodNode methodNode : classNode.methods)
									for(AbstractInsnNode ain : methodNode.instructions.toArray())
										if(ain.getOpcode() == Opcodes.GETSTATIC
											&& ((FieldInsnNode)ain).owner.equals(fieldInsn.owner)
											&& ((FieldInsnNode)ain).name.equals(fieldInsn.name)
											&& ((FieldInsnNode)ain).desc.equals(fieldInsn.desc))
										methodNode.instructions.set(ain, new LdcInsnNode(cst));
								clinit.instructions.remove(clinit.instructions.get(lastIndex + 1));
								clinit.instructions.remove(clinit.instructions.get(lastIndex));
								classNode.fields.remove(decryptedString);
							}
						}
					}
				}
			}
		});
		System.out.println(
			"[Zelix] [StringEncryptionTransformer] Decrypted strings from "
				+ encClasses.get() + " encrypted classes");
		System.out
			.println("[Zelix] [StringEncryptionTransformer] Decrypted "
				+ encStrings.get() + " strings");
		System.out
			.println("[Zelix] [StringEncryptionTransformer] Done");
		return encStrings.get() > 0;
	}
	
	private boolean isClinitMethod1(ClassNode classNode, MethodInsnNode insnNode)
	{
		if(!insnNode.owner.equals(classNode.name) || !insnNode.desc.equals("(Ljava/lang/String;)[C"))
			return false;
		MethodNode method = classNode.methods.stream().filter(m -> m.desc.equals(insnNode.desc) 
			&& m.name.equals(insnNode.name)).findFirst().orElse(null);
		if(method == null)
			return false;
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& ((MethodInsnNode)ain).name.equals("toCharArray")
				&& ((MethodInsnNode)ain).owner.equals("java/lang/String"))
			return true;
		return false;
	}
	
	private boolean isClinitMethod2(ClassNode classNode, MethodInsnNode insnNode)
	{	
		if(!insnNode.owner.equals(classNode.name) || (!insnNode.desc.equals("([C)Ljava/lang/String;")
			&& !insnNode.desc.equals("(I[C)Ljava/lang/String;")))
			return false;
		MethodNode method = classNode.methods.stream().filter(m -> m.desc.equals(insnNode.desc) 
			&& m.name.equals(insnNode.name)).findFirst().orElse(null);
		if(method == null)
			return false;
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
				&& ((MethodInsnNode)ain).name.equals("intern")
				&& ((MethodInsnNode)ain).owner.equals("java/lang/String"))
				return true;
		return false;
	}
	
	private boolean isZKMEnhancedMethod(MethodNode node, FieldNode decrypted)
	{
		for(AbstractInsnNode ain : node.instructions.toArray())
			if(ain.getOpcode() == Opcodes.ILOAD && ain.getNext() != null
				&& Utils.isInteger(ain.getNext()) && ain.getNext().getNext() != null
				&& ain.getNext().getNext().getOpcode() == Opcodes.IXOR
				&& ain.getNext().getNext().getNext() != null
				&& Utils.getIntValue(ain.getNext().getNext().getNext()) == 65535
				&& ain.getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IAND
				&& ain.getNext().getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GETSTATIC
				&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext()).desc.equals(decrypted.desc)
				&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext()).name.equals(decrypted.name))
					return true;
		return false;
	}
	
	private boolean isZKM9EnhancedMethod(MethodNode node, FieldNode decrypted)
	{
		for(AbstractInsnNode ain : node.instructions.toArray())
			if(ain.getOpcode() == Opcodes.ILOAD && ain.getNext() != null
				&& ain.getNext().getOpcode() == Opcodes.ILOAD && ain.getNext().getNext() != null
				&& ain.getNext().getNext().getOpcode() == Opcodes.IXOR && ain.getNext().getNext().getNext() != null
				&& Utils.isInteger(ain.getNext().getNext().getNext()) && ain.getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IXOR
				&& ain.getNext().getNext().getNext().getNext().getNext() != null
				&& Utils.getIntValue(ain.getNext().getNext().getNext().getNext().getNext()) == 65535
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IAND
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ISTORE
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null
				&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GETSTATIC
				&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext()).desc.equals(decrypted.desc)
				&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext()).name.equals(decrypted.name))
					return true;
		return false;
	}
}
