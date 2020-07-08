package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Triple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer.JumpData;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.special.TryCatchFixer.TryCatchChain;

public class LabelRearranger extends Transformer<TransformerConfig>
{
	public static BiFunction<ClassNode, MethodNode, Boolean> matchingFunc = null;

    @Override
    public boolean transform() throws Throwable
    {
        AtomicInteger counter = new AtomicInteger();
        for(ClassNode classNode : classNodes())
        	methodloop:
        	for(MethodNode method : classNode.methods)
        	{
        		if(matchingFunc != null && !matchingFunc.apply(classNode, method))
        			continue;
        		if(method.localVariables != null && method.localVariables.size() > 0)
        			continue;
        		if(method.instructions.size() == 0)
        			continue;
        		FlowAnalyzer analyzer = new FlowAnalyzer(method);
        		FlowAnalyzer.Result result = analyzer.analyze();
        		LinkedHashMap<LabelNode, List<AbstractInsnNode>> initialflowAnalysis = analyzer.analyze(method.instructions.getFirst(), 
        			new ArrayList<>(), new HashMap<>(), true, true);
        		List<LabelNode> addGotos = new ArrayList<>();
        		int index = 0;
        		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> res : result.labels.entrySet())
        		{
        			boolean sureJumpReached = false;
        			for(AbstractInsnNode ain : res.getValue().getKey())
        				if(ain.getOpcode() == Opcodes.GOTO || ain.getOpcode() == Opcodes.TABLESWITCH 
        					|| ain.getOpcode() == Opcodes.LOOKUPSWITCH
        					|| (ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) 
        					|| ain.getOpcode() == Opcodes.ATHROW)
        				{
        					sureJumpReached = true;
        					break;
        				}
        			if(!sureJumpReached)
        			{
        				if(index == result.labels.size() - 1)
        				{
        					if(initialflowAnalysis.containsKey(res.getKey()))
        						//Falling off code?
        						continue methodloop;
        				}else
        					addGotos.add(res.getKey());
        			}
        			index++;
        		}
        		
        		List<Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>>> reverse = 
        			new ArrayList<>(result.labels.entrySet());
        		Collections.reverse(reverse);
        		LabelNode previous = null;
        		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> res : reverse)
        		{
        			if(addGotos.contains(res.getKey()))
        			{
        				if(previous == null)
        					throw new RuntimeException("No label after?");
        				JumpInsnNode gotoJump = new JumpInsnNode(Opcodes.GOTO, previous);
        				res.getValue().getKey().add(res.getValue().getKey().size(), gotoJump);
        				if(res.getValue().getKey().size() > 1)
        					method.instructions.insert(res.getValue().getKey().get(res.getValue().getKey().size() - 2),
        						gotoJump);
        				else if(res.getKey() != FlowAnalyzer.ABSENT)
        					method.instructions.insert(res.getKey(), gotoJump);
        				else
        					method.instructions.insert(gotoJump);
        			}
        			previous = res.getKey();
        		}
        		LinkedHashMap<LabelNode, List<AbstractInsnNode>> flowAnalysis = analyzer.analyze(method.instructions.getFirst(), 
        			Arrays.asList(), new HashMap<>(), false, true);
        		result = analyzer.analyze();
        		
