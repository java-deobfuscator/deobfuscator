package com.javadeobfuscator.deobfuscator.iterablematcher;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class FieldInsnStep extends IterableStep<FieldInsnNode> {

    private final int opcode;
    private final String owner;
    private final String name;
    private final String desc;

    public FieldInsnStep(int opcode, String owner, String name, String desc) {
        Validate.inclusiveBetween(GETSTATIC, PUTFIELD, opcode, "Invalid FieldInsn opcode %s", opcode);
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        if (ain.getOpcode() != opcode) {
            return false;
        }
        if (!(ain instanceof FieldInsnNode)) {
            return false;
        }
        FieldInsnNode fin = (FieldInsnNode) ain;
        if (owner != null && !fin.owner.equals(owner)) {
            return false;
        }
        if (name != null && !fin.name.equals(name)) {
            return false;
        }
        if (desc != null && !fin.desc.equals(desc)) {
            return false;
        }
        return true;
    }

    public int getOpcode() {
        return opcode;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return "FieldInsnStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               ", opcode=" + this.opcode +
               ", owner='" + this.owner + '\'' +
               ", name='" + this.name + '\'' +
               ", desc='" + this.desc + '\'' +
               '}';
    }
}
