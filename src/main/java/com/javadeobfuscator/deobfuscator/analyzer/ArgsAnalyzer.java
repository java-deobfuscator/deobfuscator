package com.javadeobfuscator.deobfuscator.analyzer;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;

import com.javadeobfuscator.deobfuscator.utils.Utils;

/**
 * This class is used to retrieve where a method's args begin.
 * Somtimes, obfuscators don't like to put a method's args right before its method that uses it.
 * This analyzer can run both forwards and backwards.
 * @author ThisTestUser
 */
public class ArgsAnalyzer
{
	/**
	 * Which instruction do we start at?
	 */
	private final AbstractInsnNode start;
	
	/**
	 * How many args do we need?
	 */
	private final int argSize;
	
	/**
	 * What direction should we run?
	 */
	private final Mode mode;
	
	/**
	 * Are there any opcodes which we should immedately stop execution if we pass through them?
	 */
	private final int[] stopCodes;
	
	/**
	 * The breakpoint is a "stop" node that tells the execution to immediately stop if reached.
	 * Note that any other data such as "difference" will be based on the last node analyzed.
	 */
	private AbstractInsnNode breakpoint;
	
	/**
	 * If true, the analyzer will end at the breakpoint (if it is reached)
	 */
	private boolean forceBreak;
	
	/**
	 * If true, backward analysis will return the first instance the stack reaches 0, even
	 * if the node requests more args.
	 */
	private boolean ignoreNeeded;
	private boolean specialDup;
	
	/**
	 * If true, forward analysis will not stop the first time the arg reaches zero, only if the stack size is 0
	 * AFTER a node.
	 */
	private boolean onlyZero;
	
	public ArgsAnalyzer(AbstractInsnNode start, int argSize, Mode mode, int... stopCodes)
	{
		if(argSize < 0)
			throw new IllegalArgumentException("Args needed cannot be negative");
		this.start = start;
		this.argSize = argSize;
		this.mode = mode;
		this.stopCodes = stopCodes;
	}
	
	public void setBreakpoint(AbstractInsnNode breakpoint)
	{
		this.breakpoint = breakpoint;
	}
	
	public void setForcebreak(boolean forceBreak)
	{
		this.forceBreak = forceBreak;
	}
	
	public void setIgnoreNeeded(boolean ignoreNeeded)
	{
		this.ignoreNeeded = ignoreNeeded;
	}
	
	public void setSpecialDup(boolean specialDup)
	{
		this.specialDup = specialDup;
	}
	
	public void setOnlyZero(boolean onlyZero)
	{
		this.onlyZero = onlyZero;
	}
	
	public Result lookupArgs()
	{
		if(mode == Mode.BACKWARDS)
			return lookupArgsBackwards();
		else if(mode == Mode.FORWARDS)
			return lookupArgsForwards();
		else
			throw new IllegalArgumentException("Unknown mode");
	}
	
