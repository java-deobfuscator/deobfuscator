package com.javadeobfuscator.deobfuscator.analyzer.frame;

public class ArrayStoreFrame extends Frame {

    private Frame object;
    private Frame index;
    private Frame array;

    public ArrayStoreFrame(int opcode, Frame object, Frame index, Frame array) {
        super(opcode);
        this.object = object;
        this.index = index;
        this.array = array;
        this.object.children.add(this);
        this.index.children.add(this);
        this.array.children.add(this);
        this.parents.add(this.object);
        this.parents.add(this.index);
        this.parents.add(this.array);
    }

    public Frame getObject(){
         return this.object;
    }

    public Frame getIndex()  {
        return this.index;
    }

    public Frame getArray() {
        return this.array;
    }

}
