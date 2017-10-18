package com.javadeobfuscator.deobfuscator.analyzer.frame;

public class LocalFrame extends Frame {
    private int local;
    private Frame value;

    public LocalFrame(int opcode, int local, Frame value) {
        super(opcode);
        this.local = local;
        this.value = value;
        if (this.value != null)
        this.value.children.add(this);
    }
    
    public int getLocal() {
    	return local;
    }
    
    public Frame getValue() {
    	return value;
    }
}
