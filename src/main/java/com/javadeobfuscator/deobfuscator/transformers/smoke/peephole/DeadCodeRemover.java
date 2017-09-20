package com.javadeobfuscator.deobfuscator.transformers.smoke.peephole; 
 
import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult; 
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer; 
import com.javadeobfuscator.deobfuscator.analyzer.frame.JumpFrame; 
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame; 
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes; 
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*; 
import com.javadeobfuscator.deobfuscator.transformers.Transformer; 
import com.javadeobfuscator.deobfuscator.utils.Utils; 
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode; 
 
import java.util.*; 
 
import static com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes.*; 
 
public class DeadCodeRemover extends Transformer { 
    public DeadCodeRemover(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) { 
        super(classes, classpath); 
    } 
 
    @Override 
    public void transform() throws Throwable { 
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> 
                classNode.methods.forEach(methodNode -> { 
                    AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode); 
 
                    InsnList copy = Utils.copyInsnList(methodNode.instructions); 
                    for (AbstractInsnNode insn : copy.toArray()) { 
                        if (insn instanceof JumpInsnNode) { 
                            JumpFrame frame = (JumpFrame) result.getFrames().get(insn).get(0); 
 
                            if (frame.getComparators().size() == 1 && frame.getComparators().get(0) instanceof LdcFrame) { 
                                LdcFrame ldcFrame = (LdcFrame) frame.getComparators().get(0); 
                                int value = (Integer) ldcFrame.getConstant(); 
 
                                boolean jump = false; 
                                switch (insn.getOpcode()) { 
                                    case IFEQ: 
                                        if (value == 0) jump = true; 
                                        break; 
                                    case IFGE: 
                                        if (value >= 0) jump = true; 
                                        break; 
                                    case IFGT: 
                                        if (value > 0) jump = true; 
                                        break; 
                                    case IFLE: 
                                        if (value <= 0) jump = true; 
                                        break; 
                                    case IFLT: 
                                        if (value < 0) jump = true; 
                                        break; 
                                    case IFNE: 
                                        if (value != 0) jump = true; 
                                        break; 
                                } 
 
                                if (jump) { 
                                    methodNode.instructions.remove(result.getInsnNode(ldcFrame)); 
                                    methodNode.instructions.insert(insn, new LabelNode()); 
                                    methodNode.instructions.set(insn, new JumpInsnNode(Opcodes.GOTO, ((JumpInsnNode) insn).label)); 
                                }else
                                {
                                    methodNode.instructions.remove(result.getInsnNode(ldcFrame));
                                    methodNode.instructions.remove(insn);
                                }
                            } 
                        } else if (insn.getOpcode() == Opcodes.ATHROW && insn.getPrevious().getOpcode() == ACONST_NULL) { 
                            InsnList insert = new InsnList(); 
                            insert.add(new TypeInsnNode(Opcodes.NEW, "java/lang/RuntimeException")); 
                            insert.add(new InsnNode(Opcodes.DUP)); 
                            insert.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", true)); 
                            methodNode.instructions.remove(insn.getPrevious()); 
                            methodNode.instructions.insertBefore(insn, insert); 
                        } 
                    } 
 
                    if (methodNode.localVariables != null) 
                        methodNode.localVariables.clear(); // Fix for Procyon AstBuilder.convertLocalVariables 
        })); 
    } 
 
    private boolean hasBackJump(MethodNode methodNode) { 
        Set<LabelNode> labels = new HashSet<>(); 
        for (AbstractInsnNode insn = methodNode.instructions.getFirst(); insn != null; insn = insn.getNext()) { 
            if (insn instanceof LabelNode) 
                labels.add((LabelNode) insn); 
            else if (insn instanceof JumpInsnNode && labels.contains(((JumpInsnNode) insn).label)) { 
                return true; 
            } 
        } 
 
        return false; 
    } 
}
