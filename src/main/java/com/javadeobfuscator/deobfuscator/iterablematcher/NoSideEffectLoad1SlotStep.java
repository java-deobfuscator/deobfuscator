package com.javadeobfuscator.deobfuscator.iterablematcher;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

public class NoSideEffectLoad1SlotStep extends IterableStep<AbstractInsnNode> {

    private final boolean alsoMatchStackLoad;

    public NoSideEffectLoad1SlotStep(boolean alsoMatchStackLoad) {
        this.alsoMatchStackLoad = alsoMatchStackLoad;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        switch (ain.getOpcode()) {
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case BIPUSH:
            case SIPUSH:
                return true;
            case ILOAD:
            case FLOAD:
            case ALOAD:
                return alsoMatchStackLoad;
            case LDC:
                Object obj = ((LdcInsnNode) ain).cst;
                return !(obj instanceof Double || obj instanceof Long || obj instanceof Type);
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return "NoSideEffectLoad1SlotStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               '}';
    }
}
