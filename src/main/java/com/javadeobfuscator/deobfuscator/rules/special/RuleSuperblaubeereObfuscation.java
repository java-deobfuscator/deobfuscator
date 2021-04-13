package com.javadeobfuscator.deobfuscator.rules.special;

import java.util.Collection;
import java.util.Collections;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.special.SuperblaubeereTransformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class RuleSuperblaubeereObfuscation implements Rule {

    @Override
    public String getDescription() {
        return "Superblaubeere obfuscator uses a variety of methods. It can obfuscate numbers, add redundant ifs, encrypt strings, pool numbers & strings into an " +
               "array per class and obfuscate method calls with invokedynamic instructions.";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit != null) {
                AbstractInsnNode first = clinit.instructions.getFirst();
                // Number pool
                numberPool:
                {
                    if (first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) first).desc.equals("()V")
                        && ((MethodInsnNode) first).owner.equals(classNode.name)) {
                        MethodNode refMethod = classNode.methods.stream()
                                .filter(m -> m.name.equals(((MethodInsnNode) first).name) && m.desc.equals("()V")).findFirst().orElse(null);
                        if (refMethod != null && refMethod.instructions.size() > 3
                            && refMethod.instructions.getFirst().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
                            && ((FieldInsnNode) refMethod.instructions.getFirst().getNext().getNext()).desc.equals("[I")) {
                            FieldInsnNode insnNode = (FieldInsnNode) refMethod.instructions.getFirst().getNext().getNext();
                            FieldNode field = classNode.fields.stream()
                                    .filter(f -> f.name.equals(insnNode.name) && f.desc.equals(insnNode.desc)).findFirst().orElse(null);
                            if (field == null) {
                                break numberPool;
                            }
                            classNode.methods.remove(refMethod);
                            clinit.instructions.remove(clinit.instructions.getFirst());
                            classNode.fields.remove(field);
                            for (MethodNode method : classNode.methods) {
                                for (AbstractInsnNode ain : method.instructions.toArray()) {
                                    if (ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) ain).name.equals(field.name)
                                        && ((FieldInsnNode) ain).desc.equals(field.desc)
                                        && ((FieldInsnNode) ain).owner.equals(classNode.name)) {
                                        if (Utils.isInteger(ain.getNext())) {
                                            return "Found potential number pool in class " + classNode.name;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // String encryption
                stringEncrypt:
                {
                    if (first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) first).desc.equals("()V")
                        && ((MethodInsnNode) first).owner.equals(classNode.name)) {
                        MethodNode refMethod = classNode.methods.stream()
                                .filter(m -> m.name.equals(((MethodInsnNode) first).name) && m.desc.equals("()V")).findFirst().orElse(null);
                        if (refMethod == null) {
                            break stringEncrypt;
                        }
                        AbstractInsnNode methodFirst = refMethod.instructions.getFirst();
                        if (methodFirst.getOpcode() == Opcodes.NEW && ((TypeInsnNode) methodFirst).desc.equals("java/lang/Exception")
                            && methodFirst.getNext() != null && methodFirst.getNext().getOpcode() == Opcodes.DUP
                            && methodFirst.getNext().getNext() != null && methodFirst.getNext().getNext().getOpcode() == Opcodes.INVOKESPECIAL
                            && ((MethodInsnNode) methodFirst.getNext().getNext()).name.equals("<init>")
                            && ((MethodInsnNode) methodFirst.getNext().getNext()).owner.equals("java/lang/Exception")
                            && methodFirst.getNext().getNext().getNext() != null
                            && methodFirst.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
                            && ((MethodInsnNode) methodFirst.getNext().getNext().getNext()).name.equals("getStackTrace")
                            && (((MethodInsnNode) methodFirst.getNext().getNext().getNext()).owner.equals("java/lang/Exception")
                                || ((MethodInsnNode) methodFirst.getNext().getNext().getNext()).owner.equals("java/lang/Throwable"))
                            && refMethod.instructions.getLast().getPrevious().getOpcode() == Opcodes.PUTSTATIC) {
                            FieldInsnNode insnNode = (FieldInsnNode) refMethod.instructions.getLast().getPrevious();
                            FieldNode field = classNode.fields.stream()
                                    .filter(f -> f.name.equals(insnNode.name) && f.desc.equals(insnNode.desc)).findFirst().orElse(null);
                            if (field == null) {
                                break stringEncrypt;
                            }
                            for (MethodNode method : classNode.methods) {
                                for (AbstractInsnNode ain : method.instructions.toArray()) {
                                    if (ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) ain).name.equals(field.name)
                                        && ((FieldInsnNode) ain).desc.equals(field.desc)
                                        && ((FieldInsnNode) ain).owner.equals(classNode.name)) {
                                        if (Utils.isInteger(ain.getNext())) {
                                            return "Found potential string encryption in class " + classNode.name;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Unpool strings
                stringPool:
                {
                    if (first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) first).desc.equals("()V")
                        && ((MethodInsnNode) first).owner.equals(classNode.name)) {
                        MethodNode refMethod = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode) first).name)
                                                                                      && m.desc.equals("()V")).findFirst().orElse(null);
                        if (refMethod == null) {
                            break stringPool;
                        }
                        if (refMethod.instructions.size() > 3
                            && refMethod.instructions.getFirst().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC
                            && ((FieldInsnNode) refMethod.instructions.getFirst().getNext().getNext()).desc.equals("[Ljava/lang/String;")) {
                            FieldInsnNode insnNode = (FieldInsnNode) refMethod.instructions.getFirst().getNext().getNext();
                            FieldNode field = classNode.fields.stream()
                                    .filter(f -> f.name.equals(insnNode.name) && f.desc.equals(insnNode.desc)).findFirst().orElse(null);
                            if (field == null) {
                                break stringPool;
                            }
                            boolean contains = false;
                            for (AbstractInsnNode ain : refMethod.instructions.toArray()) {
                                if (ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) ain).owner.equals(classNode.name)) {
                                    contains = classNode.methods.stream()
                                            .anyMatch(m -> m.name.equals(((MethodInsnNode) ain).name) && m.desc.equals(((MethodInsnNode) ain).desc));
                                    if (contains) {
                                        break;
                                    }
                                }
                            }
                            if (!contains) {
                                break stringPool;
                            }
                            for (MethodNode method : classNode.methods) {
                                for (AbstractInsnNode ain : method.instructions.toArray()) {
                                    if (ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) ain).name.equals(field.name)
                                        && ((FieldInsnNode) ain).desc.equals(field.desc)
                                        && ((FieldInsnNode) ain).owner.equals(classNode.name)) {
                                        if (Utils.isInteger(ain.getNext())) {
                                            return "Found potential string pool in class " + classNode.name;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // invokedynamic
                invokedyn:
                {
                    if (first != null && first.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) first).desc.equals("()V")
                        && ((MethodInsnNode) first).owner.equals(classNode.name)) {
                        MethodNode refMethod = classNode.methods.stream()
                                .filter(m -> m.name.equals(((MethodInsnNode) first).name) && m.desc.equals("()V")).findFirst().orElse(null);
                        if (refMethod == null) {
                            break invokedyn;
                        }
                        FieldNode[] fields = SuperblaubeereTransformer.isIndyMethod(classNode, refMethod);
                        if (fields == null) {
                            break invokedyn;
                        }
                        MethodNode bootstrap = classNode.methods.stream()
                                .filter(m -> SuperblaubeereTransformer.isBootstrap(classNode, fields, m))
                                .findFirst()
                                .orElse(null);
                        if (bootstrap == null) {
                            break invokedyn;
                        }
                        boolean yep = false;
                        for (AbstractInsnNode ain : refMethod.instructions.toArray()) {
                            if (ain instanceof LdcInsnNode && ((LdcInsnNode) ain).cst instanceof String) {
                                yep = true;
                            } else if (ain instanceof LdcInsnNode && ((LdcInsnNode) ain).cst instanceof Type) {
                                yep = true;
                            } else if (ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) ain).name.equals("TYPE")
                                       && ((FieldInsnNode) ain).desc.equals("Ljava/lang/Class;")) {
                                yep = true;
                            }
                            if (yep) {
                                break;
                            }
                        }
                        if (!yep) {
                            break invokedyn;
                        }
                        for (MethodNode method : classNode.methods) {
                            for (AbstractInsnNode ain : method.instructions.toArray()) {
                                if (ain.getOpcode() == Opcodes.INVOKEDYNAMIC
                                    && ((InvokeDynamicInsnNode) ain).bsmArgs.length == 0
                                    && ((InvokeDynamicInsnNode) ain).bsm.getName().equals(bootstrap.name)
                                    && ((InvokeDynamicInsnNode) ain).bsm.getDesc().equals(bootstrap.desc)
                                    && ((InvokeDynamicInsnNode) ain).bsm.getOwner().equals(classNode.name)) {
                                    return "Found potential invokedynamic obfuscation in class " + classNode.name;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singleton(SuperblaubeereTransformer.class);
    }
}
