/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.stringer;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.executor.defined.DisabledFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.InvokeDynamicInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class InvokedynamicTransformer extends Transformer {
    public InvokedynamicTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() {
        System.out.println("[Stringer] [InvokedynamicTransformer] Starting");
        System.out.println("[Stringer] [InvokedynamicTransformer] Finding invokedynamic instructions");
        int amount = findInvokeDynamic();
        System.out.println("[Stringer] [InvokedynamicTransformer] Found " + amount + " invokedynamic instructions");
        if (amount > 0) {
            System.out.println("[Stringer] [InvokedynamicTransformer] transforming invokedynamic");
            long start = System.currentTimeMillis();
            int transformed = transformInvokeDynamic();
            long end = System.currentTimeMillis();
            System.out.println("[Stringer] [InvokedynamicTransformer] Removed " + transformed + " invokedynamic instructions, took " + TimeUnit.MILLISECONDS.toSeconds(end - start) + "s");
            System.out.println("[Stringer] [InvokedynamicTransformer] Cleaning up bootstrap methods");
            int cleanedup = cleanup();
            System.out.println("[Stringer] [InvokedynamicTransformer] Removed " + cleanedup + " bootstrap methods");
        }
        System.out.println("[Stringer] [InvokedynamicTransformer] Done");
    }

    private int findInvokeDynamic() {
        AtomicInteger total = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof InvokeDynamicInsnNode) {
                            total.incrementAndGet();
                    }
                }
            });
        });
        return total.get();
    }

    private int transformInvokeDynamic() {
        AtomicInteger total = new AtomicInteger();
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new DisabledFieldProvider());
        provider.register(new JVMMethodProvider());
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                    if (abstractInsnNode instanceof InvokeDynamicInsnNode) {
                        InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) abstractInsnNode;
                        String name = dyn.bsm.getName();
                        String owner = dyn.bsm.getOwner();
                        if(dyn.bsm.getTag() == Opcodes.H_INVOKESTATIC){
                            methodNode.instructions.insert(abstractInsnNode, new MethodInsnNode(Opcodes.INVOKESTATIC, owner , name, dyn.desc, false));
                            methodNode.instructions.remove(abstractInsnNode);
                            total.incrementAndGet();
                        }
                        if(dyn.bsm.getTag() == Opcodes.H_INVOKEVIRTUAL){
                            methodNode.instructions.insert(abstractInsnNode, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner , name, dyn.desc, false));
                            methodNode.instructions.remove(abstractInsnNode);
                            total.incrementAndGet();
                        }
                    }
                }
            });
        });
        return total.get();
    }

    private int cleanup() {
        AtomicInteger total = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            Iterator<MethodNode> it = classNode.methods.iterator();
            while (it.hasNext()) {
                MethodNode node = it.next();
                if (node.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/Object;")) {
                    it.remove();
                    total.incrementAndGet();
                }
            }
        });
        return total.get();
    }
}
