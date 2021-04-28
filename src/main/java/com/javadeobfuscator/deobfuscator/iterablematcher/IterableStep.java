package com.javadeobfuscator.deobfuscator.iterablematcher;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

public abstract class IterableStep<T extends AbstractInsnNode> implements Opcodes {

    private T captured;
    
    public final void reset() {
        captured = null;
        reset0();
    }
    
    public void reset0() {
        
    }

    public final T getCaptured() {
        return captured;
    }

    public final boolean match(AbstractInsnNode ain) {
        if (tryMatch(ain)) {
            captured = (T) ain;
            return true;
        }
        return false;
    }

    public abstract boolean tryMatch(AbstractInsnNode ain);

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return "IterableStep{" +
               "captured=" + this.captured +
               '}';
    }
}
