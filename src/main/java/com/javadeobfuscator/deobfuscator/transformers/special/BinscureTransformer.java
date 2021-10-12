package com.javadeobfuscator.deobfuscator.transformers.special;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.asm.source.ConstantPropagatingSourceFinder;
import com.javadeobfuscator.deobfuscator.asm.source.SourceFinder;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.matcher.InstructionMatcher;
import com.javadeobfuscator.deobfuscator.matcher.InstructionPattern;
import com.javadeobfuscator.deobfuscator.matcher.OpcodeStep;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;

@TransformerConfig.ConfigOptions(configClass = BinscureTransformer.Config.class)
public class BinscureTransformer extends Transformer<BinscureTransformer.Config>
{
	private static final Map<InstructionPattern, int[]> ARTH_REDUCER = new LinkedHashMap<>();
	
	static
	{
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IMUL)),
			new int[] {Opcodes.INEG});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(ICONST_M1), new OpcodeStep(ISUB)),
			new int[] {Opcodes.INEG});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(ICONST_1), new OpcodeStep(IADD)),
			new int[] {Opcodes.INEG});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_1), new OpcodeStep(ISUB), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR)),
			new int[] {Opcodes.INEG});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IADD), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR)),
			new int[] {Opcodes.INEG});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(INEG), new OpcodeStep(ISUB)),
			new int[] {Opcodes.IADD});
		ARTH_REDUCER.put(new InstructionPattern(
			 new OpcodeStep(DUP2), new OpcodeStep(IOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
			 new OpcodeStep(IAND), new OpcodeStep(IADD)),
			new int[] {Opcodes.IADD});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(INEG), new OpcodeStep(IADD)),
			new int[] {Opcodes.ISUB});
		ARTH_REDUCER.put(new InstructionPattern(
			 new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IADD),
			 new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR)),
			new int[] {Opcodes.ISUB});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP_X1), new OpcodeStep(IOR), new OpcodeStep(SWAP), new OpcodeStep(ISUB)),
			new int[] {Opcodes.ICONST_M1, Opcodes.IXOR, Opcodes.IAND});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(SWAP), new OpcodeStep(DUP_X1), new OpcodeStep(IAND), new OpcodeStep(ISUB)),
			new int[] {Opcodes.ICONST_M1, Opcodes.IXOR, Opcodes.IAND});
		
		//Custom (fixes many issues)
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP_X1), new OpcodeStep(IOR), new OpcodeStep(ISUB), new OpcodeStep(INEG)),
			new int[] {Opcodes.ICONST_M1, Opcodes.IXOR, Opcodes.IAND});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(SWAP), new OpcodeStep(ISUB), new OpcodeStep(ICONST_1), new OpcodeStep(ISUB)),
			new int[] {Opcodes.ISUB, Opcodes.ICONST_M1, Opcodes.IXOR});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IADD)),
			new int[] {Opcodes.ISUB, Opcodes.ICONST_M1, Opcodes.IXOR});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(INEG), new OpcodeStep(ICONST_M1), new OpcodeStep(IADD)),
			new int[] {Opcodes.ICONST_M1, Opcodes.IXOR});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(ICONST_1), new OpcodeStep(IADD)),
			new int[] {Opcodes.INEG});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(ISUB), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR)),
			new int[] {Opcodes.INEG});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(ISUB), new OpcodeStep(ICONST_1), new OpcodeStep(ISUB)),
			new int[] {Opcodes.IADD});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IXOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(IAND), new OpcodeStep(ICONST_2), new OpcodeStep(IMUL), new OpcodeStep(IADD)),
			new int[] {Opcodes.IADD});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(IAND), new OpcodeStep(IADD)),
			new int[] {Opcodes.IADD});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IOR), new OpcodeStep(ICONST_2), new OpcodeStep(IMUL),
	        new OpcodeStep(DUP_X2), new OpcodeStep(POP), new OpcodeStep(IXOR), new OpcodeStep(ISUB)),
			new int[] {Opcodes.IADD});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IADD), new OpcodeStep(ICONST_1), new OpcodeStep(IADD)),
			new int[] {Opcodes.ISUB});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IXOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IAND),
	        new OpcodeStep(ICONST_2), new OpcodeStep(IMUL), new OpcodeStep(ISUB)),
			new int[] {Opcodes.ISUB});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IAND),
	        new OpcodeStep(DUP_X2), new OpcodeStep(POP), new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1),
	        new OpcodeStep(IXOR), new OpcodeStep(IAND), new OpcodeStep(ISUB)),
			new int[] {Opcodes.ISUB});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IAND),
	        new OpcodeStep(ICONST_2), new OpcodeStep(IMUL), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(IXOR), new OpcodeStep(ISUB)),
			new int[] {Opcodes.ISUB});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(IAND), new OpcodeStep(ISUB)),
			new int[] {Opcodes.IXOR});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IAND),
	        new OpcodeStep(DUP_X2), new OpcodeStep(POP), new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1),
	        new OpcodeStep(IXOR), new OpcodeStep(IAND), new OpcodeStep(IOR)),
			new int[] {Opcodes.IXOR});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1),
	        new OpcodeStep(IXOR), new OpcodeStep(IOR), new OpcodeStep(IAND)),
			new int[] {Opcodes.IXOR});
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP2), new OpcodeStep(IOR), new OpcodeStep(DUP_X2), new OpcodeStep(POP),
	        new OpcodeStep(IAND), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IAND)),
			new int[] {Opcodes.IXOR});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(DUP_X1), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR), new OpcodeStep(IAND), new OpcodeStep(IADD)),
			new int[] {Opcodes.IOR});
		
		ARTH_REDUCER.put(new InstructionPattern(
	        new OpcodeStep(SWAP), new OpcodeStep(DUP_X1), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR),
	        new OpcodeStep(IOR), new OpcodeStep(SWAP), new OpcodeStep(ICONST_M1), new OpcodeStep(IXOR),
	        new OpcodeStep(ISUB)),
			new int[] {Opcodes.IAND});
	}
	
	@Override
	public boolean transform() throws Throwable
	{
		DelegatingProvider provider = new DelegatingProvider();
		provider.register(new MappedFieldProvider());
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
		
		AtomicInteger fakeStatic = new AtomicInteger();
		AtomicInteger arthIndir = new AtomicInteger();
		AtomicInteger fieldIfs = new AtomicInteger();
		AtomicInteger trycatch = new AtomicInteger();
		AtomicInteger xorSwitches = new AtomicInteger();
		AtomicInteger string = new AtomicInteger();
		AtomicInteger methodRedir = new AtomicInteger();
		System.out.println("[Special] [BinscureTransformer] Starting");
		//Fake static blocks
		for(ClassNode classNode : classNodes())
		{
			Iterator<MethodNode> itr = classNode.methods.iterator();
			while(itr.hasNext())
			{
				MethodNode mn = itr.next();
				if(mn.name.equals("<clinit>") && !mn.desc.equals("()V"))
				{
					itr.remove();
					fakeStatic.incrementAndGet();
				}
			}
		}
		
		//Arithmetic indirection
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				if(method.instructions == null)
					continue;
				boolean modified;
				do
				{
					modified = false;
					Iterator<AbstractInsnNode> itr = method.instructions.iterator();
					InstructionModifier modifier = new InstructionModifier();
					
					while(itr.hasNext())
					{
						AbstractInsnNode ain = itr.next();
						InstructionMatcher matcher = null;
						int[] replacementOpcodes = null;
						search:
						for(Entry<InstructionPattern, int[]> pattern : ARTH_REDUCER.entrySet())
						{
							InstructionMatcher matcherNow = pattern.getKey().matcher(ain);
							if(matcherNow.find() && (matcher == null
								|| matcher.getPattern().getSteps().length < matcherNow.getPattern().getSteps().length))
							{
								AbstractInsnNode first = matcherNow.getStart();
								while(first != matcherNow.getEnd())
								{
									first = first.getNext();
									if(first instanceof LabelNode)
										continue search;
								}
								matcher = matcherNow;
								replacementOpcodes = pattern.getValue();
							}
						}
						if(matcher != null)
						{
							List<AbstractInsnNode> list = matcher.getCapturedInstructions("all");
							//Skip instructions that are going to be removed
							for(int i = 0; i < replacementOpcodes.length - 1; i++)
								itr.next();
							InsnList replace = new InsnList();
							for(int opcode : replacementOpcodes)
								replace.add(new InsnNode(opcode));
							modifier.replace(list.remove(0), replace);
							modifier.removeAll(list);
							modified = true;
							arthIndir.incrementAndGet();
						}
					}
					modifier.apply(method);
				}while(modified);
			}
		
		//Fake fields
		Map<FieldNode, Integer> fakeFields = new HashMap<>();
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)ain).desc.equals("I")
						&& ain.getNext().getOpcode() >= Opcodes.IFEQ && ain.getNext().getOpcode() <= Opcodes.IFLE)
					{
						ClassNode fieldOwner = classNodes().stream().filter(
							f -> f.name.equals(((FieldInsnNode)ain).owner)).findFirst().orElse(null);
						if(fieldOwner == null)
							continue;
						FieldNode field = fieldOwner.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
							&& f.desc.equals(((FieldInsnNode)ain).desc)).findFirst().orElse(null);
						if(field == null)
							continue;
						Integer value;
						if(!fakeFields.containsKey(field))
						{
							value = isFakeField(fieldOwner, field);
							if(value == null)
								continue;
							fakeFields.put(field, value);
						}else
							value = fakeFields.get(field);
						if(runSingleIf(value, ain.getNext()))
						{
							JumpInsnNode jump = (JumpInsnNode)ain.getNext();
							jump.setOpcode(Opcodes.GOTO);
							method.instructions.remove(ain);
							while(jump.getNext() != null &&!(jump.getNext() instanceof LabelNode))
								method.instructions.remove(jump.getNext());
						}else
						{
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
						}
						fieldIfs.incrementAndGet();
					}
		//Useless pops
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(Utils.willPushToStack(ain.getOpcode()) 
						&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.POP)
					{
						method.instructions.remove(ain.getNext());
						method.instructions.remove(ain);
					}
		//Useless try-catches (and try-catch rerouting)
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				Iterator<TryCatchBlockNode> itr = method.tryCatchBlocks.iterator();
				List<LabelNode> processedLabels = new ArrayList<>();
				while(itr.hasNext())
				{
					TryCatchBlockNode tcbn = itr.next();
					LabelNode handler = tcbn.handler;
					if(handler.getNext() != null && handler.getNext().getOpcode() == Opcodes.ATHROW)
					{
						itr.remove();
						trycatch.incrementAndGet();
					}else if(handler.getNext() != null && handler.getNext().getOpcode() == Opcodes.DUP
						&& handler.getNext().getNext() != null && handler.getNext().getNext().getOpcode() == Opcodes.IFNULL
						&& handler.getNext().getNext().getNext() != null
						&& ((handler.getNext().getNext().getNext().getOpcode() == Opcodes.CHECKCAST
						&& ((TypeInsnNode)handler.getNext().getNext().getNext()).desc.equals("java/lang/Throwable")
						&& handler.getNext().getNext().getNext().getNext() != null
						&& handler.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW)
							|| handler.getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW))
					{
						itr.remove();
						trycatch.incrementAndGet();
						if(processedLabels.contains(handler))
							continue;
						for(AbstractInsnNode ain : method.instructions.toArray())
							if(ain.getOpcode() == Opcodes.GOTO && ((JumpInsnNode)ain).label == handler
								&& ain.getPrevious() != null && ain.getPrevious().getOpcode() == Opcodes.ACONST_NULL
								&& ((JumpInsnNode)handler.getNext().getNext()).label.getNext() != null
								&& ((JumpInsnNode)handler.getNext().getNext()).label.getNext().getOpcode() == Opcodes.POP)
							{
								method.instructions.remove(((JumpInsnNode)handler.getNext().getNext()).label.getNext());
								method.instructions.remove(ain.getPrevious());
								((JumpInsnNode)ain).label = ((JumpInsnNode)handler.getNext().getNext()).label;
							}
						processedLabels.add(handler);
					}
				}
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					if(!(ain instanceof LabelNode) || processedLabels.contains(ain))
						continue;
					LabelNode handler = (LabelNode)ain;
					if(handler.getNext() != null && handler.getNext().getOpcode() == Opcodes.DUP
						&& handler.getNext().getNext() != null && handler.getNext().getNext().getOpcode() == Opcodes.IFNULL
						&& handler.getNext().getNext().getNext() != null
						&& ((handler.getNext().getNext().getNext().getOpcode() == Opcodes.CHECKCAST
						&& ((TypeInsnNode)handler.getNext().getNext().getNext()).desc.equals("java/lang/Throwable")
						&& handler.getNext().getNext().getNext().getNext() != null
						&& handler.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW)
							|| handler.getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW))
					{
						for(AbstractInsnNode ain2 : method.instructions.toArray())
							if(ain2.getOpcode() == Opcodes.GOTO && ((JumpInsnNode)ain2).label == handler
								&& ain2.getPrevious() != null && ain2.getPrevious().getOpcode() == Opcodes.ACONST_NULL
								&& ((JumpInsnNode)handler.getNext().getNext()).label.getNext() != null
								&& ((JumpInsnNode)handler.getNext().getNext()).label.getNext().getOpcode() == Opcodes.POP)
							{
								method.instructions.remove(((JumpInsnNode)handler.getNext().getNext()).label.getNext());
								method.instructions.remove(ain2.getPrevious());
								((JumpInsnNode)ain2).label = ((JumpInsnNode)handler.getNext().getNext()).label;
							}
						processedLabels.add(handler);
					}
				}
			}
		//Fix L2I
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode)ain).cst instanceof Long
						&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.L2I)
					{
						method.instructions.remove(ain.getNext());
						((LdcInsnNode)ain).cst = (int)((Long)((LdcInsnNode)ain).cst).longValue();
					}
		//Fix xor switches
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				List<LookupSwitchInsnNode> possibleSwitches = new ArrayList<>();
				Frame<SourceValue>[] frames;
				try
				{
					frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
				}catch(AnalyzerException e)
				{
					continue;
				}
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					if(ain.getOpcode() != Opcodes.LOOKUPSWITCH)
						continue;
					Frame<SourceValue> frame = frames[method.instructions.indexOf(ain)];
					if(frame != null && frame.getStack(frame.getStackSize() - 1).insns.size() == 1
						&& frame.getStack(frame.getStackSize() - 1).insns.iterator().next().getOpcode() == Opcodes.IXOR)
						possibleSwitches.add((LookupSwitchInsnNode)ain);
				}
				boolean modified;
				do
				{
					modified = false;
					InstructionModifier modifier = new InstructionModifier();
					for(AbstractInsnNode ain : method.instructions.toArray())
					{
						if(ain.getOpcode() != Opcodes.IXOR)
							continue;
						Frame<SourceValue> frame = frames[method.instructions.indexOf(ain)];
						if(frame != null && frame.getStack(frame.getStackSize() - 1).insns.size() >= 1
							&& frame.getStack(frame.getStackSize() - 2).insns.size() >= 1)
						{
							Set<AbstractInsnNode> firstArg = frame.getStack(frame.getStackSize() - 2).insns;
							Set<AbstractInsnNode> secondArg = frame.getStack(frame.getStackSize() - 1).insns;
							boolean hasLdc1 = false;
							boolean hasLdc2 = false;
							for(AbstractInsnNode insn : firstArg)
								if(Utils.isInteger(insn))
								{
									hasLdc1 = true;
									break;
								}
							for(AbstractInsnNode insn : secondArg)
								if(Utils.isInteger(insn))
								{
									hasLdc2 = true;
									break;
								}
							if(!hasLdc1 || !hasLdc2)
								continue;
							AbstractInsnNode first = null;
							AbstractInsnNode second = null;
							if(firstArg.size() == 1)
								first = firstArg.iterator().next();
							if(secondArg.size() == 1)
								second = secondArg.iterator().next();
							if(firstArg.size() > 1 || secondArg.size() > 1)
							{
								FlowAnalyzer analyzer = new FlowAnalyzer(method);
								//If code isn't reached for some reason, safe to ignore
								LinkedHashMap<LabelNode, List<AbstractInsnNode>> passed = analyzer.analyze(
									method.instructions.getFirst(), Arrays.asList(ain), new HashMap<>(), true, true);
								outer:
								for(Entry<LabelNode, List<AbstractInsnNode>> entry : passed.entrySet())
									for(AbstractInsnNode passedInsn : entry.getValue())
									{
										if(firstArg.size() > 1 && firstArg.contains(passedInsn))
										{
											if(first != null)
											{
												first = null;
												break outer;
											}
											first = passedInsn;
										}
										if(secondArg.size() > 1 && secondArg.contains(passedInsn))
										{
											if(second != null)
											{
												second = null;
												break outer;
											}
											second = passedInsn;
										}
									}
							}
							if(first == null || second == null)
								continue;
							if(Utils.isInteger(first) && Utils.isInteger(second));
							{
								int value = Utils.getIntValue(first) ^ Utils.getIntValue(second);
								modifier.remove(first);
								modifier.remove(second);
								for(AbstractInsnNode insn : firstArg)
									if(insn != first)
									{
										InsnList list = new InsnList();
										list.add(new InsnNode(Opcodes.POP));
										modifier.append(insn, list);
									}
								for(AbstractInsnNode insn : secondArg)
									if(insn != second)
									{
										InsnList list = new InsnList();
										list.add(new InsnNode(Opcodes.POP));
										modifier.append(insn, list);
									}
								modifier.replace(ain, Utils.getIntInsn(value));
								modified = true;
							}
						}
					}
					modifier.apply(method);
					
					if(modified)
					{
						try
						{
							frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
						}catch(AnalyzerException e)
						{
							throw new RuntimeException("Unexpected analyzer exception " + classNode.name + " " + method.name + method.desc, e);
						}
					}
					
					modifier = new InstructionModifier();
					Iterator<LookupSwitchInsnNode> switchItr = possibleSwitches.iterator();
					while(switchItr.hasNext())
					{
						LookupSwitchInsnNode lookup = switchItr.next();
						Frame<SourceValue> frame = frames[method.instructions.indexOf(lookup)];
						if(frame != null && frame.getStack(frame.getStackSize() - 1).insns.size() >= 1)
						{
							Set<AbstractInsnNode> arg = frame.getStack(frame.getStackSize() - 1).insns;
							boolean hasLdc = false;
							for(AbstractInsnNode insn : arg)
								if(Utils.isInteger(insn))
								{
									hasLdc = true;
									break;
								}
							if(!hasLdc)
								continue;
							AbstractInsnNode ldc = null;
							if(arg.size() == 1)
								ldc = arg.iterator().next();
							else
							{
								FlowAnalyzer analyzer = new FlowAnalyzer(method);
								//If code isn't reached for some reason, safe to ignore
								LinkedHashMap<LabelNode, List<AbstractInsnNode>> passed = analyzer.analyze(
									method.instructions.getFirst(), Arrays.asList(lookup), new HashMap<>(), true, true);
								outer:
								for(Entry<LabelNode, List<AbstractInsnNode>> entry : passed.entrySet())
									for(AbstractInsnNode passedInsn : entry.getValue())
										if(arg.size() > 1 && arg.contains(passedInsn))
										{
											if(ldc != null)
											{
												ldc = null;
												break outer;
											}
											ldc = passedInsn;
										}
							}
							if(ldc == null)
								continue;
							if(Utils.isInteger(ldc))
							{
								int value = Utils.getIntValue(ldc);
								LabelNode jumpPoint = null;
								for(int i = 0; i < lookup.keys.size(); i++)
								{
									int key = lookup.keys.get(i);
									if(key == value)
									{
										jumpPoint = lookup.labels.get(i);
										break;
									}
								}
								if(jumpPoint == null)
									jumpPoint = lookup.dflt;
								modifier.remove(ldc);
								for(AbstractInsnNode insn : arg)
									if(insn != ldc)
									{
										InsnList list = new InsnList();
										list.add(new InsnNode(Opcodes.POP));
										modifier.append(insn, list);
									}
								modifier.replace(lookup, new JumpInsnNode(Opcodes.GOTO, jumpPoint));
								modified = true;
								switchItr.remove();
								xorSwitches.incrementAndGet();
							}
						}
					}
					
					for(AbstractInsnNode ain : method.instructions.toArray())
					{
						if(ain.getOpcode() != Opcodes.TABLESWITCH)
							continue;
						TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode)ain;
						Frame<SourceValue> frame = frames[method.instructions.indexOf(tableSwitch)];
						if(frame != null && frame.getStack(frame.getStackSize() - 1).insns.size() == 1
							&& frame.getStack(frame.getStackSize() - 1).insns.iterator().next().getOpcode() == Opcodes.IXOR)
						{
							AbstractInsnNode ixor = frame.getStack(frame.getStackSize() - 1).insns.iterator().next();
							Frame<SourceValue> ixorFrame = frames[method.instructions.indexOf(ixor)];
							if(ixorFrame != null && ixorFrame.getStack(ixorFrame.getStackSize() - 1).insns.size() >= 1
								&& ixorFrame.getStack(ixorFrame.getStackSize() - 2).insns.size() > 1)
							{
								Set<AbstractInsnNode> firstArg = ixorFrame.getStack(ixorFrame.getStackSize() - 2).insns;
								Set<AbstractInsnNode> secondArg = ixorFrame.getStack(ixorFrame.getStackSize() - 1).insns;
								boolean hasLdc1 = false;
								boolean hasLdc2 = false;
								for(AbstractInsnNode insn : firstArg)
									if(Utils.isInteger(insn))
									{
										hasLdc1 = true;
										break;
									}
								for(AbstractInsnNode insn : secondArg)
									if(Utils.isInteger(insn))
									{
										hasLdc2 = true;
										break;
									}
								if(!hasLdc1 || !hasLdc2)
									continue;
								AbstractInsnNode firstOne = null;
								AbstractInsnNode firstTwo = null;
								AbstractInsnNode second = null;
								if(secondArg.size() == 1)
									second = secondArg.iterator().next();
								FlowAnalyzer analyzer = new FlowAnalyzer(method);
								//If code isn't reached for some reason, safe to ignore
								LinkedHashMap<LabelNode, List<AbstractInsnNode>> passed = analyzer.analyze(
									method.instructions.getFirst(), Arrays.asList(ixor), new HashMap<>(), true, true);
								outer:
								for(Entry<LabelNode, List<AbstractInsnNode>> entry : passed.entrySet())
									for(AbstractInsnNode passedInsn : entry.getValue())
									{
										if(firstArg.size() > 1 && firstArg.contains(passedInsn))
										{
											if(firstOne != null)
											{
												if(firstTwo != null)
												{
													firstOne = null;
													firstTwo = null;
													break outer;
												}else
													firstTwo = passedInsn;
											}else
												firstOne = passedInsn;
										}
										if(secondArg.size() > 1 && secondArg.contains(passedInsn))
										{
											if(second != null)
											{
												second = null;
												break outer;
											}
											second = passedInsn;
										}
									}
								if(firstOne == null || firstTwo == null || second == null
									|| !Utils.isInteger(firstOne) || !Utils.isInteger(firstTwo)
									|| !Utils.isInteger(second))
									continue;
								int firstVal = Utils.getIntValue(firstOne) ^ Utils.getIntValue(second);
								int secondVal = Utils.getIntValue(firstTwo) ^ Utils.getIntValue(second);
								LabelNode jumpPoint1;
								if(tableSwitch.min <= firstVal && tableSwitch.max >= firstVal)
									jumpPoint1 = tableSwitch.labels.get(firstVal - tableSwitch.min);
								else
									jumpPoint1 = tableSwitch.dflt;
								modifier.replace(firstOne, new JumpInsnNode(Opcodes.GOTO, jumpPoint1));
								AbstractInsnNode next = firstOne.getNext();
								while(!(next instanceof LabelNode) && next != null)
								{
									modifier.remove(next);
									next = next.getNext();
								}
								LabelNode jumpPoint2 = null;
								if(tableSwitch.min <= secondVal && tableSwitch.max >= secondVal)
									jumpPoint2 = tableSwitch.labels.get(secondVal - tableSwitch.min);
								else
									jumpPoint2 = tableSwitch.dflt;
								modifier.replace(firstTwo, new JumpInsnNode(Opcodes.GOTO, jumpPoint2));
								next = firstTwo.getNext();
								while(!(next instanceof LabelNode) && next != null)
								{
									modifier.remove(next);
									next = next.getNext();
								}
								modified = true;
								xorSwitches.incrementAndGet();
							}
						}
					}
					
					modifier.apply(method);
					
					if(modified)
					{
						try
						{
							frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
						}catch(AnalyzerException e)
						{
							throw new RuntimeException("Unexpected analyzer exception " + classNode.name + " " + method.name + method.desc, e);
						}
					}
				}while(modified);
			}
		//Method Indirection (gathering data)
        Set<ClassNode> decryptorClassesNonInit = new HashSet<>();
        Map<AbstractInsnNode, Entry<ClassNode, MethodNode>> ownerCall = new HashMap<>();
        Map<Entry<ClassNode, MethodNode>, Entry<MethodNode, LinkedHashMap<LabelNode, List<AbstractInsnNode>>>> passedPerMethod
        	= new HashMap<>();
		Set<ClassNode> indirectionClasses = new HashSet<>();
        for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				if(method.instructions == null || method.instructions.getFirst() == null)
					continue;
				if((method.instructions.getFirst().getOpcode() == Opcodes.ALOAD
					&& ((VarInsnNode)method.instructions.getFirst()).var == 0
					&& !Modifier.isStatic(method.access))
					|| (method.instructions.getFirst().getOpcode() == Opcodes.ACONST_NULL
					&& Modifier.isStatic(method.access)))
				{
					AbstractInsnNode next = method.instructions.getFirst().getNext();
					if(!Utils.isInteger(next))
						continue;
					boolean failed = false;
					int index = Modifier.isStatic(method.access) ? 0 : 1;
					for(int i = 0; i < Type.getArgumentTypes(method.desc).length; i++)
					{
						next = next.getNext();
						if(next == null || next.getOpcode() < Opcodes.ILOAD || next.getOpcode() > Opcodes.ALOAD
							|| ((VarInsnNode)next).var != index)
						{
							failed = true;
							break;
						}
						if(next.getOpcode() == Opcodes.DLOAD || next.getOpcode() == Opcodes.LLOAD)
							index += 2;
						else
							index++;
					}
					if(failed)
						continue;
					next = next.getNext();
					if(next == null)
						continue;
					if(next.getOpcode() != Opcodes.INVOKESTATIC
						|| !((MethodInsnNode)next).desc.startsWith("(Ljava/lang/Object;I")
						|| !Type.getReturnType(((MethodInsnNode)next).desc).equals(Type.getReturnType(method.desc)))
						continue;
					if(next.getNext() == null || next.getNext().getOpcode() < Opcodes.IRETURN
						|| next.getNext().getOpcode() > Opcodes.RETURN)
						continue;
					MethodInsnNode redirectInsn = (MethodInsnNode)next;
					ClassNode owner = classNodes().stream().filter(c ->
						c.name.equals(redirectInsn.owner)).findFirst().orElse(null);
					if(owner == null)
						continue;
					MethodNode mn = owner.methods.stream().filter(m -> m.name.equals(redirectInsn.name)
						&& m.desc.equals(redirectInsn.desc)).findFirst().orElse(null);
					if(mn == null || mn.instructions == null || mn.instructions.getFirst() == null)
						continue;
					AbstractInsnNode first = mn.instructions.getFirst();
					if(!Utils.isInstruction(first))
						first = Utils.getNext(first);
					if(first.getOpcode() != Opcodes.ILOAD || ((VarInsnNode)first).var != 1)
						continue;
					if(first.getNext() == null || !Utils.isInteger(first.getNext()))
						continue;
					if(first.getNext().getNext() == null || first.getNext().getNext().getOpcode() != Opcodes.IXOR)
						continue;
					if(first.getNext().getNext().getNext() == null
						|| first.getNext().getNext().getNext().getOpcode() != Opcodes.TABLESWITCH)
						continue;
					int value = Utils.getIntValue(method.instructions.getFirst().getNext())
						^ Utils.getIntValue(first.getNext());
					TableSwitchInsnNode tableSwitch = (TableSwitchInsnNode)first.getNext().getNext().getNext();
					if(tableSwitch.min > value || tableSwitch.max < value)
						continue;
					LabelNode jumpPoint = tableSwitch.labels.get(value - tableSwitch.min);
					LinkedHashMap<LabelNode, List<AbstractInsnNode>> passed = new FlowAnalyzer(mn).
						analyze(jumpPoint, new ArrayList<>(), new HashMap<>(), false, true);
					passedPerMethod.put(new AbstractMap.SimpleEntry<>(classNode, method), new AbstractMap.SimpleEntry<>(mn, passed));
					indirectionClasses.add(owner);
					for(Entry<LabelNode, List<AbstractInsnNode>> entry : passed.entrySet())
						for(AbstractInsnNode ain : entry.getValue())
						{
							if(ain.getOpcode() != Opcodes.INVOKESTATIC)
								continue;
							ClassNode cn = classNodes().stream().filter(c ->
								c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
							if(cn == null)
								continue;
							if(!decryptorClassesNonInit.contains(cn) && !isStringEncClass(cn))
								continue;
							if(ownerCall.containsKey(ain))
								throw new IllegalStateException("Instruction was passed twice!");
							ownerCall.put(ain, new AbstractMap.SimpleEntry<>(classNode, method));
						}
				}
			}

        Context context = new Context(provider);
		context.dictionary = classpath;
        
		//String Encryption
        Set<ClassNode> decryptorClasses = new HashSet<>();
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
                        String strCl = m.owner;
                        if(m.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
                        	|| m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) 
                        {
                        	Frame<SourceValue> currentFrame = frames[method.instructions.indexOf(m)];
                        	List<JavaValue> args = new ArrayList<>();
                        	List<AbstractInsnNode> instructions = new ArrayList<>();
                        	
                        	Type[] argTypes = Type.getArgumentTypes(m.desc);
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
    						context.clearStackTrace();
    						if(classes.containsKey(strCl)) 
    						{
    							ClassNode innerClassNode = classes.get(strCl);
    							MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) 
    								&& mn.desc.equals(m.desc)).findFirst().orElse(null);
    							if(decrypterNode == null || decrypterNode.instructions.getFirst() == null)
    								continue;
    							if(decryptorClasses.contains(innerClassNode) || isStringEncClass(innerClassNode))
    							{
    								if(!decryptorClasses.contains(innerClassNode))
    								{
    									patchClass(context, innerClassNode);
    									MethodNode clinit = innerClassNode.methods.stream().filter(me -> me.name.equals("<clinit>")).
    										findFirst().orElse(null);
    									MethodExecutor.execute(innerClassNode, clinit, Arrays.asList(), null, context);
    									decryptorClasses.add(innerClassNode);
    								}
    								if(args.size() > 1)
    								{
    									if(!ownerCall.containsKey(ain))
    										throw new IllegalStateException("A method redirector was found but its owner does not exist!");
    									Entry<ClassNode, MethodNode> entry = ownerCall.get(ain);
    									context.push(entry.getKey().name, entry.getValue().name,
    										getDeobfuscator().getConstantPool(entry.getKey()).getSize());
    								}
    								context.push(classNode.name, method.name, getDeobfuscator().getConstantPool(classNode).getSize());
    								LdcInsnNode replace = new LdcInsnNode(MethodExecutor.execute(innerClassNode, decrypterNode, 
                        				args, null, context));
    								modifier.replace(m, replace);
    								modifier.removeAll(instructions);
    								if(args.size() > 1)
    								{
    									//Replace string in "passed" map
    									for(Entry<LabelNode, List<AbstractInsnNode>> entry : 
    										passedPerMethod.get(new AbstractMap.SimpleEntry<>(ownerCall.get(ain))).getValue().entrySet())
    									{
    										ListIterator<AbstractInsnNode> listItr = entry.getValue().listIterator();
    										while(listItr.hasNext())
    										{
    											AbstractInsnNode next = listItr.next();
    											if(instructions.contains(next))
    												listItr.remove();
    											if(m == next)
    												listItr.set(replace);
    										}
    									}
    								}
    								string.getAndIncrement();
    							}
    						}
                        }
                	}
                modifier.apply(method);
            }
		//Method Indirection (fixing)
		for(Entry<Entry<ClassNode, MethodNode>, Entry<MethodNode, LinkedHashMap<LabelNode, List<AbstractInsnNode>>>> entry
			: passedPerMethod.entrySet())
		{
			List<AbstractInsnNode> copyInstr = new ArrayList<>();
			Map<LabelNode, LabelNode> cloneMap = Utils.generateCloneMap(entry.getValue().getKey().instructions);
			//Note: The ABSENT LabelNode should not be encountered
			for(Entry<LabelNode, List<AbstractInsnNode>> labelEntry : entry.getValue().getValue().entrySet())
			{
				copyInstr.add(labelEntry.getKey());
				copyInstr.addAll(labelEntry.getValue());
			}
			InsnList cloneList = new InsnList();
			for(AbstractInsnNode ain : entry.getValue().getKey().instructions.toArray())
				if(copyInstr.contains(ain))
					cloneList.add(ain.clone(cloneMap));
			boolean isStatic = Modifier.isStatic(entry.getKey().getValue().access);
			for(AbstractInsnNode ain : cloneList.toArray())
				if(ain instanceof VarInsnNode)
				{
					if(((VarInsnNode)ain).var == 1)
						throw new IllegalStateException("Lookup key should not exist inside execution");
					if(isStatic)
					{
						if(((VarInsnNode)ain).var == 0)
							throw new IllegalStateException("Tried to access instance as static method");
						((VarInsnNode)ain).var -= 2;
					}else
					{
						if(((VarInsnNode)ain).var != 0)
							((VarInsnNode)ain).var -= 1;
						else if(ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.CHECKCAST
							&& ((TypeInsnNode)ain.getNext()).desc.equals(entry.getKey().getKey().name))
							cloneList.remove(ain.getNext());
					}
				}else if(ain instanceof IincInsnNode)
				{
					if(((IincInsnNode)ain).var == 0 || ((IincInsnNode)ain).var == 1)
						throw new IllegalStateException("Code tried to increment invalid variable");
					if(isStatic)
						((IincInsnNode)ain).var -= 2;
					else
						((IincInsnNode)ain).var--;
				}
			entry.getKey().getValue().instructions = cloneList;
			//Try-catches
			if(entry.getValue().getKey().tryCatchBlocks != null && !entry.getValue().getKey().tryCatchBlocks.isEmpty())
			{
				FlowAnalyzer.Result result = new FlowAnalyzer(entry.getValue().getKey()).analyze();
				List<TryCatchBlockNode> tryCatch = new ArrayList<>();
				List<LabelNode> clonedLabelList = new ArrayList<>();
				for(AbstractInsnNode ain : cloneList.toArray())
					if(ain instanceof LabelNode)
						clonedLabelList.add((LabelNode)ain);
				for(Entry<LabelNode, List<TryCatchBlockNode>> tryCatchLabels : result.trycatchMap.entrySet())
				{
					if(tryCatchLabels.getValue().isEmpty() || result.labels.get(tryCatchLabels.getKey()).getKey().isEmpty())
						continue;
					LabelNode currentLabel = cloneMap.get(tryCatchLabels.getKey());
					int index = clonedLabelList.indexOf(currentLabel);
					if(index != -1)
					{
						if(index == clonedLabelList.size() - 1)
						{
							LabelNode label = new LabelNode();
							cloneList.add(label);
							clonedLabelList.add(label);
						}
						LabelNode nextLabel = clonedLabelList.get(index + 1);
						for(TryCatchBlockNode oldTcbn : tryCatchLabels.getValue())
						{
							if(!clonedLabelList.contains(cloneMap.get(oldTcbn.handler)))
								throw new IllegalStateException("Exception exists in code but handler out of scope!");
							tryCatch.add(new TryCatchBlockNode(currentLabel, nextLabel,
								cloneMap.get(oldTcbn.handler), oldTcbn.type));
						}
					}
				}
				if(!tryCatch.isEmpty())
					entry.getKey().getValue().tryCatchBlocks = tryCatch;
			}
			methodRedir.incrementAndGet();
		}
		//Cleanup
		if(getConfig().shouldCleanup())
		{
			for(ClassNode classNode : decryptorClasses)
			{
				if(classNode.superName.equals("java/lang/Object"))
				{
					outer:
					for(MethodNode method : classNode.methods)
						for(AbstractInsnNode ain : method.instructions.toArray())
						{
							if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
								&& ((MethodInsnNode)ain).name.equals("get")
								&& ((MethodInsnNode)ain).desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))
							{
								boolean ownerCheck = ((MethodInsnNode)ain).owner.equals(classNode.name);
								if(!ownerCheck)
								{
									ClassNode other = classNodes().stream().filter(
										c -> c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
									if(other != null && other.superName.equals("java/util/concurrent/ConcurrentHashMap"))
									{
										classes.remove(other.name);
										classpath.remove(other.name);
										break outer;
									}
								}
							}
						}
				}else
				{
					classes.remove(classNode.superName);
					classpath.remove(classNode.superName);
				}
				classes.remove(classNode.name);
				classpath.remove(classNode.name);
			}
			for(ClassNode classNode : indirectionClasses)
			{
				classes.remove(classNode.name);
				classpath.remove(classNode.name);
			}
		}
		
		System.out.println("[Special] [BinscureTransformer] Removed " + fakeStatic + " fake static blocks");
		System.out.println("[Special] [BinscureTransformer] Removed " + arthIndir + " arithmetic complications");
		System.out.println("[Special] [BinscureTransformer] Removed " + fieldIfs + " constant field jumps");
		System.out.println("[Special] [BinscureTransformer] Removed " + trycatch + " try-catch blocks");
		System.out.println("[Special] [BinscureTransformer] Removed " + xorSwitches + " XOR switches");
		System.out.println("[Special] [BinscureTransformer] Decrypted " + string + " strings");
		System.out.println("[Special] [BinscureTransformer] Fixed " + methodRedir + " method redirects");
		return fakeStatic.get() > 0 || fieldIfs.get() > 0 || trycatch.get() > 0
			|| xorSwitches.get() > 0 || string.get() > 0 || methodRedir.get() > 0;
	}

	private Integer isFakeField(ClassNode owner, FieldNode field)
	{
		Integer value = null;
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(ain.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)ain).name.equals(field.name)
						 && ((FieldInsnNode)ain).desc.equals(field.desc)
						 && ((FieldInsnNode)ain).owner.equals(owner.name))
					{
						if(value != null)
							return null;
						if(classNode.name.equals(((FieldInsnNode)ain).owner)
							&& ain.getPrevious() != null && Utils.isInteger(ain.getPrevious()))
							value = Utils.getIntValue(ain.getPrevious());
						else
							return null;
					}
		return value;
	}
	
	private boolean runSingleIf(int value, AbstractInsnNode ain)
	{
		switch(ain.getOpcode())
		{
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
	
	private boolean isStringEncClass(ClassNode classNode)
	{
		for(FieldNode field : classNode.fields)
			if(field.desc.startsWith("[[[[[[[[[[[[[[[[[[[[[[[["))
				return true;
		return false;
	}
	
	private void patchClass(Context context, ClassNode classNode)
	{
		ConcurrentHashMap<String, String> mapCache = new ConcurrentHashMap<>();
		for(MethodNode method : classNode.methods)
			for(AbstractInsnNode ain : method.instructions.toArray())
			{
				if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).name.equals("get")
					&& ((MethodInsnNode)ain).desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))
				{
					boolean ownerCheck = ((MethodInsnNode)ain).owner.equals(classNode.name);
					if(!ownerCheck)
					{
						ClassNode other = classNodes().stream().filter(
							c -> c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
						if(other != null)
							ownerCheck = other.superName.equals("java/util/concurrent/ConcurrentHashMap");
					}
					if(ownerCheck)
						context.customMethodFunc.put(ain, (list, ctx) ->
						new JavaObject(mapCache.get(list.get(0).as(Object.class)), "java/lang/String"));
				}else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL
					&& ((MethodInsnNode)ain).name.equals("put")
					&& ((MethodInsnNode)ain).desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
				{	
					boolean ownerCheck = ((MethodInsnNode)ain).owner.equals(classNode.name);
					if(!ownerCheck)
					{
						ClassNode other = classNodes().stream().filter(
							c -> c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
						if(other != null)
							ownerCheck = other.superName.equals("java/util/concurrent/ConcurrentHashMap");
					}
					if(ownerCheck)
						context.customMethodFunc.put(ain, (list, ctx) -> {
							mapCache.put(list.get(0).as(String.class), list.get(1).as(String.class));
							return null;
						});
				}
			}
		MethodNode mainDecrypter = classNode.methods.stream().filter(m -> m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")).findFirst().orElse(null);
			for(AbstractInsnNode ain : mainDecrypter.instructions.toArray())
				if(ain.getOpcode() == Opcodes.INVOKESPECIAL
					&& ((MethodInsnNode)ain).owner.equals("java/lang/IllegalStateException")
					&& ((MethodInsnNode)ain).name.equals("<init>")
					&& ((MethodInsnNode)ain).desc.equals("()V"))
					context.customMethodFunc.put(ain, (list, ctx) -> {
						list.get(0).initialize(new IllegalStateException());
						return null;
					});
				else if(ain instanceof MethodInsnNode)
				{
					ClassNode cn = classpath.values().stream().filter(c -> c.name.equals(((MethodInsnNode)ain).owner)).findFirst().orElse(null);
					if(cn == null)
						context.customMethodFunc.put(ain, (list, ctx) -> {
							throw new NoClassDefFoundError("Fake class");
						});
				}else if(ain instanceof InvokeDynamicInsnNode)
					context.customMethodFunc.put(ain, (list, ctx) -> {
						throw new BootstrapMethodError("Fake invokedynamic");
					});
				else if(ain.getOpcode() == Opcodes.CHECKCAST)
				{
					ClassNode cn = classNodes().stream().filter(c -> c.name.equals(((TypeInsnNode)ain).desc)).findFirst().orElse(null);
					if(cn == null)
						context.customMethodFunc.put(ain, (list, ctx) -> {
							throw new NoClassDefFoundError("Fake class");
						});
				}
	}
	
	public static class Config extends TransformerConfig 
	{
		/**
		 * Should we cleanup leftover decryption classes?
		 */
		private boolean cleanup = true;
		
		public Config() 
		{
			super(BinscureTransformer.class);
		}
		
		public boolean shouldCleanup() 
		{
			return cleanup;
		}
		
		public void setCleanup(boolean cleanup) 
		{
			this.cleanup = cleanup;
		}
	}
}
