package com.javadeobfuscator.deobfuscator.iterablematcher;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class TypeInsnStep extends IterableStep<TypeInsnNode> {

    private final int opcode;
    private final String desc;

    public TypeInsnStep(int opcode, String desc) {
        Validate.isTrue(opcode == NEW || opcode == ANEWARRAY || opcode == CHECKCAST || opcode == INSTANCEOF, "Invalid TypeInsn opcode %s", opcode);
        this.opcode = opcode;
        this.desc = desc;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        if (ain.getOpcode() != opcode) {
            return false;
        }
        if (!(ain instanceof TypeInsnNode)) {
            return false;
        }
        TypeInsnNode tin = (TypeInsnNode) ain;
        return desc == null || tin.desc.equals(desc);
    }

    @Override
    public String toString() {
        return "TypeInsnStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               ", opcode=" + this.opcode +
               ", desc='" + this.desc + '\'' +
               '}';
    }
}
