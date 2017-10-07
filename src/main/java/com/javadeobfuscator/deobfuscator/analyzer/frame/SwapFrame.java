package com.javadeobfuscator.deobfuscator.analyzer.frame;

import org.objectweb.asm.Opcodes;

public class SwapFrame extends Frame {
    private Frame top;
    private Frame bottom;
    public SwapFrame(Frame top, Frame bottom) {
        super (Opcodes.SWAP);
        this.top = top;
        this.bottom = bottom;
        this.top.children.add(this);
        this.bottom.children.add(this);
    }
}
