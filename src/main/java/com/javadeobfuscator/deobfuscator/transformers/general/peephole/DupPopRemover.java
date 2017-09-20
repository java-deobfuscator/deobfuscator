/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DupPopRemover extends Transformer {
    public DupPopRemover(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode node = methodNode.instructions.get(i);
                    if(node.getOpcode() == Opcodes.POP2) 
                    {
                    	if(node.getPrevious() != null && node.getPrevious().getOpcode() == Opcodes.DUP
                    		&& node.getPrevious().getPrevious() != null
                    		&& node.getPrevious().getPrevious().getOpcode() == Opcodes.DUP)
                    	{
                    		//Dup dup pop2
                    		methodNode.instructions.remove(node.getPrevious().getPrevious());
                    		methodNode.instructions.remove(node.getPrevious());
                    		methodNode.instructions.remove(node);
                    		counter.incrementAndGet();
                    	}else if(node.getPrevious() != null && node.getPrevious().getOpcode() == Opcodes.DUP
                    		&& node.getPrevious().getPrevious() != null
                    		&& Utils.willPushToStack(node.getPrevious().getPrevious().getOpcode()))
                    	{
                    		//Push dup pop2
                    		methodNode.instructions.remove(node.getPrevious().getPrevious());
                    		methodNode.instructions.remove(node.getPrevious());
                    		methodNode.instructions.remove(node);
                    		counter.incrementAndGet();
                    	}
                    }
                }
            });
        });
        System.out.println("Removed " + counter.get() + " dup-pop like instructions");
    }
}
