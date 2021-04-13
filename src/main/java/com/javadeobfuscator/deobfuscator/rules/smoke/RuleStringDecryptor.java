package com.javadeobfuscator.deobfuscator.rules.smoke;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.smoke.StringEncryptionTransformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

public class RuleStringDecryptor implements Rule {

    @Override
    public String getDescription() {
        return "Smoke uses a method with string and int as parameters to deobfuscate strings";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        Map<String, ClassNode> classes = deobfuscator.getClasses();
        for (ClassNode classNode : classes.values()) {
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null || method.instructions.size() == 0) {
                    continue;
                }
                Frame<SourceValue>[] frames = null;
                for (AbstractInsnNode ain : method.instructions) {
                    if (!(ain instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode m = (MethodInsnNode) ain;
                    String strCl = m.owner;
                    if (m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) {
                        if (classes.containsKey(strCl)) {
                            ClassNode innerClassNode = classes.get(strCl);
                            MethodNode decrypterNode = innerClassNode.methods.stream()
                                    .filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc))
                                    .findFirst()
                                    .orElse(null);
                            if (decrypterNode != null && StringEncryptionTransformer.isSmokeMethod(decrypterNode)) {
                                if (frames == null) {
                                    try {
                                        frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
                                    } catch (AnalyzerException e) {
                                        continue;
                                    }
                                }
                                Frame<SourceValue> f = frames[method.instructions.indexOf(m)];
                                if (f.getStack(f.getStackSize() - 2).insns.size() != 1 || f.getStack(f.getStackSize() - 1).insns.size() != 1) {
                                    continue;
                                }
                                AbstractInsnNode a1 = f.getStack(f.getStackSize() - 2).insns.iterator().next();
                                AbstractInsnNode a2 = f.getStack(f.getStackSize() - 1).insns.iterator().next();
                                if (a1.getOpcode() != Opcodes.LDC || !Utils.isInteger(a2)) {
                                    continue;
                                }
                                return "Found possible string decryption method in " + classNode.name + "/" + decrypterNode.name + decrypterNode.desc;
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
        return Collections.singleton(StringEncryptionTransformer.class);
    }
}
