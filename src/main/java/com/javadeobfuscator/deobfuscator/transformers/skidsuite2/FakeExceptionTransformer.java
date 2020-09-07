package com.javadeobfuscator.deobfuscator.transformers.skidsuite2;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class FakeExceptionTransformer extends Transformer<TransformerConfig>
{
	@Override
	public boolean transform() throws Throwable
	{
		System.out.println("[SkidSuite] [FakeExceptionTransformer] Starting");
		AtomicInteger counter = new AtomicInteger();
		classNodes().forEach(classNode -> classNode.methods.stream()
			.filter(
				methodNode -> methodNode.instructions.getFirst() != null)
			.forEach(methodNode -> {
				List<TryCatchBlockNode> remove = new ArrayList<>();
				List<AbstractInsnNode> nodesRemove = new ArrayList<>();
				for(TryCatchBlockNode tc : methodNode.tryCatchBlocks)
				{
					if(tc.handler == null || tc.handler.getNext() == null)
						remove.add(tc);
					else if(tc.handler.getNext().getOpcode() == Opcodes.ACONST_NULL
						&& tc.handler.getNext().getNext() != null
						&& tc.handler.getNext().getNext().getOpcode() == Opcodes.ATHROW)
					{
						remove.add(tc);
						counter.getAndIncrement();
						LabelNode begin = tc.handler;
						AbstractInsnNode next = begin;
						while(!nodesRemove.contains(next) && next != null && (next.getOpcode() != -1 || next == begin))
						{
							nodesRemove.add(next);
							next = next.getNext();
						}
					}
				}
				for(TryCatchBlockNode toRemove : remove)
					methodNode.tryCatchBlocks.remove(toRemove);
				for(AbstractInsnNode ain : nodesRemove)
					methodNode.instructions.remove(ain);
				
			}));
		System.out.println("[SkidSuite] [FakeExceptionTransformer] Removed "
			+ counter.get() + " fake try-catch blocks");
		System.out.println("[SkidSuite] [FakeExceptionTransformer] Done");
		return counter.get() > 0;
	}
}
