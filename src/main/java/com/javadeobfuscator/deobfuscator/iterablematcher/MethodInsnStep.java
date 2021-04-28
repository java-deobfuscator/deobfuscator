package com.javadeobfuscator.deobfuscator.iterablematcher;

import java.util.function.Predicate;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class MethodInsnStep extends IterableStep<MethodInsnNode> {

    private final int opcode;
    private final String owner;
    private final String name;
    private final String desc;
    private final Predicate<MethodInsnNode> additional;

    public MethodInsnStep(int opcode, String owner, String name, String desc) {
        this(opcode, owner, name, desc, null);
    }

    public MethodInsnStep(int opcode, String owner, String name, String desc, Predicate<MethodInsnNode> additional) {
        Validate.inclusiveBetween(INVOKEVIRTUAL, INVOKEINTERFACE, opcode, "Invalid MethodInsn opcode %s", opcode);
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.additional = additional;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        if (ain.getOpcode() != opcode) {
            return false;
        }
        if (!(ain instanceof MethodInsnNode)) {
            return false;
        }
        MethodInsnNode min = (MethodInsnNode) ain;
        if (owner != null && !min.owner.equals(owner)) {
            return false;
        }
        if (name != null && !min.name.equals(name)) {
            return false;
        }
        if (desc != null && !min.desc.equals(desc)) {
            return false;
        }
        if (additional != null && !additional.test(min)) {
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
        return "MethodInsnStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               ", opcode=" + this.opcode +
               ", owner='" + this.owner + '\'' +
               ", name='" + this.name + '\'' +
               ", desc='" + this.desc + '\'' +
               '}';
    }
}
