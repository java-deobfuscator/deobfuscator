package com.javadeobfuscator.deobfuscator.transformers.general;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.Map;

public class IllegalVarargsTransformer extends Transformer {
    public IllegalVarargsTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public boolean transform() throws Throwable {
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                Type[] args = Type.getArgumentTypes(methodNode.desc);
                if (args.length > 0 && args[args.length - 1].getSort() != Type.ARRAY) {
                    methodNode.access &= ~Opcodes.ACC_VARARGS;
                }
            });
        });
        return true;
    }
}
