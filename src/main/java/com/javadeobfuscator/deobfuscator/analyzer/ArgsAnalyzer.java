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
	 * What direction should we run?
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
	
	public ArgsAnalyzer(AbstractInsnNode start, int argSize, Mode mode, int... stopCodes)
	{
		this.start = start;
		this.argSize = argSize;
		this.mode = mode;
		this.stopCodes = stopCodes;
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
		if(needed == 0)
			return new Result(0, ain, null);
		while(ain != null)
    	{
			for(int code : stopCodes)
				if(ain.getOpcode() == code)
					return null;
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
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.LALOAD || ain.getOpcode() == Opcodes.DALOAD)
    		{
    			needed += 2;
    			needed -= 2;
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
    		else if(ain.getOpcode() == Opcodes.DUP || ain.getOpcode() == Opcodes.DUP_X1 || ain.getOpcode() == Opcodes.DUP_X2)
    			needed--;
    		else if(ain.getOpcode() == Opcodes.DUP2 || ain.getOpcode() == Opcodes.DUP2_X1 || ain.getOpcode() == Opcodes.DUP2_X2)
    			needed -= 2;
    		else if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB
    			|| ain.getOpcode() == Opcodes.IMUL || ain.getOpcode() == Opcodes.IDIV
    			|| ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.ISHL
    			|| ain.getOpcode() == Opcodes.ISHR || ain.getOpcode() == Opcodes.IUSHR
    			|| ain.getOpcode() == Opcodes.IAND || ain.getOpcode() == Opcodes.IOR
    			|| ain.getOpcode() == Opcodes.IXOR)
    		{
    			needed--;
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.LADD || ain.getOpcode() == Opcodes.LSUB
    			|| ain.getOpcode() == Opcodes.LMUL || ain.getOpcode() == Opcodes.LDIV
    			|| ain.getOpcode() == Opcodes.LREM || ain.getOpcode() == Opcodes.LSHL
    			|| ain.getOpcode() == Opcodes.LSHR || ain.getOpcode() == Opcodes.LUSHR
    			|| ain.getOpcode() == Opcodes.LAND || ain.getOpcode() == Opcodes.LOR
    			|| ain.getOpcode() == Opcodes.LXOR)
    		{
    			needed -= 2;
    			needed += 4;
    		}else if(ain.getOpcode() == Opcodes.FADD || ain.getOpcode() == Opcodes.FSUB
    			|| ain.getOpcode() == Opcodes.FMUL || ain.getOpcode() == Opcodes.FDIV
    			|| ain.getOpcode() == Opcodes.FREM || ain.getOpcode() == Opcodes.FCMPL
    			|| ain.getOpcode() == Opcodes.FCMPG)
    		{
    			needed--;
    			needed += 2;
    		}else if(ain.getOpcode() == Opcodes.DADD || ain.getOpcode() == Opcodes.DSUB
    			|| ain.getOpcode() == Opcodes.DMUL || ain.getOpcode() == Opcodes.DDIV
    			|| ain.getOpcode() == Opcodes.DREM)
    		{
    			needed -= 2;
    			needed += 4;
    		}else if(ain.getOpcode() == Opcodes.LCMP || ain.getOpcode() == Opcodes.DCMPL
    			|| ain.getOpcode() == Opcodes.DCMPG)
    		{
    			needed--;
    			needed += 4;
    		}else if(ain.getOpcode() == Opcodes.I2L || ain.getOpcode() == Opcodes.I2D
    			|| ain.getOpcode() == Opcodes.F2L || ain.getOpcode() == Opcodes.F2D)
    			needed--;
    		else if(ain.getOpcode() == Opcodes.L2I || ain.getOpcode() == Opcodes.D2I
    			|| ain.getOpcode() == Opcodes.L2F || ain.getOpcode() == Opcodes.D2F)
    			needed++;
    		else if(ain.getOpcode() == Opcodes.NEW)
    			needed--;
    		else if(ain.getOpcode() == Opcodes.GETSTATIC || ain.getOpcode() == Opcodes.GETFIELD)
    		{
    			if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE
    				|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG)
    				needed -= 2;
    			else
    				needed--;
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
    			return null;
    		else if(ain.getOpcode() >= Opcodes.IF_ICMPEQ && ain.getOpcode() <= Opcodes.IF_ACMPNE)
    			return null;
    		else if(ain.getOpcode() == Opcodes.GOTO)
    			return null;
    		else if(ain.getOpcode() == Opcodes.MULTIANEWARRAY)
    			needed += ((MultiANewArrayInsnNode)ain).dims - 1;
    		else if(ain.getOpcode() == Opcodes.IRETURN || ain.getOpcode() == Opcodes.FRETURN
    			|| ain.getOpcode() == Opcodes.ARETURN)
    			return null;	
    		else if(ain.getOpcode() == Opcodes.LRETURN || ain.getOpcode() == Opcodes.DRETURN)
    			return null;	
    		diff = needed - prevNeeded;
    		if(needed <= 0)
    		{
    			result = ain;
    			break;
    		}
    		ain = Utils.getPrevious(ain);
    	}
		return new Result(diff, result, null);
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
		if(stackSize == 0)
			return new Result(0, ain, skippedDups);
		while(ain != null)
    	{
			for(int code : stopCodes)
				if(ain.getOpcode() == code)
					return null;
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
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.LALOAD || ain.getOpcode() == Opcodes.DALOAD)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0)
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
    			stackSize++;
    		else if(ain.getOpcode() == Opcodes.DUP_X1)
    		{
    			if(stackSize > 2)
    				stackSize++;
    			else
    				skippedDups.add(ain);
    		}else if(ain.getOpcode() == Opcodes.DUP_X2)
    		{
    			if(stackSize > 3)
    				stackSize++;
    			else
    				skippedDups.add(ain);
    		}else if(ain.getOpcode() == Opcodes.DUP2)
    		{
    			if(stackSize > 2)
    				stackSize += 2;
    		}else if(ain.getOpcode() == Opcodes.DUP2_X1 || ain.getOpcode() == Opcodes.DUP2_X2)
    			return null;
    		else if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB
    			|| ain.getOpcode() == Opcodes.IMUL || ain.getOpcode() == Opcodes.IDIV
    			|| ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.ISHL
    			|| ain.getOpcode() == Opcodes.ISHR || ain.getOpcode() == Opcodes.IUSHR
    			|| ain.getOpcode() == Opcodes.IAND || ain.getOpcode() == Opcodes.IOR
    			|| ain.getOpcode() == Opcodes.IXOR)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.LADD || ain.getOpcode() == Opcodes.LSUB
    			|| ain.getOpcode() == Opcodes.LMUL || ain.getOpcode() == Opcodes.LDIV
    			|| ain.getOpcode() == Opcodes.LREM || ain.getOpcode() == Opcodes.LSHL
    			|| ain.getOpcode() == Opcodes.LSHR || ain.getOpcode() == Opcodes.LUSHR
    			|| ain.getOpcode() == Opcodes.LAND || ain.getOpcode() == Opcodes.LOR
    			|| ain.getOpcode() == Opcodes.LXOR)
    		{
    			stackSize -= 4;
    			if(stackSize <= 0)
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
    			if(stackSize <= 0)
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
    			if(stackSize <= 0)
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
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.I2L || ain.getOpcode() == Opcodes.I2D
    			|| ain.getOpcode() == Opcodes.F2L || ain.getOpcode() == Opcodes.F2D)
    			stackSize++;
    		else if(ain.getOpcode() == Opcodes.L2I || ain.getOpcode() == Opcodes.D2I
    			|| ain.getOpcode() == Opcodes.L2F || ain.getOpcode() == Opcodes.D2F)
    			stackSize--;
    		else if(ain.getOpcode() == Opcodes.NEW)
    			stackSize++;
    		else if(ain.getOpcode() == Opcodes.GETSTATIC || ain.getOpcode() == Opcodes.GETFIELD)
    		{
    			if(ain.getOpcode() == Opcodes.GETFIELD)
    				stackSize--;
    			if(stackSize <= 0)
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
    			if(stackSize <= 0)
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
    			if(stackSize <= 0)
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
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			return null;
    		}else if(ain.getOpcode() >= Opcodes.IF_ICMPEQ && ain.getOpcode() <= Opcodes.IF_ACMPNE)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			return null;
    		}else if(ain.getOpcode() == Opcodes.INSTANCEOF)
    		{
    			stackSize--;
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.GOTO)
    			return null;
    		else if(ain.getOpcode() == Opcodes.MULTIANEWARRAY)
    			stackSize -= ((MultiANewArrayInsnNode)ain).dims - 1;
    		else if(ain.getOpcode() == Opcodes.NEWARRAY || ain.getOpcode() == Opcodes.ANEWARRAY
    			|| ain.getOpcode() == Opcodes.ARRAYLENGTH)
    		{
    			stackSize--;
    			if(stackSize <= 0)
        		{
    				diff = stackSize - prevNeeded;
        			result = ain;
        			break;
        		}
    			stackSize++;
    		}else if(ain.getOpcode() == Opcodes.IRETURN || ain.getOpcode() == Opcodes.FRETURN
    			|| ain.getOpcode() == Opcodes.ARETURN)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0)
    			{
    				diff = stackSize - prevNeeded;
    				result = ain;
    				break;
    			}
    			return null;	
    		}else if(ain.getOpcode() == Opcodes.LRETURN || ain.getOpcode() == Opcodes.DRETURN)
    		{
    			stackSize -= 2;
    			if(stackSize <= 0)
    			{
    				diff = stackSize - prevNeeded;
    				result = ain;
    				break;
    			}
    			return null;	
    		}
    		diff = stackSize - prevNeeded;
    		if(stackSize <= 0)
    		{
    			result = ain;
    			break;
    		}
    		ain = Utils.getNext(ain);
    	}
		return new Result(diff, result, skippedDups);
	}
	
	public enum Mode
	{
		FORWARDS,
		BACKWARDS;
	}
	
	public static class Result
	{
		private final int diff;
		private final AbstractInsnNode firstArgInsn;
		private final List<AbstractInsnNode> skippedDups;
		
		public Result(int diff, AbstractInsnNode firstArgInsn, List<AbstractInsnNode> skippedDups)
		{
			this.diff = diff;
			this.firstArgInsn = firstArgInsn;
			this.skippedDups = skippedDups;
		}
		
		public int getDiff()
		{
			return diff;
		}
		
		public AbstractInsnNode getFirstArgInsn()
		{
			return firstArgInsn;
		}
		
		public List<AbstractInsnNode> getSkippedDups()
		{
			return skippedDups;
		}
	}
}
