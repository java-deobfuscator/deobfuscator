package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import java.util.ListIterator;
import java.util.concurrent.atomic.LongAdder;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import org.objectweb.asm.tree.AbstractInsnNode;

public class NopRemover extends Transformer<TransformerConfig> {
    
    @Override
    public boolean transform() throws Throwable {
        LongAdder counter = new LongAdder();
        classNodes().forEach(classNode -> classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
            ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
            while (it.hasNext()) {
                AbstractInsnNode node = it.next();
                if (node.getOpcode() == NOP) {
                    it.remove();
                    counter.increment();
                }
            }
        }));
        if (counter.sum() > 0) {
            System.out.println("Removed " + counter + " nops");
            return true;
        }
        return false;
    }
}
