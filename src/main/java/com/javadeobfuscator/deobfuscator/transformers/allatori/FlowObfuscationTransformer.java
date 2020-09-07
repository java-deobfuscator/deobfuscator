package com.javadeobfuscator.deobfuscator.transformers.allatori; 
 
import com.javadeobfuscator.deobfuscator.analyzer.ArgsAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Triple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*; 
 
public class FlowObfuscationTransformer extends Transformer<TransformerConfig>
{
	@Override 
    public boolean transform() throws Throwable {
    	DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new MappedFieldProvider());

        AtomicInteger fixed = new AtomicInteger();

        System.out.println("[Allatori] [FlowObfuscationTransformer] Starting");
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		for(int i = 0; i < method.instructions.size(); i++) 
        		{
        			AbstractInsnNode ain = method.instructions.get(i);
        			if(ain.getOpcode() == Opcodes.GOTO)
        			{
                        AbstractInsnNode a = Utils.getNext(ain);
                        AbstractInsnNode b = Utils.getNext(((JumpInsnNode) ain).label);
                        if(a == b)
                        	method.instructions.remove(ain);
                    }
        		}
        for(int run = 0; run < 2; run++)
        {
	        for(ClassNode classNode : classNodes())
	        	for(MethodNode method : classNode.methods)
	        	{
	        		boolean modified = false;
	        		do
	        		{
	        			modified = false;
	        			for(int i = 0; i < method.instructions.size(); i++) 
	            		{
	        				AbstractInsnNode ain = method.instructions.get(i);
	        				if(ain.getOpcode() == Opcodes.DUP && Utils.getNext(ain) != null
	        					&& (Utils.getNext(ain).getOpcode() == Opcodes.ISTORE
	        					|| Utils.getNext(ain).getOpcode() == Opcodes.ASTORE
	        					|| Utils.getNext(ain).getOpcode() == Opcodes.FSTORE))
	        				{
	        					if(ain.getNext() instanceof LabelNode)
	        					{
	        						boolean used = false;
		    						loop:
		    						for(AbstractInsnNode ain1 : method.instructions.toArray())
		    							if(ain1 instanceof JumpInsnNode && ain1 != ain
		    							&& ((JumpInsnNode)ain1).label == ain.getNext())
		    							{
		    								used = true;
		    								break loop;
		    							}else if(ain1 instanceof TableSwitchInsnNode 
		    								&& (((TableSwitchInsnNode)ain1).labels.contains(ain.getNext())
		    								|| ((TableSwitchInsnNode)ain1).dflt == ain.getNext()))
		    							{
		    								used = true;
		    								break loop;
		    							}else if(ain1 instanceof LookupSwitchInsnNode 
		    								&& (((LookupSwitchInsnNode)ain1).labels.contains(ain.getNext())
		    								|| ((LookupSwitchInsnNode)ain1).dflt == ain.getNext()))
		    							{
		    								used = true;
		    								break loop;
		    							}
	        						if(!used)
		    							loop:
		    							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
		    								if(tcbn.start == ain.getNext() || tcbn.end == ain.getNext() 
		    								|| tcbn.handler == ain.getNext())
		    								{
		    									used = true;
		    									break loop;
		    								}
	        						if(used)
	        							continue;
	        					}
	        					AbstractInsnNode label = null;
	        					boolean isGoto = false;
	        					AbstractInsnNode store = Utils.getNext(ain);
	        					AbstractInsnNode next2 = Utils.getNext(ain, 2);
	        					if(next2.getOpcode() == Opcodes.GOTO)
	        					{
	        						label = ((JumpInsnNode)next2).label;
	        						isGoto = true;
	        					}
	        					if(!isGoto)
	        					{
		        					AbstractInsnNode nxt = Utils.getNext(ain).getNext();
		        					while(!Utils.isInstruction(nxt) && !(nxt instanceof LabelNode))
		        						nxt = nxt.getNext();
		        					if(nxt instanceof LabelNode)
		        						label = nxt;
		        					else
		        						label = Utils.getNext(ain);
	        					}
	        					ArgsAnalyzer.Result argUsage = new ArgsAnalyzer(Utils.getNext(ain), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
	        					if(!(argUsage instanceof ArgsAnalyzer.FailedResult) && argUsage.getFirstArgInsn() instanceof JumpInsnNode)
	        					{
	        						boolean containsJump = false;
	        						AbstractInsnNode next = ain;
	        						while(next != argUsage.getFirstArgInsn())
	        						{
	        							if(next instanceof LabelNode)
	        							{
	        								for(AbstractInsnNode a : method.instructions.toArray())
	        									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == next)
	        									{
	        										containsJump = true;
	        										break;
	        									}
	        								if(containsJump)
	        									break;
	        							}
	        							next = next.getNext();
	        						}
	        						if(!containsJump)
	        						{
		        						method.instructions.set(ain, new VarInsnNode(store.getOpcode(), ((VarInsnNode)store).var));
		        						((VarInsnNode)store).setOpcode(store.getOpcode() - 33);
		        						modified = true;
		        						fixed.getAndIncrement();
	        							continue;
	        						}
	        					}
	        					boolean cannotFix = false;
	        					for(AbstractInsnNode a : method.instructions.toArray())
	        						if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == label && a.getOpcode() != Opcodes.GOTO)
	        						{
	        							cannotFix = true;
	        							break;
	        						}
	        					if(cannotFix)
	        					{
	        						method.instructions.set(ain, new VarInsnNode(store.getOpcode(), ((VarInsnNode)store).var));
	        						((VarInsnNode)store).setOpcode(store.getOpcode() - 33);
	        						modified = true;
	        						fixed.getAndIncrement();
	        						continue;
	        					}
	        					if(isGoto)
	    						{
	    							ArgsAnalyzer backwards = new ArgsAnalyzer(label.getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS);
									backwards.setIgnoreNeeded(true);
									backwards.setSpecialDup(true);
									ArgsAnalyzer.Result result = backwards.lookupArgs();
									if(!(result instanceof ArgsAnalyzer.FailedResult) && result.getDiff() == -1)
									{
										AbstractInsnNode res = result.getFirstArgInsn();
										if(res.getOpcode() >= Opcodes.DUP && res.getOpcode() <= Opcodes.DUP_X2)
											method.instructions.remove(res);
										else if(res.getOpcode() == store.getOpcode() - 33)
											method.instructions.remove(res);
										else
											continue;
									}
	    						}
	        					for(AbstractInsnNode a : method.instructions.toArray())
	        						if(a.getOpcode() == Opcodes.GOTO && a != next2 && ((JumpInsnNode)a).label == label)
	        						{
	        							ArgsAnalyzer backwards = new ArgsAnalyzer(a.getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS);
	        							backwards.setIgnoreNeeded(true);
	        							backwards.setSpecialDup(true);
	        							ArgsAnalyzer.Result result = backwards.lookupArgs();
	        							if(result.getDiff() == -1)
	        							{
	    									AbstractInsnNode res = result.getFirstArgInsn();
	    									if(res.getOpcode() >= Opcodes.DUP && res.getOpcode() <= Opcodes.DUP_X2)
	    										method.instructions.remove(res);
	    									else if(res.getOpcode() == store.getOpcode() - 33)
	    										method.instructions.remove(res);
	    									else
	    										throw new RuntimeException("Unexpected opcode: " + res.getOpcode());
	    								}
	    							}
	        					method.instructions.insert(label, new VarInsnNode(
	        						store.getOpcode() - 33, ((VarInsnNode)store).var));
	        					method.instructions.remove(ain);
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(ain.getOpcode() == Opcodes.DUP2 && Utils.getNext(ain) != null
	        					&& (Utils.getNext(ain).getOpcode() == Opcodes.DSTORE
	        					|| Utils.getNext(ain).getOpcode() == Opcodes.LSTORE))
	        				{
	        					if(ain.getNext() instanceof LabelNode)
	        					{
	        						boolean used = false;
		    						loop:
		    						for(AbstractInsnNode ain1 : method.instructions.toArray())
		    							if(ain1 instanceof JumpInsnNode && ain1 != ain
		    							&& ((JumpInsnNode)ain1).label == ain.getNext())
		    							{
		    								used = true;
		    								break loop;
		    							}else if(ain1 instanceof TableSwitchInsnNode 
		    								&& (((TableSwitchInsnNode)ain1).labels.contains(ain.getNext())
		    								|| ((TableSwitchInsnNode)ain1).dflt == ain.getNext()))
		    							{
		    								used = true;
		    								break loop;
		    							}else if(ain1 instanceof LookupSwitchInsnNode 
		    								&& (((LookupSwitchInsnNode)ain1).labels.contains(ain.getNext())
		    								|| ((LookupSwitchInsnNode)ain1).dflt == ain.getNext()))
		    							{
		    								used = true;
		    								break loop;
		    							}
	        						if(!used)
		    							loop:
		    							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
		    								if(tcbn.start == ain.getNext() || tcbn.end == ain.getNext() 
		    								|| tcbn.handler == ain.getNext())
		    								{
		    									used = true;
		    									break loop;
		    								}
	        						if(used)
	        							continue;
	        					}
	        					AbstractInsnNode label = null;
	        					boolean isGoto = false;
	        					AbstractInsnNode store = Utils.getNext(ain);
	        					AbstractInsnNode next2 = Utils.getNext(ain, 2);
	        					if(next2.getOpcode() == Opcodes.GOTO)
	        					{
	        						label = ((JumpInsnNode)next2).label;
	        						isGoto = true;
	        					}
	        					if(!isGoto)
	        					{
		        					AbstractInsnNode nxt = Utils.getNext(ain).getNext();
		        					while(!Utils.isInstruction(nxt) && !(nxt instanceof LabelNode))
		        						nxt = nxt.getNext();
		        					if(nxt instanceof LabelNode)
		        						label = nxt;
		        					else
		        						label = Utils.getNext(ain);
	        					}
	        					ArgsAnalyzer.Result argUsage = new ArgsAnalyzer(Utils.getNext(ain), 4, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
	        					if(!(argUsage instanceof ArgsAnalyzer.FailedResult) && argUsage.getFirstArgInsn() instanceof JumpInsnNode)
	        					{
	        						boolean containsJump = false;
	        						AbstractInsnNode next = ain;
	        						while(next != argUsage.getFirstArgInsn())
	        						{
	        							if(next instanceof LabelNode)
	        							{
	        								for(AbstractInsnNode a : method.instructions.toArray())
	        									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == next)
	        									{
	        										containsJump = true;
	        										break;
	        									}
	        								if(containsJump)
	        									break;
	        							}
	        							next = next.getNext();
	        						}
	        						if(!containsJump)
	        						{
		        						method.instructions.set(ain, new VarInsnNode(store.getOpcode(), ((VarInsnNode)store).var));
		        						((VarInsnNode)store).setOpcode(store.getOpcode() - 33);
		        						modified = true;
		        						fixed.getAndIncrement();
	        							continue;
	        						}
	        					}
	        					boolean cannotFix = false;
	        					for(AbstractInsnNode a : method.instructions.toArray())
	        						if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == label && a.getOpcode() != Opcodes.GOTO)
	        						{
	        							cannotFix = true;
	        							break;
	        						}
	        					if(cannotFix)
	        					{
	        						method.instructions.set(ain, new VarInsnNode(store.getOpcode(), ((VarInsnNode)store).var));
	        						((VarInsnNode)store).setOpcode(store.getOpcode() - 33);
	        						modified = true;
	        						fixed.getAndIncrement();
	        						continue;
	        					}
	        					if(isGoto)
	        					{
	        						ArgsAnalyzer backwards = new ArgsAnalyzer(label.getPrevious(), 2, ArgsAnalyzer.Mode.BACKWARDS);
	        						backwards.setIgnoreNeeded(true);
	        						backwards.setSpecialDup(true);
	        						ArgsAnalyzer.Result result = backwards.lookupArgs();
	        						if(!(result instanceof ArgsAnalyzer.FailedResult) && result.getDiff() == -2)
	        						{
	        							AbstractInsnNode res = result.getFirstArgInsn();
	        							if(res.getOpcode() >= Opcodes.DUP2 && res.getOpcode() <= Opcodes.DUP2_X2)
	        								method.instructions.remove(res);
	        							else if(res.getOpcode() == store.getOpcode() - 33)
	        								method.instructions.remove(res);
	        							else
	        								throw new RuntimeException("Unexpected opcode: " + res.getOpcode());
	        						}
	        					}
	        					for(AbstractInsnNode a : method.instructions.toArray())
	        						if(a.getOpcode() == Opcodes.GOTO && a != next2 && ((JumpInsnNode)a).label == label)
	        						{
	        							ArgsAnalyzer backwards = new ArgsAnalyzer(a.getPrevious(), 2, ArgsAnalyzer.Mode.BACKWARDS);
	        							backwards.setIgnoreNeeded(true);
	        							backwards.setSpecialDup(true);
	        							ArgsAnalyzer.Result result = backwards.lookupArgs();
	        							if(result.getDiff() == -2)
	        							{
	        								AbstractInsnNode res = result.getFirstArgInsn();
	        								if(res.getOpcode() >= Opcodes.DUP2 && res.getOpcode() <= Opcodes.DUP2_X2)
	            								method.instructions.remove(res);
	            							else if(res.getOpcode() == store.getOpcode() - 33)
	            								method.instructions.remove(res);
	            							else
	            								throw new RuntimeException("Unexpected opcode: " + res.getOpcode());
	        							}
	        						}
	        					method.instructions.insert(label, new VarInsnNode(
	        						store.getOpcode() - 33, ((VarInsnNode)store).var));
	        					method.instructions.remove(ain);
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if((ain.getOpcode() == Opcodes.GOTO || 
	        					(ain.getOpcode() >= Opcodes.IRETURN && ain.getOpcode() <= Opcodes.RETURN))
	        					&& ain.getNext() != null)
	        				{
	        					while(!(ain.getNext() instanceof LabelNode) && ain.getNext() != null)
	        						method.instructions.remove(ain.getNext());
	        				}else if(Utils.isInteger(ain) && ain.getNext() != null
	        					&& ain.getNext().getOpcode() >= Opcodes.IFEQ && ain.getNext().getOpcode() <= Opcodes.IFLE)
	        				{
	        					int value = Utils.getIntValue(ain);
	        					boolean res;
	        					if(ain.getNext().getOpcode() == Opcodes.IFGE)
	                                res = value >= 0;
	                            else if(ain.getNext().getOpcode() == Opcodes.IFGT)
	                                res = value > 0;
	                            else if(ain.getNext().getOpcode() == Opcodes.IFLE)
	                                res = value <= 0;
	                            else if(ain.getNext().getOpcode() == Opcodes.IFLT)
	                                res = value < 0;
	                            else if(ain.getNext().getOpcode() == Opcodes.IFNE)
	                                res = value != 0;
	                            else if(ain.getNext().getOpcode() == Opcodes.IFEQ)
	                                res = value == 0;
	                            else
	                            	throw new RuntimeException();
	        					if(res)
	        					{
	        						method.instructions.set(ain.getNext(),  new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode)ain.getNext()).label));
	        						method.instructions.remove(ain);
	        					}else
	        					{
	        						LabelNode label = ((JumpInsnNode)ain.getNext()).label;
	        						boolean used = false;
	        						if(Utils.getPrevious(label) == null 
	        							|| (Utils.getPrevious(label).getOpcode() != Opcodes.GOTO
	        							&& !(Utils.getPrevious(label).getOpcode() >= Opcodes.IRETURN 
	        							&& Utils.getPrevious(label).getOpcode() <= Opcodes.RETURN)
	        							&& Utils.getPrevious(label).getOpcode() != Opcodes.TABLESWITCH 
	        							&& Utils.getPrevious(label).getOpcode() != Opcodes.LOOKUPSWITCH
	        							&& Utils.getPrevious(label).getOpcode() != Opcodes.ATHROW))
	        							used = true;
	        						for(AbstractInsnNode a : method.instructions.toArray())
	        							if(a != ain.getNext() && a instanceof JumpInsnNode && ((JumpInsnNode)a).label == label)
	        							{
	        								used = true;
	        								break;
	        							}
	        						if(!used)
	        						{
	        							while(!(label.getNext() instanceof LabelNode))
	        								method.instructions.remove(label.getNext());
	        							boolean labelUsed = false;
        					        	for(AbstractInsnNode a : method.instructions.toArray())
	    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == label)
	    									{
	    										labelUsed = true;
	    										break;
	    									}else if(a instanceof TableSwitchInsnNode)
	    									{
	    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
	    											if(l == label)
	    											{
	    												labelUsed = true;
	    												break;
	    											}
	    										if(((TableSwitchInsnNode)a).dflt == label)
	    											labelUsed = true;
	    										if(labelUsed)
	    											break;
	    									}else if(a instanceof LookupSwitchInsnNode)
	    									{
	    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
	    											if(l == label)
	    											{
	    												labelUsed = true;
	    												break;
	    											}
	    										if(((LookupSwitchInsnNode)a).dflt == label)
	    											labelUsed = true;
	    										if(labelUsed)
	    											break;
	    									}
	    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
	    									if(trycatch.start == label || trycatch.end == label || trycatch.handler == label)
	    									{
	    										labelUsed = true;
	    										break;
	    									}
	    								if(!labelUsed)
	    									method.instructions.remove(label);
	        						}
        							AbstractInsnNode prev = ain.getPrevious();
        					        while(!Utils.isInstruction(prev) && !(prev instanceof LabelNode))
        					            prev = prev.getPrevious();
        					        if(prev instanceof LabelNode)
        					        {
        					        	boolean labelUsed = false;
        					        	for(AbstractInsnNode a : method.instructions.toArray())
	    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == prev)
	    									{
	    										labelUsed = true;
	    										break;
	    									}else if(a instanceof TableSwitchInsnNode)
	    									{
	    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
	    											if(l == prev)
	    											{
	    												labelUsed = true;
	    												break;
	    											}
	    										if(((TableSwitchInsnNode)a).dflt == prev)
	    											labelUsed = true;
	    										if(labelUsed)
	    											break;
	    									}else if(a instanceof LookupSwitchInsnNode)
	    									{
	    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
	    											if(l == prev)
	    											{
	    												labelUsed = true;
	    												break;
	    											}
	    										if(((LookupSwitchInsnNode)a).dflt == prev)
	    											labelUsed = true;
	    										if(labelUsed)
	    											break;
	    									}
	    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
	    									if(trycatch.start == prev || trycatch.end == prev || trycatch.handler == prev)
	    									{
	    										labelUsed = true;
	    										break;
	    									}
	    								if(!labelUsed)
	    									method.instructions.remove(prev);
        					        }
	        						method.instructions.remove(ain.getNext());
	        						method.instructions.remove(ain);
	        					}
	        					modified = true;
	        				}else if(getPossibleSwap(ain, 0) != null)
	        				{
	        					List<AbstractInsnNode> instrs = getPossibleSwap(ain, 0);
	        					method.instructions.remove(instrs.get(2));
	        					method.instructions.remove(instrs.get(0));
	        					method.instructions.insert(instrs.get(1), instrs.get(0));
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(getPossibleSwap(ain, 1) != null)
	        				{
	        					List<AbstractInsnNode> instrs = getPossibleSwap(ain, 1);
	        					if(instrs.get(1).getOpcode() == Opcodes.DUP)
	        						method.instructions.set(instrs.get(1), instrs.get(0).clone(null));
	        					method.instructions.remove(instrs.get(3));
	        					method.instructions.remove(instrs.get(0));
	        					method.instructions.insert(instrs.get(2), instrs.get(0));
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(ain.getOpcode() == Opcodes.SWAP && ain.getNext() != null
	        					&& ((ain.getNext().getOpcode() >= Opcodes.IFEQ
	        					&& ain.getNext().getOpcode() <= Opcodes.IFLE)
	        						|| ain.getNext().getOpcode() == Opcodes.IFNULL
	        						|| ain.getNext().getOpcode() == Opcodes.IFNONNULL))
	        				{
	        					LabelNode label = ((JumpInsnNode)ain.getNext()).label;
	        					for(AbstractInsnNode ain1 : method.instructions.toArray())
	        					{
	        						if(ain1 instanceof JumpInsnNode && ain1 != ain.getNext()
	        							&& ((JumpInsnNode)ain1).label == label)
	        						{
	        							if(ain1.getPrevious().getOpcode() == Opcodes.SWAP)
	        							{
	        								method.instructions.remove(ain1.getPrevious().getPrevious());
	        								method.instructions.remove(ain1.getPrevious());
	        								continue;
	        							}
	        							ArgsAnalyzer.Result analysis = new ArgsAnalyzer(
	        	    						ain1.getPrevious(), ain1.getOpcode() == Opcodes.GOTO || ain1.getOpcode() == Opcodes.JSR ? 1 
	        	    							: (ain.getNext().getOpcode() >= Opcodes.IFEQ
	        	    	        					&& ain.getNext().getOpcode() <= Opcodes.IFLE)
	        	        						|| ain.getNext().getOpcode() == Opcodes.IFNULL
	        	        						|| ain.getNext().getOpcode() == Opcodes.IFNONNULL ? 2 : 3, 
	        	    	    						ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP).lookupArgs();
	    	        					if(analysis.getFirstArgInsn().getNext() != null && analysis.getFirstArgInsn().getNext().getOpcode() == Opcodes.DUP)
	    	        						method.instructions.set(analysis.getFirstArgInsn().getNext(), analysis.getFirstArgInsn().clone(null));
	        							method.instructions.remove(analysis.getFirstArgInsn());
	        						}
	        					}
	        					ArgsAnalyzer.Result analysis = new ArgsAnalyzer(
    	    						ain.getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP).lookupArgs();
								method.instructions.insert(label, analysis.getFirstArgInsn().clone(null));
								method.instructions.insertBefore(Utils.getNext(ain.getNext()), analysis.getFirstArgInsn().clone(null));
	        					method.instructions.remove(analysis.getFirstArgInsn());
								method.instructions.remove(ain);
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(getPossibleDupPop(ain) != null)
	        				{
	        					List<AbstractInsnNode> instrs = getPossibleDupPop(ain);
	        					method.instructions.remove(instrs.get(2));
	        					method.instructions.remove(instrs.get(1));
	        					method.instructions.remove(instrs.get(0));
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(willPush(ain) && ain.getNext() != null && willPush(ain.getNext())
	        					&& ain.getNext().getNext() != null
	        					&& ain.getNext().getNext().getOpcode() == Opcodes.DUP2)
	        				{
	        					method.instructions.set(ain.getNext().getNext(), new InsnNode(Opcodes.DUP_X1));
	        					method.instructions.insert(ain, new InsnNode(Opcodes.DUP));
	        					modified = true;
	        				}else if(ain.getOpcode() == Opcodes.DUP
	        					&& willPush(Utils.getPrevious(ain)) && Utils.getPrevious(ain).getOpcode() != Opcodes.NEW)
	        				{
	        					method.instructions.set(ain, Utils.getPrevious(ain).clone(null));
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(ain.getOpcode() == Opcodes.DUP2
	        					&& willPush2(Utils.getPrevious(ain)))
	        				{
	        					method.instructions.set(ain, Utils.getPrevious(ain).clone(null));
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if(ain.getOpcode() == Opcodes.DUP2_X2 && willPush2(Utils.getPrevious(ain)))
	        				{
	        					ArgsAnalyzer.Result res = new ArgsAnalyzer(ain.getPrevious(), 4, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
	        					method.instructions.insertBefore(res.getFirstArgInsn(), Utils.getPrevious(ain).clone(null));
	        					method.instructions.remove(ain);
	        					modified = true;
	        					fixed.incrementAndGet();
	        				}else if((ain.getOpcode() == Opcodes.DUP_X1 || ain.getOpcode() == Opcodes.DUP_X2)
	        					&& willPush(Utils.getPrevious(ain)) && Utils.getPrevious(ain).getOpcode() != Opcodes.NEW)
	        					if(fixDup(method, ain))
	        					{
	        						modified = true;
	        						fixed.incrementAndGet();
	        					}
	            		}
	        		}while(modified);
	        	}
	        for(ClassNode classNode : classNodes())
	        	for(MethodNode method : classNode.methods)
	        	{
	        		boolean modified = false;
	        		do
	        		{
	        			modified = false;
	        			for(int i = 0; i < method.instructions.size();) 
	            		{
	        				AbstractInsnNode ain = method.instructions.get(i);
	        				if(willPush(ain))
	            			{
	            				ArgsAnalyzer.Result result = new ArgsAnalyzer(ain.getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
	            				if(!(result instanceof ArgsAnalyzer.FailedResult) && result.getDiff() != -1 && result.getSwap() != null)
	            				{
	            					i++;
	            					continue;
	            				}
	            				if(result instanceof ArgsAnalyzer.FailedResult && ((ArgsAnalyzer.FailedResult)result).getFailedPoint().getOpcode() == Opcodes.GOTO
	            					&& ((ArgsAnalyzer.FailedResult)result).getSkippedDupsAtFailure().size() <= 0)
	            				{
	            					AbstractInsnNode jump = ((ArgsAnalyzer.FailedResult)result).getFailedPoint();
									LabelNode label = ((JumpInsnNode)jump).label;
									if(method.name.equals("<clinit>"))
									{
										if(Utils.isInteger(ain) && Utils.getIntValue(ain) == -1 && Utils.getNext(label) != null && Utils.getNext(label).getOpcode() == Opcodes.SWAP
											&& Utils.getNext(label, 2) != null && Utils.getNext(label, 2).getOpcode() == Opcodes.INVOKEVIRTUAL
											&& ((MethodInsnNode)Utils.getNext(label, 2)).owner.equals("java/lang/String")
											&& ((MethodInsnNode)Utils.getNext(label, 2)).name.equals("toCharArray"))
										{
			            					i++;
			            					continue;
			            				}
										if(ain.getOpcode() == Opcodes.ACONST_NULL && Utils.getNext(label) != null && Utils.getNext(label).getOpcode() == Opcodes.ASTORE
											&& Utils.getNext(label, 2) != null && Utils.getNext(label, 2).getOpcode() == Opcodes.INVOKEVIRTUAL
											&& ((((MethodInsnNode)Utils.getNext(label, 2)).owner.equals("java/lang/String")
											&& ((MethodInsnNode)Utils.getNext(label, 2)).name.equals("toCharArray")
											&& Utils.getNext(label, 3) != null && Utils.getNext(label, 3).getOpcode() == Opcodes.DUP_X1)
											|| (Utils.getNext(label, 2) != null && Utils.getNext(label, 2).getOpcode() == Opcodes.INVOKESTATIC
											&& ((MethodInsnNode)Utils.getNext(label, 2)).desc.equals("(Ljava/lang/String;)[C"))))
										{
			            					i++;
			            					continue;
			            				}
										if(Utils.isInteger(ain) && Utils.getNext(label) != null && Utils.getNext(label).getOpcode() == Opcodes.ASTORE
											&& Utils.getNext(label, 2) != null && Utils.getNext(label, 2).getOpcode() == Opcodes.INVOKEVIRTUAL
											&& ((((MethodInsnNode)Utils.getNext(label, 2)).owner.equals("java/lang/String")
											&& ((MethodInsnNode)Utils.getNext(label, 2)).name.equals("toCharArray")
											&& Utils.getNext(label, 3) != null && Utils.getNext(label, 3).getOpcode() == Opcodes.DUP)
											|| (Utils.getNext(label, 2) != null && Utils.getNext(label, 2).getOpcode() == Opcodes.INVOKESTATIC
											&& ((MethodInsnNode)Utils.getNext(label, 2)).desc.equals("(Ljava/lang/String;)[C"))))
										{
			            					i++;
			            					continue;
			            				}
									}
	            					boolean failed = false;
	            					for(AbstractInsnNode ain1 : method.instructions.toArray())
	            						if(ain1 instanceof JumpInsnNode && ain1 != jump && ((JumpInsnNode)ain1).label == label)
	            						{
	            							if(ain1.getOpcode() != Opcodes.GOTO)
	            							{
	            								failed = true;
	            								break;
	            							}
	            							ArgsAnalyzer.Result res = new ArgsAnalyzer(ain1.getPrevious(), ((ArgsAnalyzer.FailedResult)result).getExtraArgs(), 
	            								ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
	            							if(res instanceof ArgsAnalyzer.FailedResult || res.getFirstArgInsn() == null)
	            							{
	            								failed = true;
	            								break;
	            							}else if(!Utils.prettyprint(ain).equals(Utils.prettyprint(res.getFirstArgInsn())))
	            							{
	            								failed = true;
	            								break;
	            							}else
	            							{
	            								ArgsAnalyzer.Result forward = new ArgsAnalyzer(res.getFirstArgInsn().getNext(), ((ArgsAnalyzer.FailedResult)result).getExtraArgs(), 
	            									ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
	            								if(!(forward instanceof ArgsAnalyzer.FailedResult) || ((ArgsAnalyzer.FailedResult)forward).getFailedPoint() != ain1)
	            								{
	            									failed = true;
	            									break;
	            								}
	            							}
	            						}
	            					for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
	            						if(trycatch.start == label || trycatch.end == label || trycatch.handler == label)
	            						{
	            							failed = true;
	            							break;
	            						}
	    							if(!failed)
	    							{
	    								ArgsAnalyzer.Result backwards = new ArgsAnalyzer(label, ((ArgsAnalyzer.FailedResult)result).getExtraArgs(), 
	    									ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
	    								boolean failed2 = false;
	    								if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null)
	        							{
	        								if(!Utils.prettyprint(ain).equals(Utils.prettyprint(backwards.getFirstArgInsn())))
	            								failed2 = true;
	        								else
	            							{
	            								ArgsAnalyzer analyzer = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS);
	            								analyzer.setBreakpoint(label);
	            								ArgsAnalyzer.Result forward = analyzer.lookupArgs();
	            								if(forward.getArgsNeeded() != -((ArgsAnalyzer.FailedResult)result).getExtraArgs())
	            									failed2 = true;
	            							}
	        							}else if(backwards instanceof ArgsAnalyzer.FailedResult 
	        								&& ((ArgsAnalyzer.FailedResult)backwards).getFailedPoint() != Utils.getPrevious(label))
	        								failed2 = true;
	    								if(failed2)
	    								{
	    									i++;
	    									continue;
	    								}
	    								ArgsAnalyzer insertAnalyzer = new ArgsAnalyzer(((JumpInsnNode)jump).label, 
	    									((ArgsAnalyzer.FailedResult)result).getExtraArgs() - 1, ArgsAnalyzer.Mode.FORWARDS);
	    								insertAnalyzer.setOnlyZero(true);
	    								ArgsAnalyzer.Result insert = insertAnalyzer.lookupArgs();
	    								if(insert instanceof ArgsAnalyzer.FailedResult || insert.getFirstArgInsn() == null
	    									|| insert.getArgsNeeded() > 0 || insert.getSkippedDups().size() > 0)
	    								{
	    									i++;
	    									continue;
	    								}
	    								boolean pushes = false;
	    								if(insert.getFirstArgInsn().getOpcode() >= Opcodes.IALOAD && insert.getFirstArgInsn().getOpcode() <= Opcodes.SALOAD)
	    									pushes = true;
	    								else if(insert.getFirstArgInsn().getOpcode() >= Opcodes.DUP && insert.getFirstArgInsn().getOpcode() <= Opcodes.DUP2_X2)
	    									pushes = true;
	    								else if(insert.getFirstArgInsn().getOpcode() >= Opcodes.IADD && insert.getFirstArgInsn().getOpcode() <= Opcodes.LXOR)
	    									pushes = true;
	    								else if(insert.getFirstArgInsn().getOpcode() >= Opcodes.I2L && insert.getFirstArgInsn().getOpcode() <= Opcodes.DCMPG)
	    									pushes = true;
	    								else if(insert.getFirstArgInsn().getOpcode() == Opcodes.GETFIELD)
	    									pushes = true;
	    								else if(insert.getFirstArgInsn() instanceof MethodInsnNode 
	    									&& Type.getReturnType(((MethodInsnNode)insert.getFirstArgInsn()).desc).getSort() != Type.VOID
	    									&& Type.getReturnType(((MethodInsnNode)insert.getFirstArgInsn()).desc).getSort() != Type.METHOD)
	    									pushes = true;
	    								else if(insert.getFirstArgInsn().getOpcode() == Opcodes.MULTIANEWARRAY
	    									|| insert.getFirstArgInsn().getOpcode() == Opcodes.INSTANCEOF
	    									|| insert.getFirstArgInsn().getOpcode() == Opcodes.CHECKCAST
	    									|| insert.getFirstArgInsn().getOpcode() == Opcodes.NEWARRAY
	    									|| insert.getFirstArgInsn().getOpcode() == Opcodes.ANEWARRAY
	    									|| insert.getFirstArgInsn().getOpcode() == Opcodes.ARRAYLENGTH)
	    									pushes = true;
	    								if(pushes)
	    								{
	    									i++;
	    									continue;
	    								}
	    								AbstractInsnNode prev = Utils.getPrevious(insert.getFirstArgInsn());
										boolean hasJumpBetween = false;
										insn:
	    								while(prev != insert.getFirstArgInsn())
	    								{
	    									if(prev instanceof LabelNode)
	    									{
	    										for(AbstractInsnNode a : method.instructions.toArray())
	    	    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == prev)
	    	    									{
	    	    										hasJumpBetween = true;
	    	    										break;
	    	    									}else if(a instanceof TableSwitchInsnNode)
	    	    									{
	    	    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
	    	    											if(l == prev)
	    	    											{
	    	    												hasJumpBetween = true;
	    	    												break insn;
	    	    											}
	    	    										if(((TableSwitchInsnNode)a).dflt == prev)
	    	    										{
	    	    											hasJumpBetween = true;
	    	    											break insn;
	    	    										}
	    	    									}else if(a instanceof LookupSwitchInsnNode)
	    	    									{
	    	    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
	    	    											if(l == prev)
	    	    											{
		    	    											hasJumpBetween = true;
		    	    											break insn;
		    	    										}
	    	    										if(((LookupSwitchInsnNode)a).dflt == prev)
	    	    										{
	    	    											hasJumpBetween = true;
	    	    											break insn;
	    	    										}
	    	    									}
	    	    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
	    	    									if(trycatch.start == prev || trycatch.end == prev || trycatch.handler == prev)
	    	    									{
    	    											hasJumpBetween = true;
    	    											break insn;
    	    										}
	    									}
	    									prev = prev.getNext();
	    								}
										if(hasJumpBetween)
										{
											i++;
											continue;
										}
	    								if(insert.getSwap() != null && ((ArgsAnalyzer.FailedResult)result).getExtraArgs() == 2)
	    								{
	    									method.instructions.remove(ain);
		        							method.instructions.insert(((JumpInsnNode)jump).label, ain);
	    									modified = true;
	    									i++;
	    									continue;
	    								}
	    								if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null)
	    									method.instructions.remove(backwards.getFirstArgInsn());
	    								for(AbstractInsnNode ain1 : method.instructions.toArray())
	                						if(ain1 instanceof JumpInsnNode && ain1 != jump && ((JumpInsnNode)ain1).label == label)
	                						{
	                							ArgsAnalyzer.Result res = new ArgsAnalyzer(ain1.getPrevious(), ((ArgsAnalyzer.FailedResult)result).getExtraArgs(), 
	                								ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
	                							method.instructions.remove(res.getFirstArgInsn());
	                						}
	        							method.instructions.remove(ain);
	        							method.instructions.insert(insert.getFirstArgInsn(), ain);
	        							modified = true;
	    							}
	            				}else if(!(result instanceof ArgsAnalyzer.FailedResult))
	            					inlineArgs(method, ain, result);
	            			}else if(willPush2(ain))
	            			{
	            				ArgsAnalyzer.Result result = new ArgsAnalyzer(ain.getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
	            				if(!(result instanceof ArgsAnalyzer.FailedResult))
	            					inlineArgs2(method, ain, result);
	            			}
	        				if(method.instructions.get(i) == ain || !willPush(method.instructions.get(i)))
	        					i++;
	            		}
	        		}while(modified);
	        	}
        }
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		for(int i = 0; i < method.instructions.size(); i++) 
        		{
        			AbstractInsnNode ain = method.instructions.get(i);
        			if(ain.getOpcode() == Opcodes.ALOAD && Utils.getNext(ain) != null
        				&& Utils.getNext(ain).getOpcode() == Opcodes.ALOAD
        				&& ((VarInsnNode)ain).var == ((VarInsnNode)Utils.getNext(ain)).var)
        			{
        				ArgsAnalyzer.Result top = new ArgsAnalyzer(ain.getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        				ArgsAnalyzer.Result bottom = new ArgsAnalyzer(Utils.getNext(ain).getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        				if(!(top instanceof ArgsAnalyzer.FailedResult) && !(bottom instanceof ArgsAnalyzer.FailedResult)
        					&& top.getFirstArgInsn().getOpcode() == Opcodes.PUTFIELD
        					&& bottom.getFirstArgInsn().getOpcode() == Opcodes.GETFIELD)
        				{
        					FieldInsnNode get = (FieldInsnNode)top.getFirstArgInsn();
        					FieldInsnNode set = (FieldInsnNode)top.getFirstArgInsn();
        					if(get.desc.equals(set.desc) && get.owner.equals(set.owner) && get.name.equals(set.name)
        						&& Utils.getNext(top.getFirstArgInsn()) != bottom.getFirstArgInsn())
        					{
        						method.instructions.set(Utils.getNext(ain), new InsnNode(Opcodes.DUP));
                				fixed.decrementAndGet();
        					}
        				}
        			}else if(ain.getOpcode() == Opcodes.ALOAD && Utils.getNext(ain) != null
        				&& Utils.isInteger(Utils.getNext(ain))
        				&& Utils.getNext(ain, 2) != null && Utils.getNext(ain, 2).getOpcode() == Opcodes.ALOAD
        				&& ((VarInsnNode)ain).var == ((VarInsnNode)Utils.getNext(ain, 2)).var
        				&& Utils.isInteger(Utils.getNext(ain, 3))
        				&& Utils.getIntValue(Utils.getNext(ain)) == Utils.getIntValue(Utils.getNext(ain, 3)))
        			{
        				ArgsAnalyzer.Result top1 = new ArgsAnalyzer(ain.getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        				ArgsAnalyzer.Result top2 = new ArgsAnalyzer(Utils.getNext(ain).getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        				ArgsAnalyzer.Result bottom1 = new ArgsAnalyzer(Utils.getNext(ain, 2).getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        				ArgsAnalyzer.Result bottom2 = new ArgsAnalyzer(Utils.getNext(ain, 3).getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        				if(!(top1 instanceof ArgsAnalyzer.FailedResult) && !(bottom1 instanceof ArgsAnalyzer.FailedResult)
        					&& !(top2 instanceof ArgsAnalyzer.FailedResult) && !(bottom2 instanceof ArgsAnalyzer.FailedResult)
        					&& top1.getFirstArgInsn().getOpcode() >= Opcodes.IASTORE
        					&& top1.getFirstArgInsn().getOpcode() <= Opcodes.SASTORE
        					&& top1.getArgsNeeded() == 0
        					&& top2.getFirstArgInsn() == top1.getFirstArgInsn()
        					&& top2.getArgsNeeded() == 1
        					&& bottom1.getFirstArgInsn().getOpcode() == top1.getFirstArgInsn().getOpcode() - 33
        					&& bottom1.getArgsNeeded() == 0
        					&& bottom2.getFirstArgInsn() == bottom1.getFirstArgInsn()
        					&& bottom2.getArgsNeeded() == 1
        					&& Utils.getNext(top1.getFirstArgInsn()) != bottom1.getFirstArgInsn())
        				{
        					method.instructions.remove(Utils.getNext(ain, 3));
        					method.instructions.set(Utils.getNext(ain, 2), new InsnNode(Opcodes.DUP2));
            				fixed.addAndGet(-2);
        				}
        			}
        		}
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		for(int i = 0; i < method.instructions.size(); i++) 
        		{
        			AbstractInsnNode ain = method.instructions.get(i);
        			if(ain.getOpcode() == Opcodes.POP && 
        				(Utils.getPrevious(ain).getOpcode() > Opcodes.DUP2_X2 || Utils.getPrevious(ain).getOpcode() < Opcodes.DUP))
        			{
        				ArgsAnalyzer analyzer = new ArgsAnalyzer(ain.getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP);
        				analyzer.setIgnoreNeeded(true);
        				analyzer.setSpecialDup(true);
        				ArgsAnalyzer.Result res = analyzer.lookupArgs();
        				if(!(res instanceof ArgsAnalyzer.FailedResult) && res.getFirstArgInsn() != null)
        				{
        					ArgsAnalyzer.Result forward = new ArgsAnalyzer(res.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        					while(forward.getFirstArgInsn().getOpcode() >= Opcodes.DUP
        						&& forward.getFirstArgInsn().getOpcode() <= Opcodes.DUP2_X2)
        					{
        						int args = forward.getFirstArgInsn().getOpcode() >= Opcodes.DUP
            						&& forward.getFirstArgInsn().getOpcode() <= Opcodes.DUP2_X2 ? 1 : 2;
        						res = new ArgsAnalyzer(Utils.getNext(res.getFirstArgInsn()).getNext(), args,
        							ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        						forward = new ArgsAnalyzer(res.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        					}
        					boolean hasJumpBetween = false;
							AbstractInsnNode nxt = res.getFirstArgInsn();
    						while(nxt != ain)
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween && res.getFirstArgInsn().getOpcode() != Opcodes.DUP)
    						{
	        					int prev = method.instructions.indexOf(ain);
	        					method.instructions.remove(ain);
	        					method.instructions.insert(res.getFirstArgInsn(), ain);
	        					if(method.instructions.indexOf(ain) != prev)
	        						fixed.incrementAndGet();
    						}
        				}
        			}else if(ain.getOpcode() == Opcodes.POP2 && 
        				(Utils.getPrevious(ain).getOpcode() > Opcodes.DUP2_X2 || Utils.getPrevious(ain).getOpcode() < Opcodes.DUP))
        			{
        				ArgsAnalyzer analyzer = new ArgsAnalyzer(ain.getPrevious(), 2, ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP);
        				analyzer.setIgnoreNeeded(true);
        				ArgsAnalyzer.Result res = analyzer.lookupArgs();
        				if(!(res instanceof ArgsAnalyzer.FailedResult) && res.getFirstArgInsn() != null && res.getDiff() == -2)
        				{
        					ArgsAnalyzer.Result forward = new ArgsAnalyzer(res.getFirstArgInsn().getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        					while(forward.getFirstArgInsn().getOpcode() >= Opcodes.DUP
        						&& forward.getFirstArgInsn().getOpcode() <= Opcodes.DUP2_X2)
        					{
        						int args = forward.getFirstArgInsn().getOpcode() >= Opcodes.DUP
            						&& forward.getFirstArgInsn().getOpcode() <= Opcodes.DUP2_X2 ? 1 : 2;
        						res = new ArgsAnalyzer(Utils.getNext(res.getFirstArgInsn()).getNext(), args,
        							ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        						forward = new ArgsAnalyzer(res.getFirstArgInsn().getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
        					}
        					boolean hasJumpBetween = false;
        					AbstractInsnNode nxt = res.getFirstArgInsn();
    						while(nxt != ain)
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
	        					int prev = method.instructions.indexOf(ain);
	        					method.instructions.remove(ain);
	        					method.instructions.insert(res.getFirstArgInsn(), ain);
	        					if(method.instructions.indexOf(ain) != prev)
	        						fixed.incrementAndGet();
    						}
        				}else
        				{
        					ArgsAnalyzer analyzer1 = new ArgsAnalyzer(ain.getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS);
        					analyzer1.setIgnoreNeeded(true);
            				ArgsAnalyzer.Result res1 = analyzer1.lookupArgs();
            				if(!(res instanceof ArgsAnalyzer.FailedResult) 
            					&& !(res1 instanceof ArgsAnalyzer.FailedResult) && res1.getFirstArgInsn() != null)
            				{
            					if(Utils.getNext(res.getFirstArgInsn()).getOpcode() == Opcodes.DUP)
            						res = new ArgsAnalyzer(Utils.getNext(res.getFirstArgInsn()).getNext(), 1,
            							ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
            					if(Utils.getNext(res1.getFirstArgInsn()).getOpcode() == Opcodes.DUP)
            						res1 = new ArgsAnalyzer(Utils.getNext(res1.getFirstArgInsn()).getNext(), 1,
            							ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
            					method.instructions.remove(ain);
            					method.instructions.insert(res1.getFirstArgInsn(), new InsnNode(Opcodes.POP));
            					method.instructions.insert(res.getFirstArgInsn(), new InsnNode(Opcodes.POP));
            					fixed.incrementAndGet();
            				}
        				}
        			}else if(ain.getOpcode() == Opcodes.IINC)
        			{
        				if(ain.getNext().getOpcode() == Opcodes.TABLESWITCH || ain.getNext().getOpcode() == Opcodes.LOOKUPSWITCH)
        				{
        					AbstractInsnNode prev = ain;
        					while(prev != null)
        					{
        						if(prev.getOpcode() == Opcodes.ILOAD && ((VarInsnNode)prev).var == ((IincInsnNode)ain).var)
        							break;
        						else if(prev instanceof JumpInsnNode || prev instanceof TableSwitchInsnNode 
        							|| prev instanceof LookupSwitchInsnNode)
        							break;
        						else if(prev instanceof LabelNode && hasJump(prev, method))
        							break;
        						prev = prev.getPrevious();
        					}
        					method.instructions.remove(ain);
        					method.instructions.insert(prev, ain);
        				}else
        				{
        					AbstractInsnNode next = ain;
        					AbstractInsnNode jump = null;
        					while(next != null)
        					{
        						if(next instanceof VarInsnNode && ((VarInsnNode)next).var == ((IincInsnNode)ain).var)
        							break;
        						else if(next != ain && next instanceof IincInsnNode && ((IincInsnNode)next).var == ((IincInsnNode)ain).var)
        							break;
        						else if(next instanceof JumpInsnNode)
        						{
        							LabelNode label = ((JumpInsnNode)next).label;
        							if(label.getNext() != null && 
        								label.getNext().getOpcode() == Opcodes.ILOAD && ((VarInsnNode)label.getNext()).var == ((IincInsnNode)ain).var)
        							{
        								jump = next;
        								break;
        							}
        						}else if(next instanceof TableSwitchInsnNode 
        							|| next instanceof LookupSwitchInsnNode)
        							break;
        						else if(next instanceof LabelNode && hasJump(next, method))
        							break;
        						next = next.getNext();
        					}
        					if(jump != null)
        					{
        						method.instructions.remove(ain);
        						method.instructions.insertBefore(jump, ain);
        					}
        				}
        			}
        		}
        System.out.println("[Allatori] [FlowObfuscationTransformer] Fixed " + fixed + " instructions");
        System.out.println("[Allatori] [FlowObfuscationTransformer] Done");
		return fixed.get() > 0;
    }
    
    private boolean hasJump(AbstractInsnNode ain, MethodNode method)
	{
		for(AbstractInsnNode a : method.instructions.toArray())
			if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == ain)
				return true;
		return false;
	}

	private boolean willPushAll(AbstractInsnNode ain)
    {
		if(ain.getOpcode() == Opcodes.INVOKESTATIC && Type.getArgumentTypes(((MethodInsnNode)ain).desc).length == 0
			&& Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.VOID
			&& Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.LONG
			&& Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.DOUBLE)
			return true;
		if(ain.getOpcode() == Opcodes.GETSTATIC && Type.getType(((FieldInsnNode)ain).desc).getSort() != Type.VOID
			&& Type.getType(((FieldInsnNode)ain).desc).getSort() != Type.LONG
			&& Type.getType(((FieldInsnNode)ain).desc).getSort() != Type.DOUBLE)
			return true;
		return willPush(ain);
    }
    
	private boolean willPush(AbstractInsnNode ain)
    {
    	if(ain.getOpcode() == Opcodes.LDC && (((LdcInsnNode)ain).cst instanceof Long || ((LdcInsnNode)ain).cst instanceof Double))
    		return false;
    	return (Utils.willPushToStack(ain.getOpcode()) || ain.getOpcode() == Opcodes.NEW) && ain.getOpcode() != Opcodes.GETSTATIC
    		&& ain.getOpcode() != Opcodes.LLOAD && ain.getOpcode() != Opcodes.DLOAD;
    }
    
	private boolean willPush2(AbstractInsnNode ain)
    {
    	return ain.getOpcode() == Opcodes.LCONST_0 || ain.getOpcode() == Opcodes.LCONST_1
			|| ain.getOpcode() == Opcodes.DCONST_0 || ain.getOpcode() == Opcodes.DCONST_1
			|| (ain.getOpcode() == Opcodes.LDC && 
			(((LdcInsnNode)ain).cst instanceof Long || ((LdcInsnNode)ain).cst instanceof Double))
			|| ain.getOpcode() == Opcodes.LLOAD || ain.getOpcode() == Opcodes.DLOAD;
    }
    
    private List<AbstractInsnNode> getPossibleDupPop(AbstractInsnNode ain)
    {
    	AbstractInsnNode next = ain;
    	List<AbstractInsnNode> instrs = new ArrayList<>();
    	while(next != null)
    	{
    		if(Utils.isInstruction(next) && next.getOpcode() != Opcodes.IINC)
    			instrs.add(next);
    		if(instrs.size() >= 3)
    			break;
    		next = next.getNext();
    	}
    	if(instrs.size() >= 3 && (willPush(instrs.get(0)) || ain.getOpcode() == Opcodes.DUP) 
    		&& (willPush(instrs.get(1)) || instrs.get(1).getOpcode() == Opcodes.DUP)
    		&& instrs.get(2).getOpcode() == Opcodes.POP2)
    		return instrs;
    	else
    		return null;
    }
    
    private List<AbstractInsnNode> getPossibleSwap(AbstractInsnNode ain, int mode)
    {
    	AbstractInsnNode next = ain;
    	List<AbstractInsnNode> instrs = new ArrayList<>();
    	while(next != null)
    	{
    		if(Utils.isInstruction(next) && next.getOpcode() != Opcodes.IINC)
    			instrs.add(next);
    		if(instrs.size() >= (mode == 0 ? 3 : 4))
    			break;
    		next = next.getNext();
    	}
    	if(mode == 0 && instrs.size() >= 3 && willPush(instrs.get(0)) && willPush(instrs.get(1))
    		&& instrs.get(2).getOpcode() == Opcodes.SWAP)
    		return instrs;
    	else if(mode == 1 && instrs.size() >= 4 && willPush(instrs.get(0)) 
    		&& (willPush(instrs.get(1)) || instrs.get(1).getOpcode() == Opcodes.DUP)
    		&& instrs.get(2).getOpcode() == Opcodes.GETFIELD
    		&& Type.getType(((FieldInsnNode)instrs.get(2)).desc).getSort() != Type.LONG
    		&& Type.getType(((FieldInsnNode)instrs.get(2)).desc).getSort() != Type.DOUBLE
    		&& instrs.get(3).getOpcode() == Opcodes.SWAP)
    		return instrs;
    	else
    		return null;
    }
    
    private boolean fixDup(MethodNode method, AbstractInsnNode ain)
    {
    	AbstractInsnNode dup = ain;
    	AbstractInsnNode next = Utils.getPrevious(dup);
    	while(next != dup)
    	{
    		if(next instanceof LabelNode)
    		{
    			boolean cannotInline = false;
    			for(AbstractInsnNode a : method.instructions.toArray())
					if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == next)
					{
						cannotInline = true;
						break;
					}else if(a instanceof TableSwitchInsnNode)
					{
						for(LabelNode l : ((TableSwitchInsnNode)a).labels)
							if(l == next)
							{
								cannotInline = true;
								break;
							}
						if(((TableSwitchInsnNode)a).dflt == next)
							cannotInline = true;
						if(cannotInline)
							break;
					}else if(a instanceof LookupSwitchInsnNode)
					{
						for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
							if(l == next)
							{
								cannotInline = true;
								break;
							}
						if(((LookupSwitchInsnNode)a).dflt == next)
							cannotInline = true;
						if(cannotInline)
							break;
					}
				for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
					if(trycatch.start == next || trycatch.end == next || trycatch.handler == next)
					{
						cannotInline = true;
						break;
					}
				if(cannotInline)
					return false;
    		}
    		next = next.getNext();
    	}
    	int stackSize = -1;
    	if(ain.getOpcode() == Opcodes.DUP_X1)
    		stackSize = 3;
    	else if(ain.getOpcode() == Opcodes.DUP_X2)
    		stackSize = 4;
    	if(stackSize == -1)
    		return false;
    	ArgsAnalyzer.Result res = new ArgsAnalyzer(dup.getNext(), stackSize, ArgsAnalyzer.Mode.FORWARDS, Opcodes.SWAP).lookupArgs();
    	if(res instanceof ArgsAnalyzer.FailedResult)
    	{
    		ArgsAnalyzer.Result prev = new ArgsAnalyzer(Utils.getPrevious(dup), 
				dup.getOpcode() == Opcodes.DUP_X2 ? 3 : dup.getOpcode() == Opcodes.DUP_X1 
				? 2 : 0, ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP).lookupArgs();
			if(prev instanceof ArgsAnalyzer.FailedResult || prev.getFirstArgInsn() == null)
				return false;
			AbstractInsnNode dupprev = Utils.getPrevious(dup);
			Map<AbstractInsnNode, List<AbstractInsnNode>> labels = new HashMap<>();
			while(dupprev != prev.getFirstArgInsn())
			{
				if(dupprev instanceof LabelNode)
					labels.put(dupprev, new ArrayList<>());
				dupprev = dupprev.getPrevious();
			}
			if(labels.size() > 0)
				for(AbstractInsnNode a : method.instructions.toArray())
    				if(a instanceof JumpInsnNode && labels.containsKey(((JumpInsnNode)a).label))
    					labels.get(((JumpInsnNode)a).label).add(a);
			Map<AbstractInsnNode, List<AbstractInsnNode>> values = new HashMap<>();
			for(Entry<AbstractInsnNode, List<AbstractInsnNode>> entry : labels.entrySet())
    			if(entry.getValue().size() > 0)
    				values.put(entry.getKey(), entry.getValue());
			if(values.size() > 1)
				return false;
			if(values.size() > 0)
			{
				Entry<AbstractInsnNode, List<AbstractInsnNode>> ent = values.entrySet().iterator().next();
				ArgsAnalyzer analyzer = new ArgsAnalyzer(Utils.getPrevious(dup), 0, ArgsAnalyzer.Mode.BACKWARDS);
				analyzer.setBreakpoint(ent.getKey());
				analyzer.setForcebreak(true);
				ArgsAnalyzer.Result r = analyzer.lookupArgs();
				int needed = r.getArgsNeeded() + (dup.getOpcode() == Opcodes.DUP_X1 ? 2 : 3);
				for(AbstractInsnNode a : ent.getValue())
				{
					ArgsAnalyzer.Result insertPoint = new ArgsAnalyzer(a.getPrevious(), needed, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					try
					{
						method.instructions.insertBefore(insertPoint.getFirstArgInsn(), Utils.getPrevious(dup).clone(null));
					}catch(RuntimeException e)
					{
						return false;
					}
				}
			}
			method.instructions.insertBefore(prev.getFirstArgInsn(), Utils.getPrevious(dup).clone(null));
    		method.instructions.remove(dup);
			return true;
    	}
    	List<AbstractInsnNode> skippedDups = res.getSkippedDups();
    	AbstractInsnNode result = res.getFirstArgInsn();
    	int diff = res.getDiff();
    	if(result != null)
    	{
    		AbstractInsnNode insert;
    		boolean cannotInline = false;
    		AbstractInsnNode nxt = dup;
    		while(nxt != result)
    		{
    			if(nxt instanceof LabelNode)
    			{
    				for(AbstractInsnNode a : method.instructions.toArray())
    					if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    					{
    						cannotInline = true;
    						break;
    					}else if(a instanceof TableSwitchInsnNode)
    					{
    						for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    							if(l == nxt)
    							{
    								cannotInline = true;
    								break;
    							}
    						if(((TableSwitchInsnNode)a).dflt == nxt)
    							cannotInline = true;
    						if(cannotInline)
    							break;
    					}else if(a instanceof LookupSwitchInsnNode)
    					{
    						for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    							if(l == nxt)
    							{
    								cannotInline = true;
    								break;
    							}
    						if(((LookupSwitchInsnNode)a).dflt == nxt)
    							cannotInline = true;
    						if(cannotInline)
    							break;
    					}
    				for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    					if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    					{
    						cannotInline = true;
    						break;
    					}
    				if(cannotInline)
    					break;
    			}
    			nxt = nxt.getNext();
    		}
    		if(!cannotInline && -diff - (res.getArgsNeeded() + 1) == 0)
    		{
    			Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
    				fixLocalClash(Utils.getPrevious(dup), result, method, null);
    			method.instructions.insertBefore(triple.getMiddle(), insert = triple.getLeft().clone(null));
        		method.instructions.remove(dup);
    		}else
    		{
    			ArgsAnalyzer.Result prev = new ArgsAnalyzer(Utils.getPrevious(dup), 
					dup.getOpcode() == Opcodes.DUP_X2 ? 3 : dup.getOpcode() == Opcodes.DUP_X1 
					? 2 : 0, ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP).lookupArgs();
    			if(prev instanceof ArgsAnalyzer.FailedResult || prev.getFirstArgInsn() == null
    				|| prev.getFirstArgInsn().getOpcode() == Opcodes.DUP || prev.getFirstArgInsn().getOpcode() == Opcodes.DUP2)
    				return false;
    			AbstractInsnNode aaa = Utils.getPrevious(dup);
    			while(aaa != prev.getFirstArgInsn())
    			{
    				if(aaa.getOpcode() == Opcodes.DUP_X1 || aaa.getOpcode() == Opcodes.DUP_X2
    					|| aaa.getOpcode() == Opcodes.DUP2_X1 || aaa.getOpcode() == Opcodes.DUP2_X2
    					|| (Utils.getPrevious(dup).getOpcode() >= Opcodes.ILOAD && Utils.getPrevious(dup).getOpcode() <= Opcodes.ALOAD
    					&& aaa.getOpcode() >= Opcodes.ISTORE && aaa.getOpcode() <= Opcodes.ASTORE
    					&& ((VarInsnNode)Utils.getPrevious(dup)).var == ((VarInsnNode)aaa).var))
    					return false;
    				aaa = aaa.getPrevious();
    			}
    			AbstractInsnNode dupprev = Utils.getPrevious(dup);
    			Map<AbstractInsnNode, List<AbstractInsnNode>> labels = new HashMap<>();
    			while(dupprev != prev.getFirstArgInsn())
    			{
    				if(dupprev instanceof LabelNode)
    					labels.put(dupprev, new ArrayList<>());
    				dupprev = dupprev.getPrevious();
    			}
    			if(labels.size() > 0)
    				for(AbstractInsnNode a : method.instructions.toArray())
        				if(a instanceof JumpInsnNode && labels.containsKey(((JumpInsnNode)a).label))
        					labels.get(((JumpInsnNode)a).label).add(a);
    			Map<AbstractInsnNode, List<AbstractInsnNode>> values = new HashMap<>();
    			for(Entry<AbstractInsnNode, List<AbstractInsnNode>> entry : labels.entrySet())
        			if(entry.getValue().size() > 0)
        				values.put(entry.getKey(), entry.getValue());
    			if(values.size() > 1)
    				return false;
    			if(values.size() > 0)
    			{
    				Entry<AbstractInsnNode, List<AbstractInsnNode>> ent = values.entrySet().iterator().next();
					ArgsAnalyzer analyzer = new ArgsAnalyzer(Utils.getPrevious(dup), 0, ArgsAnalyzer.Mode.BACKWARDS);
					analyzer.setBreakpoint(ent.getKey());
					analyzer.setForcebreak(true);
					ArgsAnalyzer.Result r = analyzer.lookupArgs();
					int needed = r.getArgsNeeded() + (dup.getOpcode() == Opcodes.DUP_X1 ? 2 : 3);
    				for(AbstractInsnNode a : ent.getValue())
    				{
    					ArgsAnalyzer.Result insertPoint = new ArgsAnalyzer(a.getPrevious(), needed, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
    					method.instructions.insertBefore(insertPoint.getFirstArgInsn(), Utils.getPrevious(dup).clone(null));
    				}
    			}
				method.instructions.insertBefore(prev.getFirstArgInsn(), insert = Utils.getPrevious(dup).clone(null));
	    		method.instructions.remove(dup);
    		}
    		if(insert != null)
    		{
	    		AbstractInsnNode prev = insert.getPrevious();
	    		while(!Utils.isInstruction(prev) && !(prev instanceof LabelNode))
	    			prev = prev.getPrevious();
	    		if(prev instanceof LabelNode)
	    			for(AbstractInsnNode a : method.instructions.toArray())
	    				if(a.getOpcode() == Opcodes.GOTO && ((JumpInsnNode)a).label == prev)
	    				{
	    					ArgsAnalyzer.Result analysis = new ArgsAnalyzer(
	    						a.getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS, Opcodes.SWAP).lookupArgs();
	    					if(analysis instanceof ArgsAnalyzer.FailedResult || analysis.getFirstArgInsn() == null)
	    						return false;
	    					if(analysis.getFirstArgInsn().getNext().getOpcode() >= Opcodes.DUP
	    						&& analysis.getFirstArgInsn().getNext().getOpcode() <= Opcodes.DUP2_X2)
	    						throw new RuntimeException("Did not expect a dup");
	    					method.instructions.remove(analysis.getFirstArgInsn());
	    				}
    		}else
    			return false;
    		final AbstractInsnNode insertF = insert;
    		skippedDups.removeIf(ins -> method.instructions.indexOf(ins) > method.instructions.indexOf(insertF));
    		for(AbstractInsnNode insn : skippedDups)
    			if(insn.getOpcode() == Opcodes.DUP_X2)
    				method.instructions.set(insn, new InsnNode(Opcodes.DUP_X1));
    			else if(insn.getOpcode() == Opcodes.DUP_X1)
    				method.instructions.set(insn, new InsnNode(Opcodes.DUP));
    			else if(insn.getOpcode() == Opcodes.DUP2_X1)
    				method.instructions.set(insn, new InsnNode(Opcodes.DUP2));
    			else if(insn.getOpcode() == Opcodes.DUP2_X2)
    				method.instructions.set(insn, new InsnNode(Opcodes.DUP2_X1));
        	return true;
    	}
    	return false;
    }
    
    private boolean inlineArgs(MethodNode method, AbstractInsnNode ain, ArgsAnalyzer.Result result)
    {
    	if(ain.getOpcode() == Opcodes.NEW || Utils.getNext(ain).getOpcode() == Opcodes.CHECKCAST)
			return false;
		boolean cannotInline = false;
		AbstractInsnNode next = ain;
		while(next != result.getFirstArgInsn())
		{
			if(next instanceof LabelNode)
			{
				for(AbstractInsnNode a : method.instructions.toArray())
					if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == next)
					{
						cannotInline = true;
						break;
					}else if(a instanceof TableSwitchInsnNode)
					{
						for(LabelNode l : ((TableSwitchInsnNode)a).labels)
							if(l == next)
							{
								cannotInline = true;
								break;
							}
						if(((TableSwitchInsnNode)a).dflt == next)
							cannotInline = true;
						if(cannotInline)
							break;
					}else if(a instanceof LookupSwitchInsnNode)
					{
						for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
							if(l == next)
							{
								cannotInline = true;
								break;
							}
						if(((LookupSwitchInsnNode)a).dflt == next)
							cannotInline = true;
						if(cannotInline)
							break;
					}
				for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
					if(trycatch.start == next || trycatch.end == next || trycatch.handler == next)
					{
						cannotInline = true;
						break;
					}
				if(cannotInline)
					break;
			}else if(ain.getOpcode() >= Opcodes.ILOAD && ain.getOpcode() <= Opcodes.ALOAD
				&& ((next.getOpcode() >= Opcodes.ISTORE && next.getOpcode() <= Opcodes.ASTORE && ((VarInsnNode)ain).var == ((VarInsnNode)next).var)
					|| (next.getOpcode() == Opcodes.IINC && ((VarInsnNode)ain).var == ((IincInsnNode)next).var)))
			{
				cannotInline = true;
				break;
			}
			next = next.getNext();
		}
		if(!cannotInline)
		{
			if(result.getDiff() == -1)
			{
				if(isFailedInline(method, ain, result.getFirstArgInsn()))
					return false;
				Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
					fixLocalClash(ain, result.getFirstArgInsn(), method, null);
				method.instructions.remove(triple.getLeft());
				method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
				if(result.getSwap() != null)
					method.instructions.remove(result.getSwap());
				return true;
			}else if(result.getDiff() == -2)
			{
				if(result.getArgsNeeded() == 1)
				{
					if(isFailedInline(method, ain, result.getFirstArgInsn()))
						return false;
					Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
						fixLocalClash(ain, result.getFirstArgInsn(), method, null);
					method.instructions.remove(triple.getLeft());
					method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(triple.getMiddle().getPrevious(), 2, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush(backwards.getFirstArgInsn()))
					{
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == triple.getMiddle())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != triple.getLeft())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							if(isFailedInline(method, backwards.getFirstArgInsn(), triple.getLeft()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(backwards.getFirstArgInsn(), triple.getLeft(), method, null);
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    						}
						}
					}
					return true;
				}else
				{
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(result.getFirstArgInsn().getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush(backwards.getFirstArgInsn()))
					{
						if(backwards.getFirstArgInsn().getOpcode() == Opcodes.NEW)
						{
							if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
								return false;
							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
								fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
							method.instructions.remove(triple.getLeft());
    						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
							return true;
						}
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == result.getFirstArgInsn())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != result.getFirstArgInsn())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							AbstractInsnNode backwardsResult = backwards.getFirstArgInsn();
    							if(isFailedInline(method, ain, result.getFirstArgInsn()) || isFailedInline(method, backwardsResult, result.getFirstArgInsn()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
    								fixLocalClash(backwards.getFirstArgInsn(), result.getFirstArgInsn(), method, ain);
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(triple.getRight(), triple.getMiddle(), method, backwardsResult);
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    							method.instructions.remove(triple1.getRight());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getRight());
    							return true;
    						}
						}else
						{
							if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
								return false;
							inlineArgs(method, backwards.getFirstArgInsn(), forwards);
							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple =
								fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
							method.instructions.remove(triple.getLeft());
    						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
    						return true;
						}
					}
				}
				return false;
			}else if(result.getDiff() == -3)
			{
				if(result.getArgsNeeded() == 2)
				{
					if(isFailedInline(method, ain, result.getFirstArgInsn()))
						return false;
					Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple =
						fixLocalClash(ain, result.getFirstArgInsn(), method, null);
					method.instructions.remove(triple.getLeft());
					method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(triple.getMiddle().getPrevious(), 3, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush2(backwards.getFirstArgInsn()))
					{
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == triple.getMiddle())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != triple.getLeft())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							if(isFailedInline(method, backwards.getFirstArgInsn(), triple.getLeft()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 =
    								fixLocalClash(backwards.getFirstArgInsn(), triple.getLeft(), method, null);
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    						}
						}
					}
					return true;
				}else if(result.getArgsNeeded() == 0)
				{
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(result.getFirstArgInsn().getPrevious(), 2, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush2(backwards.getFirstArgInsn()))
					{
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == result.getFirstArgInsn())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != result.getFirstArgInsn())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							AbstractInsnNode backwardsResult = backwards.getFirstArgInsn();
    							if(isFailedInline(method, ain, result.getFirstArgInsn()) || isFailedInline(method, backwardsResult, result.getFirstArgInsn()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
    								fixLocalClash(backwards.getFirstArgInsn(), result.getFirstArgInsn(), method, ain);
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(triple.getRight(), triple.getMiddle(), method, backwardsResult);
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    							method.instructions.remove(triple1.getRight());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getRight());
    							return true;
    						}
						}else
						{
							if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
								return false;
							inlineArgs2(method, backwards.getFirstArgInsn(), forwards);
							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
								fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
							method.instructions.remove(triple.getLeft());
    						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
    						return true;
						}
					}else if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush(backwards.getFirstArgInsn()))
					{
						if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
							return false;
						Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
							fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
						method.instructions.remove(triple.getLeft());
						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
						return true;
					}
				}
				return false;
			}else
				return false;
		}else
			return false;
    }
    
    private boolean inlineArgs2(MethodNode method, AbstractInsnNode ain, ArgsAnalyzer.Result result)
    {
    	if(ain.getOpcode() == Opcodes.NEW || Utils.getNext(ain).getOpcode() == Opcodes.CHECKCAST)
			return false;
		boolean cannotInline = false;
		AbstractInsnNode next = ain;
		while(next != result.getFirstArgInsn())
		{
			if(next instanceof LabelNode)
			{
				for(AbstractInsnNode a : method.instructions.toArray())
					if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == next)
					{
						cannotInline = true;
						break;
					}else if(a instanceof TableSwitchInsnNode)
					{
						for(LabelNode l : ((TableSwitchInsnNode)a).labels)
							if(l == next)
							{
								cannotInline = true;
								break;
							}
						if(((TableSwitchInsnNode)a).dflt == next)
							cannotInline = true;
						if(cannotInline)
							break;
					}else if(a instanceof LookupSwitchInsnNode)
					{
						for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
							if(l == next)
							{
								cannotInline = true;
								break;
							}
						if(((LookupSwitchInsnNode)a).dflt == next)
							cannotInline = true;
						if(cannotInline)
							break;
					}
				for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
					if(trycatch.start == next || trycatch.end == next || trycatch.handler == next)
					{
						cannotInline = true;
						break;
					}
				if(cannotInline)
					break;
			}else if(ain.getOpcode() >= Opcodes.ILOAD && ain.getOpcode() <= Opcodes.ALOAD
				&& ((next.getOpcode() >= Opcodes.ISTORE && next.getOpcode() <= Opcodes.ASTORE && ((VarInsnNode)ain).var == ((VarInsnNode)next).var)
					|| (next.getOpcode() == Opcodes.IINC && ((VarInsnNode)ain).var == ((IincInsnNode)next).var)))
			{
				cannotInline = true;
				break;
			}
			next = next.getNext();
		}
		if(!cannotInline)
		{
			if(result.getDiff() == -2)
			{
				if(isFailedInline(method, ain, result.getFirstArgInsn()))
					return false;
				Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
					fixLocalClash(ain, result.getFirstArgInsn(), method, null);
				method.instructions.remove(triple.getLeft());
				method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
				return true;
			}else if(result.getDiff() == -3)
			{
				if(result.getArgsNeeded() == 1)
				{
					if(isFailedInline(method, ain, result.getFirstArgInsn()))
						return false;
					Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
						fixLocalClash(ain, result.getFirstArgInsn(), method, null);
					method.instructions.remove(triple.getLeft());
					method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(triple.getMiddle().getPrevious(), 3, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush(backwards.getFirstArgInsn()))
					{
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == triple.getMiddle())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != triple.getLeft())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							if(isFailedInline(method, backwards.getFirstArgInsn(), triple.getLeft()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(backwards.getFirstArgInsn(), triple.getLeft(), method, null);
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    						}
						}
					}
					return true;
				}else
				{
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(result.getFirstArgInsn().getPrevious(), 1, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush(backwards.getFirstArgInsn()))
					{
						if(backwards.getFirstArgInsn().getOpcode() == Opcodes.NEW)
						{
							if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
								return false;
							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
								fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
							method.instructions.remove(triple.getLeft());
    						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
							return true;
						}
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == result.getFirstArgInsn())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != result.getFirstArgInsn())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							AbstractInsnNode backwardsResult = backwards.getFirstArgInsn();
    							if(isFailedInline(method, ain, result.getFirstArgInsn()) || isFailedInline(method, backwardsResult, result.getFirstArgInsn()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
    								fixLocalClash(backwards.getFirstArgInsn(), result.getFirstArgInsn(), method, ain);
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(triple.getRight(), triple.getMiddle(), method, backwardsResult);
    							backwardsResult = triple1.getRight();
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    							method.instructions.remove(triple1.getRight());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getRight());
    							return true;
    						}
						}else
						{
							if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
								return false;
							inlineArgs(method, backwards.getFirstArgInsn(), forwards);
							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple =
								fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
							method.instructions.remove(triple.getLeft());
    						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
    						return true;
						}
					}
				}
				return false;
			}else if(result.getDiff() == -4)
			{
				if(result.getArgsNeeded() == 2)
				{
					if(isFailedInline(method, ain, result.getFirstArgInsn()))
						return false;
					Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
						fixLocalClash(ain, result.getFirstArgInsn(), method, null);
					method.instructions.remove(triple.getLeft());
					method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(triple.getMiddle().getPrevious(), 4, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush2(backwards.getFirstArgInsn()))
					{
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == triple.getMiddle())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != triple.getLeft())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
       							if(isFailedInline(method, backwards.getFirstArgInsn(), triple.getLeft()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(backwards.getFirstArgInsn(), triple.getLeft(), method, null);
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    						}
						}
					}
					return true;
				}else if(result.getArgsNeeded() == 0)
				{
					ArgsAnalyzer.Result backwards = new ArgsAnalyzer(result.getFirstArgInsn().getPrevious(), 2, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
					if(!(backwards instanceof ArgsAnalyzer.FailedResult) && backwards.getFirstArgInsn() != null
						&& willPush2(backwards.getFirstArgInsn()))
					{
						ArgsAnalyzer.Result forwards = new ArgsAnalyzer(backwards.getFirstArgInsn().getNext(), 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
						if(forwards.getFirstArgInsn() == result.getFirstArgInsn())
						{
							boolean hasJumpBetween = false;
							AbstractInsnNode nxt = backwards.getFirstArgInsn();
    						while(nxt != result.getFirstArgInsn())
    						{
    							if(nxt instanceof LabelNode)
    							{
    								for(AbstractInsnNode a : method.instructions.toArray())
    									if(a instanceof JumpInsnNode && ((JumpInsnNode)a).label == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}else if(a instanceof TableSwitchInsnNode)
    									{
    										for(LabelNode l : ((TableSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((TableSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}else if(a instanceof LookupSwitchInsnNode)
    									{
    										for(LabelNode l : ((LookupSwitchInsnNode)a).labels)
    											if(l == nxt)
    											{
    												hasJumpBetween = true;
    												break;
    											}
    										if(((LookupSwitchInsnNode)a).dflt == nxt)
    											hasJumpBetween = true;
    										if(hasJumpBetween)
    											break;
    									}
    								for(TryCatchBlockNode trycatch : method.tryCatchBlocks)
    									if(trycatch.start == nxt || trycatch.end == nxt || trycatch.handler == nxt)
    									{
    										hasJumpBetween = true;
    										break;
    									}
    								if(hasJumpBetween)
    									break;
    							}else if(backwards.getFirstArgInsn().getOpcode() >= Opcodes.ILOAD && backwards.getFirstArgInsn().getOpcode() <= Opcodes.ALOAD
    								&& ((nxt.getOpcode() >= Opcodes.ISTORE && nxt.getOpcode() <= Opcodes.ASTORE 
    								&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((VarInsnNode)nxt).var)
    									|| (nxt.getOpcode() == Opcodes.IINC 
    									&& ((VarInsnNode)backwards.getFirstArgInsn()).var == ((IincInsnNode)nxt).var)))
    							{
    								hasJumpBetween = true;
    								break;
    							}
    							nxt = nxt.getNext();
    						}
    						if(!hasJumpBetween)
    						{
    							AbstractInsnNode backwardsResult = backwards.getFirstArgInsn();
    							if(isFailedInline(method, ain, result.getFirstArgInsn()) || isFailedInline(method, backwardsResult, result.getFirstArgInsn()))
    								return false;
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple = 
    								fixLocalClash(backwards.getFirstArgInsn(), result.getFirstArgInsn(), method, ain);
    							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple1 = 
    								fixLocalClash(triple.getRight(), triple.getMiddle(), method, backwardsResult);
    							backwardsResult = triple1.getRight();
    							method.instructions.remove(triple1.getLeft());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getLeft());
    							method.instructions.remove(triple1.getRight());
    							method.instructions.insertBefore(triple1.getMiddle(), triple1.getRight());
    							return true;
    						}
						}else
						{
							if(isFailedInline(method, ain, backwards.getFirstArgInsn()))
								return false;
							inlineArgs2(method, backwards.getFirstArgInsn(), forwards);
							Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> triple =
								fixLocalClash(ain, backwards.getFirstArgInsn(), method, null);
							method.instructions.remove(triple.getLeft());
    						method.instructions.insertBefore(triple.getMiddle(), triple.getLeft());
    						return true;
						}
					}
				}
				return false;
			}else
				return false;
		}else
			return false;
    }
    
    private Triple<AbstractInsnNode, AbstractInsnNode, AbstractInsnNode> fixLocalClash(AbstractInsnNode ain, 
    	AbstractInsnNode insPoint, MethodNode method, AbstractInsnNode other)
    {
    	if(ain.getOpcode() > Opcodes.ALOAD || ain.getOpcode() < Opcodes.ILOAD)
    		return Triple.of(ain, insPoint, other);
    	AbstractInsnNode store = null;
    	AbstractInsnNode next = ain;
    	while(next != insPoint)
    	{
    		if(next.getOpcode() >= Opcodes.ISTORE && next.getOpcode() <= Opcodes.ASTORE
    			&& ((VarInsnNode)next).var == ((VarInsnNode)ain).var)
    		{
    			store = next;
    			break;
    		}
    		next = next.getNext();
    	}
    	if(store != null)
    	{
    		int maxLocal = ((VarInsnNode)store).var;
    		boolean stack2 = false;
    		for(AbstractInsnNode a : method.instructions.toArray())
    			if(a instanceof VarInsnNode && ((VarInsnNode)a).var > maxLocal)
    			{
    				if(a.getOpcode() == Opcodes.LLOAD || a.getOpcode() == Opcodes.DLOAD
    					|| a.getOpcode() == Opcodes.LSTORE || a.getOpcode() == Opcodes.DSTORE)
    					stack2 = true;
    				else 
    					stack2 = false;
    				maxLocal = ((VarInsnNode)a).var;
    			}
    		if(stack2)
    		{
    			maxLocal += 2;
    			method.maxLocals += 2;
    		}else
    		{
    			maxLocal++;
    			method.maxLocals++;
    		}
    		AbstractInsnNode next2 = store;
    		while(next2 != null)
    		{
    			if(next2 instanceof JumpInsnNode && 
    				method.instructions.indexOf(((JumpInsnNode)next2).label) < method.instructions.indexOf(store))
    				throw new RuntimeException("Jump before clash found");
    			if(next2 instanceof VarInsnNode && ((VarInsnNode)next2).var == ((VarInsnNode)store).var)
    			{
    				VarInsnNode v = (VarInsnNode)next2;
    				method.instructions.set(v, next2 = new VarInsnNode(v.getOpcode(), maxLocal));
    				if(other == v)
    					other = next2;
    				if(ain == v)
    					ain = next2;
    				if(insPoint == v)
    					insPoint = next2;
    			}else if(next2 instanceof IincInsnNode && ((IincInsnNode)next2).var == ((VarInsnNode)store).var)
    			{
    				IincInsnNode v = (IincInsnNode)next2;
    				method.instructions.set(v, next2 = new IincInsnNode(maxLocal, v.incr));
    			}
    			next2 = next2.getNext();
    		}
    	}
    	return Triple.of(ain, insPoint, other);
    }
    
    private boolean isFailedInline(MethodNode method, AbstractInsnNode start, AbstractInsnNode end)
    {
    	if(start.getOpcode() >= Opcodes.ILOAD && start.getOpcode() <= Opcodes.ALOAD)
    	{
	    	int var = ((VarInsnNode)start).var;
	    	AbstractInsnNode next = start;
	    	while(next != end)
	    	{
	    		if(next.getOpcode() == Opcodes.IINC && ((IincInsnNode)next).var == var)
	    			return true;
	    		next = next.getNext();
	    	}
	    	LinkedHashMap<LabelNode, List<AbstractInsnNode>> firstFlow = new FlowAnalyzer(method).analyze(
	    		method.instructions.getFirst(), Arrays.asList(end), new HashMap<>(), false, true);
	    	LinkedHashMap<LabelNode, List<AbstractInsnNode>> endFlow = new FlowAnalyzer(method).analyze(
	    		end, Arrays.asList(), new HashMap<>(), false, true);
	    	for(Entry<LabelNode, List<AbstractInsnNode>> entry : firstFlow.entrySet())
	    		for(AbstractInsnNode ain : entry.getValue())
	    			if((ain.getOpcode() == Opcodes.IINC && ((IincInsnNode)ain).var == var)
	    			|| (ain.getOpcode() >= Opcodes.ILOAD && ain.getOpcode() <= Opcodes.ALOAD && ((VarInsnNode)ain).var == var))
	    				for(Entry<LabelNode, List<AbstractInsnNode>> entry1 : endFlow.entrySet())
	    		    		for(AbstractInsnNode ain1 : entry1.getValue())
	    		    			if(ain1 == ain)
	    		    				return true;
    	}
    	if(Utils.getNext(start).getOpcode() == Opcodes.DUP || Utils.getNext(start).getOpcode() == Opcodes.DUP2)
    		return true;
    	ArgsAnalyzer.Result res = new ArgsAnalyzer(start.getNext(), willPushAll(start) ? 1 : 2, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
    	if(res instanceof ArgsAnalyzer.FailedResult)
    	{
    		for(AbstractInsnNode skipped : ((ArgsAnalyzer.FailedResult)res).getSkippedDupsAtFailure())
    			if(method.instructions.indexOf(skipped) < method.instructions.indexOf(end))
    				return true;
    		if(((ArgsAnalyzer.FailedResult)res).getSwapAtFailure() != null)
    			if(method.instructions.indexOf(((ArgsAnalyzer.FailedResult)res).getSwapAtFailure()) < method.instructions.indexOf(end))
    				return true;
    	}else
    	{
    		if(res.getFirstArgInsn().getOpcode() == Opcodes.DUP || res.getFirstArgInsn().getOpcode() == Opcodes.DUP2)
    			return true;
    		for(AbstractInsnNode skipped : res.getSkippedDups())
    			if(method.instructions.indexOf(skipped) < method.instructions.indexOf(end))
    				return true;
    		if(res.getSwap() != null)
    			if(method.instructions.indexOf(res.getSwap()) < method.instructions.indexOf(end))
    				return true;
    	}
    	return false;
    }
}
