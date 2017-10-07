package com.javadeobfuscator.deobfuscator.analyzer;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.Utils;

/**
 * This class is used to retrieve where a method's args begin/end.
 * Somtimes, obfuscators don't like to put a method's args right before its method that uses it.
 * This analyzer can only run backwards, because running forwards doesn't work.
 * @author ThisTestUser
 */
public class ArgsAnalyzer
{
	private final MethodNode method;

	/**
	 * Which index do we start at?
	 */
	private final int startIndex;
	
	/**
	 * How many arguments do we need?
	 */
	private final int argSize;
	
	public ArgsAnalyzer(MethodNode method, int startIndex, int argSize)
	{
		this.method = method;
		this.startIndex = startIndex;
		this.argSize = argSize;
	}
	
	/**
	 * Performs an arg lookup.
	 * @return The list of nodes as the arguments, in order.
	 */
	public List<AbstractInsnNode> lookupArgs()
	{
		//Reverse bytecode analysis
		List<AbstractInsnNode> stack = new ArrayList<>();
		int neededNodes = 0;
		//This isn't complete, fix as new errors come
		for(int i = startIndex; i >= 0; i--)
		{
			AbstractInsnNode node = method.instructions.get(i);
			if(Utils.isNumber(node))
			{
				stack.add(0, node);
				if(neededNodes > 0)
				{
					neededNodes--;
					stack.remove(0);
				}
				if(stack.size() >= argSize && neededNodes == 0)
				{
					if(stack.size() > argSize)
						throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
						+ argSize + " !");
					return stack;
				}
			}else if(node.getOpcode() == Opcodes.ACONST_NULL
				|| node.getOpcode() == Opcodes.LDC)
			{
				stack.add(0, node);
				if(neededNodes > 0)
				{
					neededNodes--;
					stack.remove(0);
				}
				if(stack.size() >= argSize && neededNodes == 0)
				{
					if(stack.size() > argSize)
						throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
						+ argSize + " !");
					return stack;
				}
			}else if(node.getOpcode() >= Opcodes.ILOAD
				&& node.getOpcode() <= Opcodes.ALOAD)
			{
				stack.add(0, node);
				if(neededNodes > 0)
				{
					neededNodes--;
					stack.remove(0);
				}
				if(stack.size() >= argSize && neededNodes == 0)
				{
					if(stack.size() > argSize)
						throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
						+ argSize + " !");
					return stack;
				}
			}else if(node.getOpcode() >= Opcodes.ISTORE
				&& node.getOpcode() <= Opcodes.ASTORE)
				neededNodes++;
			else if(node.getOpcode() == Opcodes.INVOKEVIRTUAL
				|| node.getOpcode() == Opcodes.INVOKESPECIAL
				|| node.getOpcode() == Opcodes.INVOKEINTERFACE)
			{
				//Note that we add one for the instance
				MethodInsnNode cast = (MethodInsnNode)node;
				neededNodes += Type.getArgumentTypes(cast.desc).length + 1;
				if(Type.getReturnType(cast.desc).getReturnType().getSort() > 0
					&& Type.getReturnType(cast.desc).getReturnType().getSort() < 11)
				{
					stack.add(0, node);
					if(neededNodes > 0)
					{
						neededNodes--;
						stack.remove(0);
					}
					if(stack.size() >= argSize && neededNodes == 0)
					{
						if(stack.size() > argSize)
							throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
							+ argSize + " !");
						return stack;
					}
				}
			}else if(node.getOpcode() == Opcodes.INVOKESTATIC)
			{
				MethodInsnNode cast = (MethodInsnNode)node;
				neededNodes += Type.getArgumentTypes(cast.desc).length;
				if(Type.getReturnType(cast.desc).getReturnType().getSort() > 0
					&& Type.getReturnType(cast.desc).getReturnType().getSort() < 11)
				{
					stack.add(0, node);
					if(neededNodes > 0)
					{
						neededNodes--;
						stack.remove(0);
					}
					if(stack.size() >= argSize && neededNodes == 0)
					{
						if(stack.size() > argSize)
							throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
							+ argSize + " !");
						return stack;
					}
				}
			}else if(node.getOpcode() == Opcodes.GETSTATIC || node.getOpcode() == Opcodes.GETFIELD)
			{
				if(node.getOpcode() == Opcodes.GETFIELD)
					neededNodes++;
				stack.add(0, node);
				if(neededNodes > 0)
				{
					neededNodes--;
					stack.remove(0);
				}
				if(stack.size() >= argSize && neededNodes == 0)
				{
					if(stack.size() > argSize)
						throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
						+ argSize + " !");
					return stack;
				}
			}else if(node.getOpcode() == Opcodes.PUTFIELD || node.getOpcode() == Opcodes.PUTSTATIC)
			{
				if(node.getOpcode() == Opcodes.PUTFIELD)
					neededNodes++;
				neededNodes++;
			}else if(node.getOpcode() == Opcodes.INVOKEDYNAMIC)
			{
				//InvokeDynamic adds to the stack
				stack.add(0, node);
				if(neededNodes > 0)
				{
					neededNodes--;
					stack.remove(0);
				}
				if(stack.size() >= argSize && neededNodes == 0)
				{
					if(stack.size() > argSize)
						throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
						+ argSize + " !");
					return stack;
				}
			}else if(node.getOpcode() == Opcodes.NEW)
			{
				if(node.getNext() != null 
					&& node.getNext().getOpcode() == Opcodes.DUP)
				{
					stack.add(0, node);
					if(neededNodes > 0)
					{
						neededNodes--;
						stack.remove(0);
					}
					if(stack.size() >= argSize && neededNodes == 0)
					{
						if(stack.size() > argSize)
							throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
							+ argSize + " !");
						return stack;
					}
				}
				stack.add(0, node);
				if(neededNodes > 0)
				{
					neededNodes--;
					stack.remove(0);
				}
				if(stack.size() >= argSize && neededNodes == 0)
				{
					if(stack.size() > argSize)
						throw new RuntimeException("Recieved an arg size of " + stack.size() + " but expected "
						+ argSize + " !");
					return stack;
				}
			}
		}
		return null;
	}
}
