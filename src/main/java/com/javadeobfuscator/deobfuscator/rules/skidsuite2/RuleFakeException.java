package com.javadeobfuscator.deobfuscator.rules.skidsuite2;

import java.util.Collection;
import java.util.Collections;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.skidsuite2.FakeExceptionTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public class RuleFakeException implements Rule {

    @Override
    public String getDescription() {
        return "SkidSuite2 uses exceptions for flow obfuscation";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.instructions == null || methodNode.instructions.size() == 0) {
                    continue;
                }
                for (TryCatchBlockNode tc : methodNode.tryCatchBlocks) {
                    if (tc.handler != null && tc.handler.getNext() != null
                        && tc.handler.getNext().getOpcode() == Opcodes.ACONST_NULL
                        && tc.handler.getNext().getNext() != null
                        && tc.handler.getNext().getNext().getOpcode() == Opcodes.ATHROW) {
                        return "Found possible fake excepton pattern in " + classNode.name + "/" + methodNode.name;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singleton(FakeExceptionTransformer.class);
    }
}
