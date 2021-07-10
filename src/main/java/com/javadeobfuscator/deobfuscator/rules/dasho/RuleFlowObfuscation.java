package com.javadeobfuscator.deobfuscator.rules.dasho;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.dasho.FlowObfuscationTransformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.apache.commons.lang3.tuple.Triple;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class RuleFlowObfuscation implements Rule {

    @Override
    public String getDescription() {
        return "DashO Flow Obfuscation";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null || method.instructions.size() == 0) {
                    continue;
                }
                try {
                    FlowAnalyzer analyzer = new FlowAnalyzer(method);
                    LinkedHashMap<LabelNode, List<AbstractInsnNode>> result = analyzer.analyze(method.instructions.getFirst(),
                            new ArrayList<>(), new HashMap<>(), false, true);
                    FlowAnalyzer.Result jumpAnalysis = analyzer.analyze();
                    List<AbstractInsnNode> ordered = new ArrayList<>();
                    for (Map.Entry<LabelNode, List<AbstractInsnNode>> e : result.entrySet()) {
                        if (e.getKey() != FlowAnalyzer.ABSENT) {
                            ordered.add(e.getKey());
                        }
                        ordered.addAll(e.getValue());
                        for (Triple<LabelNode, FlowAnalyzer.JumpData, Integer> entry : jumpAnalysis.labels.get(e.getKey()).getValue()) {
                            if (entry.getMiddle().cause == FlowAnalyzer.JumpCause.NEXT) {
                                ordered.add(new JumpInsnNode(Opcodes.GOTO, entry.getLeft()));
                            }
                        }
                    }
                    for (AbstractInsnNode ain : ordered) {
                        if (Utils.getIntValue(ain) == -1
                            && FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 1).getOpcode() == Opcodes.ISTORE
                            && FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 2).getOpcode() == Opcodes.LDC
                            && ((LdcInsnNode) FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 2)).cst.equals("0")
                            && FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 3).getOpcode() == Opcodes.IINC
                            && ((IincInsnNode) FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 3)).incr == 1
                            && ((IincInsnNode) FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 3)).var ==
                               ((VarInsnNode) FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 1)).var
                            && FlowObfuscationTransformer.getNext(ordered, result, jumpAnalysis, ain, 4).getOpcode() == Opcodes.ASTORE) {
                            return "Found possible flow obfuscation pattern in " + classNode.name + "/" + method.name + method.desc;
                        }
                    }
                } catch (Exception e) {
                    if (deobfuscator.getConfig().isDebugRulesAnalyzer()) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singleton(FlowObfuscationTransformer.class);
    }
}
