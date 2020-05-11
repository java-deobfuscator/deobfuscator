package com.javadeobfuscator.deobfuscator.analyzer;

import java.util.*;
import java.util.Map.Entry;

import org.apache.commons.lang3.tuple.Triple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

/**
 * Flow Analyzer is a tool used for determining the flow of the code, using each label as a "block".
 * @author ThisTestUser
 */
public class FlowAnalyzer
{
	private final MethodNode method;
	public static final LabelNode ABSENT = new LabelNode();
	
	public FlowAnalyzer(MethodNode method) 
	{
		this.method = method;
	}
	
	public Result analyze()
	{
		LinkedHashMap<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> labels = new LinkedHashMap<>();
		LinkedHashMap<LabelNode, List<TryCatchBlockNode>> trycatchMap = new LinkedHashMap<>();
		LabelNode currentLabel = null;
		boolean hit = false;
		labels.putIfAbsent(ABSENT, new AbstractMap.SimpleEntry<>(new ArrayList<>(), new ArrayList<>()));
		List<TryCatchBlockNode> trycatchNow = new ArrayList<>();
		for(AbstractInsnNode ain : method.instructions.toArray())
		{
			if(ain.getOpcode() == Opcodes.RET || ain.getOpcode() == Opcodes.JSR)
				throw new RuntimeException("JSR/RET not supported");
			if(ain instanceof LabelNode)
			{
				if(!hit)
					if(currentLabel == null)
						labels.get(ABSENT).getValue().add(Triple.of((LabelNode)ain, new JumpData(JumpCause.NEXT, null), 0));
					else
						labels.get(currentLabel).getValue().add(Triple.of((LabelNode)ain, new JumpData(JumpCause.NEXT, null), 0));
				hit = false;
				currentLabel = (LabelNode)ain;
				labels.putIfAbsent(currentLabel, new AbstractMap.SimpleEntry<>(new ArrayList<>(), new ArrayList<>()));
				for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
				{
					if(tcbn.start == currentLabel)
					{
						TryCatchBlockNode insertBefore = null;
						for(TryCatchBlockNode tcbn2 : trycatchNow)
							if(method.tryCatchBlocks.indexOf(tcbn2) > method.tryCatchBlocks.indexOf(tcbn))
							{
								insertBefore = tcbn2;
								break;
							}
						if(insertBefore == null)
							trycatchNow.add(tcbn);
						else
							trycatchNow.add(trycatchNow.indexOf(insertBefore), tcbn);
					}
					if(tcbn.end == currentLabel)
						trycatchNow.remove(tcbn);
				}
				if(currentLabel != null)
					trycatchMap.put(currentLabel, new ArrayList<>(trycatchNow));
				continue;
			}
			if(ain.getOpcode() == Opcodes.GOTO || ain.getOpcode() == Opcodes.TABLESWITCH || ain.getOpcode() == Opcodes.LOOKUPSWITCH
				|| (ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) || ain.getOpcode() == Opcodes.ATHROW)
				hit = true;
			if(currentLabel == null)
				labels.get(ABSENT).getKey().add(ain);
			else
				labels.get(currentLabel).getKey().add(ain);
		}
		loop:
		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry : labels.entrySet())
			for(AbstractInsnNode ain : entry.getValue().getKey())
				if(ain.getOpcode() == Opcodes.GOTO)
				{
					entry.getValue().getValue().add(Triple.of(((JumpInsnNode)ain).label, new JumpData(JumpCause.GOTO, ain), 0));
					continue loop;
				}else if(ain instanceof JumpInsnNode)
					entry.getValue().getValue().add(Triple.of(((JumpInsnNode)ain).label, new JumpData(JumpCause.CONDITIONAL, ain), 0));	
				else if(ain instanceof TableSwitchInsnNode || ain instanceof LookupSwitchInsnNode)
				{
					List<LabelNode> jumps = ain instanceof TableSwitchInsnNode ? ((TableSwitchInsnNode)ain).labels : 
						((LookupSwitchInsnNode)ain).labels;
					LabelNode dflt = ain instanceof TableSwitchInsnNode ? ((TableSwitchInsnNode)ain).dflt : 
						((LookupSwitchInsnNode)ain).dflt;
					int i = 0;
					for(LabelNode label : jumps)
					{
						entry.getValue().getValue().add(Triple.of(label, new JumpData(JumpCause.SWITCH, ain), i));
						i++;
					}
					entry.getValue().getValue().add(Triple.of(dflt, new JumpData(JumpCause.SWITCH, ain), i));
					continue loop;
				}else if((ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) 
					|| ain.getOpcode() == Opcodes.ATHROW)
					continue loop;
		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry : labels.entrySet())
		{
			if(!entry.getValue().getValue().isEmpty() && entry.getValue().getValue().get(0).getMiddle().cause == JumpCause.NEXT)
				entry.getValue().getValue().add(entry.getValue().getValue().remove(0));
		}
		return new Result(labels, trycatchMap);
	}
	
	/**
	 * Analyzers all the instructions passed from a starting point. 
	 * @param start
	 * @param breaks
	 * @return
	 */
	public LinkedHashMap<LabelNode, List<AbstractInsnNode>> analyze(AbstractInsnNode start, List<AbstractInsnNode> breaks, 
		Map<AbstractInsnNode, List<LabelNode>> switchBreaks, boolean includeBreaks, boolean includeTryCatch)
	{
		LabelNode currentLabel = ABSENT;
		AbstractInsnNode prev = start;
		while(prev != null)
		{
			if(prev instanceof LabelNode)
			{
				currentLabel = (LabelNode)prev;
				break;
			}
			prev = prev.getPrevious();
		}
		//Calculate try-catches
		LabelNode lbl = ABSENT;
		LinkedHashMap<LabelNode, List<TryCatchBlockNode>> trycatchMap = new LinkedHashMap<>();
		trycatchMap.put(ABSENT, new ArrayList<>());
		List<TryCatchBlockNode> trycatchNow = new ArrayList<>();
		for(AbstractInsnNode ain : method.instructions.toArray())
			if(ain instanceof LabelNode)
			{
				lbl = (LabelNode)ain;
				//Note: Empty try-catches are not added
				for(TryCatchBlockNode tc : method.tryCatchBlocks)
				{
					if(tc.start == lbl)
					{
						TryCatchBlockNode insertBefore = null;
						for(TryCatchBlockNode tcbn2 : trycatchNow)
							if(method.tryCatchBlocks.indexOf(tcbn2) > method.tryCatchBlocks.indexOf(tc))
							{
								insertBefore = tcbn2;
								break;
							}
						if(insertBefore == null)
							trycatchNow.add(tc);
						else
							trycatchNow.add(trycatchNow.indexOf(insertBefore), tc);
					}
					if(tc.end == lbl)
						trycatchNow.remove(tc);
				}
				trycatchMap.put(lbl, new ArrayList<>(trycatchNow));
			}
		LinkedHashMap<LabelNode, List<AbstractInsnNode>> result = new LinkedHashMap<>();
		//Recursive iteration through next
		analyze0(trycatchMap, result, currentLabel, start, breaks, switchBreaks, includeBreaks, includeTryCatch);
		return result;
	}
	
	public void analyze0(LinkedHashMap<LabelNode, List<TryCatchBlockNode>> trycatchMap,
		LinkedHashMap<LabelNode, List<AbstractInsnNode>> result, 
		LabelNode currentLabel, AbstractInsnNode start, List<AbstractInsnNode> breaks, Map<AbstractInsnNode, List<LabelNode>> switchBreaks,
		boolean includeBreaks, boolean includeTryCatch)
	{
		if(start != currentLabel && !breaks.contains(start))
		{
			result.put(currentLabel, new ArrayList<>());
			if(includeTryCatch)
				for(TryCatchBlockNode trycatch : trycatchMap.get(currentLabel))
					analyze0(trycatchMap, result, trycatch.handler, trycatch.handler, breaks, switchBreaks, includeBreaks, includeTryCatch);
		}
		while(start != null)
		{
			if(breaks.contains(start))
			{
				if(start instanceof LabelNode && includeBreaks)
					result.putIfAbsent((LabelNode)start, new ArrayList<>());
				else if(includeBreaks)
				{
					result.putIfAbsent(currentLabel, new ArrayList<>());
					result.get(currentLabel).add(start);
				}
				return;
			}
			if(start instanceof LabelNode)
			{
				if(result.containsKey(start))
					return;
				currentLabel = (LabelNode)start;
				result.put(currentLabel, new ArrayList<>());
				//Try-catch
				if(includeTryCatch)
					for(TryCatchBlockNode trycatch : trycatchMap.get(currentLabel))
						analyze0(trycatchMap, result, trycatch.handler, trycatch.handler, breaks, switchBreaks, includeBreaks, 
							includeTryCatch);
				start = start.getNext();
				continue;
			}
			result.get(currentLabel).add(start);
			if(start.getOpcode() == Opcodes.GOTO)
			{
				analyze0(trycatchMap, result, ((JumpInsnNode)start).label, ((JumpInsnNode)start).label, breaks, switchBreaks, 
					includeBreaks, includeTryCatch);
				return;
			}else if(start instanceof JumpInsnNode)
				analyze0(trycatchMap, result, ((JumpInsnNode)start).label, ((JumpInsnNode)start).label, breaks, switchBreaks, 
					includeBreaks, includeTryCatch);
			else if(start instanceof TableSwitchInsnNode || start instanceof LookupSwitchInsnNode)
			{
				List<LabelNode> jumps = start instanceof TableSwitchInsnNode ? ((TableSwitchInsnNode)start).labels : 
					((LookupSwitchInsnNode)start).labels;
				LabelNode dflt = start instanceof TableSwitchInsnNode ? ((TableSwitchInsnNode)start).dflt : 
					((LookupSwitchInsnNode)start).dflt;
				for(LabelNode lbl : jumps)
					if(!switchBreaks.containsKey(start) || switchBreaks.get(start) != lbl)
						analyze0(trycatchMap, result, lbl, lbl, breaks, switchBreaks, includeBreaks, includeTryCatch);
				if(!switchBreaks.containsKey(start) || switchBreaks.get(start) != dflt)
					analyze0(trycatchMap, result, dflt, dflt, breaks, switchBreaks, includeBreaks, includeTryCatch);
				return;
			}else if((start.getOpcode() >= Opcodes.IRETURN && start.getOpcode() <= Opcodes.RETURN) 
				|| start.getOpcode() == Opcodes.ATHROW)
				return;
			start = start.getNext();
		}
	}
	
	public static enum JumpCause
	{
		NEXT,
		GOTO,
		CONDITIONAL,
		SWITCH,
	}
	
	public static class JumpData
	{
		public final JumpCause cause;
		public final AbstractInsnNode ain;
		
		public JumpData(JumpCause cause, AbstractInsnNode ain)
		{
			this.cause = cause;
			this.ain = ain;
		}
	}
	
	public static class Result
	{
		public final LinkedHashMap<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> labels;
		public final LinkedHashMap<LabelNode, List<TryCatchBlockNode>> trycatchMap;
		
		public Result(LinkedHashMap<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> labels,
			LinkedHashMap<LabelNode, List<TryCatchBlockNode>> trycatchMap)
		{
			this.labels = labels;
			this.trycatchMap = trycatchMap;
		}
	}
}
