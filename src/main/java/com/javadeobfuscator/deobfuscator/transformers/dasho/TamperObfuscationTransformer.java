package com.javadeobfuscator.deobfuscator.transformers.dasho;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.tuple.Triple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer.JumpData;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.special.TryCatchFixer.TryCatchChain;

public class TamperObfuscationTransformer extends Transformer<TransformerConfig>
{
	 @Override
	 public boolean transform() throws Throwable {
		 System.out.println("[DashO] [TamperObfuscationTransformer] Starting");
		 AtomicInteger counter = new AtomicInteger();
		 for(ClassNode classNode : classNodes())
			 methodloop:
			 for(MethodNode method : classNode.methods)
			 {
				 AbstractInsnNode startingPoint = null;
				 for(AbstractInsnNode ain : method.instructions.toArray())
				 {
					 if(ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)ain).var == 0 && !Modifier.isStatic(method.access)
						 && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						 && ((MethodInsnNode)ain.getNext()).name.equals("getClass")
						 && ((MethodInsnNode)ain.getNext()).owner.equals("java/lang/Object")
						 && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL 
						 && ((MethodInsnNode)ain.getNext().getNext()).name.equals("getProtectionDomain")
						 && ((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Class")
						 && ain.getNext().getNext().getNext() != null 
						 && ain.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL 
						 && ((MethodInsnNode)ain.getNext().getNext().getNext()).name.equals("getCodeSource")
						 && ((MethodInsnNode)ain.getNext().getNext().getNext()).owner.equals("java/security/ProtectionDomain")
						 && ain.getNext().getNext().getNext().getNext() != null 
						 && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						 && ((MethodInsnNode)ain.getNext().getNext().getNext().getNext()).name.equals("getCertificates")
						 && ((MethodInsnNode)ain.getNext().getNext().getNext().getNext()).owner.equals("java/security/CodeSource")
						 && ain.getPrevious() != null && ain.getPrevious() instanceof LabelNode)
					 {
						 startingPoint = ain.getPrevious();
						 break;
					 }else if(ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode)ain).cst instanceof Type
						 && ((Type)((LdcInsnNode)ain).cst).getInternalName().equals(classNode.name) && Modifier.isStatic(method.access)
						 && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INVOKEVIRTUAL 
						 && ((MethodInsnNode)ain.getNext()).name.equals("getProtectionDomain")
						 && ((MethodInsnNode)ain.getNext()).owner.equals("java/lang/Class")
						 && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL 
						 && ((MethodInsnNode)ain.getNext().getNext()).name.equals("getCodeSource")
						 && ((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/security/ProtectionDomain")
						 && ain.getNext().getNext().getNext() != null 
						 && ain.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
						 && ((MethodInsnNode)ain.getNext().getNext().getNext()).name.equals("getCertificates")
						 && ((MethodInsnNode)ain.getNext().getNext().getNext()).owner.equals("java/security/CodeSource")
						 && ain.getPrevious() != null && ain.getPrevious() instanceof LabelNode)
					 {
						 startingPoint = ain.getPrevious();
						 break;
					 }else if(ain.getOpcode() == Opcodes.ACONST_NULL && ain.getNext() != null 
						 && ain.getNext().getOpcode() == Opcodes.ASTORE && ain.getNext().getNext() != null 
						 && ain.getNext().getNext().getOpcode() == Opcodes.LDC
						 && ((LdcInsnNode)ain.getNext().getNext()).cst.equals("java.lang.management.ManagementFactory")
						 && ain.getNext().getNext().getNext() != null 
						 && ain.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKESTATIC
						 && ((MethodInsnNode)ain.getNext().getNext().getNext()).name.equals("forName")
						 && ((MethodInsnNode)ain.getNext().getNext().getNext()).owner.equals("java/lang/Class")
						 && ain.getNext().getNext().getNext().getNext() != null 
						 && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ASTORE 
						 && ain.getNext().getNext().getNext().getNext().getNext() != null 
						 && ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ALOAD
						 && ((VarInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).var ==
						 ((VarInsnNode)ain.getNext().getNext().getNext().getNext()).var
						 && ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.LDC
						 && ((LdcInsnNode)ain.getNext().getNext().getNext().getNext().getNext().getNext()).cst.equals("getRuntimeMXBean")
						 && ain.getPrevious() != null && ain.getPrevious() instanceof LabelNode)
						 startingPoint = ain.getPrevious();
				 }
				 if(startingPoint == null)
					 continue;
				 int mode = -1;
				 LabelNode jump = null;
				 LinkedHashMap<LabelNode, List<AbstractInsnNode>> flowAnalysis = new FlowAnalyzer(method).analyze(
					 startingPoint, new ArrayList<>(), new HashMap<>(), false, true);
				 boolean verify1 = false;
				 boolean verify2 = false;
				 for(Entry<LabelNode, List<AbstractInsnNode>> entry : flowAnalysis.entrySet())
					 for(AbstractInsnNode ain : entry.getValue())
						 if(ain.getOpcode() == Opcodes.ALOAD && ain.getNext() != null
							 && ain.getNext().getOpcode() == Opcodes.INVOKESTATIC
							 && ((MethodInsnNode)ain.getNext()).owner.equals("java/util/Arrays")
							 && ((MethodInsnNode)ain.getNext()).name.equals("equals")
							 && ain.getNext().getNext() != null 
							 && ain.getNext().getNext().getOpcode() == Opcodes.IFNE)
						 {
							 jump = ((JumpInsnNode)ain.getNext().getNext()).label;
							 mode = 0;
						 }else if(ain instanceof LdcInsnNode && ((LdcInsnNode)ain).cst.equals("java.lang.management.ManagementFactory"))
							 verify1 = true;
						 else if(ain instanceof LdcInsnNode && ((LdcInsnNode)ain).cst.equals("getRuntimeMXBean"))
							 verify2 = true;
						 else if(verify1 && verify2 && ain.getOpcode() == Opcodes.ILOAD && ain.getNext() != null
							 && ain.getNext().getOpcode() == Opcodes.ILOAD
							 && ain.getNext().getNext() != null 
							 && ain.getNext().getNext().getOpcode() == Opcodes.IF_ICMPGE)
						 {
							 LabelNode jumpLabel = ((JumpInsnNode)ain.getNext().getNext()).label;
							 if(jumpLabel != null && jumpLabel.getNext().getOpcode() == Opcodes.ILOAD
								 && jumpLabel.getNext().getNext() != null
								 && jumpLabel.getNext().getNext().getOpcode() == Opcodes.IFEQ)
							 {
								 jump = ((JumpInsnNode)jumpLabel.getNext().getNext()).label;
								 mode = 1;
								 break;
							 }
						 }
				 if(jump == null)
					 continue;
				 LinkedHashMap<LabelNode, List<AbstractInsnNode>> flowAnalysis2 = new FlowAnalyzer(method).analyze(
					 startingPoint, Arrays.asList(jump), new HashMap<>(), false, true);
				 FlowAnalyzer.Result result = new FlowAnalyzer(method).analyze();
				 boolean counted = false;
				 boolean specialCounted = false;
				 for(Entry<LabelNode, Entry<List<AbstractInsnNode>, List<Triple<LabelNode, JumpData, Integer>>>> entry : result.labels.entrySet())
					 for(Triple<LabelNode, JumpData, Integer> tri : entry.getValue().getValue())
						 if(tri.getLeft() == jump)
							 if(counted)
							 {
								 if(mode == 1 && flowAnalysis2.containsKey(entry.getKey()) && !specialCounted)
								 {
									 specialCounted = true;
									 continue;
								 }
								 System.out.println("Warning: Unexpected multi-jump at " + classNode.name + ", method " 
									 + method.name + method.desc);
								 continue methodloop;
							 }else
								 counted = true;
				 if(mode == 1 && !specialCounted)
					 continue methodloop;
				 for(Entry<LabelNode, List<AbstractInsnNode>> entry : flowAnalysis2.entrySet())
				 {
					 if(entry.getKey() != startingPoint)
						 method.instructions.remove(entry.getKey());
					 for(AbstractInsnNode ain : entry.getValue())
						 method.instructions.remove(ain);
				 }
				 method.instructions.insert(startingPoint, new JumpInsnNode(Opcodes.GOTO, jump));
				 counter.incrementAndGet();
				 if(method.tryCatchBlocks.isEmpty())
					 continue methodloop;
				 //Recalcuate exception blocks
				 LinkedHashMap<LabelNode, List<TryCatchBlockNode>> resNow = new LinkedHashMap<>();
				 for(AbstractInsnNode ain : method.instructions.toArray())
					 if(ain instanceof LabelNode)
						 if(result.trycatchMap.containsKey(ain))
							 resNow.put((LabelNode)ain, result.trycatchMap.get(ain));
						 else
							 resNow.put((LabelNode)ain, new ArrayList<>());
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
					boolean modified;
					do
					{
						modified = false;
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
							modified = true;
							chains.remove(remove);
						}
					}while(modified);
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
			 }
		 System.out.println("[DashO] [TamperObfuscationTransformer] Removed " + counter.get() + " anti-tamper calls");
		 System.out.println("[DashO] [TamperObfuscationTransformer] Done");
		 return counter.get() > 0;
	 }
}
