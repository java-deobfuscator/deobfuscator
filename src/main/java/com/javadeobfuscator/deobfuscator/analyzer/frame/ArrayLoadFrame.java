package com.javadeobfuscator.deobfuscator.analyzer.frame;

public class ArrayLoadFrame extends Frame {
    private Frame index;
    private Frame array;

    public ArrayLoadFrame(int opcode, Frame index, Frame array) {
        super(opcode);
        this.index = index;
        this.array = array;
        this.index.children.add(this);
        this.array.children.add(this);
    }

    public Frame getIndex() {
        return this.index;
    }

    public Frame getArray() {
        return this.array;
    }

    @Override
    public boolean isConstant() {
        return false;
    }
}
