package com.javadeobfuscator.deobfuscator.transformers.smoke;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MathFrame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;

public class NumberObfuscationTransformer extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        for (int i = 0; i < 2; i++) {
            transform0();
        }
        return true;
    }

    private void transform0() throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        Map<String, Integer> numberMethods = new HashMap<>();

        System.out.println("[Smoke] [NumberObfuscationTransformer] Starting");

        classNodes().forEach(classNode ->
                classNode.methods.forEach(methodNode -> {
                    if (methodNode.instructions.size() > 0) {
                        AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode); //FIXME: still slow? (modify analyzerresult directly) 
                        boolean modified = false;

                        InsnList copy = Utils.copyInsnList(methodNode.instructions);
                        for (int i = 0; i < copy.size(); i++) {
                            AbstractInsnNode insn = copy.get(i);

                            if (modified) {
                                modified = false;
                                result = MethodAnalyzer.analyze(classNode, methodNode);
                            }

                            List<Frame> frames = result.getFrames().get(insn);
                            if (frames != null && frames.size() > 0 && frames.get(0) instanceof MathFrame) {
                                MathFrame mathFrame = (MathFrame) result.getFrames().get(insn).get(0);
                                if (mathFrame.getTargets().size() == 2) {
                                    Frame frame1 = mathFrame.getTargets().get(0);
                                    Frame frame2 = mathFrame.getTargets().get(1);

                                    if (frame1 instanceof LdcFrame && frame2 instanceof LdcFrame) {
                                        Number value1 = (Number) ((LdcFrame) frame1).getConstant();
                                        Number value2 = (Number) ((LdcFrame) frame2).getConstant();

                                        if (!(value1 instanceof Long) && !(value2 instanceof Long)) { //TODO long 
                                            AbstractInsnNode insn1 = result.getInsnNode(frame1);
                                            AbstractInsnNode insn2 = result.getInsnNode(frame2);
                                            boolean skip = false;
                                            AbstractInsnNode current = insn2;
                                            while (true) {
                                                current = current.getNext();

                                                if (current == insn1)
                                                    break;
                                                else if (current instanceof JumpInsnNode) {
                                                    skip = true;
                                                    break;
                                                }

                                            }

                                            if (!skip) {
                                                Integer resultValue;
                                                if ((resultValue = doMath(value1.intValue(), value2.intValue(), insn.getOpcode())) != null) {
                                                    setNumber(methodNode.instructions, insn, resultValue);
                                                    methodNode.instructions.remove(insn1);
                                                    methodNode.instructions.remove(insn2);
                                                    count.getAndAdd(2);
                                                    modified = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (insn instanceof MethodInsnNode &&
                                    ((MethodInsnNode) insn).owner.equals("java/lang/String") &&
                                    ((MethodInsnNode) insn).name.equals("length") &&
                                    insn.getPrevious() instanceof LdcInsnNode && ((LdcInsnNode) insn.getPrevious()).cst instanceof String) {
                                AbstractInsnNode previous = insn.getPrevious();
                                setNumber(methodNode.instructions, insn, ((String) ((LdcInsnNode) insn.getPrevious()).cst).length());
                                methodNode.instructions.remove(previous);
                                count.getAndIncrement();
                                modified = true;
                            }
                        }

                        if (Modifier.isStatic(methodNode.access) && methodNode.desc.endsWith("()I")) {
                            int returnCount = Arrays.stream(methodNode.instructions.toArray()).filter(insn -> insn.getOpcode() == Opcodes.IRETURN).collect(Collectors.toList()).size();
                            if (returnCount == 1) {
                                for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                                    if (insn.getOpcode() == Opcodes.IRETURN) {
                                        if (insn.getPrevious() instanceof LdcInsnNode) {
                                            numberMethods.put(classNode.name + methodNode.name + methodNode.desc, (int) ((LdcInsnNode) insn.getPrevious()).cst);
                                        } else if (insn.getPrevious().getOpcode() == Opcodes.ICONST_1 || insn.getPrevious().getOpcode() == Opcodes.ICONST_0) {
                                            numberMethods.put(classNode.name + methodNode.name + methodNode.desc, insn.getPrevious().getOpcode() - 3);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }));

        classNodes().forEach(classNode ->
                classNode.methods.forEach(methodNode -> {
                    for (int i = 0; i < methodNode.instructions.size(); i++) {
                        AbstractInsnNode insn = methodNode.instructions.get(i);

                        if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                            Integer number = numberMethods.get(((MethodInsnNode) insn).owner + ((MethodInsnNode) insn).name + ((MethodInsnNode) insn).desc);
                            if (number != null) {
                                setNumber(methodNode.instructions, insn, number);
                                count.getAndIncrement();
                            }
                        }
                    }
                }));

        classNodes().forEach(classNode ->
                classNode.methods = classNode.methods.stream().filter(methodNode -> !numberMethods.containsKey(classNode.name + methodNode.name + methodNode.desc)).collect(Collectors.toList()));

        System.out.println("[Smoke] [NumberObfuscationTransformer] Removed " + count.get() + " instructions");
    }

    private Integer doMath(int value1, int value2, int opcode) {
        switch (opcode) { //TODO: bit shift & long & single value 
            case IADD:
                return value2 + value1;
            case IDIV:
                return value2 / value1;
            case IREM:
                return value2 % value1;
            case ISUB:
                return value2 - value1;
            case IMUL:
                return value2 * value1;
            case IXOR:
                return value2 ^ value1;
        }

        return null;
    }

    private void setNumber(InsnList insns, AbstractInsnNode insn, Integer number) {
        switch (number) {
            case 0:
                insns.set(insn, new InsnNode(Opcodes.ICONST_0));
                break;
            case 1:
                insns.set(insn, new InsnNode(Opcodes.ICONST_1));
                break;
            default:
                insns.set(insn, new LdcInsnNode(number));
        }
    }
}
