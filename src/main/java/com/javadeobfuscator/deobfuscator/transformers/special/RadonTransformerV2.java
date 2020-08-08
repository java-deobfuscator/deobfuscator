package com.javadeobfuscator.deobfuscator.transformers.special;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.internal.asm.Opcodes;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.asm.source.ConstantPropagatingSourceFinder;
import com.javadeobfuscator.deobfuscator.asm.source.SourceFinder;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class RadonTransformerV2 extends Transformer<TransformerConfig>
{
	public static boolean EJECTOR = true;
	public static boolean ANTI_DEBUG = true;
	public static boolean FLOW_OBF = true;
	public static boolean STRING_POOL = true;
	public static boolean NUMBER = true;
	public static boolean NUMBER_CONTEXT_OBF = true;
	public static boolean INDY = true;
	public static boolean STRING = true;
	
	@Override
	public boolean transform() throws Throwable
	{
		DelegatingProvider provider = new DelegatingProvider();
        provider.register(new PrimitiveFieldProvider());
        provider.register(new MappedFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
            	if(type.getDescriptor().equals("Ljava/lang/Long;"))
					if(!(target.value() instanceof Long))
						return false;
				if(type.getDescriptor().equals("Ljava/lang/Integer;"))
					if(!(target.value() instanceof Integer))
						return false;
				return true;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                if (type.getDescriptor().equals("[C")) {
                    if (!(target.value() instanceof char[])) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return true;
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
        
		System.out.println("[Special] [RadonTransformerV2] Starting");
		AtomicInteger eject = new AtomicInteger();
		AtomicInteger antiDebug = new AtomicInteger();
		AtomicInteger tryCatch = new AtomicInteger();
		AtomicInteger flowObf = new AtomicInteger();
		AtomicInteger strPool = new AtomicInteger();
		AtomicInteger number = new AtomicInteger();
		AtomicInteger indy = new AtomicInteger();
		AtomicInteger str = new AtomicInteger();
		//Bad Annotations
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				if(method.visibleAnnotations != null)
				{
					Iterator<AnnotationNode> itr = method.visibleAnnotations.iterator();
					while(itr.hasNext())
					{
						AnnotationNode node = itr.next();
						if(node.desc.equals("@") || node.desc.equals(""))
							itr.remove();
					}
				}
				if(method.invisibleAnnotations != null)
				{
					Iterator<AnnotationNode> itr = method.invisibleAnnotations.iterator();
					while(itr.hasNext())
					{
						AnnotationNode node = itr.next();
						if(node.desc.equals("@") || node.desc.equals(""))
							itr.remove();
					}
				}
			}
		if(EJECTOR)
			for(ClassNode classNode : classNodes())
			{
				Set<MethodNode> ejectMethods = new HashSet<>();
				for(MethodNode method : classNode.methods)
					for(AbstractInsnNode ain : method.instructions.toArray())
						if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(classNode.name))
						{
							MethodNode ejectMethod = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)ain).name)
								&& m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
							String start = method.name.replace('<', '_').replace('>', '_') + "$";
							if(ejectMethod != null && ejectMethod.name.startsWith(start))
							{
								ejectMethods.add(ejectMethod);
								for(AbstractInsnNode a1 : ejectMethod.instructions.toArray())
									if(a1.getOpcode() == Opcodes.ILOAD && a1.getNext() != null
										&& Utils.isInteger(a1.getNext()) && a1.getNext().getNext() != null
										&& a1.getNext().getNext().getOpcode() == Opcodes.IXOR
										&& a1.getNext().getNext().getNext() != null
										&& Utils.isInteger(a1.getNext().getNext().getNext())
										&& a1.getNext().getNext().getNext().getNext() != null
										&& a1.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IF_ICMPNE)
									{
										LdcInsnNode prevInt = (LdcInsnNode)ain.getPrevious();
										int res = (int)prevInt.cst ^ Utils.getIntValue(a1.getNext());
										if(res != Utils.getIntValue(a1.getNext().getNext().getNext()))
											continue;
										//Get nodes passed
										LinkedHashMap<LabelNode, List<AbstractInsnNode>> passed = new FlowAnalyzer(method).analyze(
											a1.getNext().getNext().getNext().getNext(), new ArrayList<>(), new HashMap<>(),
											false, false);
										List<AbstractInsnNode> list = new ArrayList<>();
										passed.values().forEach(li -> list.addAll(li));
										list.remove(0);//ICMPNE
										list.remove(list.size() - 1);//return
										list.removeIf(a -> a.getOpcode() == Opcodes.CHECKCAST);//Won't be dealing with that
										AbstractInsnNode last = list.get(list.size() - 1);
										if(last instanceof FieldInsnNode)
										{
											if(last.getOpcode() == Opcodes.PUTSTATIC)
											{
												if(list.size() != 2)
													throw new RuntimeException("Unexpected Ejector pattern (PS)");
												AbstractInsnNode prev = list.get(0);
												method.instructions.remove(ain.getPrevious().getPrevious());
												method.instructions.remove(ain.getPrevious());
												method.instructions.insertBefore(ain, prev.clone(null));
												method.instructions.set(ain, last.clone(null));
												eject.incrementAndGet();
											}else if(last.getOpcode() == Opcodes.PUTFIELD)
											{
												if(list.size() != 3)
													throw new RuntimeException("Unexpected Ejector pattern (PS)");
												AbstractInsnNode prev = list.get(1);
												method.instructions.remove(ain.getPrevious());//number
												method.instructions.insertBefore(ain, prev.clone(null));
												method.instructions.set(ain, last.clone(null));
												eject.incrementAndGet();
											}
										}else if(last instanceof MethodInsnNode)
										{
											if(last.getOpcode() == Opcodes.INVOKESTATIC)
											{
												List<AbstractInsnNode> constants = new ArrayList<>();
												for(int i = 0; i < list.size(); i++)
												{
													AbstractInsnNode a = list.get(i);
													if(a.getOpcode() >= Opcodes.ISTORE
														&& a.getOpcode() <= Opcodes.ASTORE)
														constants.add(list.get(i - 1));
												}
												Collections.reverse(constants);
												method.instructions.remove(ain.getPrevious().getPrevious().getPrevious());
												method.instructions.remove(ain.getPrevious().getPrevious());
												method.instructions.remove(ain.getPrevious());
												for(AbstractInsnNode a : constants)
													method.instructions.insertBefore(ain, a.clone(null));
												method.instructions.set(ain, last.clone(null));
												eject.incrementAndGet();
											}else if(last.getOpcode() == Opcodes.INVOKEVIRTUAL)
											{
												List<AbstractInsnNode> constants = new ArrayList<>();
												for(int i = 0; i < list.size(); i++)
												{
													AbstractInsnNode a = list.get(i);
													if(a.getOpcode() >= Opcodes.ISTORE
														&& a.getOpcode() <= Opcodes.ASTORE)
														constants.add(list.get(i - 1));
												}
												Collections.reverse(constants);
												method.instructions.remove(ain.getPrevious().getPrevious());
												method.instructions.remove(ain.getPrevious());
												for(AbstractInsnNode a : constants)
													method.instructions.insertBefore(ain, a.clone(null));
												method.instructions.set(ain, last.clone(null));
												eject.incrementAndGet();
											}
										}else
											throw new RuntimeException("Unexpected Ejector pattern");
									}
							}
						}
				ejectMethods.forEach(m -> classNode.methods.remove(m));
			}
		if(ANTI_DEBUG)
			for(ClassNode classNode : classNodes())
			{
				MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
				if(clinit == null)
					continue;
				for(AbstractInsnNode ain : clinit.instructions.toArray())
				{
					if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals("java/lang/management/ManagementFactory")
						&& ((MethodInsnNode)ain).name.equals("getRuntimeMXBean") && ain.getNext() != null
						&& ain.getNext().getOpcode() == Opcodes.INVOKEINTERFACE
						&& ((MethodInsnNode)ain.getNext()).owner.equals("java/lang/management/RuntimeMXBean")
						&& ((MethodInsnNode)ain.getNext()).name.equals("getInputArguments")
						&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						&& ((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Object")
						&& ((MethodInsnNode)ain.getNext().getNext()).name.equals("toString")
						&& ain.getNext().getNext().getNext() != null && ain.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						&& ((MethodInsnNode)ain.getNext().getNext().getNext()).owner.equals("java/lang/String")
						&& (((MethodInsnNode)ain.getNext().getNext().getNext()).name.equals("toLowerCase")
							|| ((MethodInsnNode)ain.getNext().getNext().getNext()).name.equals("toUpperCase"))
						&& ain.getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.LDC
						&& ain.getNext().getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						&& ((MethodInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).owner.equals("java/lang/String")
						&& ((MethodInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name.equals("contains")
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IFEQ)
					{
						AbstractInsnNode jumpSite = ((JumpInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext()).label;
						while(ain.getNext() != jumpSite)
							clinit.instructions.remove(ain.getNext());
						clinit.instructions.remove(ain);
						antiDebug.incrementAndGet();
					}
				}
			}
		//Reverse nullcheckmutilator
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode)ain).owner.equals("java/lang/Object")
						&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.POP
						&& ain.getNext().getNext() != null && ain.getNext().getNext() instanceof LabelNode
						&& ain.getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.GOTO
						&& ain.getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext() instanceof LabelNode)
					{
						String desc = ((MethodInsnNode)ain).desc;
						if(Type.getArgumentTypes(desc).length == 0
							&& ain.getPrevious() != null && ain.getPrevious() instanceof LabelNode)
						{
							TryCatchBlockNode nullCatch = null;
							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
								if(tcbn.type.equals("java/lang/NullPointerException")
									&& tcbn.start == ain.getPrevious() && tcbn.end == ain.getNext().getNext()
									&& tcbn.handler == ain.getNext().getNext().getNext().getNext())
								{
									nullCatch = tcbn;
									break;
								}
							if(nullCatch != null)
							{
								method.tryCatchBlocks.remove(nullCatch);
								method.instructions.remove(nullCatch.handler.getNext());
								((JumpInsnNode)ain.getNext().getNext().getNext()).setOpcode(Opcodes.IFNONNULL);
								method.instructions.remove(ain.getNext());
								method.instructions.remove(ain);
							}
						}else if(Type.getArgumentTypes(desc).length == 1
							&& ain.getPrevious() != null && ain.getPrevious().getOpcode() == Opcodes.ACONST_NULL
							&& ain.getPrevious().getPrevious() != null && ain.getPrevious().getPrevious() instanceof LabelNode)
						{
							TryCatchBlockNode nullCatch = null;
							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
								if(tcbn.type.equals("java/lang/NullPointerException")
									&& tcbn.start == ain.getPrevious().getPrevious() && tcbn.end == ain.getNext().getNext()
									&& tcbn.handler == ain.getNext().getNext().getNext().getNext())
								{
									nullCatch = tcbn;
									break;
								}
							if(nullCatch != null)
							{
								method.tryCatchBlocks.remove(nullCatch);
								method.instructions.remove(nullCatch.handler.getNext());
								((JumpInsnNode)ain.getNext().getNext().getNext()).setOpcode(Opcodes.IFNONNULL);
								method.instructions.remove(ain.getNext());
								method.instructions.remove(ain.getPrevious());
								method.instructions.remove(ain);
							}
						}
					}
				}
		//Reverse instructionsetreducer
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					AbstractInsnNode replace = null;
					List<AbstractInsnNode> remove = new ArrayList<>();
					List<LabelNode> labels = new ArrayList<>();
					if(ain.getOpcode() == Opcodes.DUP && ain.getNext() != null && Utils.isInteger(ain.getNext())
						&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.IADD
						&& ain.getNext().getNext().getNext() != null && ain.getNext().getNext().getNext().getOpcode() == Opcodes.IFLT
						&& ain.getNext().getNext().getNext().getNext() != null && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.DUP
						&& ain.getNext().getNext().getNext().getNext().getNext() != null
						&& Utils.isInteger(ain.getNext().getNext().getNext().getNext().getNext())
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IADD
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IFGT
						&& ((JumpInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext()).label == 
						((JumpInsnNode)ain.getNext().getNext().getNext()).label
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null
						&& ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.DUP)
					{
						AbstractInsnNode now = ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getNext();
						remove.add(ain.getNext().getNext().getNext().getNext().getNext().getNext().getNext());
						remove.add(ain.getNext().getNext().getNext().getNext().getNext().getNext());
						remove.add(ain.getNext().getNext().getNext().getNext().getNext());
						remove.add(ain.getNext().getNext().getNext().getNext());
						remove.add(ain.getNext().getNext().getNext());
						remove.add(ain.getNext().getNext());
						remove.add(ain.getNext());
						boolean firstPass = true;
						while(true)
						{
							if(now.getNext() != null && ((!firstPass && Utils.getIntValue(now.getNext()) == -1) || 
								(firstPass && Utils.getIntValue(now.getNext()) == Utils.getIntValue(ain.getNext())))
								&& now.getNext().getNext() != null && now.getNext().getNext().getOpcode() == Opcodes.IADD
								&& now.getNext().getNext().getNext() != null && now.getNext().getNext().getNext().getOpcode() == Opcodes.DUP
								&& now.getNext().getNext().getNext().getNext() != null
								&& now.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IFNE
								&& now.getNext().getNext().getNext().getNext().getNext() != null
								&& now.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
								&& now.getNext().getNext().getNext().getNext().getNext().getNext() != null
								&& now.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
								&& now.getNext().getNext().getNext().getNext().getNext().getNext().getNext() != null
								&& now.getNext().getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
							{
								labels.add(((JumpInsnNode)now.getNext().getNext().getNext().getNext().getNext().getNext().getNext()).label);
								remove.add(now.getNext().getNext().getNext().getNext().getNext().getNext().getNext());
								remove.add(now.getNext().getNext().getNext().getNext().getNext().getNext());
								remove.add(now.getNext().getNext().getNext().getNext().getNext());
								remove.add(now.getNext().getNext().getNext().getNext());
								remove.add(now.getNext().getNext().getNext());
								remove.add(now.getNext().getNext());
								remove.add(now.getNext());
								remove.add(now);
								now = ((JumpInsnNode)now.getNext().getNext().getNext().getNext()).label;
								firstPass = false;
							}else
								break;
						}
						if(now.getNext() != null
							&& now.getNext().getOpcode() == Opcodes.POP && now.getNext().getNext() != null
							&& now.getNext().getNext().getOpcode() == Opcodes.GOTO
							&& ((JumpInsnNode)now.getNext().getNext()).label == 
							((JumpInsnNode)ain.getNext().getNext().getNext()).label)
						{
							remove.add(now.getNext().getNext());
							remove.add(now.getNext());
							remove.add(now);
							now = ((JumpInsnNode)now.getNext().getNext()).label;
							if(now.getNext() != null && now.getNext().getOpcode() == Opcodes.POP
								&& now.getNext().getNext() != null && now.getNext().getNext().getOpcode() == Opcodes.GOTO)
							{
								remove.add(now.getNext().getNext());
								remove.add(now.getNext());
								remove.add(now);
								labels.add(((JumpInsnNode)now.getNext().getNext()).label);
								if(ain.getPrevious() instanceof LabelNode)
								{
									Frame<SourceValue>[] frames;
									try
									{
										frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
									}catch(AnalyzerException e)
									{
										throw new RuntimeException(e);
									}
									Frame<SourceValue> value = frames[method.instructions.indexOf(ain.getPrevious())];
									Set<AbstractInsnNode> insns = value.getStack(value.getStackSize() - 1).insns;
									if(insns.size() == 1
										&& insns.iterator().next().getNext() != null
										&& insns.iterator().next().getNext().getOpcode() == Opcodes.GOTO)
									{
										replace = insns.iterator().next().getNext();
										remove.add(ain);
										remove.add(ain.getPrevious());
									}
								}
								if(replace == null)
									replace = ain;
								LabelNode dflt = labels.remove(labels.size() - 1);
								method.instructions.set(replace, new TableSwitchInsnNode(-Utils.getIntValue(ain.getNext()),
									-Utils.getIntValue(ain.getNext().getNext().getNext().getNext().getNext()),
									dflt, labels.toArray(new LabelNode[0])));
								for(AbstractInsnNode a : remove)
									method.instructions.remove(a);
							}
						}
					}
			}
		if(FLOW_OBF)
		{
			List<String> fakeExceptionClasses = new ArrayList<>();
			for(ClassNode classNode : classNodes())
			{
				try
				{
					if(classNode.methods.stream().filter(m -> m.name.equals("<init>")).findFirst().orElse(null) == null
						&& getDeobfuscator().isSubclass("java/lang/Throwable", classNode.name))
						fakeExceptionClasses.add(classNode.name);
				}catch(NoClassInPathException e)
				{
					//Ignore errors
				}
			}
			for(ClassNode classNode : classNodes())
				for(MethodNode method : classNode.methods)
				{
					Iterator<TryCatchBlockNode> itr = method.tryCatchBlocks.iterator();
					while(itr.hasNext())
					{
						TryCatchBlockNode tcbn = itr.next();
						if(fakeExceptionClasses.contains(tcbn.type))
						{
							itr.remove();
							tryCatch.incrementAndGet();
						}
					}
					//Dead code
					InstructionModifier modifier = new InstructionModifier();

	                Frame<BasicValue>[] frames = new Analyzer<>(new BasicInterpreter()).analyze(classNode.name, method);
	                for(int i = 0; i < method.instructions.size(); i++)
	                {
	                    if(!Utils.isInstruction(method.instructions.get(i)))
	                    	continue;
	                    if(frames[i] != null)
	                    	continue;

	                    modifier.remove(method.instructions.get(i));
	                }
	                modifier.apply(method);
				}
			fakeExceptionClasses.forEach(s -> {
				classes.remove(s);
				classpath.remove(s);
			});
			//Jumps
			for(ClassNode classNode : classNodes())
			{
				List<FieldNode> remove = new ArrayList<>();
				for(MethodNode method : classNode.methods)
				{
					LinkedHashMap<LabelNode, List<AbstractInsnNode>> res = new FlowAnalyzer(method).analyze(method.instructions.getFirst(), Arrays.asList(),
						new HashMap<>(), true, true);
					InstructionModifier modifier = new InstructionModifier();
					boolean fail = false;
					int store = -1;
					FieldNode field = null;
					for(Entry<LabelNode, List<AbstractInsnNode>> entry : res.entrySet())
						for(AbstractInsnNode ain : entry.getValue())
							if(store == -1 && ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).desc.equals("Z")
								&& ((FieldInsnNode)ain).owner.equals(classNode.name)
								&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.ISTORE)
							{
								field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
									&& f.desc.equals("Z")).findFirst().orElse(null);
								if(field != null && Modifier.isFinal(field.access) && Modifier.isPublic(field.access)
									&& field.value == null)
								{
									modifier.remove(ain.getNext());
									modifier.remove(ain);
									store = ((VarInsnNode)ain.getNext()).var;
								}
							}else if(store != -1 && ain.getOpcode() == Opcodes.ILOAD && ((VarInsnNode)ain).var == store)
								if(getNextFollowGoto(ain, 1) != null && getNextFollowGoto(ain, 1).getOpcode() == Opcodes.IFEQ
									&& getNextFollowGoto(ain, 2) != null && getNextFollowGoto(ain, 2).getOpcode() == Opcodes.ACONST_NULL
									&& getNextFollowGoto(ain, 3) != null
									&& getNextFollowGoto(ain, 3).getOpcode() == Opcodes.ATHROW)
								{
									modifier.remove(getNextFollowGoto(ain, 3));
									modifier.remove(getNextFollowGoto(ain, 2));
									modifier.remove(getNextFollowGoto(ain, 1));
									modifier.replace(ain, new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode)getNextFollowGoto(ain, 1)).label));
									flowObf.incrementAndGet();
								}else
								{
									fail = true;
									break;
								}
					if(!fail)
					{
						modifier.apply(method);
						if(!remove.contains(field))
							remove.add(field);
					}
				}
				remove.forEach(f -> classNode.fields.remove(f));
			}
			//2nd jump
			for(ClassNode classNode : classNodes())
			{
				List<FieldNode> remove = new ArrayList<>();
				for(MethodNode method : classNode.methods)
				{
					LinkedHashMap<LabelNode, List<AbstractInsnNode>> res = new FlowAnalyzer(method).analyze(method.instructions.getFirst(), Arrays.asList(),
						new HashMap<>(), true, true);
					InstructionModifier modifier = new InstructionModifier();
					List<AbstractInsnNode> breaks = new ArrayList<>();
					List<AbstractInsnNode> checked = new ArrayList<>();
					int store = -1;
					FieldNode field = null;
					for(Entry<LabelNode, List<AbstractInsnNode>> entry : res.entrySet())
						for(AbstractInsnNode ain : entry.getValue())
						{
							if(store == -1 && ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).desc.equals("I")
								&& ((FieldInsnNode)ain).owner.equals(classNode.name)
								&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.ISTORE)
							{
								field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
									&& f.desc.equals("I")).findFirst().orElse(null);
								if(field != null && Modifier.isFinal(field.access) && Modifier.isPublic(field.access)
									&& field.value == null)
								{
									checked.add(ain.getNext());
									modifier.remove(ain.getNext());
									modifier.remove(ain);
									store = ((VarInsnNode)ain.getNext()).var;
								}
							}else if(store != -1 && ain.getOpcode() == Opcodes.ILOAD && ((VarInsnNode)ain).var == store)
								if(getNextFollowGoto(ain, 1) != null && getNextFollowGoto(ain, 1).getOpcode() == Opcodes.IFEQ)
							{
								breaks.add(getNextFollowGoto(ain, 2));
								checked.add(ain);
								modifier.remove(getNextFollowGoto(ain, 1));
								modifier.replace(ain, new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode)getNextFollowGoto(ain, 1)).label));
								flowObf.incrementAndGet();
							}
						}
					if(store != -1)
					{
						LinkedHashMap<LabelNode, List<AbstractInsnNode>> res2 = new FlowAnalyzer(method).analyze(method.instructions.getFirst(), breaks,
							new HashMap<>(), false, true);
						boolean reached = false;
						boolean pass = true;
						lp:
						for(Entry<LabelNode, List<AbstractInsnNode>> entry : res2.entrySet())
							for(AbstractInsnNode ain : entry.getValue())
							{
								if(reached && (ain.getOpcode() == Opcodes.ISTORE || ain.getOpcode() == Opcodes.ILOAD)
									&& ((VarInsnNode)ain).var == store && !checked.contains(ain))
								{
									pass = false;
									break lp;
								}
								if(ain == checked.get(0))
									reached = true;
							}
						if(pass)
						{
							modifier.apply(method);
							if(!remove.contains(field))
								remove.add(field);
						}
						//Dead code
						InstructionModifier modifier2 = new InstructionModifier();

		                Frame<BasicValue>[] frames = new Analyzer<>(new BasicInterpreter()).analyze(classNode.name, method);
		                for(int i = 0; i < method.instructions.size(); i++)
		                {
		                    if(!Utils.isInstruction(method.instructions.get(i)))
		                    	continue;
		                    if(frames[i] != null)
		                    	continue;

		                    modifier2.remove(method.instructions.get(i));
		                }
		                modifier2.apply(method);
		                for(int i = 0; i < method.instructions.size(); i++) 
		                {
		                    AbstractInsnNode node = method.instructions.get(i);
		                    if(node.getOpcode() == Opcodes.GOTO) 
		                    {
		                        AbstractInsnNode a = Utils.getNext(node);
		                        AbstractInsnNode b = Utils.getNext(((JumpInsnNode)node).label);
		                        if(a == b) 
		                        	method.instructions.remove(node);
		                    }
		                }
					}
				}
				remove.forEach(f -> classNode.fields.remove(f));
			}
			//BlockSplitter
			for(ClassNode classNode : classNodes())
				for(MethodNode method : classNode.methods)
					if(method.localVariables == null || method.localVariables.isEmpty())
					{
						if(skipLabel(method.instructions.getFirst()) != null
							&& skipLabel(method.instructions.getFirst()).getOpcode() == Opcodes.GOTO
							&& skipLabel(method.instructions.getFirst()).getNext() != null
							&& skipLabel(method.instructions.getFirst()).getNext() instanceof LabelNode
							&& ((JumpInsnNode)skipLabel(method.instructions.getFirst())).label !=
								skipLabel(method.instructions.getFirst()).getNext())
						{
							List<AbstractInsnNode> p2Block = new ArrayList<>();
							LabelNode jumpPoint1 = ((JumpInsnNode)skipLabel(method.instructions.getFirst())).label;
							AbstractInsnNode now = skipLabel(method.instructions.getFirst());
							if(method.instructions.getLast().getOpcode() == Opcodes.GOTO
								&& ((JumpInsnNode)method.instructions.getLast()).label == skipLabel(method.instructions.getFirst()).getNext())
								method.instructions.remove(method.instructions.getLast());
	                        while(now.getNext() != jumpPoint1)
	                        {
	                        	p2Block.add(now.getNext());
	                        	method.instructions.remove(now.getNext());
	                        }
	                        method.instructions.remove(now);
							for(AbstractInsnNode ain : p2Block)
								method.instructions.add(ain);
						}
					}
		}
		if(NUMBER)
		{
	        for(ClassNode classNode : classNodes())
	            for(MethodNode method : classNode.methods)
	            {
					Map<AbstractInsnNode, Frame<SourceValue>> frames = new HashMap<>();
					Map<AbstractInsnNode, AbstractInsnNode> replace = new LinkedHashMap<>();
	    			try
	    			{
	    				Frame<SourceValue>[] fr = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
	    				for(int i = 0; i < fr.length; i++)
	    				{
	    					Frame<SourceValue> f = fr[i];
	    					frames.put(method.instructions.get(i), f);
	    				}
	    			}catch(AnalyzerException e)
	    			{
	    				oops("unexpected analyzer exception", e);
	                    continue;
	    			}
	    			for(AbstractInsnNode ain : method.instructions.toArray())
	    			{
	    				int mode = -1;
	    				if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB || ain.getOpcode() == Opcodes.IMUL
	    					|| ain.getOpcode() == Opcodes.IDIV || ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.ISHL
	    					|| ain.getOpcode() == Opcodes.ISHR || ain.getOpcode() == Opcodes.IUSHR || ain.getOpcode() == Opcodes.IAND
	    					|| ain.getOpcode() == Opcodes.IOR || ain.getOpcode() == Opcodes.IXOR)
	    					mode = 0;//Int
	    				else if(ain.getOpcode() == Opcodes.LADD || ain.getOpcode() == Opcodes.LSUB || ain.getOpcode() == Opcodes.LMUL
	    					|| ain.getOpcode() == Opcodes.LDIV || ain.getOpcode() == Opcodes.LREM || ain.getOpcode() == Opcodes.LAND
	    					|| ain.getOpcode() == Opcodes.LOR || ain.getOpcode() == Opcodes.LXOR)
	    					mode = 1;//Long
	    				else if(ain.getOpcode() == Opcodes.LSHL || ain.getOpcode() == Opcodes.LSHR || ain.getOpcode() == Opcodes.LUSHR)
	    					mode = 2;//Long shift
	    				else if(ain.getOpcode() == Opcodes.DADD || ain.getOpcode() == Opcodes.DSUB || ain.getOpcode() == Opcodes.DMUL
	    					|| ain.getOpcode() == Opcodes.DDIV || ain.getOpcode() == Opcodes.DREM)
	    					mode = 3;//Double
	    				else if(ain.getOpcode() == Opcodes.FADD || ain.getOpcode() == Opcodes.FSUB || ain.getOpcode() == Opcodes.FMUL
	    					|| ain.getOpcode() == Opcodes.FDIV || ain.getOpcode() == Opcodes.FREM)
	    					mode = 4;//Float
	    				if(mode == -1)
	    					continue;
	    				Frame<SourceValue> f = frames.get(ain);
    					SourceValue arg1 = f.getStack(f.getStackSize() - 2);
    					SourceValue arg2 = f.getStack(f.getStackSize() - 1);
    					if(arg1.insns.size() != 1 || arg2.insns.size() != 1)
    						continue;
    					AbstractInsnNode a1 = arg1.insns.iterator().next();
    					AbstractInsnNode a2 = arg2.insns.iterator().next();
    					for(Entry<AbstractInsnNode, AbstractInsnNode> entry : replace.entrySet())
    						if(entry.getKey() == a1)
    							a1 = entry.getValue();
    						else if(entry.getKey() == a2)
    							a2 = entry.getValue();
    					boolean verify = false;
    					if(mode == 0 && Utils.isInteger(a1) && Utils.isInteger(a2))
    						verify = true;
    					else if(mode == 1 && Utils.isLong(a1) && Utils.isLong(a2))
    						verify = true;
    					else if(mode == 2 && Utils.isLong(a1) && Utils.isInteger(a2))
    						verify = true;
    					else if(mode == 3 && isDouble(a1) && isDouble(a2))
    						verify = true;
    					else if(mode == 4 && isFloat(a1) && isFloat(a2))
    						verify = true;
    					if(verify)
    					{
	    					AbstractInsnNode newValue;
	                        if((newValue = doMath(a1, a2, ain.getOpcode(), mode)) != null)
	                        {
	                        	replace.put(ain, newValue);
	                            method.instructions.set(ain, newValue);
	                            method.instructions.remove(a1);
	                            method.instructions.remove(a2);
	                            number.getAndAdd(2);
	                        }
    					}
    				}
	            }
		}
		Set<ClassNode> numberDecryptClass = new HashSet<>();
		if(NUMBER_CONTEXT_OBF)
		{
			for(ClassNode classNode : classNodes())
				for(MethodNode method : classNode.methods)
				{
					InstructionModifier modifier = new InstructionModifier();
					for(AbstractInsnNode ain : TransformerHelper.instructionIterator(method))
						if(ain.getOpcode() == Opcodes.INVOKESTATIC
							&& ((MethodInsnNode)ain).desc.equals("(Ljava/lang/Object;I)Ljava/lang/Object;")
							&& ain.getPrevious() != null && Utils.isInteger(ain.getPrevious())
							&& ain.getPrevious().getPrevious() != null
							&& ain.getPrevious().getPrevious().getOpcode() == Opcodes.INVOKESTATIC
							&& ((MethodInsnNode)ain.getPrevious().getPrevious()).name.equals("valueOf")
							&& (((MethodInsnNode)ain.getPrevious().getPrevious()).owner.equals("java/lang/Integer")
								|| ((MethodInsnNode)ain.getPrevious().getPrevious()).owner.equals("java/lang/Long"))
							&& ain.getPrevious().getPrevious().getPrevious() != null
							&& (Utils.isInteger(ain.getPrevious().getPrevious().getPrevious())
								|| Utils.isLong(ain.getPrevious().getPrevious().getPrevious()))
							&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.CHECKCAST
							&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
							&& ((((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Integer")
								&& ((MethodInsnNode)ain.getNext().getNext()).name.equals("intValue"))
								|| (((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Long")
									&& ((MethodInsnNode)ain.getNext().getNext()).name.equals("longValue"))
								|| (((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Double")
									&& ((MethodInsnNode)ain.getNext().getNext()).name.equals("doubleValue"))
								|| (((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Float")
									&& ((MethodInsnNode)ain.getNext().getNext()).name.equals("floatValue"))))
						{
							boolean isLong = Utils.isLong(ain.getPrevious().getPrevious().getPrevious());
							ClassNode decryptorNode = classNodes().stream().filter(c -> c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
							MethodNode decryptorMethod = decryptorNode == null ? null : decryptorNode.methods.stream().filter(m -> 
								m.name.equals(((MethodInsnNode)ain).name) && m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
							MethodNode clinit = decryptorNode == null ? null : decryptorNode.methods.stream().filter(m -> 
								m.name.equals("<clinit>")).findFirst().orElse(null);
							if(numberDecryptClass.contains(decryptorNode) || isCorrectNumberDecrypt(clinit))
							{
								Context context = new Context(provider);
		                        context.dictionary = classpath;
		                        context.push(classNode.name, method.name, getDeobfuscator().getConstantPool(classNode).getSize());
								try
								{
									if(!numberDecryptClass.contains(decryptorNode))
									{
										patchMethodNumber(clinit);
										MethodExecutor.execute(decryptorNode, clinit, Arrays.asList(), null, context);
										numberDecryptClass.add(decryptorNode);
									}
									JavaValue first = isLong ? new JavaObject(Utils.getLongValue(ain.getPrevious().getPrevious().getPrevious()), "java/lang/Long")
										: new JavaObject(Utils.getIntValue(ain.getPrevious().getPrevious().getPrevious()), "java/lang/Integer");
									Object res = MethodExecutor.execute(decryptorNode, decryptorMethod,
                        				Arrays.asList(first, new JavaInteger(Utils.getIntValue(ain.getPrevious()))), null, context);
									switch(((MethodInsnNode)ain.getNext().getNext()).owner)
									{
										case "java/lang/Integer":
											modifier.replace(ain, Utils.getIntInsn((int)res));
											break;
										case "java/lang/Long":
											modifier.replace(ain, Utils.getLongInsn((long)res));
											break;
										case "java/lang/Float":
											modifier.replace(ain, Utils.getFloatInsn((float)res));
											break;
										case "java/lang/Double":
											modifier.replace(ain, Utils.getDoubleInsn((double)res));
											break;
										default:
											throw new RuntimeException("Unexpected type: " + ((MethodInsnNode)ain.getNext().getNext()).owner);
									}
                        			modifier.removeAll(Arrays.asList(ain.getPrevious().getPrevious().getPrevious(),
                        				ain.getPrevious().getPrevious(), ain.getPrevious(), ain.getNext().getNext(), ain.getNext()));
                        			numberDecryptClass.add(decryptorNode);
                        			number.getAndIncrement();
								}catch(Exception e)
								{
									e.printStackTrace();
								}
							}
						}
					modifier.apply(method);
				}
		}
		if(STRING_POOL)
			for(ClassNode classNode : classNodes())
			{
				MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
				if(clinit != null)
				{
					AbstractInsnNode firstInstr = skipGoto(clinit.instructions.getFirst());
					if(firstInstr != null && firstInstr.getOpcode() == Opcodes.INVOKESTATIC 
						&& ((MethodInsnNode)firstInstr).owner.equals(classNode.name))
					{
						MethodNode pool = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)firstInstr).name)
							&& m.desc.equals(((MethodInsnNode)firstInstr).desc)).findFirst().orElse(null);
						if(pool != null && isCorrectStringPool(pool))
						{
							FieldInsnNode putstatic = null;
							for(AbstractInsnNode ain : pool.instructions.toArray())
								if(ain.getOpcode() == Opcodes.PUTSTATIC)
								{
									putstatic = (FieldInsnNode)ain;
									break;
								}
							if(putstatic != null && putstatic.owner.equals(classNode.name))
							{
								FieldInsnNode putstaticF = putstatic;
								FieldNode poolField = classNode.fields.stream().filter(f -> f.name.equals(putstaticF.name)
									&& f.desc.equals(putstaticF.desc)).findFirst().orElse(null);
								if(poolField != null)
								{
									Context context = new Context(provider);
									context.dictionary = classpath;
									MethodExecutor.execute(classNode, pool, Arrays.asList(), null, context);
									Object[] value = (Object[])context.provider.getField(classNode.name, poolField.name, poolField.desc, null, context);
									for(MethodNode method : classNode.methods)
									{
										InstructionModifier modifier = new InstructionModifier();
										for(AbstractInsnNode ain : TransformerHelper.instructionIterator(method))
										{
											if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).owner.equals(classNode.name)
												&& ((FieldInsnNode)ain).name.equals(poolField.name)
												&& ((FieldInsnNode)ain).desc.equals(poolField.desc)
												&& getNextFollowGoto(ain, 1) != null && Utils.isInteger(getNextFollowGoto(ain, 1))
												&& getNextFollowGoto(ain, 2) != null && getNextFollowGoto(ain, 2).getOpcode() == Opcodes.AALOAD)
											{
												modifier.remove(getNextFollowGoto(ain, 2));
												modifier.remove(getNextFollowGoto(ain, 1));
												modifier.replace(ain, new LdcInsnNode(value[Utils.getIntValue(getNextFollowGoto(ain, 1))]));
												strPool.incrementAndGet();
											}
										}
										modifier.apply(method);
									}
									classNode.methods.remove(pool);
									classNode.fields.remove(poolField);
									clinit.instructions.remove(firstInstr);
								}
							}
						}
					}
				}
			}
		Set<ClassNode> indyBootstrap = new HashSet<>();
		if(INDY)
		{
			for(ClassNode classNode : classNodes())
				for(MethodNode method : classNode.methods)
					for(AbstractInsnNode ain : method.instructions.toArray())
						if(ain.getOpcode() == Opcodes.INVOKEDYNAMIC && ((InvokeDynamicInsnNode)ain).bsmArgs.length == 4)
						{
							InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode)ain;
							boolean verify = true;
							if(!(dyn.bsmArgs[0] instanceof Integer))
								verify = false;
							for(int i = 1; i < 4; i++)
							{
								Object o = dyn.bsmArgs[i];
								if(!(o instanceof String))
								{
									verify = false;
									break;
								}
							}
							if(verify)
							{
								Handle bootstrap = dyn.bsm;
								ClassNode bootstrapClassNode = classes.get(bootstrap.getOwner());
	                            MethodNode bootstrapMethodNode = bootstrapClassNode.methods.stream().filter(mn -> mn.name.equals(bootstrap.getName()) 
	                            	&& mn.desc.equals(bootstrap.getDesc())).findFirst().orElse(null);
	                            if(!indyBootstrap.contains(bootstrapClassNode))
	                            	patchMethod(bootstrapMethodNode);
	                            List<JavaValue> args = new ArrayList<>();
	                            args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
	                            args.add(JavaValue.valueOf(dyn.name)); //dyn method name
	                            args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
	                            for(Object o : dyn.bsmArgs)
	                                args.add(JavaValue.valueOf(o));
	                            try
	                            {	                            
	                                Context context = new Context(provider);
	                                context.dictionary = this.classpath;
	
	                                JavaMethodHandle result = MethodExecutor.execute(bootstrapClassNode, bootstrapMethodNode, args, null, context);
	                                String clazz = result.clazz.replace('.', '/');
	                                MethodInsnNode replacement = null;
	                                switch (result.type) {
	                                    case "virtual":
	                                        replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ? 
	                                        	 Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, result.name, result.desc,
	                                        	 (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
	                                        break;
	                                    case "static":
	                                        replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, result.name, result.desc, false);
	                                        break;
	                                }
	                                method.instructions.insert(ain, replacement);
	                                method.instructions.remove(ain);
		                            indyBootstrap.add(bootstrapClassNode);
	                                indy.incrementAndGet();
	                            }catch(Exception e)
	                            {
	                            	e.printStackTrace();
	                            }
							}
						}
		}
		Set<ClassNode> stringDecryptClass = new HashSet<>();
		if(STRING)
		{
			for(ClassNode classNode : classNodes())
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
							ClassNode decryptorNode = classNodes().stream().filter(c -> c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
							MethodNode decryptorMethod = decryptorNode == null ? null : decryptorNode.methods.stream().filter(m -> 
								m.name.equals(((MethodInsnNode)ain).name) && m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
							if(isCorrectStringDecrypt(decryptorNode, decryptorMethod))
							{
								Frame<SourceValue> f1 = frames[method.instructions.indexOf(ain)];
								if(f1 == null)
									continue;
								Type[] argTypes = Type.getArgumentTypes(((MethodInsnNode)ain).desc);
								Frame<SourceValue> currentFrame = frames[method.instructions.indexOf(ain)];
								List<JavaValue> args = new ArrayList<>();
								List<AbstractInsnNode> instructions = new ArrayList<>();
			                        
								for(int i = 0, stackOffset = currentFrame.getStackSize() - argTypes.length; i < argTypes.length; i++) 
								{
									Optional<Object> consensus = SourceFinder.findSource(method, frames, instructions, new ConstantPropagatingSourceFinder(), 
										ain, currentFrame.getStack(stackOffset)).consensus();
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
								try
								{
									if(!stringDecryptClass.contains(decryptorNode))
									{
										MethodExecutor.execute(decryptorNode, decryptorNode.methods.stream().filter(m -> m.name.equals("<clinit>")).
											findFirst().orElse(null), Arrays.asList(), null, context);
										patchMethodString(decryptorNode);
									}
									List<AbstractInsnNode> pops = new ArrayList<>();
									for(AbstractInsnNode a : method.instructions.toArray())
										if(a.getOpcode() == Opcodes.POP && frames[method.instructions.indexOf(a)] != null)
										{
											SourceValue value = frames[method.instructions.indexOf(a)].getStack(
												frames[method.instructions.indexOf(a)].getStackSize() - 1);
											if(instructions.contains(value.insns.iterator().next()))
												pops.add(a);
										}
									modifier.replace(ain, new LdcInsnNode(MethodExecutor.execute(decryptorNode, decryptorMethod, 
                        				args, null, context)));
                        			modifier.removeAll(instructions);
                        			modifier.removeAll(pops);
                        			stringDecryptClass.add(decryptorNode);
                        			str.getAndIncrement();
								}catch(Exception e)
								{
									e.printStackTrace();
								}
							}
						}
					modifier.apply(method);
				}
		}
		numberDecryptClass.forEach(e -> {
			classes.remove(e.name);
			classpath.remove(e.name);
		});
		indyBootstrap.forEach(c -> {
			classes.remove(c.name);
			classpath.remove(c.name);
		});
		stringDecryptClass.forEach(e -> {
			classes.remove(e.name);
			classpath.remove(e.name);
		});
		System.out.println("[Special] [RadonTransformerV2] Unejected " + eject + " methods");
		System.out.println("[Special] [RadonTransformerV2] Removed " + antiDebug + " anti-debug injections");
		System.out.println("[Special] [RadonTransformerV2] Removed " + tryCatch + " try-catch blocks");
		System.out.println("[Special] [RadonTransformerV2] Removed " + flowObf + " fake jumps");
		System.out.println("[Special] [RadonTransformerV2] Folded " + number + " numbers");
		System.out.println("[Special] [RadonTransformerV2] Unpooled " + strPool + " strings");
		System.out.println("[Special] [RadonTransformerV2] Removed " + indy + " invokedynamics");
		System.out.println("[Special] [RadonTransformerV2] Decrypted " + str + " strings");
		return antiDebug.get() > 0 || tryCatch.get() > 0 || flowObf.get() > 0 || strPool.get() > 0 || indy.get() > 0 || str.get() > 0;
	}
	
	private void patchMethodNumber(MethodNode clinit)
	{
		for(AbstractInsnNode ain : clinit.instructions.toArray())
			if(ain instanceof MethodInsnNode
				&& ((MethodInsnNode)ain).owner.equals("java/util/concurrent/ThreadLocalRandom"))
			{
				if(((MethodInsnNode)ain).name.equals("nextInt"))
					clinit.instructions.set(ain, new InsnNode(Opcodes.ICONST_0));
				else
					clinit.instructions.remove(ain);
			}
	}
	
	private boolean isCorrectNumberDecrypt(MethodNode clinit)
	{
		if(clinit == null)
			return false;
		int threadLoc = 0;
		for(AbstractInsnNode ain : clinit.instructions.toArray())
			if(ain instanceof MethodInsnNode
			&& ((MethodInsnNode)ain).owner.equals("java/util/concurrent/ThreadLocalRandom"))
				threadLoc++;
		if(threadLoc == 2)
			return true;
		return false;
	}
	
	private void patchMethodString(ClassNode classNode)
	{
		int var = -1;
		for(MethodNode method : classNode.methods)
			for(AbstractInsnNode ain : method.instructions.toArray())
				if(ain instanceof MethodInsnNode && ((MethodInsnNode)ain).name.equals("availableProcessors")
					&& ((MethodInsnNode)ain).owner.equals("java/lang/Runtime"))
					method.instructions.set(ain, new LdcInsnNode(4));
				else if(ain instanceof MethodInsnNode && ((MethodInsnNode)ain).name.equals("getRuntime")
					&& ((MethodInsnNode)ain).owner.equals("java/lang/Runtime"))
					method.instructions.set(ain, new InsnNode(Opcodes.ACONST_NULL));
				else if(ain.getOpcode() == Opcodes.NEW
					&& ((TypeInsnNode)ain).desc.equals("java/util/concurrent/atomic/AtomicInteger"))
				{
					method.instructions.remove(Utils.getNext(ain));
					method.instructions.set(Utils.getNext(ain), new IincInsnNode(var = ((VarInsnNode)Utils.getNext(ain)).var, 1));
					method.instructions.remove(ain);
				}else if(ain.getOpcode() == Opcodes.INVOKESPECIAL
					&& ((MethodInsnNode)ain).owner.equals("java/util/concurrent/atomic/AtomicInteger")
					&& ((MethodInsnNode)ain).name.equals("<init>"))
					method.instructions.set(ain, new InsnNode(Opcodes.ACONST_NULL));
				else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).owner.equals("java/util/concurrent/atomic/AtomicInteger")
					&& ((MethodInsnNode)ain).name.equals("incrementAndGet"))
				{
					method.instructions.remove(Utils.getNext(ain));
					method.instructions.remove(ain);
				}else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).owner.equals("java/util/concurrent/atomic/AtomicInteger")
					&& ((MethodInsnNode)ain).name.equals("get"))
				{
					method.instructions.remove(Utils.getPrevious(ain));
					method.instructions.set(ain, new VarInsnNode(Opcodes.ILOAD, var));
				}
	}
	
	private boolean isCorrectStringDecrypt(ClassNode classNode, MethodNode method)
	{
		if(method == null)
			return false;
		if(method.desc.equals("(Ljava/lang/Object;I)Ljava/lang/String;"))
		{
			int stackTrace = 0;
			int charArray = 0;
			int hashCode = 0;
			for(AbstractInsnNode ain : method.instructions.toArray())
				if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).name.equals("hashCode")
					&& ((MethodInsnNode)ain).owner.equals("java/lang/String"))
					hashCode++;
				else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).name.equals("toCharArray")
					&& ((MethodInsnNode)ain).owner.equals("java/lang/String"))
					charArray++;
				else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).name.equals("getStackTrace")
					&& ((MethodInsnNode)ain).owner.equals("java/lang/Thread"))
					stackTrace++;
			if(charArray == 1 && stackTrace == 1 && hashCode == 2)
				return true;
			if(charArray == 2 && stackTrace == 1 && hashCode == 9)
				return true;
			if(charArray == 2 && stackTrace == 2 && hashCode == 21)
				return true;
		}
		return false;
	}
	
	private void patchMethod(MethodNode method)
	{
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(ain instanceof MethodInsnNode && (((MethodInsnNode)ain).owner.equals("java/lang/Runtime")
				|| (((MethodInsnNode)ain).owner.equals("java/util/concurrent/ThreadLocalRandom"))))
			{
				if(((MethodInsnNode)ain).name.equals("nextInt"))
					method.instructions.remove(Utils.getNext(ain));
				method.instructions.remove(ain);
			}
	}
	
	private boolean isCorrectStringPool(MethodNode method)
	{
		if(method == null)
			return false;
		if(!method.desc.equals("()V"))
			return false;
		int numberCount = 0;
		int storeCount = 0;
		int dupCount = 0;
		int ldcCount = 0;
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(Utils.isInteger(ain))
				numberCount++;
			else if(ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode)ain).cst instanceof String)
				ldcCount++;
			else if(ain.getOpcode() == Opcodes.DUP)
				dupCount++;
			else if(ain.getOpcode() == Opcodes.AASTORE)
				storeCount++;
		if(numberCount == storeCount + 1 && dupCount == storeCount && ldcCount == storeCount)
			return true;
		return false;
	}
	
	private AbstractInsnNode skipGoto(AbstractInsnNode node)
	{
		while(node instanceof LabelNode || node instanceof LineNumberNode || node instanceof FrameNode)
			node = node.getNext();
		if(node.getOpcode() == Opcodes.GOTO)
		{
			JumpInsnNode cast = (JumpInsnNode)node;
			node = cast.label;
			while(!Utils.isInstruction(node))
				node = node.getNext();
		}
		return node;
	}
	 
	private AbstractInsnNode skipLabel(AbstractInsnNode node)
	{
		while(node instanceof LabelNode || node instanceof LineNumberNode || node instanceof FrameNode)
			node = node.getNext();
		return node;
	}
	
    private AbstractInsnNode getNextFollowGoto(AbstractInsnNode node, int amount)
    {
        for(int i = 0; i < amount; i++)
            node = Utils.getNextFollowGoto(node);
        return node;
    }
    
    private boolean isDouble(AbstractInsnNode node)
	{
    	if(node == null) return false;
    	if(node.getOpcode() == Opcodes.DCONST_0
    		|| node.getOpcode() == Opcodes.DCONST_1)
    		return true;
    	if(node instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)node;
			if(ldc.cst instanceof Double)
				return true;
		}
		return false;
	}
    
	private double getDoubleValue(AbstractInsnNode node)
	{
		if(node.getOpcode() >= Opcodes.DCONST_0
			&& node.getOpcode() <= Opcodes.DCONST_1)
			return node.getOpcode() - 14;
		if(node instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)node;
			if(ldc.cst instanceof Double)
				return (double)ldc.cst;
		}
		return 0;
	}
	
	private AbstractInsnNode getDoubleInsn(double d)
	{
		if(d == 0)
			return new InsnNode(Opcodes.DCONST_0);
		if(d == 1)
			return new InsnNode(Opcodes.DCONST_1);
		return new LdcInsnNode(d);
	}
    
    private boolean isFloat(AbstractInsnNode node)
	{
    	if(node == null) return false;
    	if(node.getOpcode() >= Opcodes.FCONST_0
			&& node.getOpcode() <= Opcodes.FCONST_2)
    		return true;
    	if(node instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)node;
			if(ldc.cst instanceof Float)
				return true;
		}
		return false;
	}
    
	private float getFloatValue(AbstractInsnNode node)
	{
		if(node.getOpcode() >= Opcodes.FCONST_0
			&& node.getOpcode() <= Opcodes.FCONST_2)
			return node.getOpcode() - 11;
		if(node instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)node;
			if(ldc.cst instanceof Float)
				return (float)ldc.cst;
		}
		return 0;
	}
	
	private AbstractInsnNode getFloatInsn(float f)
	{
		if(f == 0)
			return new InsnNode(Opcodes.FCONST_0);
		if(f == 1)
			return new InsnNode(Opcodes.FCONST_1);
		if(f == 2)
			return new InsnNode(Opcodes.FCONST_2);
		return new LdcInsnNode(f);
	}
	
	private AbstractInsnNode getLongInsn(long l)
	{
		if(l == 0)
			return new InsnNode(Opcodes.LCONST_0);
		if(l == 1)
			return new InsnNode(Opcodes.LCONST_1);
		return new LdcInsnNode(l);
	}
    
    private AbstractInsnNode doMath(AbstractInsnNode value1, AbstractInsnNode value2, int opcode, int mode)
    {
        switch(mode)
        {
        	case 0:
        		int i = Utils.getIntValue(value1);
        		int i2 = Utils.getIntValue(value2);
        		int iRes = -1;
        		switch(opcode)
        		{
        			case Opcodes.IADD:
        				iRes = i + i2;
        				break;
        			case Opcodes.ISUB:
        				iRes = i - i2;
        				break;
        			case Opcodes.IMUL:
        				iRes = i * i2;
        				break;
        			case Opcodes.IDIV:
        				iRes = i / i2;
        				break;
        			case Opcodes.IREM:
        				iRes = i % i2;
        				break;
        			case Opcodes.ISHL:
        				iRes = i << i2;
        				break;
        			case Opcodes.ISHR:
        				iRes = i >> i2;
        				break;
        			case Opcodes.IUSHR:
        				iRes = i >>> i2;
        				break;
        			case Opcodes.IAND:
        				iRes = i & i2;
        				break;
        			case Opcodes.IOR:
        				iRes = i | i2;
        				break;
        			case Opcodes.IXOR:
        				iRes = i ^ i2;
        				break;
        			default:
        				throw new RuntimeException();
        		}
        		return Utils.getIntInsn(iRes);
        	case 1:
        		long l = Utils.getLongValue(value1);
        		long l2 = Utils.getLongValue(value2);
        		long lRes = -1;
        		switch(opcode)
        		{
        			case Opcodes.LADD:
        				lRes = l + l2;
        				break;
        			case Opcodes.LSUB:
        				lRes = l - l2;
        				break;
        			case Opcodes.LMUL:
        				lRes = l * l2;
        				break;
        			case Opcodes.LDIV:
        				lRes = l / l2;
        				break;
        			case Opcodes.LREM:
        				lRes = l % l2;
        				break;
        			case Opcodes.LAND:
        				lRes = l & l2;
        				break;
        			case Opcodes.LOR:
        				lRes = l | l2;
        				break;
        			case Opcodes.LXOR:
        				lRes = l ^ l2;
        				break;
        			default:
        				throw new RuntimeException();
        		}
        		return getLongInsn(lRes);
        	case 2:
        		long li = Utils.getLongValue(value1);
        		int li2 = Utils.getIntValue(value2);
        		long liRes = -1;
        		switch(opcode)
        		{
        			case Opcodes.LSHL:
        				liRes = li << li2;
        				break;
        			case Opcodes.LSHR:
        				liRes = li >> li2;
        				break;
        			case Opcodes.LUSHR:
        				liRes = li >>> li2;
        				break;
        			default:
        				throw new RuntimeException();
        		}
        		return getLongInsn(liRes);
        	case 3:
        		double d = getDoubleValue(value1);
        		double d2 = getDoubleValue(value2);
        		double dRes = -1;
        		switch(opcode)
        		{
        			case Opcodes.DADD:
        				dRes = d + d2;
        				break;
        			case Opcodes.DSUB:
        				dRes = d - d2;
        				break;
        			case Opcodes.DMUL:
        				dRes = d * d2;
        				break;
        			case Opcodes.DDIV:
        				dRes = d / d2;
        				break;
        			case Opcodes.DREM:
        				dRes = d % d2;
        				break;
        			default:
        				throw new RuntimeException();
        		}
        		return getDoubleInsn(dRes);
        	case 4:
        		float f = getFloatValue(value1);
        		float f2 = getFloatValue(value2);
        		float fRes = -1;
        		switch(opcode)
        		{
        			case Opcodes.FADD:
        				fRes = f + f2;
        				break;
        			case Opcodes.FSUB:
        				fRes = f - f2;
        				break;
        			case Opcodes.FMUL:
        				fRes = f * f2;
        				break;
        			case Opcodes.FDIV:
        				fRes = f / f2;
        				break;
        			case Opcodes.FREM:
        				fRes = f % f2;
        				break;
        			default:
        				throw new RuntimeException();
        		}
        		return getFloatInsn(fRes);
        }
        throw new RuntimeException();
    }
}
