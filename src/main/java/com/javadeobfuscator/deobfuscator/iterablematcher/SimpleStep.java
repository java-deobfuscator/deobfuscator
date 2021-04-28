package com.javadeobfuscator.deobfuscator.iterablematcher;

import java.util.Arrays;

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.apache.commons.lang3.Validate;
import org.objectweb.asm.tree.AbstractInsnNode;

public class SimpleStep extends IterableStep<AbstractInsnNode> {

    private final int[] opcodes;

    public SimpleStep(int... opcodes) {
        this.opcodes = opcodes;
    }

    public static SimpleStep ofOpcodeRangeInclusive(int start, int end) {
        Validate.isTrue(start < end, "start opcode %s not smaller than end opcode %s", start, end);
        int[] arr = new int[end - start + 1];
        int j = 0;
        for (int i = start; i <= end; i++) {
            arr[j++] = i;
        }
        return new SimpleStep(arr);
    }

    public static SimpleStep ofOpcodeRangeInclusive(int start, int end, int[] additional) {
        Validate.isTrue(start < end, "start opcode %s not smaller than end opcode %s", start, end);
        int[] arr = new int[end - start + 1 + additional.length];
        int j = 0;
        for (int i = start; i <= end; i++) {
            arr[j++] = i;
        }
        for (int i : additional) {
            arr[j++] = i;
        }
        return new SimpleStep(arr);
    }

    public int[] getOpcodes() {
        return opcodes;
    }

    @Override
    public boolean tryMatch(AbstractInsnNode ain) {
        int ainOpcode = ain.getOpcode();
        for (int opcode : opcodes) {
            if (ainOpcode == opcode) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "SimpleStep{" +
               "captured=" + Utils.prettyprint(this.getCaptured()) +
               ", opcodes=" + Arrays.toString(this.opcodes) +
               '}';
    }
}
