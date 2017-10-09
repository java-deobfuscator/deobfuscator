package com.javadeobfuscator.deobfuscator.transformers.zelix;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;

import java.util.concurrent.atomic.AtomicInteger;

public class FlowObfuscationTransformer extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        System.out.println("[Zelix] [FlowObfuscationTransformer] Starting");
        AtomicInteger counter = new AtomicInteger();

        classNodes().forEach(classNode -> {
            classNode.methods.stream().filter(Utils::notAbstractOrNative).forEach(methodNode -> {
                counter.addAndGet(methodNode.tryCatchBlocks.size());
                methodNode.tryCatchBlocks.removeIf(tc -> (tc.handler.getNext().getOpcode() == Opcodes.INVOKESTATIC && tc.handler.getNext().getNext().getOpcode() == Opcodes.ATHROW) || tc.handler.getNext().getOpcode() == Opcodes.ATHROW);
                counter.addAndGet(-methodNode.tryCatchBlocks.size());
            });
        });

        System.out.println("[Zelix] [FlowObfuscationTransformer] Removed " + counter.get() + " fake try-catch blocks");
        System.out.println("[Zelix] [FlowObfuscationTransformer] Done");
        return true;
    }
}