        		List<Block> blocks = new ArrayList<>();
        		List<Block> orderedBlocks = new ArrayList<>();
        		Map<Block, LabelNode> blockToLabel = new HashMap<>();
        		Map<LabelNode, Block> labelToBlock = new HashMap<>();
        		
        		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> res : result.labels.entrySet())
        		{
        			Block block = new Block(res.getKey(), res.getValue().getKey());
        			blockToLabel.put(block, res.getKey());
        			labelToBlock.put(res.getKey(), block);
        			blocks.add(block);
        		}
        		
        		//Begin with first block
        		List<Entry<LabelNode, AbstractInsnNode>> jumpQueue = new ArrayList<>();
        		//The LabelNodes that can be written in the try-catch handler
        		List<LabelNode> excluded = new ArrayList<>();
        		AbstractInsnNode lastGoto = null;
        		LabelNode next = null;
        		
        		Block first = blocks.get(0);
        		orderedBlocks.add(first);
        		for(AbstractInsnNode ain : first.instructions)
        		{
        			if(ain instanceof TableSwitchInsnNode)
        			{
        				jumpQueue.add(0, new AbstractMap.SimpleEntry<>(((TableSwitchInsnNode)ain).dflt, ain));
        				List<LabelNode> switches = new ArrayList<>(((TableSwitchInsnNode)ain).labels);
        				Collections.reverse(switches);
        				for(LabelNode label : switches)
        					jumpQueue.add(0, new AbstractMap.SimpleEntry<>(label, ain));
        				break;
        			}else if(ain instanceof LookupSwitchInsnNode)
        			{
        				jumpQueue.add(0, new AbstractMap.SimpleEntry<>(((LookupSwitchInsnNode)ain).dflt, ain));
        				List<LabelNode> switches = new ArrayList<>(((LookupSwitchInsnNode)ain).labels);
        				Collections.reverse(switches);
        				for(LabelNode label : switches)
        					jumpQueue.add(0, new AbstractMap.SimpleEntry<>(label, ain));
        				break;
        			}else if(ain instanceof JumpInsnNode && ain.getOpcode() != Opcodes.GOTO)
        				jumpQueue.add(0, new AbstractMap.SimpleEntry<>(((JumpInsnNode)ain).label, ain));
        			else if((ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) 
    					|| ain.getOpcode() == Opcodes.ATHROW)
        				break;
        			else if(ain.getOpcode() == Opcodes.GOTO)
        			{
        				next = ((JumpInsnNode)ain).label;
        				lastGoto = ain;
        				break;
        			}
        		}
        		blocks.remove(0);
        		Block lastPlaced = first;
        		while(next != null || !jumpQueue.isEmpty())
        		{
        			boolean placeGoto = false;
        			//Determine exceptions
        			if(next != null)
        			{
        				//Place block if it's unreachable when all jump statements removed
        				Map<AbstractInsnNode, List<LabelNode>> switchBreaks = new HashMap<>();
        				List<AbstractInsnNode> breaks = new ArrayList<>();
        				LabelNode label = FlowAnalyzer.ABSENT;
    					for(AbstractInsnNode ain : method.instructions.toArray())
    						if(ain instanceof LabelNode)
    							label = (LabelNode)ain;
    						else if(orderedBlocks.contains(labelToBlock.get(label)))
    							if(ain instanceof JumpInsnNode && ((JumpInsnNode)ain).label == next)
    								breaks.add(ain);
    							else if((ain instanceof TableSwitchInsnNode 
									&& (((TableSwitchInsnNode)ain).labels.contains(next)
										|| ((TableSwitchInsnNode)ain).dflt == next))
    								|| (ain instanceof LookupSwitchInsnNode 
    									&& (((LookupSwitchInsnNode)ain).labels.contains(next)
    										|| ((LookupSwitchInsnNode)ain).dflt == next)))
    								{
    									switchBreaks.putIfAbsent(ain, new ArrayList<>());
    									switchBreaks.get(ain).add(label);
    								}
    					breaks.add(next);
    					boolean unreachable = true;
    					LinkedHashMap<LabelNode, List<AbstractInsnNode>> reached = 
    						analyzer.analyze(method.instructions.getFirst(), breaks, switchBreaks, true, false);
    					if(reached.containsKey(next))
    						unreachable = false;
    					if(!unreachable)
    						tcbn:
    						for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
    						{
    							LinkedHashMap<LabelNode, List<AbstractInsnNode>> trycatchAnalysis =
    								analyzer.analyze(tcbn.handler, Arrays.asList(), new HashMap<>(), false, false);
    							for(Entry<LabelNode, List<AbstractInsnNode>> en : trycatchAnalysis.entrySet())
    								if(en.getKey() == next)
    								{
    									excluded.add(next);
    									break tcbn;
    								}
    						}
    					else
    						placeGoto = true;
    					int jumpQueueIndex = -1;
    					for(int i = 0; i < jumpQueue.size(); i++)
    					{
    						AbstractInsnNode cause = jumpQueue.get(i).getValue();
    						if(cause.getOpcode() == Opcodes.GOTO)
    							continue;
    						jumpQueueIndex = i;
    						break;
    					}
        				if(placeGoto && jumpQueueIndex >= 0 && jumpQueue.get(jumpQueueIndex).getValue() instanceof JumpInsnNode)
        				{
        					//If jump site is a loop, we put the conditional block first
        					LinkedHashMap<LabelNode, List<AbstractInsnNode>> reached1 = 
        						analyzer.analyze(jumpQueue.get(jumpQueueIndex).getKey(), Arrays.asList(lastGoto, next), new HashMap<>(), true, true);
        					if(!reached1.containsKey(next) && reached1.get(lastPlaced.label) != null
        						&& reached1.get(lastPlaced.label).contains(lastGoto))
        					{
        						LinkedHashMap<LabelNode, List<AbstractInsnNode>> res2 = analyzer.analyze(method.instructions.getFirst(), 
        							Arrays.asList(lastGoto), new HashMap<>(), false, true);
        						boolean intersects = false;
        						for(LabelNode reach : reached1.keySet())
        							if(reach != lastPlaced.label && res2.containsKey(reach))
        							{
        								intersects = true;
        								break;
        							}
        						if(!intersects)
        						{
        							placeGoto = false;
            						jumpQueue.add(jumpQueueIndex + 1, new AbstractMap.SimpleEntry<>(next, lastGoto));
        						}
        					}
        					//Check to see if jumpQueue intersects with jump
        					if(placeGoto && reached1.containsKey(next))
        					{
        						LinkedHashMap<LabelNode, List<AbstractInsnNode>> reachedWithoutJump = 
            						analyzer.analyze(method.instructions.getFirst(), Arrays.asList(jumpQueue.get(jumpQueueIndex).getKey()), 
            							new HashMap<>(), false, true);
        						boolean found = false;
        						find:
        						for(Entry<LabelNode, List<AbstractInsnNode>> entry1 : reachedWithoutJump.entrySet())
        							for(AbstractInsnNode a1 : entry1.getValue())
        							{
        								for(Entry<LabelNode, List<AbstractInsnNode>> entry2 : reached1.entrySet())
        	        						for(AbstractInsnNode a2 : entry2.getValue())
        	        							if(a2 == a1)
        	        							{
        	        								found = true;
        	        								break find;
        	        							}
        							}
        						if(!found)
        						{
	        						placeGoto = false;
	        						jumpQueue.add(jumpQueueIndex + 1, new AbstractMap.SimpleEntry<>(next, lastGoto));
	        						jumpQueue.add(0, jumpQueue.remove(jumpQueueIndex));
        						}
        					}
        				}
        			}
        			
        			List<Entry<LabelNode, AbstractInsnNode>> placeAfter = new ArrayList<>();
        			while(!placeGoto && !jumpQueue.isEmpty() && jumpQueue.get(0).getValue() instanceof JumpInsnNode)
        			{
        				//Place jump if it's unreachable when all jump statements removed
        				AbstractInsnNode jump = jumpQueue.get(0).getKey();
        				Map<AbstractInsnNode, List<LabelNode>> switchBreaks = new HashMap<>();
        				List<AbstractInsnNode> breaks = new ArrayList<>();
        				LabelNode label = FlowAnalyzer.ABSENT;
    					for(AbstractInsnNode ain : method.instructions.toArray())
    						if(ain instanceof LabelNode)
    							label = (LabelNode)ain;
    						else if(orderedBlocks.contains(labelToBlock.get(label)))
    							if(ain instanceof JumpInsnNode && ((JumpInsnNode)ain).label == jump)
    								breaks.add(ain);
    							else if((ain instanceof TableSwitchInsnNode 
									&& (((TableSwitchInsnNode)ain).labels.contains(jump)
										|| ((TableSwitchInsnNode)ain).dflt == jump))
    								|| (ain instanceof LookupSwitchInsnNode 
    									&& (((LookupSwitchInsnNode)ain).labels.contains(jump)
    										|| ((LookupSwitchInsnNode)ain).dflt == jump)))
    								{
    									switchBreaks.putIfAbsent(ain, new ArrayList<>());
    									switchBreaks.get(ain).add(label);
    								}
    					breaks.add(jump);
    					boolean unreachable = true;
    					LinkedHashMap<LabelNode, List<AbstractInsnNode>> reached = 
    						analyzer.analyze(method.instructions.getFirst(), breaks, switchBreaks, true, false);
    					if(reached.containsKey(jump))
    						unreachable = false;
    					if(!unreachable)
    						placeAfter.add(jumpQueue.remove(0));
    					else
    						break;
        			}
        			if(!placeGoto && jumpQueue.isEmpty())
        			{
        				if(excluded.contains(next))
        					break;
        				else
        				{
        					if(next == null)
        					{
        						boolean shouldBreak = true;
        						for(Entry<LabelNode, AbstractInsnNode> entry : placeAfter)
        							if(!orderedBlocks.contains(labelToBlock.get(entry.getKey())))
        							{
        								shouldBreak = false;
        								break;
        							}
        						if(shouldBreak)
        							break;
        					}
        					System.out.println("Unknown pattern at method " + method.name + method.desc + ", class " + classNode.name);
        					continue methodloop;
        				}
        			}
        			if(!placeGoto)
        				jumpQueue.addAll(1, placeAfter);
        			Block block;
        			if(placeGoto)
        				block = labelToBlock.get(next);
        			else
        				block = labelToBlock.get(jumpQueue.get(0).getKey());
        			next = null;
        			jumpQueue.removeIf((lbl) -> block.label == lbl.getKey());
        			if(orderedBlocks.contains(block))
        				continue;
        			orderedBlocks.add(block);
        			blocks.remove(block);
        			lastPlaced = block;
        			for(AbstractInsnNode ain : block.instructions)
            		{
            			if(ain instanceof TableSwitchInsnNode)
            			{
            				jumpQueue.add(0, new AbstractMap.SimpleEntry<>(((TableSwitchInsnNode)ain).dflt, ain));
            				List<LabelNode> switches = new ArrayList<>(((TableSwitchInsnNode)ain).labels);
            				Collections.reverse(switches);
            				for(LabelNode label : switches)
            					jumpQueue.add(0, new AbstractMap.SimpleEntry<>(label, ain));
            				break;
            			}else if(ain instanceof LookupSwitchInsnNode)
            			{
            				jumpQueue.add(0, new AbstractMap.SimpleEntry<>(((LookupSwitchInsnNode)ain).dflt, ain));
            				List<LabelNode> switches = new ArrayList<>(((LookupSwitchInsnNode)ain).labels);
            				Collections.reverse(switches);
            				for(LabelNode label : switches)
            					jumpQueue.add(0, new AbstractMap.SimpleEntry<>(label, ain));
            				break;
            			}else if(ain instanceof JumpInsnNode && ain.getOpcode() != Opcodes.GOTO)
            				jumpQueue.add(0, new AbstractMap.SimpleEntry<>(((JumpInsnNode)ain).label, ain));
            			else if((ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) 
        					|| ain.getOpcode() == Opcodes.ATHROW)
            				break;
            			else if(ain.getOpcode() == Opcodes.GOTO)
            			{
            				next = ((JumpInsnNode)ain).label;
            				lastGoto = ain;
            				break;
            			}
            		}
        		}
        		//Write exception handlers
        		boolean modified;
        		do
        		{
        			modified = false;
        			List<TryCatchBlockNode> inlined = new ArrayList<>();
        			for(int i = orderedBlocks.size() - 1; i >= 0; i--)
        			{
        				Block blockNow = orderedBlocks.get(i);
        				LabelNode now = blockNow.label;
        				if(!result.trycatchMap.containsKey(now))
        					continue;
        				List<TryCatchBlockNode> exceptions = new ArrayList<>(result.trycatchMap.get(now));
        				List<Block> addAfter = new ArrayList<>();
        				Collections.reverse(exceptions);
        				for(TryCatchBlockNode exception : exceptions)
        					if(!inlined.contains(exception))
        					{
        						inlined.add(exception);
        						Block handler = labelToBlock.get(exception.handler);
        						if(!orderedBlocks.contains(handler) && !addAfter.contains(handler))
        						{
        							modified = true;
        							List<Entry<LabelNode, AbstractInsnNode>> jumpQueue1 = new ArrayList<>();
        			        		AbstractInsnNode lastGoto1 = null;
        			        		LabelNode next1 = null;
        			        		
        			        		addAfter.add(handler);
        			        		for(AbstractInsnNode ain : handler.instructions)
        			        		{
        			        			if(ain instanceof TableSwitchInsnNode)
        			        			{
        			        				jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(((TableSwitchInsnNode)ain).dflt, ain));
        			        				List<LabelNode> switches = new ArrayList<>(((TableSwitchInsnNode)ain).labels);
        			        				Collections.reverse(switches);
        			        				for(LabelNode label : switches)
        			        					jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(label, ain));
        			        				break;
        			        			}else if(ain instanceof LookupSwitchInsnNode)
        			        			{
        			        				jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(((LookupSwitchInsnNode)ain).dflt, ain));
        			        				List<LabelNode> switches = new ArrayList<>(((LookupSwitchInsnNode)ain).labels);
        			        				Collections.reverse(switches);
        			        				for(LabelNode label : switches)
        			        					jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(label, ain));
        			        				break;
        			        			}else if(ain instanceof JumpInsnNode && ain.getOpcode() != Opcodes.GOTO)
        			        				jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(((JumpInsnNode)ain).label, ain));
        			        			else if((ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) 
        			    					|| ain.getOpcode() == Opcodes.ATHROW)
        			        				break;
        			        			else if(ain.getOpcode() == Opcodes.GOTO)
        			        			{
        			        				next1 = ((JumpInsnNode)ain).label;
        			        				lastGoto1 = ain;
        			        				break;
        			        			}
        			        		}
        			        		blocks.remove(handler);
        			        		Block lastPlaced1 = first;
        			        		while(next1 != null || !jumpQueue1.isEmpty())
        			        		{
        			        			boolean placeGoto = next1 != null;
        			        			//Determine exceptions
        			        			if(next1 != null)
        			        			{
        			        				//Place block if it's unreachable when all jump statements removed
        			        				Map<AbstractInsnNode, List<LabelNode>> switchBreaks = new HashMap<>();
        			        				List<AbstractInsnNode> breaks = new ArrayList<>();
        			        				LabelNode label = FlowAnalyzer.ABSENT;
        			    					for(AbstractInsnNode ain : method.instructions.toArray())
        			    						if(ain instanceof LabelNode)
        			    							label = (LabelNode)ain;
        			    						else if(orderedBlocks.contains(labelToBlock.get(label)))
        			    							if(ain instanceof JumpInsnNode && ((JumpInsnNode)ain).label == next1)
        			    								breaks.add(ain);
        			    							else if((ain instanceof TableSwitchInsnNode 
        												&& (((TableSwitchInsnNode)ain).labels.contains(next1)
        													|| ((TableSwitchInsnNode)ain).dflt == next1))
        			    								|| (ain instanceof LookupSwitchInsnNode 
        			    									&& (((LookupSwitchInsnNode)ain).labels.contains(next1)
        			    										|| ((LookupSwitchInsnNode)ain).dflt == next1)))
        			    								{
        			    									switchBreaks.putIfAbsent(ain, new ArrayList<>());
        			    									switchBreaks.get(ain).add(label);
        			    								}
        			    					breaks.add(next1);
        			    					boolean unreachable = true;
        			    					LinkedHashMap<LabelNode, List<AbstractInsnNode>> reached = 
        			    						analyzer.analyze(method.instructions.getFirst(), breaks, switchBreaks, true, false);
        			    					if(reached.containsKey(next1))
        			    						unreachable = false;
        			    					if(unreachable)
        			    						placeGoto = true;
        			        					
        			    					int jumpQueueIndex = -1;
        			    					for(int i1 = 0; i1 < jumpQueue.size(); i1++)
        			    					{
        			    						AbstractInsnNode cause = jumpQueue.get(i1).getValue();
        			    						if(cause.getOpcode() == Opcodes.GOTO)
        			    							continue;
        			    						jumpQueueIndex = i1;
        			    						break;
        			    					}
        			        				if(placeGoto && jumpQueueIndex >= 0 && jumpQueue1.get(jumpQueueIndex).getValue() instanceof JumpInsnNode)
        			        				{
        			        					//If jump site is a loop, we put the conditional block first
        			        					LinkedHashMap<LabelNode, List<AbstractInsnNode>> reached1 = 
        			        						analyzer.analyze(jumpQueue1.get(jumpQueueIndex).getKey(), Arrays.asList(lastGoto1, next1), 
        			        							new HashMap<>(), true, true);
        			        					if(!reached1.containsKey(next1) && reached1.get(lastPlaced1.label) != null
        			        						&& reached1.get(lastPlaced1.label).contains(lastGoto1))
        			        					{
        			        						LinkedHashMap<LabelNode, List<AbstractInsnNode>> res2 = 
        			        							analyzer.analyze(method.instructions.getFirst(), 
        			        							Arrays.asList(lastGoto1), new HashMap<>(), false, true);
        			        						boolean intersects = false;
        			        						for(LabelNode reach : reached1.keySet())
        			        							if(reach != lastPlaced1.label && res2.containsKey(reach))
        			        							{
        			        								intersects = true;
        			        								break;
        			        							}
        			        						if(!intersects)
        			        						{
        			        							placeGoto = false;
        			            						jumpQueue1.add(1, new AbstractMap.SimpleEntry<>(next1, lastGoto1));
        			        						}
        			        					}
        			        					//Check to see if jumpQueue intersects with jump
        			        					if(placeGoto && reached1.containsKey(next))
        			        					{
        			        						placeGoto = false;
        			        						jumpQueue1.add(jumpQueueIndex + 1, new AbstractMap.SimpleEntry<>(next1, lastGoto1));
        			        						jumpQueue1.add(0, jumpQueue1.remove(jumpQueueIndex));
        			        					}
        			        				}
        			        			}
        			        			
        			        			List<Entry<LabelNode, AbstractInsnNode>> placeAfter = new ArrayList<>();
        			        			while(!placeGoto && !jumpQueue1.isEmpty() && jumpQueue1.get(0).getValue() instanceof JumpInsnNode)
        			        			{
        			        				//Place jump if it's unreachable when all jump statements removed
        			        				AbstractInsnNode jump = jumpQueue1.get(0).getKey();
        			        				Map<AbstractInsnNode, List<LabelNode>> switchBreaks = new HashMap<>();
        			        				List<AbstractInsnNode> breaks = new ArrayList<>();
        			        				LabelNode label = FlowAnalyzer.ABSENT;
        			    					for(AbstractInsnNode ain : method.instructions.toArray())
        			    						if(ain instanceof LabelNode)
        			    							label = (LabelNode)ain;
        			    						else if(orderedBlocks.contains(labelToBlock.get(label)))
        			    							if(ain instanceof JumpInsnNode && ((JumpInsnNode)ain).label == jump)
        			    								breaks.add(ain);
        			    							else if((ain instanceof TableSwitchInsnNode 
        												&& (((TableSwitchInsnNode)ain).labels.contains(jump)
        													|| ((TableSwitchInsnNode)ain).dflt == jump))
        			    								|| (ain instanceof LookupSwitchInsnNode 
        			    									&& (((LookupSwitchInsnNode)ain).labels.contains(jump)
        			    										|| ((LookupSwitchInsnNode)ain).dflt == jump)))
        			    								{
        			    									switchBreaks.putIfAbsent(ain, new ArrayList<>());
        			    									switchBreaks.get(ain).add(label);
        			    								}
        			    					breaks.add(jump);
        			    					boolean unreachable = true;
        			    					LinkedHashMap<LabelNode, List<AbstractInsnNode>> reached = 
        			    						analyzer.analyze(method.instructions.getFirst(), breaks, switchBreaks, true, false);
        			    					if(reached.containsKey(jump))
        			    						unreachable = false;
        			    					if(!unreachable)
        			    						placeAfter.add(jumpQueue1.remove(0));
        			    					else
        			    						break;
        			        			}
        			        			if(!placeGoto && jumpQueue1.isEmpty())
        			        			{
        			        				if(next1 == null)
        		        					{
        		        						boolean shouldBreak = true;
        		        						for(Entry<LabelNode, AbstractInsnNode> entry : placeAfter)
        		        							if(orderedBlocks.contains(labelToBlock.get(entry.getKey())))
        		        							{
        		        								shouldBreak = false;
        		        								break;
        		        							}
        		        						if(shouldBreak)
        		        							break;
        		        					}
        			        				System.out.println("Unknown pattern at method " + method.name + method.desc + ", class " + classNode.name);
        			        				continue methodloop;
        			        			}
        			        			if(!placeGoto)
        			        				jumpQueue1.addAll(1, placeAfter);
        			        			Block block;
        			        			if(placeGoto)
        			        				block = labelToBlock.get(next1);
        			        			else
        			        				block = labelToBlock.get(jumpQueue1.get(0).getKey());
        			        			next1 = null;
        			        			jumpQueue1.removeIf((lbl) -> block.label == lbl.getKey());
        			        			if(orderedBlocks.contains(block) || addAfter.contains(block))
        			        				continue;
        			        			addAfter.add(block);
        			        			blocks.remove(block);
        			        			lastPlaced1 = block;
        			        			for(AbstractInsnNode ain : block.instructions)
        			            		{
        			            			if(ain instanceof TableSwitchInsnNode)
        			            			{
        			            				jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(((TableSwitchInsnNode)ain).dflt, ain));
        			            				List<LabelNode> switches = new ArrayList<>(((TableSwitchInsnNode)ain).labels);
        			            				Collections.reverse(switches);
        			            				for(LabelNode label : switches)
        			            					jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(label, ain));
        			            				break;
        			            			}else if(ain instanceof LookupSwitchInsnNode)
        			            			{
        			            				jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(((LookupSwitchInsnNode)ain).dflt, ain));
        			            				List<LabelNode> switches = new ArrayList<>(((LookupSwitchInsnNode)ain).labels);
        			            				Collections.reverse(switches);
        			            				for(LabelNode label : switches)
        			            					jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(label, ain));
        			            				break;
        			            			}else if(ain instanceof JumpInsnNode && ain.getOpcode() != Opcodes.GOTO)
        			            				jumpQueue1.add(0, new AbstractMap.SimpleEntry<>(((JumpInsnNode)ain).label, ain));
        			            			else if((ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN) 
        			        					|| ain.getOpcode() == Opcodes.ATHROW)
        			            				break;
        			            			else if(ain.getOpcode() == Opcodes.GOTO)
        			            			{
        			            				next1 = ((JumpInsnNode)ain).label;
        			            				lastGoto1 = ain;
        			            				break;
        			            			}
        			            		}
        			        		}
        						}
        					}
        				if(orderedBlocks.indexOf(blockNow) + 2 > orderedBlocks.size())
        					orderedBlocks.add(new Block(new LabelNode(), new ArrayList<>()));
        				orderedBlocks.addAll(orderedBlocks.indexOf(blockNow) + 2, addAfter);
        			}
        		}while(modified);
        		//Write
        		for(AbstractInsnNode ain : method.instructions.toArray())
        			method.instructions.remove(ain);
        		for(Block block : orderedBlocks)
        		{
        			LabelNode label = block.label;
        			if(label != FlowAnalyzer.ABSENT)
        				method.instructions.add(label);
        			for(AbstractInsnNode ain : block.instructions)
        				method.instructions.add(ain);
        		}
        		//Recalcuate exception blocks
        		LinkedHashMap<LabelNode, List<TryCatchBlockNode>> resNow = new LinkedHashMap<>();
        		for(Block b : orderedBlocks)
        			if(result.trycatchMap.containsKey(b.label))
        				resNow.put(b.label, result.trycatchMap.get(b.label));
        			else
        				resNow.put(b.label, new ArrayList<>());
        		List<TryCatchChain> chains = new ArrayList<>();
				Map<LabelNode, List<TryCatchBlockNode>> pass = new HashMap<>();
				List<LabelNode> labels = new ArrayList<>(resNow.keySet());
				for(Entry<LabelNode, List<TryCatchBlockNode>> entry : resNow.entrySet())
					for(TryCatchBlockNode tcbn : entry.getValue())
						if(!pass.containsKey(entry.getKey()) || !pass.get(entry.getKey()).contains(tcbn))
						{
							TryCatchChain chain = new TryCatchChain(tcbn.handler, tcbn.type,
								tcbn.visibleTypeAnnotations, tcbn.visibleTypeAnnotations);
							chains.add(chain);
							chain.covered.add(entry.getKey());
							if(labels.indexOf(entry.getKey()) + 1 >= labels.size())
							{
								LabelNode lbl = new LabelNode();
								labels.add(lbl);
    							method.instructions.add(lbl);
							}
							chain.end = labels.get(labels.indexOf(entry.getKey()) + 1);
							pass.putIfAbsent(entry.getKey(), new ArrayList<>());
							pass.get(entry.getKey()).add(tcbn);
							for(int i = labels.indexOf(entry.getKey()) + 1; i < labels.size(); i++)
							{
								List<TryCatchBlockNode> list = resNow.get(labels.get(i));
								boolean found = false;
								for(TryCatchBlockNode tcbn2 : list)
									if(tcbn.handler.equals(tcbn2.handler) 
	        							&& ((tcbn.type == null && tcbn2.type == null) || tcbn.type.equals(tcbn2.type))
	        							&& ((tcbn.visibleTypeAnnotations == null && tcbn2.visibleTypeAnnotations == null) 
	        								|| tcbn.visibleTypeAnnotations.equals(tcbn2.visibleTypeAnnotations))
	        							&& ((tcbn.invisibleTypeAnnotations == null && tcbn2.invisibleTypeAnnotations == null) 
	        								|| tcbn.invisibleTypeAnnotations.equals(tcbn2.invisibleTypeAnnotations)))
									{
										chain.covered.add(labels.get(i));
										if(i + 1 >= labels.size())
										{
											LabelNode lbl = new LabelNode();
											labels.add(lbl);
			    							method.instructions.add(lbl);
										}
										chain.end = labels.get(i + 1);
										pass.putIfAbsent(labels.get(i), new ArrayList<>());
										pass.get(labels.get(i)).add(tcbn2);
										found = true;
										break;
									}
								if(!found)
									break;
							}
						}
				Map<TryCatchChain, Set<LabelNode>> splits = new HashMap<>();
				for(int i = 0; i < chains.size(); i++)
					for(int i2 = i + 1; i2 < chains.size(); i2++)
					{
						TryCatchChain chain1 = chains.get(i);
						TryCatchChain chain2 = chains.get(i2);
						LabelNode start = labels.indexOf(chain1.covered.get(0)) > labels.indexOf(chain2.covered.get(0))
							? chain1.covered.get(0) : chain2.covered.get(0);
						LabelNode end = labels.indexOf(chain1.end) > labels.indexOf(chain2.end)
							? chain2.end : chain1.end;
						if(labels.indexOf(start) >= labels.indexOf(end))
							continue;
						int index1 = -1;
						for(int ii = 0; ii < resNow.get(start).size(); ii++)
						{
							TryCatchBlockNode tcbn = resNow.get(start).get(ii);
							if(tcbn.handler.equals(chain1.handler) 
    							&& ((tcbn.type == null && chain1.type == null) || tcbn.type.equals(chain1.type))
    							&& ((tcbn.visibleTypeAnnotations == null && chain1.visibleTypeAnnotations == null) 
    								|| tcbn.visibleTypeAnnotations.equals(chain1.visibleTypeAnnotations))
    							&& ((tcbn.invisibleTypeAnnotations == null && chain1.invisibleTypeAnnotations == null) 
    								|| tcbn.invisibleTypeAnnotations.equals(chain1.invisibleTypeAnnotations)))
							{
								index1 = ii;
								break;
							}
						}
						int index2 = -1;
						for(int ii = 0; ii < resNow.get(start).size(); ii++)
						{
							TryCatchBlockNode tcbn = resNow.get(start).get(ii);
							if(tcbn.handler.equals(chain2.handler) 
    							&& ((tcbn.type == null && chain2.type == null) || tcbn.type.equals(chain2.type))
    							&& ((tcbn.visibleTypeAnnotations == null && chain2.visibleTypeAnnotations == null) 
    								|| tcbn.visibleTypeAnnotations.equals(chain2.visibleTypeAnnotations))
    							&& ((tcbn.invisibleTypeAnnotations == null && chain2.invisibleTypeAnnotations == null) 
    								|| tcbn.invisibleTypeAnnotations.equals(chain2.invisibleTypeAnnotations)))
							{
								index2 = ii;
								break;
							}
						}
						boolean oneOnTop = index1 > index2;
						index1 = -1;
						index2 = -1;
						for(int ii = labels.indexOf(start); ii < labels.indexOf(end); ii++)
						{
							LabelNode now = labels.get(ii);
							for(int iii = 0; iii < resNow.get(now).size(); iii++)
							{
								TryCatchBlockNode tcbn = resNow.get(now).get(iii);
								if(tcbn.handler.equals(chain1.handler) 
	    							&& ((tcbn.type == null && chain1.type == null) || tcbn.type.equals(chain1.type))
	    							&& ((tcbn.visibleTypeAnnotations == null && chain1.visibleTypeAnnotations == null) 
	    								|| tcbn.visibleTypeAnnotations.equals(chain1.visibleTypeAnnotations))
	    							&& ((tcbn.invisibleTypeAnnotations == null && chain1.invisibleTypeAnnotations == null) 
	    								|| tcbn.invisibleTypeAnnotations.equals(chain1.invisibleTypeAnnotations)))
								{
									index1 = iii;
									break;
								}
							}
							for(int iii = 0; iii < resNow.get(now).size(); iii++)
							{
								TryCatchBlockNode tcbn = resNow.get(now).get(iii);
								if(tcbn.handler.equals(chain2.handler) 
	    							&& ((tcbn.type == null && chain2.type == null) || tcbn.type.equals(chain2.type))
	    							&& ((tcbn.visibleTypeAnnotations == null && chain2.visibleTypeAnnotations == null) 
	    								|| tcbn.visibleTypeAnnotations.equals(chain2.visibleTypeAnnotations))
	    							&& ((tcbn.invisibleTypeAnnotations == null && chain2.invisibleTypeAnnotations == null) 
	    								|| tcbn.invisibleTypeAnnotations.equals(chain2.invisibleTypeAnnotations)))
								{
									index2 = iii;
									break;
								}
							}
							boolean oneOnTopTemp = index1 > index2;
							if(oneOnTop != oneOnTopTemp)
							{
								splits.putIfAbsent(chain1, new HashSet<>());
								splits.get(chain1).add(now);
								oneOnTop = oneOnTopTemp;
							}
						}
					}
				if(splits.size() > 0)
					System.out.println("Irregular exception table at " + classNode.name + ", " + method.name + method.desc);
				for(Entry<TryCatchChain, Set<LabelNode>> entry : splits.entrySet())
				{
					List<LabelNode> orderedSplits = new ArrayList<>(entry.getValue());
					orderedSplits.sort(new Comparator<LabelNode>()
					{
						@Override
						public int compare(LabelNode l1, LabelNode l2)
						{
							return Integer.valueOf(labels.indexOf(l1)).compareTo(labels.indexOf(l2));
						}
					});
					List<TryCatchChain> replacements = new ArrayList<>();
					replacements.add(entry.getKey());
					for(LabelNode l : orderedSplits)
					{
						int lIndex = labels.indexOf(l);
						TryCatchChain toModify = null;
						for(TryCatchChain ch : replacements)
							if(labels.indexOf(ch.covered.get(0)) <= lIndex
								&& labels.indexOf(ch.covered.get(ch.covered.size() - 1)) >= lIndex)
							{
								toModify = ch;
								break;
							}
						TryCatchChain split1 = new TryCatchChain(toModify.handler,
							toModify.type, toModify.visibleTypeAnnotations,
							toModify.invisibleTypeAnnotations);
						for(LabelNode lbl : toModify.covered)
						{
							if(lbl == l)
								break;
							split1.covered.add(lbl);
						}
						split1.end = l;
						TryCatchChain split2 = new TryCatchChain(toModify.handler,
							toModify.type, toModify.visibleTypeAnnotations,
							toModify.invisibleTypeAnnotations);
						for(int iii = toModify.covered.indexOf(l); iii < toModify.covered.size(); iii++)
							split2.covered.add(toModify.covered.get(iii));
						split2.end = toModify.end;
						int toModifyIndex = replacements.indexOf(toModify);
						replacements.set(toModifyIndex, split2);
						replacements.add(toModifyIndex, split1);
					}
					int chainIndex = chains.indexOf(entry.getKey());
					chains.set(chainIndex, replacements.get(replacements.size() - 1));
					replacements.remove(replacements.size() - 1);
					chains.addAll(chainIndex, replacements);
				}
				List<TryCatchBlockNode> exceptions = new ArrayList<>();
				boolean modified1;
				do
				{
					modified1 = false;
					TryCatchChain remove = null;
					for(TryCatchChain chain : chains)
					{
						boolean failed = false;
						for(LabelNode lbl : chain.covered)
						{
							List<TryCatchBlockNode> list = resNow.get(lbl);
							if(!(!list.isEmpty() && list.get(0).handler.equals(chain.handler) 
        						&& ((list.get(0).type == null && chain.type == null) || list.get(0).type.equals(chain.type))
        						&& ((list.get(0).visibleTypeAnnotations == null && chain.visibleTypeAnnotations == null) 
        							|| list.get(0).visibleTypeAnnotations.equals(chain.visibleTypeAnnotations))
        						&& ((list.get(0).invisibleTypeAnnotations == null && chain.invisibleTypeAnnotations == null) 
        							|| list.get(0).invisibleTypeAnnotations.equals(chain.invisibleTypeAnnotations))))
							{
								failed = true;
								break;
							}
						}
						if(!failed)
						{
							TryCatchBlockNode tcbn = new TryCatchBlockNode(chain.covered.get(0), chain.end,
								chain.handler, chain.type);
							tcbn.visibleTypeAnnotations = chain.visibleTypeAnnotations;
							tcbn.invisibleTypeAnnotations = tcbn.invisibleTypeAnnotations;
							exceptions.add(tcbn);
							remove = chain;
							for(LabelNode lbl : chain.covered)
								resNow.get(lbl).remove(0);
							break;
						}
					}
					if(remove != null)
					{
						modified1 = true;
						chains.remove(remove);
					}
				}while(modified1);
				if(chains.size() > 0)
					throw new IllegalStateException("Impossible exception table at " + classNode.name + ", " + method.name + method.desc);
					
				boolean same = true;
        		if(method.tryCatchBlocks.size() != exceptions.size())
        			same = false;
        		if(same)
        			for(int i = 0; i < method.tryCatchBlocks.size(); i++)
        			{
        				TryCatchBlockNode tcbn1 = method.tryCatchBlocks.get(i);
        				TryCatchBlockNode tcbn2 = exceptions.get(i);
        				if(tcbn1.start != tcbn2.start)
        					same = false;
        				else if(tcbn1.end != tcbn2.end)
        					same = false;
        				else if(tcbn1.handler != tcbn2.handler)
        					same = false;
        				else if(!((tcbn1.type == null && tcbn2.type == null) || tcbn1.type.equals(tcbn2.type)))
        					same = false;
        				else if(!((tcbn1.invisibleTypeAnnotations == null && tcbn2.invisibleTypeAnnotations == null) 
        					|| tcbn1.invisibleTypeAnnotations.equals(tcbn2.invisibleTypeAnnotations)))
        					same = false;
        				else if(!((tcbn1.visibleTypeAnnotations == null && tcbn2.visibleTypeAnnotations == null) 
        					|| tcbn1.visibleTypeAnnotations.equals(tcbn2.visibleTypeAnnotations)))
        					same = false;
        				if(!same)
        					break;
        			}
        		if(!same)
        			counter.incrementAndGet();
        		method.tryCatchBlocks = exceptions;
        		FlowAnalyzer.Result resAfter = new FlowAnalyzer(method).analyze();
        		List<LabelNode> notFound = new ArrayList<>();
        		for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry
        			: result.labels.entrySet())
        			if(!flowAnalysis.containsKey(entry.getKey()))
        				notFound.add(entry.getKey());
        		for(LabelNode l : notFound)
        		{
        			result.labels.remove(l);
        			resAfter.labels.remove(l);
        		}
    			for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry : 
    				result.labels.entrySet())
    			{
    				List<Triple<LabelNode, JumpData, Integer>> toRemove = new ArrayList<>();
    				for(Triple<LabelNode, JumpData, Integer> tri : entry.getValue().getValue())
    					if(notFound.contains(tri.getLeft()))
    						toRemove.add(tri);
    				entry.getValue().getValue().removeAll(toRemove);
    			}
    			for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry : 
    				resAfter.labels.entrySet())
    			{
    				List<Triple<LabelNode, JumpData, Integer>> toRemove = new ArrayList<>();
    				for(Triple<LabelNode, JumpData, Integer> tri : entry.getValue().getValue())
    					if(notFound.contains(tri.getLeft()))
    						toRemove.add(tri);
    				entry.getValue().getValue().removeAll(toRemove);
    			}
    			int verify = 0;
    			for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry : 
    				result.labels.entrySet())
    			{
    				if(!resAfter.labels.containsKey(entry.getKey()))
    				{
    					verify = 1;
    					break;
    				}
    				List<Triple<LabelNode, JumpData, Integer>> resultList = entry.getValue().getValue();
    				List<Triple<LabelNode, JumpData, Integer>> resultAfterList = 
    					resAfter.labels.get(entry.getKey()).getValue();
    				List<LabelNode> one = new ArrayList<>();
    				List<LabelNode> two = new ArrayList<>();
    				for(Triple<LabelNode, JumpData, Integer> tri : resultList)
    					one.add(tri.getLeft());
    				for(Triple<LabelNode, JumpData, Integer> tri : resultAfterList)
    					two.add(tri.getLeft());
    				if(!new HashSet<>(one).equals(new HashSet<>(two)))
    				{
    					verify = 2;
    					break;
    				}
    			}
    			if(verify == 1)
    				System.out.println("Lost label while rearranging: " + classNode.name + " " + method.name + method.desc);
    			else if(verify == 2)
    				System.out.println("Lost flow while rearranging: " + classNode.name + " " + method.name + method.desc);
        	}
        System.out.println("Rearranged " + counter.get() + " methods");
		return counter.get() > 0;
    }
    
    private class Block
    {
    	public final LabelNode label;
    	public final List<AbstractInsnNode> instructions;
    	
    	public Block(LabelNode label, List<AbstractInsnNode> instructions)
    	{
    		this.label = label;
    		this.instructions = instructions;
    	}
    }
}
