/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.smoke.peephole;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.InsnList;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.JumpInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LabelNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.TableSwitchInsnNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class ImpossibleSwitchRemover extends Transformer
{
	public ImpossibleSwitchRemover(Map<String, WrappedClassNode> classes,
		Map<String, WrappedClassNode> classpath)
	{
		super(classes, classpath);
	}
	
	@Override
	public boolean transform() throws Throwable
	{
		AtomicInteger count = new AtomicInteger(0);
		classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> 
		classNode.methods.forEach(methodNode -> { 
			InsnList copy = Utils.copyInsnList(methodNode.instructions); 
			for (AbstractInsnNode insn : copy.toArray()) {
				if(Utils.isNumber(insn) && insn.getNext() != null
					&& insn.getNext() instanceof LabelNode
					&& insn.getNext().getNext() != null
					&& insn.getNext().getNext().getOpcode() == Opcodes.TABLESWITCH)
				{
					int value = Utils.getIntValue(insn);
					TableSwitchInsnNode cast = (TableSwitchInsnNode)insn.getNext().getNext();
					int offset = cast.min;
					LabelNode node;
					if(value < cast.labels.size() + offset && value - offset >= 0)
						node = cast.labels.get(value - offset);
					else
						node = cast.dflt;
					methodNode.instructions.set(insn.getNext().getNext(), new JumpInsnNode(Opcodes.GOTO, node));
					methodNode.instructions.remove(insn);
					count.incrementAndGet();
				}
			} 
		}));
		System.out.println("Removed " + count.get() + " impossible switches");
		return true;
	}
}
