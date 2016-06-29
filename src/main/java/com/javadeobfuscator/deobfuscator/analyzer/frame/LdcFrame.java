package com.javadeobfuscator.deobfuscator.analyzer.frame;

public class LdcFrame extends Frame {
    private Object cst;

    public LdcFrame(int opcode, Object cst) {
        super(opcode);
        this.cst = cst;
    }

    public Object getConstant() {
        return cst;
    }

    @Override
    public boolean isConstant() {
        return true;
    }
}
