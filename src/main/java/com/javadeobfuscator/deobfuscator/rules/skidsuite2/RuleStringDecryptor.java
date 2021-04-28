package com.javadeobfuscator.deobfuscator.rules.skidsuite2;

import java.util.Collection;
import java.util.Collections;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.NewArrayFrame;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.skidsuite2.StringEncryptionTransformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class RuleStringDecryptor implements Rule {

    @Override
    public String getDescription() {
        return "SkidSuite2 uses String Arrays for String Obfuscation";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.instructions == null || methodNode.instructions.size() == 0) {
                    continue;
                }
                AnalyzerResult analyzerResult = null;
                insns: for (AbstractInsnNode ain : methodNode.instructions) {
                    if (!TransformerHelper.isInvokeStatic(ain,null, null, "([CLjava/lang/String;I)Ljava/lang/String;")) {
                        continue;
                    }
                    MethodInsnNode min = (MethodInsnNode) ain;

                    if (analyzerResult == null) {
                        analyzerResult = MethodAnalyzer.analyze(classNode, methodNode);
                    }
                    MethodFrame frame = (MethodFrame) analyzerResult.getFrames().get(min).get(0);

                    for (Frame arg : frame.getArgs()) {
                        if (arg instanceof LdcFrame) {
                            Object cst = ((LdcFrame) arg).getConstant();
                            if (cst == null) {
                                continue insns;
                            }
                            if (deobfuscator.getClasses().containsKey(min.owner)) {
                                return "Found possible encrypted string array in " + classNode.name + "/" + methodNode.name + methodNode.desc;
                            }
                        } else if (arg instanceof NewArrayFrame) {
                            if (deobfuscator.getClasses().containsKey(min.owner)) {
                                return "Found possible encrypted string array in " + classNode.name + "/" + methodNode.name + methodNode.desc;
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