	/**
	 * Performs an arg lookup backwards.
	 * @return The first insn node where the argSize is 0.
	 */
	private Result lookupArgsBackwards()
	{
		AbstractInsnNode result = null;
    	int diff = 0;
		int needed = argSize;
		AbstractInsnNode ain = start;
		AbstractInsnNode swap = null;
		if(needed == 0 && !forceBreak)
			return new Result(0, needed, ain, swap, null);
		while(ain != null)
    	{
			for(int code : stopCodes)
				if(ain.getOpcode() == code)
					return new FailedResult(diff, needed, ain, swap, null);
			if(breakpoint == ain)
				return new Result(diff, needed, ain, swap, null);
    		int prevNeeded = needed;
    		if((ain.getOpcode() >= Opcodes.ACONST_NULL && ain.getOpcode() <= Opcodes.ICONST_5)
    			|| (ain.getOpcode() >= Opcodes.FCONST_0 && ain.getOpcode() <= Opcodes.FCONST_2)
    			|| ain.getOpcode() == Opcodes.BIPUSH || ain.getOpcode() == Opcodes.SIPUSH)
    			needed--;
    		else if((ain.getOpcode() >= Opcodes.LCONST_0 && ain.getOpcode() <= Opcodes.LCONST_1)
    			|| (ain.getOpcode() >= Opcodes.DCONST_0 && ain.getOpcode() <= Opcodes.DCONST_1))
    			needed -= 2;
    		else if(ain.getOpcode() == Opcodes.LDC)
    		{
    			LdcInsnNode cst = (LdcInsnNode)ain;
    			if(cst.cst instanceof Double || cst.cst instanceof Long)
    				needed -= 2;
    			else 
    				needed--;
    		}else if(ain.getOpcode() == Opcodes.ILOAD || ain.getOpcode() == Opcodes.ALOAD
    			|| ain.getOpcode() == Opcodes.FLOAD)
    			needed--;
    		else if(ain.getOpcode() == Opcodes.DLOAD || ain.getOpcode() == Opcodes.LLOAD)
    			needed -= 2;
    		else if(ain.getOpcode() == Opcodes.ISTORE || ain.getOpcode() == Opcodes.FSTORE
    			|| ain.getOpcode() == Opcodes.ASTORE)
    			needed++;
    		else if(ain.getOpcode() == Opcodes.DSTORE || ain.getOpcode() == Opcodes.LSTORE)
    			needed += 2;
    		else if(ain.getOpcode() == Opcodes.IALOAD || ain.getOpcode() == Opcodes.FALOAD
    			|| ain.getOpcode() == Opcodes.AALOAD || ain.getOpcode() == Opcodes.BALOAD
    			|| ain.getOpcode() == Opcodes.CALOAD || ain.getOpcode() == Opcodes.SALOAD)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.LALOAD || ain.getOpcode() == Opcodes.DALOAD)
    		{
    			needed -= 2;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.IASTORE || ain.getOpcode() == Opcodes.FASTORE
    			|| ain.getOpcode() == Opcodes.AASTORE || ain.getOpcode() == Opcodes.BASTORE
    			|| ain.getOpcode() == Opcodes.CASTORE || ain.getOpcode() == Opcodes.SASTORE)
    			needed += 3;
    		else if(ain.getOpcode() == Opcodes.LASTORE || ain.getOpcode() == Opcodes.DASTORE)
    			needed += 4;
    		else if(ain.getOpcode() == Opcodes.POP)
    			needed++;
    		else if(ain.getOpcode() == Opcodes.POP2)
    			needed += 2;
    		else if(ain.getOpcode() == Opcodes.DUP)
    		{
    			needed--;
    			if(specialDup && needed == 1)
    			{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
    			}
    		}else if(ain.getOpcode() == Opcodes.DUP_X1 && needed != 1 && needed != 2)
    		{
    			needed--;
    			if(needed == 2)
    			{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
    			}
    		}else if(ain.getOpcode() == Opcodes.DUP_X2 && needed != 1 && needed != 2 && needed != 3)
    		{
    			needed--;
    			if(needed == 3)
    			{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
    			}
    		}else if(ain.getOpcode() == Opcodes.DUP2 && needed != 2 && needed != 3)
    		{
    			needed -= 2;
    			if(needed == 2)
    			{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    		}else if(ain.getOpcode() == Opcodes.DUP2_X1 && needed != 2 && needed != 3 && needed != 4)
    		{
    			needed -= 2;
    			if(needed == 3)
    			{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
    			}
    		}else if(ain.getOpcode() == Opcodes.DUP2_X2 && needed != 2 && needed != 3 && needed != 4 && needed != 5)
    		{
    			needed -= 2;
    			if(needed == 4)
    			{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
    			}
    		}else if(ain.getOpcode() == Opcodes.SWAP)
    		{
    			if(needed == 2)
    			{
    				if(swap != null)
    					return new FailedResult(diff, needed, ain, swap, null);
        			swap = ain;
    				needed--;
    			}else if(needed == 1)
    			{
    				if(swap != null)
    					return new FailedResult(diff, needed, ain, swap, null);
        			swap = ain;
    				needed++;
    			}
    		}else if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB
    			|| ain.getOpcode() == Opcodes.IMUL || ain.getOpcode() == Opcodes.IDIV
    			|| ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.ISHL
    			|| ain.getOpcode() == Opcodes.ISHR || ain.getOpcode() == Opcodes.IUSHR
    			|| ain.getOpcode() == Opcodes.IAND || ain.getOpcode() == Opcodes.IOR
    			|| ain.getOpcode() == Opcodes.IXOR)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.LADD || ain.getOpcode() == Opcodes.LSUB
    			|| ain.getOpcode() == Opcodes.LMUL || ain.getOpcode() == Opcodes.LDIV
    			|| ain.getOpcode() == Opcodes.LREM || ain.getOpcode() == Opcodes.LAND 
    			|| ain.getOpcode() == Opcodes.LOR || ain.getOpcode() == Opcodes.LXOR)
    		{
    			needed -= 2;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 4;
    		}else if(ain.getOpcode() == Opcodes.LSHL
    			|| ain.getOpcode() == Opcodes.LSHR || ain.getOpcode() == Opcodes.LUSHR)
    		{
    			needed -= 2;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 3;
    		}else if(ain.getOpcode() == Opcodes.FADD || ain.getOpcode() == Opcodes.FSUB
    			|| ain.getOpcode() == Opcodes.FMUL || ain.getOpcode() == Opcodes.FDIV
    			|| ain.getOpcode() == Opcodes.FREM || ain.getOpcode() == Opcodes.FCMPL
    			|| ain.getOpcode() == Opcodes.FCMPG)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.DADD || ain.getOpcode() == Opcodes.DSUB
    			|| ain.getOpcode() == Opcodes.DMUL || ain.getOpcode() == Opcodes.DDIV
    			|| ain.getOpcode() == Opcodes.DREM)
    		{
    			needed -= 2;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 4;
    		}else if(ain.getOpcode() == Opcodes.LCMP || ain.getOpcode() == Opcodes.DCMPL
    			|| ain.getOpcode() == Opcodes.DCMPG)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 4;
    		}else if(ain.getOpcode() == Opcodes.I2L || ain.getOpcode() == Opcodes.I2D
    			|| ain.getOpcode() == Opcodes.F2L || ain.getOpcode() == Opcodes.F2D)
    			needed--;
    		else if(ain.getOpcode() == Opcodes.L2I || ain.getOpcode() == Opcodes.D2I
    			|| ain.getOpcode() == Opcodes.L2F || ain.getOpcode() == Opcodes.D2F)
    			needed++;
    		else if(ain.getOpcode() == Opcodes.I2F || ain.getOpcode() == Opcodes.I2B 
    			|| ain.getOpcode() == Opcodes.I2C || ain.getOpcode() == Opcodes.I2S 
    			|| ain.getOpcode() == Opcodes.F2I || ain.getOpcode() == Opcodes.INEG 
    			|| ain.getOpcode() == Opcodes.FNEG)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed++;
    		}else if(ain.getOpcode() == Opcodes.L2D || ain.getOpcode() == Opcodes.D2L
    			|| ain.getOpcode() == Opcodes.DNEG || ain.getOpcode() == Opcodes.LNEG)
    		{
    			needed -= 2;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.NEW)
    			needed--;
    		else if(ain.getOpcode() == Opcodes.GETSTATIC || ain.getOpcode() == Opcodes.GETFIELD)
    		{
    			if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG)
    				needed -= 2;
    			else
    				needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			if(ain.getOpcode() == Opcodes.GETFIELD)
    				needed++;
    		}else if(ain.getOpcode() == Opcodes.PUTSTATIC || ain.getOpcode() == Opcodes.PUTFIELD)
    		{
    			if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG)
    				needed += 2;
    			else
    				needed++;
    			if(ain.getOpcode() == Opcodes.PUTFIELD)
    				needed++;
    		}else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL || ain.getOpcode() == Opcodes.INVOKESPECIAL
    			|| ain.getOpcode() == Opcodes.INVOKEINTERFACE || ain.getOpcode() == Opcodes.INVOKESTATIC)
    		{
    			if(Type.getReturnType(((MethodInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getReturnType(((MethodInsnNode)ain).desc).getSort() == Type.LONG)
    				needed -= 2;
    			else if(Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.VOID
    				&& Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.METHOD)
    				needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			if(ain.getOpcode() != Opcodes.INVOKESTATIC)
    				needed++;
    			for(Type t : Type.getArgumentTypes(((MethodInsnNode)ain).desc)) 
    			{
                     if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
                    	 needed += 2;
                     else
                    	 needed++;
    			}
    		}else if(ain.getOpcode() == Opcodes.INVOKEDYNAMIC)
    		{
    			if(Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() == Type.LONG)
    				needed -= 2;
    			else if(Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() != Type.VOID
    				&& Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() != Type.METHOD)
    				needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			for(Type t : Type.getArgumentTypes(((InvokeDynamicInsnNode)ain).desc)) 
    			{
                     if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
                    	 needed += 2;
                     else
                    	 needed++;
    			}
    		}else if((ain.getOpcode() >= Opcodes.IFEQ && ain.getOpcode() <= Opcodes.IFLE)
    			|| ain.getOpcode() == Opcodes.IFNULL || ain.getOpcode() == Opcodes.IFNONNULL 
    			|| ain.getOpcode() == Opcodes.TABLESWITCH || ain.getOpcode() == Opcodes.LOOKUPSWITCH 
    			|| ain.getOpcode() == Opcodes.ATHROW || ain.getOpcode() == Opcodes.RET)
    			return new FailedResult(diff, needed, ain, swap, null);
    		else if(ain.getOpcode() >= Opcodes.IF_ICMPEQ && ain.getOpcode() <= Opcodes.IF_ACMPNE)
    			return new FailedResult(diff, needed, ain, swap, null);
    		else if(ain.getOpcode() == Opcodes.GOTO)
    			return new FailedResult(diff, needed, ain, swap, null);
    		else if(ain.getOpcode() == Opcodes.MULTIANEWARRAY)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed += ((MultiANewArrayInsnNode)ain).dims;		
    		}else if(ain.getOpcode() == Opcodes.INSTANCEOF)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed++;
    		}else if(ain.getOpcode() == Opcodes.CHECKCAST)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed++;
    		}else if(ain.getOpcode() == Opcodes.NEWARRAY || ain.getOpcode() == Opcodes.ANEWARRAY
    			|| ain.getOpcode() == Opcodes.ARRAYLENGTH)
    		{
    			needed--;
    			if(needed <= 0 && ignoreNeeded)
        		{
    				diff = needed - prevNeeded;
        			result = ain;
        			break;
        		}
    			needed++;
    		}else if(ain.getOpcode() == Opcodes.MONITORENTER || ain.getOpcode() == Opcodes.MONITOREXIT)
    			needed++;
    		else if(ain.getOpcode() == Opcodes.IRETURN || ain.getOpcode() == Opcodes.FRETURN
    			|| ain.getOpcode() == Opcodes.ARETURN)
    			return new FailedResult(diff, needed, ain, swap, null);
    		else if(ain.getOpcode() == Opcodes.LRETURN || ain.getOpcode() == Opcodes.DRETURN)
    			return new FailedResult(diff, needed, ain, swap, null);
    		else if(ain.getOpcode() == Opcodes.RETURN)
    			return new FailedResult(diff, needed, ain, swap, null);
    		if(Utils.isInstruction(ain))
    			diff = needed - prevNeeded;
    		if(needed <= 0 && !forceBreak)
    		{
    			result = ain;
    			break;
    		}
    		ain = ain.getPrevious();
    	}
		return new Result(diff, needed, result, swap, null);
	}
	
	/**
	 * Performs an arg lookup forwards.
	 * @return The first insn node where the argSize is 0.
	 */
	private Result lookupArgsForwards()
	{
		List<AbstractInsnNode> skippedDups = new ArrayList<>();
		AbstractInsnNode result = null;
    	int diff = 0;
		int stackSize = argSize;
		AbstractInsnNode ain = start;
		AbstractInsnNode swap = null;
		if(stackSize == 0 && !forceBreak)
			return new Result(0, stackSize, ain, swap, skippedDups);
		while(ain != null)
    	{
			for(int code : stopCodes)
				if(ain.getOpcode() == code)
					return new FailedResult(diff, stackSize, ain, swap, skippedDups);
			if(breakpoint == ain)
				return new Result(diff, -stackSize, ain, swap, skippedDups);
    		int prevNeeded = stackSize;
    		if((ain.getOpcode() >= Opcodes.ACONST_NULL && ain.getOpcode() <= Opcodes.ICONST_5)
    			|| (ain.getOpcode() >= Opcodes.FCONST_0 && ain.getOpcode() <= Opcodes.FCONST_2)
    			|| ain.getOpcode() == Opcodes.BIPUSH || ain.getOpcode() == Opcodes.SIPUSH)
    			stackSize++;
    		else if((ain.getOpcode() >= Opcodes.LCONST_0 && ain.getOpcode() <= Opcodes.LCONST_1)
    			|| (ain.getOpcode() >= Opcodes.DCONST_0 && ain.getOpcode() <= Opcodes.DCONST_1))
    			stackSize += 2;
    		else if(ain.getOpcode() == Opcodes.LDC)
    		{
    			LdcInsnNode cst = (LdcInsnNode)ain;
    			if(cst.cst instanceof Double || cst.cst instanceof Long)
    				stackSize += 2;
    			else 
    				stackSize++;
    		}else if(ain.getOpcode() == Opcodes.ILOAD || ain.getOpcode() == Opcodes.ALOAD
    			|| ain.getOpcode() == Opcodes.FLOAD)
    			stackSize++;
    		else if(ain.getOpcode() == Opcodes.DLOAD || ain.getOpcode() == Opcodes.LLOAD)
    			stackSize += 2;
    		else if(ain.getOpcode() == Opcodes.ISTORE || ain.getOpcode() == Opcodes.FSTORE
    			|| ain.getOpcode() == Opcodes.ASTORE)
    			stackSize--;
    		else if(ain.getOpcode() == Opcodes.DSTORE || ain.getOpcode() == Opcodes.LSTORE)
    			stackSize -= 2;
    		else if(ain.getOpcode() == Opcodes.IALOAD || ain.getOpcode() == Opcodes.FALOAD
    			|| ain.getOpcode() == Opcodes.AALOAD || ain.getOpcode() == Opcodes.BALOAD
    			|| ain.getOpcode() == Opcodes.CALOAD || ain.getOpcode() == Opcodes.SALOAD)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.LALOAD || ain.getOpcode() == Opcodes.DALOAD)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.IASTORE || ain.getOpcode() == Opcodes.FASTORE
    			|| ain.getOpcode() == Opcodes.AASTORE || ain.getOpcode() == Opcodes.BASTORE
    			|| ain.getOpcode() == Opcodes.CASTORE || ain.getOpcode() == Opcodes.SASTORE)
    			stackSize -= 3;
    		else if(ain.getOpcode() == Opcodes.LASTORE || ain.getOpcode() == Opcodes.DASTORE)
    			stackSize -= 4;
    		else if(ain.getOpcode() == Opcodes.POP)
    			stackSize--;
    		else if(ain.getOpcode() == Opcodes.POP2)
    			stackSize -= 2;
    		else if(ain.getOpcode() == Opcodes.DUP)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.DUP_X1)
    		{
    			if(stackSize > 2)
    			{
    				stackSize--;
        			if(stackSize <= 0 && !forceBreak && !onlyZero)
            		{
        				diff = stackSize - prevNeeded;
            			result = ain;
            			break;
            		}
        			stackSize += 2;
    			}else if(stackSize == 1)
    			{
    				stackSize--;
    				if(stackSize <= 0 && !forceBreak && !onlyZero)
    				{
    					diff = stackSize - prevNeeded;
    					result = ain;
    					break;
    				}
    			}else
    				skippedDups.add(ain);
    		}else if(ain.getOpcode() == Opcodes.DUP_X2)
    		{
    			if(stackSize > 3)
    			{
    				stackSize--;
        			if(stackSize <= 0 && !forceBreak && !onlyZero)
            		{
        				diff = stackSize - prevNeeded;
            			result = ain;
            			break;
            		}
        			stackSize += 2;
    			}else if(stackSize == 1)
    			{
    				stackSize--;
    				if(stackSize <= 0 && !forceBreak && !onlyZero)
    				{
    					diff = stackSize - prevNeeded;
    					result = ain;
    					break;
    				}
    			}else
    				skippedDups.add(ain);
    		}else if(ain.getOpcode() == Opcodes.DUP2)
    		{
    			if(stackSize > 2)
    			{
    				stackSize -= 2;
        			if(stackSize <= 0 && !forceBreak && !onlyZero)
            		{
        				diff = stackSize - prevNeeded;
            			result = ain;
            			break;
            		}
        			stackSize += 4;
    			}else
    			{
    				stackSize -= 2;
    				diff = stackSize - prevNeeded;
    				result = ain;
    				break;
    			}
    		}else if(ain.getOpcode() == Opcodes.DUP2_X1)
    		{
    			if(stackSize > 3)
    			{
    				stackSize -= 2;
        			if(stackSize <= 0 && !forceBreak && !onlyZero)
            		{
        				diff = stackSize - prevNeeded;
            			result = ain;
            			break;
            		}
        			stackSize += 4;
    			}else if(stackSize == 1 || stackSize == 2)
    			{
    				stackSize -= 2;
    				if(stackSize <= 0 && !forceBreak && !onlyZero)
    				{
    					diff = stackSize - prevNeeded;
    					result = ain;
    					break;
    				}
    			}else
    				skippedDups.add(ain);
    		}else if(ain.getOpcode() == Opcodes.DUP2_X2)
    		{
    			if(stackSize > 4)
    			{
    				stackSize -= 2;
        			if(stackSize <= 0 && !forceBreak && !onlyZero)
            		{
        				diff = stackSize - prevNeeded;
            			result = ain;
            			break;
            		}
        			stackSize += 4;
    			}else if(stackSize == 1 || stackSize == 2)
    			{
    				stackSize -= 2;
    				if(stackSize <= 0 && !forceBreak && !onlyZero)
    				{
    					diff = stackSize - prevNeeded;
    					result = ain;
    					break;
    				}
    			}else
    				skippedDups.add(ain);
    		}else if(ain.getOpcode() == Opcodes.SWAP)
    		{
    			if(stackSize == 2)
    			{
    				if(swap != null)
    					return new FailedResult(stackSize - prevNeeded, stackSize, ain, swap, skippedDups);
        			swap = ain;
    				stackSize--;
    			}else if(stackSize == 1)
    			{
    				if(swap != null)
    					return new FailedResult(stackSize - prevNeeded, stackSize, ain, swap, skippedDups);
        			swap = ain;
    				stackSize++;
    			}
    		}else if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB
    			|| ain.getOpcode() == Opcodes.IMUL || ain.getOpcode() == Opcodes.IDIV
    			|| ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.ISHL
    			|| ain.getOpcode() == Opcodes.ISHR || ain.getOpcode() == Opcodes.IUSHR
    			|| ain.getOpcode() == Opcodes.IAND || ain.getOpcode() == Opcodes.IOR
    			|| ain.getOpcode() == Opcodes.IXOR)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.LADD || ain.getOpcode() == Opcodes.LSUB
    			|| ain.getOpcode() == Opcodes.LMUL || ain.getOpcode() == Opcodes.LDIV
    			|| ain.getOpcode() == Opcodes.LREM || ain.getOpcode() == Opcodes.LAND 
    			|| ain.getOpcode() == Opcodes.LOR || ain.getOpcode() == Opcodes.LXOR)
    		{
    			stackSize -= 4;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.LSHL
    			|| ain.getOpcode() == Opcodes.LSHR || ain.getOpcode() == Opcodes.LUSHR)
    		{
    			stackSize -= 3;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.FADD || ain.getOpcode() == Opcodes.FSUB
    			|| ain.getOpcode() == Opcodes.FMUL || ain.getOpcode() == Opcodes.FDIV
    			|| ain.getOpcode() == Opcodes.FREM || ain.getOpcode() == Opcodes.FCMPL
    			|| ain.getOpcode() == Opcodes.FCMPG)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.DADD || ain.getOpcode() == Opcodes.DSUB
    			|| ain.getOpcode() == Opcodes.DMUL || ain.getOpcode() == Opcodes.DDIV
    			|| ain.getOpcode() == Opcodes.DREM)
    		{
    			stackSize -= 4;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.LCMP || ain.getOpcode() == Opcodes.DCMPL
    			|| ain.getOpcode() == Opcodes.DCMPG)
    		{
    			stackSize -= 4;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.I2L || ain.getOpcode() == Opcodes.I2D
    			|| ain.getOpcode() == Opcodes.F2L || ain.getOpcode() == Opcodes.F2D)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.L2I || ain.getOpcode() == Opcodes.D2I
    			|| ain.getOpcode() == Opcodes.L2F || ain.getOpcode() == Opcodes.D2F)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.I2F || ain.getOpcode() == Opcodes.I2B 
    			|| ain.getOpcode() == Opcodes.I2C || ain.getOpcode() == Opcodes.I2S 
    			|| ain.getOpcode() == Opcodes.F2I || ain.getOpcode() == Opcodes.INEG 
    			|| ain.getOpcode() == Opcodes.FNEG)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.L2D || ain.getOpcode() == Opcodes.D2L
    			|| ain.getOpcode() == Opcodes.DNEG || ain.getOpcode() == Opcodes.LNEG)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.NEW)
    			stackSize++;
    		else if(ain.getOpcode() == Opcodes.GETSTATIC || ain.getOpcode() == Opcodes.GETFIELD)
    		{
    			if(ain.getOpcode() == Opcodes.GETFIELD)
    				stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG)
    				stackSize += 2;
    			else
    				stackSize++;
    		}else if(ain.getOpcode() == Opcodes.PUTSTATIC || ain.getOpcode() == Opcodes.PUTFIELD)
    		{
    			if(ain.getOpcode() == Opcodes.PUTFIELD)
    				stackSize--;
    			if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG)
    				stackSize -= 2;
    			else
    				stackSize--;
    		}else if(ain.getOpcode() == Opcodes.INVOKEVIRTUAL || ain.getOpcode() == Opcodes.INVOKESPECIAL
    			|| ain.getOpcode() == Opcodes.INVOKEINTERFACE || ain.getOpcode() == Opcodes.INVOKESTATIC)
    		{
    			if(ain.getOpcode() != Opcodes.INVOKESTATIC)
    				stackSize--;
    			for(Type t : Type.getArgumentTypes(((MethodInsnNode)ain).desc)) 
    			{
                     if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
                    	 stackSize -= 2;
                     else
                    	 stackSize--;
    			}
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			if(Type.getReturnType(((MethodInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getReturnType(((MethodInsnNode)ain).desc).getSort() == Type.LONG)
    				stackSize += 2;
    			else if(Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.VOID
    				&& Type.getReturnType(((MethodInsnNode)ain).desc).getSort() != Type.METHOD)
    				stackSize++;
    		}else if(ain.getOpcode() == Opcodes.INVOKEDYNAMIC)
    		{
    			for(Type t : Type.getArgumentTypes(((InvokeDynamicInsnNode)ain).desc)) 
    			{
                     if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
                    	 stackSize -= 2;
                     else
                    	 stackSize--;
    			}
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			if(Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() == Type.LONG)
    				stackSize += 2;
    			else if(Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() != Type.VOID
    				&& Type.getReturnType(((InvokeDynamicInsnNode)ain).desc).getSort() != Type.METHOD)
    				stackSize++;
    		}else if((ain.getOpcode() >= Opcodes.IFEQ && ain.getOpcode() <= Opcodes.IFLE)
    			|| ain.getOpcode() == Opcodes.IFNULL || ain.getOpcode() == Opcodes.IFNONNULL 
    			|| ain.getOpcode() == Opcodes.TABLESWITCH || ain.getOpcode() == Opcodes.LOOKUPSWITCH 
    			|| ain.getOpcode() == Opcodes.ATHROW || ain.getOpcode() == Opcodes.RET)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			return new FailedResult(stackSize - prevNeeded, stackSize, ain, swap, skippedDups);
    		}else if(ain.getOpcode() >= Opcodes.IF_ICMPEQ && ain.getOpcode() <= Opcodes.IF_ACMPNE)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			return new FailedResult(stackSize - prevNeeded, stackSize, ain, swap, skippedDups);
    		}else if(ain.getOpcode() == Opcodes.INSTANCEOF)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.CHECKCAST)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.GOTO)
    			return new FailedResult(diff, stackSize, ain, swap, skippedDups);
    		else if(ain.getOpcode() == Opcodes.MULTIANEWARRAY)
    		{
    			stackSize -= ((MultiANewArrayInsnNode)ain).dims;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.NEWARRAY || ain.getOpcode() == Opcodes.ANEWARRAY
    			|| ain.getOpcode() == Opcodes.ARRAYLENGTH)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.MONITORENTER || ain.getOpcode() == Opcodes.MONITOREXIT)
    			stackSize--;
    		else if(ain.getOpcode() == Opcodes.IRETURN || ain.getOpcode() == Opcodes.FRETURN
    			|| ain.getOpcode() == Opcodes.ARETURN)
    		{
    			stackSize--;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
    			{
    				diff = stackSize - prevNeeded;
    				result = ain;
    				break;
    			}
    			return new FailedResult(stackSize - prevNeeded, stackSize, ain, swap, skippedDups);
    		}else if(ain.getOpcode() == Opcodes.LRETURN || ain.getOpcode() == Opcodes.DRETURN)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0 && !forceBreak && !onlyZero)
    			{
    				diff = stackSize - prevNeeded;
    				result = ain;
    				break;
    			}
    			return new FailedResult(stackSize - prevNeeded, stackSize, ain, swap, skippedDups);
    		}else if(ain.getOpcode() == Opcodes.RETURN)
    			return new FailedResult(diff, stackSize, ain, swap, skippedDups);
    		if(Utils.isInstruction(ain))
    			diff = stackSize - prevNeeded;
    		if(stackSize <= 0 && !forceBreak)
    		{
    			result = ain;
    			break;
    		}
    		ain = ain.getNext();
    	}
		return new Result(diff, -stackSize, result, swap, skippedDups);
	}
	
	public enum Mode
	{
		FORWARDS,
		BACKWARDS;
	}
	
	public static class Result
	{
		private final int diff;
		private final int argsNeeded;
		private final AbstractInsnNode firstArgInsn;
		private final AbstractInsnNode swap;
		private final List<AbstractInsnNode> skippedDups;
		
		public Result(int diff, int argsNeeded, AbstractInsnNode firstArgInsn, 
			AbstractInsnNode swap, List<AbstractInsnNode> skippedDups)
		{
			this.diff = diff;
			this.argsNeeded = argsNeeded;
			this.firstArgInsn = firstArgInsn;
			this.swap = swap;
			this.skippedDups = skippedDups;
		}
		
		public int getDiff()
		{
			return diff;
		}
		
		public int getArgsNeeded()
		{
			return argsNeeded;
		}
		
		public AbstractInsnNode getFirstArgInsn()
		{
			return firstArgInsn;
		}
		
		public AbstractInsnNode getSwap()
		{
			return swap;
		}
		
		public List<AbstractInsnNode> getSkippedDups()
		{
			return skippedDups;
		}
	}
	
	public static class FailedResult extends Result
	{
		private final int diff;
		private final int extraArgs;
		private final AbstractInsnNode failedPoint;
		private final AbstractInsnNode swap;
		private final List<AbstractInsnNode> skippedDups;
		
		public FailedResult(int diff, int extraArgs, AbstractInsnNode failedPoint, AbstractInsnNode swap, List<AbstractInsnNode> skippedDups)
		{
			super(-1, -1, null, null, null);
			this.diff = diff;
			this.extraArgs = extraArgs;
			this.failedPoint = failedPoint;
			this.swap = swap;
			this.skippedDups = skippedDups;
		}
		
		public AbstractInsnNode getFailedPoint()
		{
			return failedPoint;
		}
		
		public int getLastSuccessfulNodeDiff()
		{
			return diff;
		}
		
		public int getExtraArgs()
		{
			return extraArgs;
		}
		
		public AbstractInsnNode getSwapAtFailure()
		{
			return swap;
		}
		
		public List<AbstractInsnNode> getSkippedDupsAtFailure()
		{
			return skippedDups;
		}
		
		@Override
		public int getDiff()
		{
			throw new RuntimeException("ArgsAnalyzer lookup failed");
		}
		
		@Override
		public int getArgsNeeded()
		{
			throw new RuntimeException("ArgsAnalyzer lookup failed");
		}
		
		@Override
		public AbstractInsnNode getFirstArgInsn()
		{
			throw new RuntimeException("ArgsAnalyzer lookup failed");
		}
		
		@Override
		public AbstractInsnNode getSwap()
		{
			throw new RuntimeException("ArgsAnalyzer lookup failed");
		}
		
		@Override
		public List<AbstractInsnNode> getSkippedDups()
		{
			throw new RuntimeException("ArgsAnalyzer lookup failed");
		}
	}
}
