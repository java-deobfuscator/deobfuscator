package com.javadeobfuscator.deobfuscator.iterablematcher;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

public class NoSideEffectLoad2SlotStep extends IterableStep<AbstractInsnNode> {

    private final boolean alsoMatchStackLoad;

    public NoSideEffectLoad2SlotStep(boolean alsoMatchStackLoad) {
        this.alsoMatchStackLoad = alsoMatchStackLoad;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        switch (ain.getOpcode()) {
            case LCONST_0:
            case LCONST_1:
            case DCONST_0:
            case DCONST_1:
                return true;
            case LLOAD:
            case DLOAD:
                return alsoMatchStackLoad;
            case LDC:
                Object obj = ((LdcInsnNode) ain).cst;
                return obj instanceof Double || obj instanceof Long;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "NoSideEffectLoad2SlotStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               '}';
    }
}
