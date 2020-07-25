package com.javadeobfuscator.deobfuscator.transformers.special;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class FlowObfuscationTransformer extends Transformer<TransformerConfig>
{
	@Override
	public boolean transform() throws Throwable
	{
		AtomicInteger count = new AtomicInteger();
		System.out.println("[Special] [FlowObfuscationTransformer] Starting");
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				boolean modified = false;
				do
				{
					modified = false;
					for(AbstractInsnNode ain : method.instructions.toArray())
					{
	        			if(ain.getOpcode() == Opcodes.GOTO)
	        			{
	                        AbstractInsnNode a = Utils.getNext(ain);
	                        AbstractInsnNode b = Utils.getNext(((JumpInsnNode)ain).label);
	                        if(a == b)
	                        {
	                        	method.instructions.remove(ain);
	                        	modified = true;
	                        }
	                    }
						if(willPush(ain) && ain.getNext() != null 
							&& ain.getNext().getOpcode() == Opcodes.POP)
						{
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							modified = true;
							count.getAndIncrement();
						}
						if(willPush(ain) && ain.getNext() != null 
							&& (willPush(ain.getNext()) || ain.getNext().getOpcode() == Opcodes.DUP) && ain.getNext().getNext() != null
							&& ain.getNext().getNext().getOpcode() == Opcodes.POP2)
						{
							method.instructions.remove(ain.getNext().getNext());
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							modified = true;
							count.getAndIncrement();
						}
						if(ain.getOpcode() == Opcodes.DUP && ain.getNext() != null 
							&& ain.getNext().getOpcode() == Opcodes.ACONST_NULL 
							&& ain.getNext().getNext() != null
							&& ain.getNext().getNext().getOpcode() == Opcodes.SWAP
							&& ain.getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getOpcode() >= Opcodes.ISTORE
							&& ain.getNext().getNext().getNext().getOpcode() <= Opcodes.ASTORE
							&& ain.getNext().getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
							&& ain.getNext().getNext().getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == ain.getNext().getNext().getNext().getOpcode()
							&& ((VarInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).var 
							== ((VarInsnNode)ain.getNext().getNext().getNext()).var)
						{
							method.instructions.remove(ain.getNext().getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext());
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							modified = true;
							count.getAndIncrement();
						}
						if(willPush(ain) && ain.getNext() != null 
							&& willPush(ain.getNext()) 
							&& ain.getNext().getNext() != null
							&& willPush(ain.getNext().getNext())
							&& ain.getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.SWAP
							&& ain.getNext().getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP
							&& ain.getNext().getNext().getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP2)
						{
							method.instructions.remove(ain.getNext().getNext().getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext());
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							modified = true;
							count.getAndIncrement();
						}
						if(willPush(ain) && ain.getNext() != null 
							&& willPush(ain.getNext()) 
							&& ain.getNext().getNext() != null
							&& ain.getNext().getNext().getOpcode() == Opcodes.SWAP
							&& ain.getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.POP
							&& ain.getNext().getNext().getNext().getNext() != null
							&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP)
						{
							method.instructions.remove(ain.getNext().getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext());
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							modified = true;
							count.getAndIncrement();
						}
						if(isEqual(ain) && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.SWAP
							&& ain.getNext().getNext().getNext() != null && ain.getNext().getNext().getNext().getOpcode() == Opcodes.POP)
						{
							method.instructions.remove(ain.getNext().getNext().getNext());
							method.instructions.remove(ain.getNext().getNext());
							method.instructions.remove(ain.getNext());
							modified = true;
							count.getAndIncrement();
						}
						if(ain.getOpcode() == Opcodes.INEG && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INEG)
						{
							method.instructions.remove(ain.getNext());
							method.instructions.remove(ain);
							modified = true;
							count.getAndIncrement();
						}
						if(Utils.isInteger(ain) && ain.getNext() != null)
						{
							int res = -1;
							int value = Utils.getIntValue(ain);
							switch(ain.getNext().getOpcode())
							{
								case IFNE:
									res = (value != 0) ? 1 : 0;
									break;
								case IFEQ:
									res = (value == 0) ? 1 : 0;
									break;
								case IFLT:
									res = (value < 0) ? 1 : 0;
									break;
								case IFGE:
									res = (value >= 0) ? 1 : 0;
									break;
								case IFGT:
									res = (value > 0) ? 1 : 0;
									break;
								case IFLE:
									res = (value <= 0) ? 1 : 0;
									break;
							}
							if(res == 1)
							{
								method.instructions.set(ain.getNext(), new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode)ain.getNext()).label));
								method.instructions.remove(ain);
								modified = true;
							}else if(res == 0)
							{
								method.instructions.remove(ain.getNext());
								method.instructions.remove(ain);
								modified = true;
							}
							count.incrementAndGet();
						}
					}
					Frame<BasicValue>[] frames;
					try
					{
						frames = new Analyzer<>(new BasicInterpreter()).analyze(classNode.name, method);
					}catch(AnalyzerException e)
					{
						continue;
					}
					InstructionModifier modifier = new InstructionModifier();
					for(int i = 0; i < method.instructions.size(); i++)
					{
						if (!Utils.isInstruction(method.instructions.get(i))) continue;
						if (frames[i] != null) continue;
						
						modifier.remove(method.instructions.get(i));
					}
					modifier.apply(method);
					if(method.tryCatchBlocks != null)
					{
						method.tryCatchBlocks.removeIf(tryCatchBlockNode -> 
						(Utils.getNext(tryCatchBlockNode.start) == Utils.getNext(tryCatchBlockNode.end) 
						|| tryCatchBlockNode.handler.getNext().getOpcode() == Opcodes.ATHROW));
					}
				}while(modified);
			}
        for(ClassNode classNode : classes.values())
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
    				if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB || ain.getOpcode() == Opcodes.IMUL
    					|| ain.getOpcode() == Opcodes.IDIV || ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.IXOR)
    				{
    					Frame<SourceValue> f = frames.get(ain);
    					SourceValue arg1 = f.getStack(f.getStackSize() - 1);
    					SourceValue arg2 = f.getStack(f.getStackSize() - 2);
    					if(arg1.insns.size() != 1 || arg2.insns.size() != 1)
    						continue;
    					AbstractInsnNode a1 = arg1.insns.iterator().next();
    					AbstractInsnNode a2 = arg2.insns.iterator().next();
    					for(Entry<AbstractInsnNode, AbstractInsnNode> entry : replace.entrySet())
    						if(entry.getKey() == a1)
    							a1 = entry.getValue();
    						else if(entry.getKey() == a2)
    							a2 = entry.getValue();
    					if(Utils.isInteger(a1) && Utils.isInteger(a2))
    					{
	    					Integer resultValue;
	                        if((resultValue = doMath(Utils.getIntValue(a1), Utils.getIntValue(a2), ain.getOpcode())) != null) 
	                        {
	                        	AbstractInsnNode newValue = Utils.getIntInsn(resultValue);
	                        	replace.put(ain, newValue);
	                            method.instructions.set(ain, newValue);
	                            method.instructions.remove(a1);
	                            method.instructions.remove(a2);
	                        }
	                        count.incrementAndGet();
    					}
    				}
    			}
            }
        System.out.println("[Special] [FlowObfuscationTransformer] Fixed " + count + " chunks");
		return count.get() > 0;
	}
	
    private Integer doMath(int value1, int value2, int opcode) 
    {
        switch(opcode) 
        {
            case IADD:
                return value2 + value1;
            case IDIV:
                return value2 / value1;
            case IREM:
                return value2 % value1;
            case ISUB:
                return value2 - value1;
            case IMUL:
                return value2 * value1;
            case IXOR:
                return value2 ^ value1;
        }
        return null;
    }
	
	private boolean isEqual(AbstractInsnNode ain)
    {
		if(ain.getNext() == null)
			return false;
		if(ain.getOpcode() == Opcodes.LDC && ain.getNext().getOpcode() == Opcodes.LDC
			&& ((LdcInsnNode)ain).cst.equals(((LdcInsnNode)ain.getNext()).cst))
			return true;
		if(ain.getOpcode() >= Opcodes.ILOAD && ain.getOpcode() <= Opcodes.ALOAD
			&& ain.getNext().getOpcode() == ain.getOpcode() 
			&& ((VarInsnNode)ain).var == ((VarInsnNode)ain.getNext()).var)
			return true;
		return false;
    }
	
	private boolean willPush(AbstractInsnNode ain)
    {
    	if(ain.getOpcode() == Opcodes.LDC && (((LdcInsnNode)ain).cst instanceof Long || ((LdcInsnNode)ain).cst instanceof Double))
    		return false;
    	return (Utils.willPushToStack(ain.getOpcode()) || ain.getOpcode() == Opcodes.NEW) && ain.getOpcode() != Opcodes.GETSTATIC
    		&& ain.getOpcode() != Opcodes.LLOAD && ain.getOpcode() != Opcodes.DLOAD;
    }
}
