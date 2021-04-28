package com.javadeobfuscator.deobfuscator.iterablematcher;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class StackStep extends IterableStep<VarInsnNode> {
    
    private final Boolean isLoad;
    private final boolean twoSlotSize;

    /**
     * 
     * @param isLoad true: load, false: store, null: both
     */
    public StackStep(Boolean isLoad, boolean twoSlotSize) {
        this.isLoad = isLoad;
        this.twoSlotSize = twoSlotSize;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        boolean isStackInsn = ain instanceof VarInsnNode;
        if (!isStackInsn) {
            return false;
        }
        int opcode = ain.getOpcode();
        if (isLoad == null) {
            switch (opcode) {
                case DLOAD:
                case LLOAD:
                case DSTORE:
                case LSTORE:
                    return twoSlotSize;
                default:
                    return !twoSlotSize;
            }
        }
        if (isLoad) {
            if (opcode == DLOAD || opcode == LLOAD) {
                return twoSlotSize;
            } else if (opcode <= ALOAD) {
                return !twoSlotSize;
            }
        } else {
            if (opcode == DSTORE || opcode == LSTORE) {
                return twoSlotSize;
            } else if (opcode > ALOAD) {
                return !twoSlotSize;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "StackStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               ", isLoad=" + this.isLoad +
               ", twoSlotSize=" + this.twoSlotSize +
               '}';
    }
}
