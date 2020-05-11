package com.javadeobfuscator.deobfuscator.transformers.special;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class TryCatchFixer extends Transformer<TransformerConfig>
{
	@Override
	public boolean transform() throws Throwable
	{
		System.out.println("[Special] [TryCatchFixer] Starting");
		AtomicInteger count = new AtomicInteger();
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				if(method.tryCatchBlocks.size() <= 0)
					continue;
				FlowAnalyzer.Result result = new FlowAnalyzer(method).analyze();
				List<TryCatchChain> chains = new ArrayList<>();
				Map<LabelNode, List<TryCatchBlockNode>> pass = new HashMap<>();
				List<LabelNode> labels = new ArrayList<>(result.trycatchMap.keySet());
				for(Entry<LabelNode, List<TryCatchBlockNode>> entry : result.trycatchMap.entrySet())
					for(TryCatchBlockNode tcbn : entry.getValue())
						if(!pass.containsKey(entry.getKey()) || !pass.get(entry.getKey()).contains(tcbn))
						{
							TryCatchChain chain = new TryCatchChain(tcbn.handler, tcbn.type,
								tcbn.visibleTypeAnnotations, tcbn.visibleTypeAnnotations);
							chains.add(chain);
							chain.covered.add(entry.getKey());
							chain.end = labels.get(labels.indexOf(entry.getKey()) + 1);
							pass.putIfAbsent(entry.getKey(), new ArrayList<>());
							pass.get(entry.getKey()).add(tcbn);
							for(int i = labels.indexOf(entry.getKey()) + 1; i < labels.size(); i++)
							{
								List<TryCatchBlockNode> list = result.trycatchMap.get(labels.get(i));
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
						for(int ii = 0; ii < result.trycatchMap.get(start).size(); ii++)
						{
							TryCatchBlockNode tcbn = result.trycatchMap.get(start).get(ii);
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
						for(int ii = 0; ii < result.trycatchMap.get(start).size(); ii++)
						{
							TryCatchBlockNode tcbn = result.trycatchMap.get(start).get(ii);
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
							for(int iii = 0; iii < result.trycatchMap.get(now).size(); iii++)
							{
								TryCatchBlockNode tcbn = result.trycatchMap.get(now).get(iii);
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
							for(int iii = 0; iii < result.trycatchMap.get(now).size(); iii++)
							{
								TryCatchBlockNode tcbn = result.trycatchMap.get(now).get(iii);
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
							List<TryCatchBlockNode> list = result.trycatchMap.get(lbl);
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
								result.trycatchMap.get(lbl).remove(0);
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
        			count.incrementAndGet();
        		method.tryCatchBlocks = exceptions;
			}
		System.out.println("[Special] [TryCatchFixer] Fixed " + count + " methods");
		System.out.println("[Special] [TryCatchFixer] Done");
		return count.get() > 0;
	}
	
	public static class TryCatchChain
	{
		public List<LabelNode> covered;
		public LabelNode end;
		public LabelNode handler;
		public String type;
		public List<TypeAnnotationNode> visibleTypeAnnotations;
		public List<TypeAnnotationNode> invisibleTypeAnnotations;
		
		public TryCatchChain(LabelNode handler, String type, List<TypeAnnotationNode> visibleTypeAnnotations,
			List<TypeAnnotationNode> invisibleTypeAnnotations)
		{
			this.handler = handler;
			this.type = type;
			this.visibleTypeAnnotations = visibleTypeAnnotations;
			this.invisibleTypeAnnotations = invisibleTypeAnnotations;
			covered = new ArrayList<>();
		}
	}
}
	