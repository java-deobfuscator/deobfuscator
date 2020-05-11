package com.javadeobfuscator.deobfuscator.transformers.special;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.analyzer.ArgsAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class WrappedLocalsTransformer extends Transformer<TransformerConfig>
{
	@Override
	public boolean transform() throws Throwable
	{
		System.out.println("[Special] [WrappedLocalsTransformer] Starting");
		AtomicInteger count = new AtomicInteger();
		AtomicInteger methods = new AtomicInteger();
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				Set<Integer> loadInts = new HashSet<>();
				Set<Integer> loadFloats = new HashSet<>();
				Set<Integer> loadLongs = new HashSet<>();
				Set<Integer> loadDoubles = new HashSet<>();
				Set<Integer> loads = new HashSet<>();
				Map<AbstractInsnNode, Frame<SourceValue>> frames = new HashMap<>();
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
					if(ain.getOpcode() == Opcodes.ASTORE)
					{
						SourceValue s = frames.get(ain).getStack(frames.get(ain).getStackSize() - 1);
						if(s.insns.size() == 1 && s.insns.iterator().next().getOpcode() == Opcodes.NEW)
						{
							AbstractInsnNode a = s.insns.iterator().next();
							if(((TypeInsnNode)a).desc.equals("java/lang/Integer"))
								loadInts.add(((VarInsnNode)ain).var);
							else if(((TypeInsnNode)a).desc.equals("java/lang/Float"))
								loadFloats.add(((VarInsnNode)ain).var);
							else if(((TypeInsnNode)a).desc.equals("java/lang/Long"))
								loadLongs.add(((VarInsnNode)ain).var);
							else if(((TypeInsnNode)a).desc.equals("java/lang/Double"))
								loadDoubles.add(((VarInsnNode)ain).var);
						}else if(s.insns.size() == 1 && (s.insns.iterator().next().getOpcode() == Opcodes.DUP_X1
							|| s.insns.iterator().next().getOpcode() == Opcodes.DUP_X2))
						{
							SourceValue s1 = frames.get(s.insns.iterator().next()).getStack(frames.get(
								s.insns.iterator().next()).getStackSize() - 1);
							if(s1.insns.size() == 1 && s1.insns.iterator().next().getOpcode() == Opcodes.NEW)
							{
								AbstractInsnNode a = s1.insns.iterator().next();
								if(((TypeInsnNode)a).desc.equals("java/lang/Integer"))
									loadInts.add(((VarInsnNode)ain).var);
								else if(((TypeInsnNode)a).desc.equals("java/lang/Float"))
									loadFloats.add(((VarInsnNode)ain).var);
								else if(((TypeInsnNode)a).desc.equals("java/lang/Long"))
									loadLongs.add(((VarInsnNode)ain).var);
								else if(((TypeInsnNode)a).desc.equals("java/lang/Double"))
									loadDoubles.add(((VarInsnNode)ain).var);
							}
						}
					}
				loads.addAll(loadInts);
				loads.addAll(loadFloats);
				loads.addAll(loadLongs);
				loads.addAll(loadDoubles);
				if(loads.isEmpty())
					continue;
				//Load, List<Store>
				Map<VarInsnNode, List<VarInsnNode>> loadMap = new HashMap<>();
				//Store, List<Load>
				Map<VarInsnNode, List<VarInsnNode>> storeMap = new HashMap<>();
				for(Integer var : loads)
				{
					List<AbstractInsnNode> vars = new ArrayList<>();
					for(AbstractInsnNode ain : method.instructions.toArray())
						if(ain.getOpcode() == Opcodes.ASTORE && ((VarInsnNode)ain).var == var)
							vars.add(ain);
					for(AbstractInsnNode ain : vars)
					{
						List<AbstractInsnNode> excluded = new ArrayList<>(vars);
						excluded.remove(ain);
						LinkedHashMap<LabelNode, List<AbstractInsnNode>> map = new FlowAnalyzer(method).
							analyze(ain, excluded, new HashMap<>(), false, true);
						storeMap.put((VarInsnNode)ain, new ArrayList<>());
						for(Entry<LabelNode, List<AbstractInsnNode>> entry : map.entrySet())
							for(AbstractInsnNode a : entry.getValue())
								if(a.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)a).var == var)
								{
									loadMap.putIfAbsent((VarInsnNode)a, new ArrayList<>());
									loadMap.get(a).add((VarInsnNode)ain);
									storeMap.get(ain).add((VarInsnNode)a);
								}
					}
				}
				Map<VarInsnNode, Integer> succeededStores = new HashMap<>();
				for(VarInsnNode store : storeMap.keySet())
				{
					if(succeededStores.containsKey(store))
						continue;
					Map<VarInsnNode, Integer> tried = new HashMap<>();
					if(!isFail(method, store, frames, loadMap, storeMap, tried, -1))
						succeededStores.putAll(tried);
				}
				TreeSet<Integer> doubleWidth = new TreeSet<>();
				for(VarInsnNode store : succeededStores.keySet())
				{
					//Revert the store itself
					if(store.getPrevious().getOpcode() == Opcodes.INVOKESPECIAL
						&& store.getPrevious().getPrevious() != null
						&& store.getPrevious().getPrevious().getOpcode() == Opcodes.POP
						&& store.getPrevious().getPrevious().getPrevious() != null
						&& (store.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.DUP_X1
						|| store.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.DUP_X2)
						&& store.getPrevious().getPrevious().getPrevious().getPrevious() != null
						&& (store.getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.DUP_X1
							|| store.getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.DUP_X2)
						&& store.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious() != null
						&& store.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.NEW)
					{
						method.instructions.remove(store.getPrevious().getPrevious().getPrevious().getPrevious().getPrevious());
						method.instructions.remove(store.getPrevious().getPrevious().getPrevious().getPrevious());
						method.instructions.remove(store.getPrevious().getPrevious().getPrevious());
						method.instructions.remove(store.getPrevious().getPrevious());
						method.instructions.remove(store.getPrevious());
					}else
					{
						SourceValue v = frames.get(store).getStack(frames.get(store).getStackSize() - 1);
						AbstractInsnNode newOpcode = v.insns.iterator().next();
						method.instructions.remove(Utils.getNext(newOpcode));
						method.instructions.remove(newOpcode);
						method.instructions.remove(Utils.getPrevious(store));
					}
					switch(succeededStores.get(store))
					{
						case 0:
							store.setOpcode(Opcodes.ISTORE);
							break;
						case 1:
							store.setOpcode(Opcodes.FSTORE);
							break;
						case 2:
							store.setOpcode(Opcodes.LSTORE);
							doubleWidth.add(store.var);
							break;
						case 3:
							store.setOpcode(Opcodes.DSTORE);
							doubleWidth.add(store.var);
							break;
					}
					//Revert all loads
					for(VarInsnNode load : storeMap.get(store))
						if(load.getOpcode() == Opcodes.ALOAD)
						{
							ArgsAnalyzer.Result res = new ArgsAnalyzer(load.getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
							method.instructions.remove(res.getFirstArgInsn());
							switch(succeededStores.get(store))
							{
								case 0:
									load.setOpcode(Opcodes.ILOAD);
									break;
								case 1:
									load.setOpcode(Opcodes.FLOAD);
									break;
								case 2:
									load.setOpcode(Opcodes.LLOAD);
									break;
								case 3:
									load.setOpcode(Opcodes.DLOAD);
									break;
							}
						}
				}
				if(!doubleWidth.isEmpty())
				{
					//Calculate new locals (ASM will calculate new stack size)
					Map<Integer, Integer> oldToNewLocals = new HashMap<>();
					for(int i = 0; i < method.maxLocals; i++)
						oldToNewLocals.put(i, i);
					for(Integer i : doubleWidth)
						for(Entry<Integer, Integer> entry : oldToNewLocals.entrySet())
							if(entry.getKey() > i)
								entry.setValue(entry.getValue() + 1);
					for(AbstractInsnNode ain : method.instructions.toArray())
						if(ain instanceof VarInsnNode)
							((VarInsnNode)ain).var = oldToNewLocals.get(((VarInsnNode)ain).var);
						else if(ain instanceof IincInsnNode)
							((IincInsnNode)ain).var = oldToNewLocals.get(((IincInsnNode)ain).var);
				}
				if(succeededStores.size() > 0)
				{
					count.getAndAdd(succeededStores.size());
					methods.incrementAndGet();
				}
			}
		System.out.println("[Special] [WrappedLocalsTransformer] Cleaned " + count + " locals");
		System.out.println("[Special] [WrappedLocalsTransformer] Fixed " + methods + " methods");
		System.out.println("[Special] [WrappedLocalsTransformer] Done");
		return count.get() > 0;
	}
	
	private boolean isFail(MethodNode method, VarInsnNode store, Map<AbstractInsnNode, Frame<SourceValue>> frames, 
		Map<VarInsnNode, List<VarInsnNode>> loadMap, Map<VarInsnNode, List<VarInsnNode>> storeMap, Map<VarInsnNode, Integer> tried, int varType)
	{
		if(tried.containsKey(store))
			return false;
		//Check if this store loads wrapped number
		int type = -1;
		SourceValue s = frames.get(store).getStack(frames.get(store).getStackSize() - 1);
		if(s.insns.size() == 1 && s.insns.iterator().next().getOpcode() == Opcodes.NEW)
		{
			AbstractInsnNode a = s.insns.iterator().next();
			if(((TypeInsnNode)a).desc.equals("java/lang/Integer"))
				type = 0;
			else if(((TypeInsnNode)a).desc.equals("java/lang/Float"))
				type = 1;
			else if(((TypeInsnNode)a).desc.equals("java/lang/Long"))
				type = 2;
			else if(((TypeInsnNode)a).desc.equals("java/lang/Double"))
				type = 3;
		}else if(s.insns.size() == 1 && (s.insns.iterator().next().getOpcode() == Opcodes.DUP_X1
			|| s.insns.iterator().next().getOpcode() == Opcodes.DUP_X2))
		{
			SourceValue s1 = frames.get(s.insns.iterator().next()).getStack(frames.get(
				s.insns.iterator().next()).getStackSize() - 1);
			if(s1.insns.size() == 1 && s1.insns.iterator().next().getOpcode() == Opcodes.NEW)
			{
				AbstractInsnNode a = s1.insns.iterator().next();
				if(((TypeInsnNode)a).desc.equals("java/lang/Integer"))
					type = 0;
				else if(((TypeInsnNode)a).desc.equals("java/lang/Float"))
					type = 1;
				else if(((TypeInsnNode)a).desc.equals("java/lang/Long"))
					type = 2;
				else if(((TypeInsnNode)a).desc.equals("java/lang/Double"))
					type = 3;
			}
		}
		if(type == -1 || (varType != -1 && type != varType))
			return true;
		tried.put(store, type);
		//Now check all the loads
		for(VarInsnNode load : storeMap.get(store))
		{
			//Check if load is followed by unboxing
			boolean unbox = false;
			ArgsAnalyzer.Result res = new ArgsAnalyzer(load.getNext(), 1, ArgsAnalyzer.Mode.FORWARDS).lookupArgs();
			if(!(res instanceof ArgsAnalyzer.FailedResult) && res.getFirstArgInsn().getOpcode() == Opcodes.INVOKEVIRTUAL
				&& ((((MethodInsnNode)res.getFirstArgInsn()).owner.equals("java/lang/Integer")
					&& ((MethodInsnNode)res.getFirstArgInsn()).name.equals("intValue") && type == 0)
					|| (((MethodInsnNode)res.getFirstArgInsn()).owner.equals("java/lang/Float")
						&& ((MethodInsnNode)res.getFirstArgInsn()).name.equals("floatValue") && type == 1)
					|| (((MethodInsnNode)res.getFirstArgInsn()).owner.equals("java/lang/Long")
						&& ((MethodInsnNode)res.getFirstArgInsn()).name.equals("longValue") && type == 2)
					|| (((MethodInsnNode)res.getFirstArgInsn()).owner.equals("java/lang/Double")
						&& ((MethodInsnNode)res.getFirstArgInsn()).name.equals("doubleValue") && type == 3)))
				unbox = true;
			if(!unbox)
				return true;
			//Go through loadmap and check all stores there
			for(VarInsnNode otherStore : loadMap.get(load))
				if(otherStore != store && isFail(method, otherStore, frames, loadMap, storeMap, tried, type))
					return true;
		}
		return false;
	}
}
