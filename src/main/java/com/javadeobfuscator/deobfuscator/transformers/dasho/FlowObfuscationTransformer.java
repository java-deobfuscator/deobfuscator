package com.javadeobfuscator.deobfuscator.transformers.dasho;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Triple;
import org.assertj.core.internal.asm.Opcodes;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer.JumpData;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

/**
 * This transformer can be VERY slow.
 */
public class FlowObfuscationTransformer extends Transformer<TransformerConfig>
{
	private static final LabelNode NO_INSN = new LabelNode();
		
	@Override
	public boolean transform() throws Throwable
	{
		System.out.println("[DashO] [FlowObfuscationTransformer] Starting");
		AtomicInteger suspect = new AtomicInteger();
		AtomicInteger counter = new AtomicInteger();
		
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				if(method.instructions == null || method.instructions.size() == 0)
					continue;
				FlowAnalyzer analyzer = new FlowAnalyzer(method);
				LinkedHashMap<LabelNode, List<AbstractInsnNode>> result = analyzer.analyze(method.instructions.getFirst(),
					new ArrayList<>(), new HashMap<>(), false, true);
				FlowAnalyzer.Result jumpAnalysis = analyzer.analyze();
				List<AbstractInsnNode> ordered = new ArrayList<>();
				for(Entry<LabelNode, List<AbstractInsnNode>> e : result.entrySet())
				{
					if(e.getKey() != FlowAnalyzer.ABSENT)
						ordered.add(e.getKey());
					ordered.addAll(e.getValue());
					for(Triple<LabelNode, JumpData, Integer> entry : jumpAnalysis.labels.get(e.getKey()).getValue())
						if(entry.getMiddle().cause == FlowAnalyzer.JumpCause.NEXT)
							ordered.add(new JumpInsnNode(Opcodes.GOTO, entry.getLeft()));
				}
				loop:
				for(AbstractInsnNode ain : ordered)
				{
					if(Utils.getIntValue(ain) == -1
						&& getNext(ordered, result, jumpAnalysis, ain, 1).getOpcode() == Opcodes.ISTORE
						&& getNext(ordered, result, jumpAnalysis, ain, 2).getOpcode() == Opcodes.LDC
						&& ((LdcInsnNode)getNext(ordered, result, jumpAnalysis, ain, 2)).cst.equals("0")
						&& getNext(ordered, result, jumpAnalysis, ain, 3).getOpcode() == Opcodes.IINC
						&& ((IincInsnNode)getNext(ordered, result, jumpAnalysis, ain, 3)).incr == 1
						&& ((IincInsnNode)getNext(ordered, result, jumpAnalysis, ain, 3)).var == ((VarInsnNode)getNext(ordered, result, jumpAnalysis, ain, 1)).var
						&& getNext(ordered, result, jumpAnalysis, ain, 4).getOpcode() == Opcodes.ASTORE)
					{
						suspect.incrementAndGet();
						List<AbstractInsnNode> remove = new ArrayList<>();
						Map<AbstractInsnNode, AbstractInsnNode> replace = new HashMap<>();
						remove.add(ain);
						remove.add(getNext(ordered, result, jumpAnalysis, ain, 1));
						remove.add(getNext(ordered, result, jumpAnalysis, ain, 2));
						remove.add(getNext(ordered, result, jumpAnalysis, ain, 3));
						remove.add(getNext(ordered, result, jumpAnalysis, ain, 4));
						int var1Index = ((VarInsnNode)getNext(ordered, result, jumpAnalysis, ain, 1)).var;
						int var1Value = 0;
						int var2Index = ((VarInsnNode)getNext(ordered, result, jumpAnalysis, ain, 4)).var;
						int var2Value = 0;
						
						AbstractInsnNode next = getNext(ordered, result, jumpAnalysis, ain, 4);
						int count = 0;
						search:
						while(true)
						{
							boolean found = false;
							LabelNode lbl = null;
							while(next != NO_INSN)
							{
								if(next instanceof LabelNode)
								{
									int jumpCount = 0;
									outer:
									for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry
										: jumpAnalysis.labels.entrySet())
										for(Triple<LabelNode, JumpData, Integer> triple : entry.getValue().getValue())
											if(triple.getLeft() == next)
											{
												jumpCount++;
												if(jumpCount > 1)
													break outer;
											}
									if(jumpCount > 1)
										break;
								}
								if(Utils.getIntValue(next) == -1
									&& getNext(ordered, result, jumpAnalysis, next, 1).getOpcode() == Opcodes.ISTORE
									&& getNext(ordered, result, jumpAnalysis, next, 2).getOpcode() == Opcodes.LDC
									&& ((LdcInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).cst.equals("0")
									&& getNext(ordered, result, jumpAnalysis, next, 3).getOpcode() == Opcodes.IINC
									&& ((IincInsnNode)getNext(ordered, result, jumpAnalysis, next, 3)).incr == 1
									&& ((IincInsnNode)getNext(ordered, result, jumpAnalysis, next, 3)).var == ((VarInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).var
									&& getNext(ordered, result, jumpAnalysis, next, 4).getOpcode() == Opcodes.ASTORE)
									break;
								if(next.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)next).var == var2Index
									&& getNext(ordered, result, jumpAnalysis, next, 1).getOpcode() == Opcodes.INVOKESTATIC
									&& ((MethodInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).name.equals("parseInt")
									&& ((MethodInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).owner.equals("java/lang/Integer")
									&& getNext(ordered, result, jumpAnalysis, next, 2) != null
									&& getNext(ordered, result, jumpAnalysis, next, 2).getOpcode() == Opcodes.TABLESWITCH
									&& ((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).min == 0
									&& ((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).labels.size() == 1)
								{
									if(!method.instructions.contains(next))
										break search;
									if(remove.contains(next))
										break search;
									remove.add(next);
									remove.add(getNext(ordered, result, jumpAnalysis, next, 1));
									found = true;
									lbl = var2Value == 0 ? ((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).labels.get(0) :
										((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).dflt;
									replace.put(getNext(ordered, result, jumpAnalysis, next, 2), new JumpInsnNode(Opcodes.GOTO, lbl));
									next = getNext(ordered, result, jumpAnalysis, lbl, 1, false);
									break;
								}else if(next.getOpcode() == Opcodes.ILOAD && ((VarInsnNode)next).var == var1Index
									&& getNext(ordered, result, jumpAnalysis, next, 1).getOpcode() == Opcodes.TABLESWITCH
									&& ((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).min == 0
									&& ((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).labels.size() == 1)
								{
									if(!method.instructions.contains(next))
										break search;
									if(remove.contains(next))
										break search;
									remove.add(next);
									found = true;
									lbl = var1Value == 0 ? ((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).labels.get(0) :
										((TableSwitchInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).dflt;
									replace.put(getNext(ordered, result, jumpAnalysis, next, 1), new JumpInsnNode(Opcodes.GOTO, lbl));
									next = getNext(ordered, result, jumpAnalysis, lbl, 1, false);
									break;
								}
								next = getNext(ordered, result, jumpAnalysis, next, 1, false);
							}
							if(!found)
								break;
							count++;
							boolean found2 = false;
							while(next != NO_INSN)
							{
								if(next instanceof LabelNode)
								{
									int jumpCount = 0;
									outer:
									for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry
										: jumpAnalysis.labels.entrySet())
										if(ordered.contains(entry.getKey()))
											for(Triple<LabelNode, JumpData, Integer> triple : entry.getValue().getValue())
												if(triple.getLeft() == next)
												{
													jumpCount++;
													if(jumpCount > 1)
														break outer;
												}
									if(jumpCount > 1)
										break;
								}
								if(next.getOpcode() == Opcodes.IINC && ((IincInsnNode)next).var == var1Index
									&& getNext(ordered, result, jumpAnalysis, next, 1).getOpcode() == Opcodes.LDC
									&& getNext(ordered, result, jumpAnalysis, next, 2).getOpcode() == Opcodes.ASTORE
									&& ((VarInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).var == var2Index)
								{
									if(!method.instructions.contains(next))
										break search;
									if(remove.contains(next))
										break search;
									remove.add(next);
									remove.add(getNext(ordered, result, jumpAnalysis, next, 1));
									remove.add(getNext(ordered, result, jumpAnalysis, next, 2));
									found2 = true;
									var1Value += ((IincInsnNode)next).incr;
									var2Value = Integer.parseInt((String)((LdcInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).cst);
									if(getNext(ordered, result, jumpAnalysis, next, 3, false).getOpcode() == Opcodes.GOTO)
									{
										LabelNode lb = ((JumpInsnNode)getNext(ordered, result, jumpAnalysis, next, 3, false)).label;
										next = getNext(ordered, result, jumpAnalysis, lb, 1, false);
									}else if(getNext(ordered, result, jumpAnalysis, next, 3, false) instanceof LabelNode)
										next = getNext(ordered, result, jumpAnalysis, next, 4, false);
									break;
								}else if(Utils.isInteger(next)
									&& getNext(ordered, result, jumpAnalysis, next, 1).getOpcode() == Opcodes.ISTORE 
									&& ((VarInsnNode)getNext(ordered, result, jumpAnalysis, next, 1)).var == var1Index
									&& getNext(ordered, result, jumpAnalysis, next, 2).getOpcode() == Opcodes.LDC
									&& getNext(ordered, result, jumpAnalysis, next, 3).getOpcode() == Opcodes.ASTORE
									&& ((VarInsnNode)getNext(ordered, result, jumpAnalysis, next, 3)).var == var2Index)
								{
									if(!method.instructions.contains(next))
										break search;
									if(remove.contains(next))
										break search;
									remove.add(next);
									remove.add(getNext(ordered, result, jumpAnalysis, next, 1));
									remove.add(getNext(ordered, result, jumpAnalysis, next, 2));
									remove.add(getNext(ordered, result, jumpAnalysis, next, 3));
									found2 = true;
									var1Value = Utils.getIntValue(next);
									var2Value = Integer.parseInt((String)((LdcInsnNode)getNext(ordered, result, jumpAnalysis, next, 2)).cst);
									AbstractInsnNode varPoint = getNext(ordered, result, jumpAnalysis, next, 3);
									if(getNext(ordered, result, jumpAnalysis, varPoint, 1, false).getOpcode() == Opcodes.GOTO)
									{
										LabelNode lb = ((JumpInsnNode)getNext(ordered, result, jumpAnalysis, varPoint, 1, false)).label;
										next = getNext(ordered, result, jumpAnalysis, lb, 1, false);
									}else if(getNext(ordered, result, jumpAnalysis, varPoint, 1, false) instanceof LabelNode)
										next = getNext(ordered, result, jumpAnalysis, varPoint, 2, false);
									break;
								}
								next = getNext(ordered, result, jumpAnalysis, next, 1, false);
							}
							if(!found2)
								continue loop;
						}
						
						if(count > 0)
						{
							for(AbstractInsnNode a : remove)
								method.instructions.remove(a);
							for(Entry<AbstractInsnNode, AbstractInsnNode> en : replace.entrySet())
								method.instructions.set(en.getKey(), en.getValue());
							counter.incrementAndGet();
						}
					}
				}
			}
		 System.out.println("[DashO] [FlowObfuscationTransformer] Suspected " + suspect.get() + " flow obfuscated chunks");
		 System.out.println("[DashO] [FlowObfuscationTransformer] Removed " + counter.get() + " flow obfuscated chunks");
		 System.out.println("[DashO] [FlowObfuscationTransformer] Done");
		 return counter.get() > 0;
	}
	
	public static AbstractInsnNode getNext(List<AbstractInsnNode> list,
		LinkedHashMap<LabelNode, List<AbstractInsnNode>> flow, FlowAnalyzer.Result result, AbstractInsnNode ain, int count)
	{
		return getNext(list, flow, result, ain, count, true);
	}
	
	private static AbstractInsnNode getNext(List<AbstractInsnNode> list,
		LinkedHashMap<LabelNode, List<AbstractInsnNode>> flow, FlowAnalyzer.Result result, AbstractInsnNode ain, int count, boolean doJump)
	{
		int index = list.indexOf(ain);
		for(int i = index + 1; i < list.size();)
		{
			AbstractInsnNode here = list.get(i);
			if(here.getOpcode() == Opcodes.GOTO && doJump)
			{
				if(i + 1 >= list.size())
					return NO_INSN;
				//Note: Should be safe if there is one jump (will be caught otherwise)
				LabelNode next = ((JumpInsnNode)here).label;
				int idx = list.indexOf(next);
				if(idx == -1)
					return NO_INSN;
				int diff = idx - i;
				here = next;
				index += diff;
				i = idx;
			}
			if(here instanceof LabelNode && doJump)
			{
				if(!verifyJumps(flow, result, here))
					return NO_INSN;
				index++;
				i++;
				continue;
			}
			if(i == index + count)
				return here;
			else
				i++;
		}
		return NO_INSN;
	}
	 
	private static boolean verifyJumps(LinkedHashMap<LabelNode, List<AbstractInsnNode>> flow, FlowAnalyzer.Result result, AbstractInsnNode lbl)
	{
		int jumpCount = 0;
		outer:
		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry
			: result.labels.entrySet())
			if(flow.containsKey(entry.getKey()))
				for(Triple<LabelNode, JumpData, Integer> triple : entry.getValue().getValue())
					if(triple.getLeft() == lbl)
					{
						jumpCount++;
						if(jumpCount > 1)
							break outer;
					}
		return jumpCount <= 1;
	}
}
