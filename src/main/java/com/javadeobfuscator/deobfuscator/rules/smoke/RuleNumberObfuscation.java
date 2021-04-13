package com.javadeobfuscator.deobfuscator.rules.smoke;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.smoke.NumberObfuscationTransformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class RuleNumberObfuscation implements Rule {

    @Override
    public String getDescription() {
        return "Smoke uses various methods to obfuscate numbers, including String.length(), useless math and methods which return a constant number";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        Map<String, ClassNode> classes = deobfuscator.getClasses();
        Set<String> numberMethods = new HashSet<>();

        for (ClassNode classNode : classes.values()) {
            method:
            for (MethodNode method : classNode.methods) {
                if (!Modifier.isStatic(method.access) || !method.desc.endsWith("()I")) {
                    continue;
                }
                long returnCount = Arrays.stream(method.instructions.toArray()).filter(insn -> insn.getOpcode() == Opcodes.IRETURN).count();
                if (returnCount != 1) {
                    continue;
                }
                boolean returnInsn = false;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    int opcode = insn.getOpcode();
                    if (opcode == IRETURN) {
                        if (Utils.isInteger(insn.getPrevious())) {
                            returnInsn = true;
                        }
                        break;
                    } else if (opcode != IADD && opcode != ISUB && opcode != IMUL && opcode != IDIV && opcode != IREM && opcode != IXOR
                               && !Utils.isInteger(insn)) {
                        break method;
                    }
                }
                if (returnInsn) {
                    numberMethods.add(classNode.name + method.name + method.desc);
                }
            }
        }
        if (numberMethods.isEmpty()) {
            return null;
        }
        for (ClassNode classNode : classes.values()) {
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null || method.instructions.size() < 3) {
                    continue;
                }
                int count = 0;
                for (AbstractInsnNode ain : method.instructions) {
                    if (ain instanceof MethodInsnNode &&
                        ((MethodInsnNode) ain).owner.equals("java/lang/String") &&
                        ((MethodInsnNode) ain).name.equals("length") &&
                        ain.getPrevious() instanceof LdcInsnNode && ((LdcInsnNode) ain.getPrevious()).cst instanceof String) {
                        count++;
                    }
                }
                if (count > 3) {
                    return "Found potential number obfuscation (" + count + " times) in " + classNode.name + "/" + method.name + method.desc;
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singleton(NumberObfuscationTransformer.class);
    }
}
