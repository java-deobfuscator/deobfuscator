package com.javadeobfuscator.deobfuscator.transformers.special;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class UselessArithmeticTransformer extends Transformer<TransformerConfig>
{
	@Override
	public boolean transform() throws Throwable
	{
		System.out.println("[Special] [UselessArithmeticTransformer] Starting");
		AtomicInteger count = new AtomicInteger();
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(Utils.isInteger(ain) && Utils.getIntValue(ain) == 0 &&  ain.getNext() != null
						&& (ain.getNext().getOpcode() == Opcodes.IADD || ain.getNext().getOpcode() == Opcodes.ISUB))
					{
						method.instructions.remove(ain.getNext());
						method.instructions.remove(ain);
						count.incrementAndGet();
					}
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				List<IincInsnNode> iincsToRemove = new ArrayList<>();
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					List<AbstractInsnNode> possibleInsns = getPossibleInsns(ain);
					if(possibleInsns != null)
					{
						iincsToRemove.add((IincInsnNode)possibleInsns.get(possibleInsns.size() - 1));
						possibleInsns.forEach(a -> method.instructions.remove(a));
						count.incrementAndGet();
					}
				}
				for(IincInsnNode iinc : iincsToRemove)
					for(AbstractInsnNode ain : method.instructions.toArray())
						if(ain.getOpcode() == Opcodes.ICONST_0 && ain.getNext() != null
							&& ain.getNext().getOpcode() == Opcodes.ISTORE
							&& ((VarInsnNode)ain.getNext()).var == iinc.var)
						{
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							break;
						}
			}
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					List<AbstractInsnNode> possibleInsns = getPossibleInsns2(ain);
					if(possibleInsns != null)
					{
						possibleInsns.forEach(a -> method.instructions.remove(a));
						count.incrementAndGet();
					}
				}
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					List<AbstractInsnNode> possibleInsns = getPossibleInsns3(ain);
					if(possibleInsns != null)
					{
						for(int i = 1; i < possibleInsns.size(); i++)
							method.instructions.remove(possibleInsns.get(i));
						method.instructions.set(possibleInsns.get(0), new InsnNode(Opcodes.ICONST_0));
						count.incrementAndGet();
					}
				}
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					List<AbstractInsnNode> possibleInsns = getPossibleInsns4(ain);
					if(possibleInsns != null)
					{
						for(int i = 1; i < possibleInsns.size(); i++)
							method.instructions.remove(possibleInsns.get(i));
						method.instructions.set(possibleInsns.get(0), new InsnNode(Opcodes.ICONST_0));
						count.incrementAndGet();
					}
				}
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if(Utils.isInteger(ain) && Utils.getIntValue(ain) == 0 &&  ain.getNext() != null
						&& (ain.getNext().getOpcode() == Opcodes.IADD || ain.getNext().getOpcode() == Opcodes.ISUB))
					{
						method.instructions.remove(ain.getNext());
						method.instructions.remove(ain);
					}
		System.out.println("[Special] [UselessArithmeticTransformer] Removed " + count + " instruction sets");
		System.out.println("[Special] [UselessArithmeticTransformer] Done");
		return count.get() > 0;
	}

	private List<AbstractInsnNode> getPossibleInsns(AbstractInsnNode ain)
	{
		List<AbstractInsnNode> instrs = new ArrayList<>();
		while(ain != null)
		{
			if(ain instanceof LineNumberNode || ain instanceof FrameNode)
			{
				ain = ain.getNext();
				continue;
			}
			instrs.add(ain);
			if(instrs.size() >= 23)
				break;
			ain = ain.getNext();
		}
		if(instrs.size() == 23 && instrs.get(0).getOpcode() == Opcodes.ILOAD
			&& instrs.get(1).getOpcode() == Opcodes.ICONST_1
			&& instrs.get(2).getOpcode() == Opcodes.ISUB
			&& instrs.get(3).getOpcode() == Opcodes.ISTORE)
		{
			int var1 = ((VarInsnNode)instrs.get(0)).var;
			int var2 = ((VarInsnNode)instrs.get(3)).var;
			if(instrs.get(4).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(4)).var == var1
				&& instrs.get(5).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(5)).var == var2
				&& instrs.get(6).getOpcode() == Opcodes.IMUL
				&& instrs.get(7).getOpcode() == Opcodes.ISTORE
				&& ((VarInsnNode)instrs.get(7)).var == var2
				&& instrs.get(8).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(8)).var == var2
				&& instrs.get(9).getOpcode() == Opcodes.ICONST_2
				&& instrs.get(10).getOpcode() == Opcodes.IREM
				&& instrs.get(11).getOpcode() == Opcodes.ISTORE
				&& ((VarInsnNode)instrs.get(11)).var == var2
				&& instrs.get(12).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(12)).var == var2
				&& instrs.get(13).getOpcode() == Opcodes.I2L
				&& instrs.get(14).getOpcode() == Opcodes.ICONST_0
				&& instrs.get(15).getOpcode() == Opcodes.I2L
				&& instrs.get(16).getOpcode() == Opcodes.LCMP
				&& instrs.get(17).getOpcode() == Opcodes.ICONST_1
				&& instrs.get(18).getOpcode() == Opcodes.IXOR
				&& instrs.get(19).getOpcode() == Opcodes.ICONST_1
				&& instrs.get(20).getOpcode() == Opcodes.IAND
				&& instrs.get(21).getOpcode() == Opcodes.IFEQ
				&& instrs.get(22).getOpcode() == Opcodes.IINC)
				return instrs;
		}
		return null;
	}
	
	private List<AbstractInsnNode> getPossibleInsns2(AbstractInsnNode ain)
	{
		List<AbstractInsnNode> instrs = new ArrayList<>();
		while(ain != null)
		{
			if(ain instanceof LineNumberNode || ain instanceof FrameNode)
			{
				ain = ain.getNext();
				continue;
			}
			instrs.add(ain);
			if(instrs.size() >= 14)
				break;
			ain = ain.getNext();
		}
		if(instrs.size() == 14 && instrs.get(0).getOpcode() == Opcodes.ILOAD
			&& instrs.get(1).getOpcode() == Opcodes.ICONST_1
			&& instrs.get(2).getOpcode() == Opcodes.ISUB
			&& instrs.get(3).getOpcode() == Opcodes.ISTORE)
		{
			int var1 = ((VarInsnNode)instrs.get(0)).var;
			int var2 = ((VarInsnNode)instrs.get(3)).var;
			if(instrs.get(4).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(4)).var == var1
				&& instrs.get(5).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(5)).var == var2
				&& instrs.get(6).getOpcode() == Opcodes.IMUL
				&& instrs.get(7).getOpcode() == Opcodes.ISTORE
				&& ((VarInsnNode)instrs.get(7)).var == var2
				&& instrs.get(8).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(8)).var == var2
				&& instrs.get(9).getOpcode() == Opcodes.ICONST_2
				&& instrs.get(10).getOpcode() == Opcodes.IREM
				&& instrs.get(11).getOpcode() == Opcodes.ISTORE
				&& ((VarInsnNode)instrs.get(11)).var == var2
				&& instrs.get(12).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(12)).var == var2
				&& instrs.get(13).getOpcode() == Opcodes.IADD)
			return instrs;
		}
		return null;
	}
	
	private List<AbstractInsnNode> getPossibleInsns3(AbstractInsnNode ain)
	{
		List<AbstractInsnNode> instrs = new ArrayList<>();
		while(ain != null)
		{
			if(ain instanceof LineNumberNode || ain instanceof FrameNode)
			{
				ain = ain.getNext();
				continue;
			}
			instrs.add(ain);
			if(instrs.size() >= 7)
				break;
			ain = ain.getNext();
		}
		if(instrs.size() == 7 && instrs.get(0).getOpcode() == Opcodes.ILOAD
			&& instrs.get(1).getOpcode() == Opcodes.ILOAD
			&& instrs.get(2).getOpcode() == Opcodes.ICONST_1
			&& instrs.get(3).getOpcode() == Opcodes.ISUB
			&& instrs.get(4).getOpcode() == Opcodes.IMUL
			&& instrs.get(5).getOpcode() == Opcodes.ICONST_2
			&& instrs.get(6).getOpcode() == Opcodes.IREM)
			return instrs;
		return null;
	}
	
	private List<AbstractInsnNode> getPossibleInsns4(AbstractInsnNode ain)
	{
		List<AbstractInsnNode> instrs = new ArrayList<>();
		while(ain != null)
		{
			if(ain instanceof LineNumberNode || ain instanceof FrameNode)
			{
				ain = ain.getNext();
				continue;
			}
			instrs.add(ain);
			if(instrs.size() >= 11)
				break;
			ain = ain.getNext();
		}
		if(instrs.size() == 11 && instrs.get(0).getOpcode() == Opcodes.ILOAD
			&& instrs.get(1).getOpcode() == Opcodes.ICONST_1
			&& instrs.get(2).getOpcode() == Opcodes.ISUB
			&& instrs.get(3).getOpcode() == Opcodes.ISTORE)
		{
			int var1 = ((VarInsnNode)instrs.get(0)).var;
			int var2 = ((VarInsnNode)instrs.get(3)).var;
			if(instrs.get(4).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(4)).var == var1
				&& instrs.get(5).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(5)).var == var2
				&& instrs.get(6).getOpcode() == Opcodes.IMUL
				&& instrs.get(7).getOpcode() == Opcodes.ISTORE
				&& ((VarInsnNode)instrs.get(7)).var == var2
				&& instrs.get(8).getOpcode() == Opcodes.ILOAD
				&& ((VarInsnNode)instrs.get(8)).var == var2
				&& instrs.get(9).getOpcode() == Opcodes.ICONST_2
				&& instrs.get(10).getOpcode() == Opcodes.IREM)
			return instrs;
		}
		return null;
	}
}
