package com.javadeobfuscator.deobfuscator.transformers.general;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.lang.reflect.Modifier;
import java.util.Map;

public class IllegalVarargsTransformer extends Transformer {
    public IllegalVarargsTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode ->
                classNode.methods.forEach(methodNode -> {
                    if (Modifier.isTransient(methodNode.access)) {
                        Type type = Type.getType(methodNode.desc);
                        if (type.getArgumentTypes().length > 0 &&
                                type.getArgumentTypes()[type.getArgumentTypes().length - 1].getClassName().endsWith("[]")) return;

                        methodNode.access &= ~Opcodes.ACC_VARARGS;
                    }
        }));
    }
}
