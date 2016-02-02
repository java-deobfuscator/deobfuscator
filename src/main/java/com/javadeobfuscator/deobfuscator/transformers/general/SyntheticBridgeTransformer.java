package com.javadeobfuscator.deobfuscator.transformers.general;

import java.util.Map;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class SyntheticBridgeTransformer extends Transformer {
    public SyntheticBridgeTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                methodNode.access &= ~Opcodes.ACC_SYNTHETIC;
                methodNode.access &= ~Opcodes.ACC_BRIDGE;
            });
            classNode.fields.forEach(fieldNode -> {
                fieldNode.access &= ~Opcodes.ACC_SYNTHETIC;
                fieldNode.access &= ~Opcodes.ACC_BRIDGE;
            });
        });
    }
}
