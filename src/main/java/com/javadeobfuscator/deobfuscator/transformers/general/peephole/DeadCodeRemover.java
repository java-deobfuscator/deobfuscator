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

import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DeadCodeRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        AtomicInteger deadInstructions = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                if (methodNode.name.startsWith("DECRYPTOR_METHOD"))
                    return;

                Map<AbstractInsnNode, List<Frame>> f = MethodAnalyzer.analyze(classNode, methodNode).getFrames();
                Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode next = iterator.next();
                    if (Utils.isInstruction(next) && f.get(next) == null) {
                        iterator.remove();
                        deadInstructions.incrementAndGet();
                    }
                }

                // empty try catch nodes are illegal
                if (methodNode.tryCatchBlocks != null) {
                    methodNode.tryCatchBlocks.removeIf(tryCatchBlockNode -> Utils.getNext(tryCatchBlockNode.start) == Utils.getNext(tryCatchBlockNode.end));
                }
            });
        });
        System.out.println("Removed " + deadInstructions.get() + " dead instructions");
        return deadInstructions.get() > 0;
    }
}
