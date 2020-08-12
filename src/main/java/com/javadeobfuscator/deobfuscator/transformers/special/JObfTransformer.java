package com.javadeobfuscator.deobfuscator.transformers.special;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassReader;
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
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaFieldHandle;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaHandle;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaArray;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class JObfTransformer extends Transformer<TransformerConfig>
{
	public static boolean FAST_INDY = true;
	public static boolean CLASS_ENCRYPTION = false;
	
	@Override
	public boolean transform() throws Throwable
	{
		DelegatingProvider provider = new DelegatingProvider();
		provider.register(new MappedFieldProvider());
		provider.register(new PrimitiveFieldProvider());
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

		System.out.println("[Special] [JObfTransformer] Starting");
		AtomicInteger num = new AtomicInteger();
		AtomicInteger unpoolNum = new AtomicInteger();
		AtomicInteger unpoolString = new AtomicInteger();
		AtomicInteger inlinedIfs = new AtomicInteger();
		AtomicInteger indy = new AtomicInteger();
		//Fold numbers
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				boolean modified;
				do
				{
					modified = false;
					for(AbstractInsnNode ain : method.instructions.toArray())
						if(Utils.isInteger(ain) && ain.getNext() != null && Utils.isInteger(ain.getNext()) && ain.getNext().getNext() != null
							&& isArth(ain.getNext().getNext()))
						{
							int res = doArth(Utils.getIntValue(ain), Utils.getIntValue(ain.getNext()), ain.getNext().getNext());
							method.instructions.remove(ain.getNext().getNext());
							method.instructions.remove(ain.getNext());
							method.instructions.set(ain, Utils.getIntInsn(res));
							num.incrementAndGet();
							modified = true;
						}else if(Utils.isInteger(ain) && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INEG)
						{
							method.instructions.remove(ain.getNext());
							method.instructions.set(ain, Utils.getIntInsn(-Utils.getIntValue(ain)));
							num.incrementAndGet();
							modified = true;
						}else if(ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode)ain).cst instanceof String
							&& "".equals(((String)((LdcInsnNode)ain).cst).replace(" ", ""))
							&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
							&& ((MethodInsnNode)ain.getNext()).name.equals("length")
							&& ((MethodInsnNode)ain.getNext()).owner.equals("java/lang/String"))
						{
							if(ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.POP)
							{
								method.instructions.remove(ain.getNext().getNext());
								method.instructions.remove(ain.getNext());
								method.instructions.remove(ain);
							}else if(ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.POP2)
							{
								method.instructions.set(ain.getNext().getNext(), new InsnNode(Opcodes.POP));
								method.instructions.remove(ain.getNext());
								method.instructions.remove(ain);
							}else
							{
								method.instructions.remove(ain.getNext());
								method.instructions.set(ain, Utils.getIntInsn(((String)((LdcInsnNode)ain).cst).length()));
								num.incrementAndGet();
							}
							modified = true;
						}
				}while(modified);
			}
		//Reduntant ifs
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					if((Utils.isInteger(ain) || ain.getOpcode() == Opcodes.ACONST_NULL)
						&& ain.getNext() != null && isSingleIf(ain.getNext()))
					{
						AbstractInsnNode next = ain.getNext();
						if(runSingleIf(ain, next))
						{
							while(ain.getNext() != null && !(ain.getNext() instanceof LabelNode))
								method.instructions.remove(ain.getNext());
							method.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode)next).label));
						}else
						{
							method.instructions.remove(next);
							method.instructions.remove(ain);
						}
					}else if(Utils.isInteger(ain) && ain.getNext() != null
						&& Utils.isInteger(ain.getNext()) && ain.getNext().getNext() != null
						&& isDoubleIf(ain.getNext().getNext()))
					{
						AbstractInsnNode next = ain.getNext().getNext();
						if(runDoubleIf(Utils.getIntValue(ain), Utils.getIntValue(ain.getNext()), next))
						{
							while(ain.getNext() != null && !(ain.getNext() instanceof LabelNode))
								method.instructions.remove(ain.getNext());
							method.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode)next).label));
						}else
						{
							method.instructions.remove(next);
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
						}
					}
				}
		//Reduntant ifs
		for(ClassNode classNode : classNodes())
		{
			Set<MethodNode> toRemove = new HashSet<>();
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(classNode.name))
					{
						MethodNode refer = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)ain).name)
							&& m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
						if(refer != null && !Modifier.isNative(refer.access) && !Modifier.isAbstract(refer.access)
							&& Modifier.isPrivate(refer.access) && Modifier.isStatic(refer.access))
						{
							int mode = -1;
							AbstractInsnNode first = refer.instructions.getFirst();
							if(first.getOpcode() >= Opcodes.LLOAD && first.getOpcode() <= Opcodes.DLOAD
								&& first.getNext() != null && first.getNext().getOpcode() >= Opcodes.LLOAD
								&& first.getNext().getOpcode() <= Opcodes.DLOAD
								&& first.getNext().getNext() != null
								&& first.getNext().getNext().getOpcode() >= Opcodes.LCMP
								&& first.getNext().getNext().getOpcode() <= Opcodes.DCMPG
								&& first.getNext().getNext().getNext() != null
								&& first.getNext().getNext().getNext().getOpcode() == Opcodes.IRETURN)
								mode = 0;
							else if((first.getOpcode() == Opcodes.ILOAD || first.getOpcode() == Opcodes.ALOAD)
								&& ((first.getNext().getOpcode() >= Opcodes.IFEQ
								&& first.getNext().getOpcode() <= Opcodes.IFLE)
									|| first.getNext().getOpcode() == Opcodes.IFNULL || first.getNext().getOpcode() == Opcodes.IFNONNULL)
								&& first.getNext().getNext() != null
								&& Utils.getIntValue(first.getNext().getNext()) == 1
								&& first.getNext().getNext().getNext() != null
								&& first.getNext().getNext().getNext().getOpcode() == Opcodes.GOTO
								&& Utils.isInteger(((JumpInsnNode)first.getNext().getNext().getNext()).label.getPrevious())
								&& Utils.getIntValue(((JumpInsnNode)first.getNext().getNext().getNext()).label.getPrevious()) == 0
								&& refer.instructions.getLast().getOpcode() == Opcodes.IRETURN
								&& refer.instructions.getLast().getPrevious()
									== ((JumpInsnNode)first.getNext().getNext().getNext()).label)
								mode = 1;
							else if((first.getOpcode() == Opcodes.ILOAD || first.getOpcode() == Opcodes.ALOAD)
								&& first.getNext() != null && (first.getNext().getOpcode() == Opcodes.ILOAD
								|| first.getNext().getOpcode() == Opcodes.ALOAD)
								&& first.getNext().getNext() != null
								&& ((first.getNext().getNext().getOpcode() >= Opcodes.IF_ICMPEQ
								&& first.getNext().getNext().getOpcode() <= Opcodes.IF_ICMPLE)
									|| first.getNext().getNext().getOpcode() == Opcodes.IF_ACMPEQ
									|| first.getNext().getNext().getOpcode() == Opcodes.IF_ACMPNE)
								&& first.getNext().getNext().getNext() != null
								&& Utils.getIntValue(first.getNext().getNext().getNext()) == 1
								&& first.getNext().getNext().getNext().getNext() != null
								&& first.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO
								&& Utils.isInteger(((JumpInsnNode)first.getNext().getNext().getNext().getNext()).label.getPrevious())
								&& Utils.getIntValue(((JumpInsnNode)first.getNext().getNext().getNext().getNext()).label.getPrevious()) == 0
								&& refer.instructions.getLast().getOpcode() == Opcodes.IRETURN
								&& refer.instructions.getLast().getPrevious()
									== ((JumpInsnNode)first.getNext().getNext().getNext().getNext()).label)
								mode = 2;
							if(mode == 0)
							{
								toRemove.add(refer);
								method.instructions.set(ain, first.getNext().getNext().clone(null));
								inlinedIfs.incrementAndGet();
							}else if(mode == 1)
							{
								toRemove.add(refer);
								LabelNode jump = ((JumpInsnNode)ain.getNext()).label;
								method.instructions.remove(ain.getNext());
								method.instructions.set(ain,
									new JumpInsnNode(first.getNext().getOpcode(), jump));
								inlinedIfs.incrementAndGet();
							}else if(mode == 2)
							{
								toRemove.add(refer);
								LabelNode jump = ((JumpInsnNode)ain.getNext()).label;
								method.instructions.remove(ain.getNext());
								method.instructions.set(ain,
									new JumpInsnNode(first.getNext().getNext().getOpcode(), jump));
								inlinedIfs.incrementAndGet();
							}
						}
					}
			toRemove.forEach(m -> classNode.methods.remove(m));
		}
		//Unpool numbers
		for(ClassNode classNode : classNodes())
		{
			MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
			if(clinit != null)
			{
				AbstractInsnNode first = clinit.instructions.getFirst();
				if(first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)first).desc.equals("()V")
					&& ((MethodInsnNode)first).owner.equals(classNode.name))
				{
					MethodNode refMethod = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)first).name)
						&& m.desc.equals("()V")).findFirst().orElse(null);
					if(refMethod.instructions.getFirst().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
						&& ((FieldInsnNode)refMethod.instructions.getFirst().getNext().getNext()).desc.equals("[I"))
					{
						FieldInsnNode insnNode = (FieldInsnNode)refMethod.instructions.getFirst().getNext().getNext();
						FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(insnNode.name) 
							&& f.desc.equals(insnNode.desc)).findFirst().orElse(null);
						Context context = new Context(provider);
						MethodExecutor.execute(classNode, refMethod, Arrays.asList(), null, context);
						int[] result = (int[])context.provider.getField(classNode.name, insnNode.name, insnNode.desc, 
							null, context);
						classNode.methods.remove(refMethod);
						clinit.instructions.remove(clinit.instructions.getFirst());
						classNode.fields.remove(field);
						for(MethodNode method : classNode.methods)
							for(AbstractInsnNode ain : method.instructions.toArray())
							{
								if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).name.equals(field.name)
									&& ((FieldInsnNode)ain).desc.equals(field.desc)
									&& ((FieldInsnNode)ain).owner.equals(classNode.name))
								{
									if(!Utils.isInteger(ain.getNext()))
										throw new IllegalStateException();
									method.instructions.remove(ain.getNext().getNext());
									int value = Utils.getIntValue(ain.getNext());
									method.instructions.remove(ain.getNext());
									method.instructions.set(ain, Utils.getIntInsn(result[value]));
									unpoolNum.incrementAndGet();
								}
							}
					}
				}
			}
		}
		//Decrypt encrypted strings
		for(ClassNode classNode : classNodes())
		{
			MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
			if(clinit != null)
			{
				AbstractInsnNode first = clinit.instructions.getFirst();
				if(first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)first).desc.equals("()V")
					&& ((MethodInsnNode)first).owner.equals(classNode.name))
				{
					MethodNode refMethod = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)first).name)
						&& m.desc.equals("()V")).findFirst().orElse(null);
					AbstractInsnNode methodFirst = refMethod.instructions.getFirst();
					if(methodFirst.getOpcode() == Opcodes.NEW && ((TypeInsnNode)methodFirst).desc.equals("java/lang/Exception")
						&& methodFirst.getNext() != null && methodFirst.getNext().getOpcode() == Opcodes.DUP
						&& methodFirst.getNext().getNext() != null && methodFirst.getNext().getNext().getOpcode() == Opcodes.INVOKESPECIAL
						&& ((MethodInsnNode)methodFirst.getNext().getNext()).name.equals("<init>")
						&& ((MethodInsnNode)methodFirst.getNext().getNext()).owner.equals("java/lang/Exception")
						&& methodFirst.getNext().getNext().getNext() != null
						&& methodFirst.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						&& ((MethodInsnNode)methodFirst.getNext().getNext().getNext()).name.equals("getStackTrace")
						&& (((MethodInsnNode)methodFirst.getNext().getNext().getNext()).owner.equals("java/lang/Exception")
							|| ((MethodInsnNode)methodFirst.getNext().getNext().getNext()).owner.equals("java/lang/Throwable"))
						&& refMethod.instructions.getLast().getPrevious().getOpcode() == Opcodes.PUTSTATIC)
					{
						FieldInsnNode insnNode = (FieldInsnNode)refMethod.instructions.getLast().getPrevious();
						FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(insnNode.name) 
							&& f.desc.equals(insnNode.desc)).findFirst().orElse(null);
						Context context = new Context(provider);
						context.dictionary = classpath;
						MethodExecutor.execute(classNode, refMethod, Arrays.asList(), null, context);
						Object[] result = (Object[])context.provider.getField(classNode.name, insnNode.name, insnNode.desc, 
							null, context);
						classNode.methods.remove(refMethod);
						clinit.instructions.remove(clinit.instructions.getFirst());
						classNode.fields.remove(field);
						for(MethodNode method : classNode.methods)
							for(AbstractInsnNode ain : method.instructions.toArray())
							{
								if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).name.equals(field.name)
									&& ((FieldInsnNode)ain).desc.equals(field.desc)
									&& ((FieldInsnNode)ain).owner.equals(classNode.name))
								{
									if(!Utils.isInteger(ain.getNext()))
										throw new IllegalStateException();
									method.instructions.remove(ain.getNext().getNext());
									int value = Utils.getIntValue(ain.getNext());
									method.instructions.remove(ain.getNext());
									if(result[value] == null)
										System.out.println("Array contains null string?");
									method.instructions.set(ain, new LdcInsnNode(result[value]));
								}
							}
						classNode.sourceFile = null;
					}
				}
			}
		}
		//Unpool strings
		for(ClassNode classNode : classNodes())
		{
			MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
			if(clinit != null)
			{
				AbstractInsnNode first = clinit.instructions.getFirst();
				if(first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)first).desc.equals("()V")
					&& ((MethodInsnNode)first).owner.equals(classNode.name))
				{
					MethodNode refMethod = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)first).name)
						&& m.desc.equals("()V")).findFirst().orElse(null);
					if(refMethod.instructions.getFirst().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
						&& ((FieldInsnNode)refMethod.instructions.getFirst().getNext().getNext()).desc.equals("[Ljava/lang/String;"))
					{
						FieldInsnNode insnNode = (FieldInsnNode)refMethod.instructions.getFirst().getNext().getNext();
						FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(insnNode.name) 
							&& f.desc.equals(insnNode.desc)).findFirst().orElse(null);
						Context context = new Context(provider);
						context.dictionary = classpath;
						Set<MethodNode> toRemove = new HashSet<>();
						for(AbstractInsnNode ain : refMethod.instructions.toArray())
							if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(classNode.name))
								toRemove.add(classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)ain).name)
									&& m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null));
						for(MethodNode m : toRemove)
							for(AbstractInsnNode ain : m.instructions.toArray())
								if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode)ain).owner.equals("java/lang/String")
									&& ((MethodInsnNode)ain).name.equals("getBytes")
									&& ((MethodInsnNode)ain).desc.equals("(Ljava/nio/charset/Charset;)[B"))
									MethodExecutor.customMethodFunc.put(ain, (list, ctx) -> 
										new JavaArray((list.get(1).as(String.class)).getBytes(StandardCharsets.UTF_8)));
								else if(ain.getOpcode() == Opcodes.INVOKESPECIAL && ((MethodInsnNode)ain).owner.equals("java/lang/String")
									&& ((MethodInsnNode)ain).name.equals("<init>")
									&& ((MethodInsnNode)ain).desc.equals("([BLjava/nio/charset/Charset;)V"))
									MethodExecutor.customMethodFunc.put(ain, (list, ctx) -> {
										list.get(2).initialize(new String(list.get(0).as(byte[].class), StandardCharsets.UTF_8)); 
										return null;
									});
						MethodExecutor.execute(classNode, refMethod, Arrays.asList(), null, context);
						Object[] result = (Object[])context.provider.getField(classNode.name, insnNode.name, insnNode.desc, 
							null, context);
						for(MethodNode m : toRemove)
							classNode.methods.remove(m);
						classNode.methods.remove(refMethod);
						clinit.instructions.remove(clinit.instructions.getFirst());
						classNode.fields.remove(field);
						for(MethodNode method : classNode.methods)
							for(AbstractInsnNode ain : method.instructions.toArray())
							{
								if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).name.equals(field.name)
									&& ((FieldInsnNode)ain).desc.equals(field.desc)
									&& ((FieldInsnNode)ain).owner.equals(classNode.name))
								{
									if(!Utils.isInteger(ain.getNext()))
										throw new IllegalStateException();
									method.instructions.remove(ain.getNext().getNext());
									int value = Utils.getIntValue(ain.getNext());
									method.instructions.remove(ain.getNext());
									if(result[value] == null)
										System.out.println("Array contains null string?");
									method.instructions.set(ain, new LdcInsnNode(result[value]));
									unpoolString.incrementAndGet();
								}
							}
					}
				}
			}
		}
		//Remove InvokeDynamics
		for(ClassNode classNode : classNodes())
		{
			MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
			if(clinit != null)
			{
				AbstractInsnNode first = clinit.instructions.getFirst();
				if(first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)first).desc.equals("()V")
					&& ((MethodInsnNode)first).owner.equals(classNode.name))
				{
					MethodNode refMethod = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)first).name)
						&& m.desc.equals("()V")).findFirst().orElse(null);
					FieldNode[] fields = isIndyMethod(classNode, refMethod);
					if(fields != null)
					{
						MethodNode bootstrap = classNode.methods.stream().filter(m -> isBootstrap(classNode, fields, m)).findFirst().orElse(null);
						Context refCtx = new Context(provider);
						refCtx.dictionary = classpath;
						if(FAST_INDY)
						{
							Map<Integer, String> indys = new HashMap<>();
							Map<Integer, Type> indyClasses = new HashMap<>();
							for(AbstractInsnNode ain : refMethod.instructions.toArray())
								if(ain instanceof LdcInsnNode && ((LdcInsnNode)ain).cst instanceof String)
									indys.put(Utils.getIntValue(ain.getPrevious()), (String)((LdcInsnNode)ain).cst);
								else if(ain instanceof LdcInsnNode && ((LdcInsnNode)ain).cst instanceof Type)
									indyClasses.put(Utils.getIntValue(ain.getPrevious()), (Type)((LdcInsnNode)ain).cst);
								else if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).name.equals("TYPE")
									&& ((FieldInsnNode)ain).desc.equals("Ljava/lang/Class;"))
									indyClasses.put(Utils.getIntValue(ain.getPrevious()), getTypeForClass(((FieldInsnNode)ain).owner));
							for(MethodNode method : classNode.methods)
								for(AbstractInsnNode ain : method.instructions.toArray())
									if(ain.getOpcode() == Opcodes.INVOKEDYNAMIC
										&& ((InvokeDynamicInsnNode)ain).bsmArgs.length == 0
										&& ((InvokeDynamicInsnNode)ain).bsm.getName().equals(bootstrap.name)
										&& ((InvokeDynamicInsnNode)ain).bsm.getDesc().equals(bootstrap.desc)
										&& ((InvokeDynamicInsnNode)ain).bsm.getOwner().equals(classNode.name))
									{
										int value = Integer.parseInt(((InvokeDynamicInsnNode)ain).name);
										String[] decrypted = indys.get(value).split(":");
										if(decrypted[3].length() <= 2)
										{
											method.instructions.set(ain, new MethodInsnNode(decrypted[3].length() == 2 ? Opcodes.INVOKEVIRTUAL
												: Opcodes.INVOKESTATIC, decrypted[0].replace('.', '/'),
												decrypted[1], decrypted[2], false));
										}else
											method.instructions.set(ain, new FieldInsnNode(decrypted[3].length() == 3 ? Opcodes.GETFIELD
												: decrypted[3].length() == 4 ? Opcodes.GETSTATIC : decrypted[3].length() == 5 ?
													Opcodes.PUTFIELD : Opcodes.PUTSTATIC, decrypted[0].replace('.', '/'),
											decrypted[1], indyClasses.get(Integer.parseInt(decrypted[2])).getDescriptor()));
										indy.incrementAndGet();
									}
						}else
						{
							MethodExecutor.execute(classNode, refMethod, Arrays.asList(), null, refCtx);
							for(MethodNode method : classNode.methods)
								for(AbstractInsnNode ain : method.instructions.toArray())
									if(ain.getOpcode() == Opcodes.INVOKEDYNAMIC
										&& ((InvokeDynamicInsnNode)ain).bsmArgs.length == 0
										&& ((InvokeDynamicInsnNode)ain).bsm.getName().equals(bootstrap.name)
										&& ((InvokeDynamicInsnNode)ain).bsm.getDesc().equals(bootstrap.desc)
										&& ((InvokeDynamicInsnNode)ain).bsm.getOwner().equals(classNode.name))
									{
										List<JavaValue> args = new ArrayList<>();
										args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
										args.add(JavaValue.valueOf(((InvokeDynamicInsnNode)ain).name)); //dyn method name
										args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
										try
										{	                            
											Context context = new Context(provider);
											context.dictionary = classpath;
											
											JavaHandle result = MethodExecutor.execute(classNode, bootstrap, args, null, context);
			                                AbstractInsnNode replacement = null;
			                                if(result instanceof JavaMethodHandle)
			                                {
			                                	JavaMethodHandle jmh = (JavaMethodHandle)result;
				                                String clazz = jmh.clazz.replace('.', '/');
				                                switch (jmh.type) {
				                                    case "virtual":
				                                        replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ? 
				                                        	 Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, jmh.name, jmh.desc,
				                                        	 (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
				                                        break;
				                                    case "static":
				                                        replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, jmh.name, jmh.desc, false);
				                                        break;
				                                }
			                                }else
			                                {
			                                	JavaFieldHandle jfh = (JavaFieldHandle)result;
				                                String clazz = jfh.clazz.replace('.', '/');
				                                switch (jfh.type) {
				                                    case "virtual":
				                                        replacement = new FieldInsnNode(jfh.setter ? 
				                                        	 Opcodes.PUTFIELD : Opcodes.GETFIELD, clazz, jfh.name, jfh.desc);
				                                        break;
				                                    case "static":
				                                        replacement = new FieldInsnNode(jfh.setter ? 
				                                        	Opcodes.PUTSTATIC : Opcodes.GETSTATIC, clazz, jfh.name, jfh.desc);
				                                        break;
				                                }
			                                }
											method.instructions.insert(ain, replacement);
											method.instructions.remove(ain);
											indy.incrementAndGet();
										}catch(Exception e)
										{
											e.printStackTrace();
										}
									}
						}
						classNode.fields.remove(fields[0]);
						classNode.fields.remove(fields[1]);
						classNode.methods.remove(bootstrap);
						classNode.methods.remove(refMethod);
						clinit.instructions.remove(first);
					}
				}
			}
		}
		AtomicInteger decrypted = new AtomicInteger();
		//Warning: No checks will be done to verify if classloader is from JObf
		if(CLASS_ENCRYPTION)
		{
			String[] lines = null;
			int index = -1;
			if(getDeobfuscator().getInputPassthrough().containsKey("META-INF/MANIFEST.MF"))
			{
				lines = new String(getDeobfuscator().getInputPassthrough().get("META-INF/MANIFEST.MF")).split("\n");
	    		for(int i = 0; i < lines.length; i++)
	    			if(lines[i].startsWith("Main-Class: "))
	    			{
	    				index = i;
	    				break;
	    			}
			}
    		String className = index == -1 ? null : lines[index].substring("Main-Class: ".length(), lines[index].length() - 1).replace('.', '/');
			ClassNode loader = classNodes().stream().filter(c -> className == null ?
				c.superName.equals("java/lang/ClassLoader") : c.name.equals(className)).findFirst().orElse(null);
			if(loader != null)
			{
				Context context = new Context(provider);
				context.dictionary = classpath;
				MethodNode clinit = loader.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
				MethodExecutor.execute(loader, clinit, Arrays.asList(), null, context);
				String realMainClass = null;
				MethodNode main = loader.methods.stream().filter(m -> m.name.equals("main")
					&& m.desc.equals("([Ljava/lang/String;)V")).findFirst().orElse(null);
				for(AbstractInsnNode ain : main.instructions.toArray())
					if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode)ain).owner.equals("java/lang/ClassLoader")
						&& ((MethodInsnNode)ain).name.equals("loadClass"))
					{
						realMainClass = (String)((LdcInsnNode)ain.getPrevious()).cst;
						break;
					}
				MethodNode decMethod = loader.methods.stream().filter(m -> m.desc.equals("([B[B)[B")).findFirst().orElse(null);
				//Decryption array
				byte[] b = (byte[])context.provider.getField(className, loader.fields.get(0).name, loader.fields.get(0).desc,
					null, context);
				//Decrypt all classes
				List<String> remove = new ArrayList<>();
				for(Entry<String, byte[]> entry : getDeobfuscator().getInputPassthrough().entrySet())
				{
	    			byte[] decBytes = MethodExecutor.execute(loader, decMethod, Arrays.asList(
	    				new JavaArray(entry.getValue()), new JavaArray(b)),
	    				null, context);
	    			try
	    			{
		    			ClassReader reader = new ClassReader(decBytes);
		                ClassNode node = new ClassNode();
		                reader.accept(node, ClassReader.SKIP_FRAMES);
		    			getDeobfuscator().loadInput(node.name + ".class", decBytes);
		    			remove.add(entry.getKey());
		    			decrypted.incrementAndGet();
	    			}catch(Exception e)
	    			{
	    				//Not an encrypted resource
	    				continue;
	    			}
				}
				remove.forEach(n -> getDeobfuscator().getInputPassthrough().remove(n));
				classes.remove(className);
				classpath.remove(className);
				if(index != -1)
				{
					lines[index] = "Main-Class: " + realMainClass;
		    		String res = "";
		    		for(String line : lines)
		    			res += line + "\n";
		    		res = res.substring(0, res.length() - 1);
		    		getDeobfuscator().getInputPassthrough().put("META-INF/MANIFEST.MF", res.getBytes());
				}
			}
		}
		System.out.println("[Special] [JObfTransformer] Removed " + num + " number obfuscation instructions");
		System.out.println("[Special] [JObfTransformer] Inlined " + unpoolNum + " numbers");
		System.out.println("[Special] [JObfTransformer] Unpooled " + unpoolString + " strings");
		System.out.println("[Special] [JObfTransformer] Inlined " + inlinedIfs + " if statements");
		System.out.println("[Special] [JObfTransformer] Removed " + indy + " invokedynamics");
		if(CLASS_ENCRYPTION)
			System.out.println("[Special] [JObfTransformer] Decrypted " + decrypted + " classes");
		System.out.println("[Special] [JObfTransformer] Done");
		return num.get() > 0 || unpoolNum.get() > 0 || unpoolString.get() > 0 || inlinedIfs.get() > 0 || indy.get() > 0;
	}
	
	private Type getTypeForClass(String clazz)
	{
		switch(clazz)
		{
			case "java/lang/Integer":
				return Type.INT_TYPE;
			case "java/lang/Boolean":
				return Type.BOOLEAN_TYPE;
			case "java/lang/Character":
				return Type.CHAR_TYPE;
			case "java/lang/Byte":
				return Type.BYTE_TYPE;
			case "java/lang/Short":
				return Type.SHORT_TYPE;
			case "java/lang/Float":
				return Type.FLOAT_TYPE;
			case "java/lang/Long":
				return Type.LONG_TYPE;
			case "java/lang/Double":
				return Type.DOUBLE_TYPE;
			default:
				return null;
		}
	}
	
	private boolean isBootstrap(ClassNode classNode, FieldNode[] fields, MethodNode method)
	{
		if(!method.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;"))
			return false;
		boolean[] verify = new boolean[2];
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).desc.equals(fields[0].desc)
				&& ((FieldInsnNode)ain).name.equals(fields[0].name)
				&& ((FieldInsnNode)ain).owner.equals(classNode.name))
				verify[0] = true;
			else if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).desc.equals(fields[1].desc)
				&& ((FieldInsnNode)ain).name.equals(fields[1].name)
				&& ((FieldInsnNode)ain).owner.equals(classNode.name))
				verify[1] = true;
		if(verify[0] && verify[1])
			return true;
		return false;
	}
	
	private FieldNode[] isIndyMethod(ClassNode classNode, MethodNode method)
	{
		FieldNode[] arrs = new FieldNode[2];
		if(method.instructions.getFirst() != null && Utils.isInteger(method.instructions.getFirst())
			&& method.instructions.getFirst().getNext() != null && method.instructions.getFirst().getNext().getOpcode() == Opcodes.ANEWARRAY
			&& ((TypeInsnNode)method.instructions.getFirst().getNext()).desc.equals("java/lang/String")
			&& method.instructions.getFirst().getNext().getNext() != null
			&& method.instructions.getFirst().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
			&& classNode.name.equals(((FieldInsnNode)method.instructions.getFirst().getNext().getNext()).owner))
			arrs[0] = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)method.instructions.getFirst().getNext().getNext()).name)
				&& f.desc.equals(((FieldInsnNode)method.instructions.getFirst().getNext().getNext()).desc)).findFirst().orElse(null);
		if(arrs[0] == null)
			return null;
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(ain != null && Utils.isInteger(ain)
				&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.ANEWARRAY
				&& ((TypeInsnNode)ain.getNext()).desc.equals("java/lang/Class")
				&& ain.getNext().getNext() != null
				&& ain.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
				&& classNode.name.equals(((FieldInsnNode)ain.getNext().getNext()).owner))
			{
				arrs[1] = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain.getNext().getNext()).name)
					&& f.desc.equals(((FieldInsnNode)ain.getNext().getNext()).desc)).findFirst().orElse(null);
				return arrs;
			}
		return null;
	}
	
	private boolean isArth(AbstractInsnNode ain)
	{
		if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB || ain.getOpcode() == Opcodes.IMUL
			|| ain.getOpcode() == Opcodes.IDIV || ain.getOpcode() == Opcodes.IXOR || ain.getOpcode() == Opcodes.IAND
			|| ain.getOpcode() == Opcodes.ISHL)
			return true;
		return false;
	}
	
	private int doArth(int num1, int num2, AbstractInsnNode ain)
	{
		switch(ain.getOpcode())
		{
			case IADD:
				return num1 + num2;
			case ISUB:
				return num1 - num2;
			case IMUL:
				return num1 * num2;
			case IDIV:
				return num1 / num2;
			case IXOR:
				return num1 ^ num2;
			case IAND:
				return num1 & num2;
			case ISHL:
				return num1 << num2;
		}
		throw new RuntimeException("Unexpected opcode");
	}
	
	private boolean isSingleIf(AbstractInsnNode ain)
	{
		if(ain.getOpcode() == Opcodes.IFNULL || ain.getOpcode() == Opcodes.IFNONNULL
			|| (ain.getOpcode() >= Opcodes.IFEQ && ain.getOpcode() <= Opcodes.IFLE))
			return true;
		return false;
	}
	
	private boolean runSingleIf(AbstractInsnNode v, AbstractInsnNode ain)
	{
		int value = Utils.getIntValue(v);
		switch(ain.getOpcode())
		{
			case IFNULL:
				return true;
			case IFNONNULL:
				return false;
			case IFEQ:
				return value == 0;
			case IFNE:
				return value != 0;
			case IFLT:
				return value < 0;
			case IFGE:
				return value >= 0;
			case IFGT:
				return value > 0;
			case IFLE:
				return value <= 0;
		}
		throw new RuntimeException("Unexpected opcode");
	}
	
	private boolean isDoubleIf(AbstractInsnNode ain)
	{
		if(ain.getOpcode() >= Opcodes.IF_ICMPEQ && ain.getOpcode() <= Opcodes.IF_ICMPLE)
			return true;
		return false;
	}
	
	private boolean runDoubleIf(int num1, int num2, AbstractInsnNode ain)
	{
		switch(ain.getOpcode())
		{
			case IF_ICMPEQ:
				return num1 == num2;
			case IF_ICMPNE:
				return num1 != num2;
			case IF_ICMPLT:
				return num1 < num2;
			case IF_ICMPGE:
				return num1 >= num2;
			case IF_ICMPGT:
				return num1 > num2;
			case IF_ICMPLE:
				return num1 <= num2;
		}
		throw new RuntimeException("Unexpected opcode");
	}
}
