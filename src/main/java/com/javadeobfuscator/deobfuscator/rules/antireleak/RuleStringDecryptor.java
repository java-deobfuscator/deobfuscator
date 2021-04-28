package com.javadeobfuscator.deobfuscator.rules.antireleak;

import java.util.Collection;
import java.util.Collections;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.antireleak.StringEncryptionTransformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class RuleStringDecryptor implements Rule {

    @Override
    public String getDescription() {
        return "AntiReleak StringEncryption";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                boolean type2Possible1 = false;
                boolean type2Possible2 = false;
                for (AbstractInsnNode ain : methodNode.instructions) {
                    switch (ain.getOpcode()) {
                        case INVOKESTATIC: {
                            MethodInsnNode min = (MethodInsnNode) ain;
                            AbstractInsnNode previous = ain.getPrevious();
                            // Type 1
                            if (min.owner.equals(classNode.name)
                                && min.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")
                                && methodNode.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                                                          + "Ljava/lang/invoke/MethodType;Ljava/lang/Class;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;")
                                && TransformerHelper.nullsafeOpcodeEqual(previous, LDC)) {
                                return "Found possible string decryption method (type 1): " + classNode.name + " " + methodNode.name + methodNode.desc;
                            }
                            // Type 2
                            if (!type2Possible1 && min.desc.equals(methodNode.desc)
                                && min.name.equals(methodNode.name)
                                && min.owner.equals(classNode.name)
                                && methodNode.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                                          + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                                          + "Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                                type2Possible1 = true;
                            }
                            //Type 3
                            if (min.desc.equals(methodNode.desc)
                                && min.name.equals(methodNode.name)
                                && min.owner.equals(classNode.name)
                                && methodNode.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                                          + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;"
                                                          + "Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                                return "Found possible string decryption method (type 3): " + classNode.name + " " + methodNode.name + methodNode.desc;
                            }
                            break;
                        }
                        case AASTORE: {
                            // Type 2
                            if (!type2Possible2 && ain.getPrevious().getOpcode() == INVOKESTATIC
                                && ain.getPrevious().getPrevious().getOpcode() == LDC
                                && TransformerHelper.nullsafeOpcodeEqual(ain.getNext(), AASTORE)
                                && TransformerHelper.nullsafeOpcodeEqual(ain.getNext().getNext(), GETSTATIC)) {
                                AbstractInsnNode next = ain.getNext().getNext().getNext().getNext().getNext().getNext();
                                while (next != null) {
                                    if (next.getOpcode() == -1
                                        && TransformerHelper.nullsafeOpcodeEqual(next.getNext(), ALOAD)
                                        && TransformerHelper.nullsafeOpcodeEqual(next.getNext().getNext(), INSTANCEOF)) {
                                        type2Possible2 = true;
                                        break;
                                    }
                                    next = next.getNext();
                                }
                            }
                            break;
                        }
                    }
                    if (type2Possible1 && type2Possible2) {
                        return "Found possible string decryption method (type 2): " + classNode.name + " " + methodNode.name + methodNode.desc;
                    }
                }
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singleton(StringEncryptionTransformer.class);
    }
}
