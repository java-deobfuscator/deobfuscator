package com.javadeobfuscator.deobfuscator.transformers.smoke.peephole; 
 
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode; 
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.InsnList; 
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.JumpInsnNode; 
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LabelNode; 
import com.javadeobfuscator.deobfuscator.transformers.Transformer; 
import com.javadeobfuscator.deobfuscator.utils.Utils; 
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode; 
 
import java.util.Map; 
 
public class EndlessSelfJump extends Transformer { 
    public EndlessSelfJump(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) { 
        super(classes, classpath); 
    } 
 
    @Override 
    public void transform() throws Throwable { 
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> 
                classNode.methods.forEach(methodNode -> { 
                    InsnList copy = Utils.copyInsnList(methodNode.instructions); 
                    for (AbstractInsnNode insn : copy.toArray()) { 
                        if (insn instanceof JumpInsnNode && ((JumpInsnNode) insn).label == insn.getPrevious()) { 
                            methodNode.instructions.remove(insn); 
                        } 
                    } 
        })); 
    } 
}
