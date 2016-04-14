package com.javadeobfuscator.deobfuscator.analyzer.frame;

import com.javadeobfuscator.deobfuscator.analyzer.Value;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Frame {
    protected int opcode;

    protected List<Frame> parents = new ArrayList<>(); // Represents all the frames which contributed to creating this frame
    protected List<Frame> children = new ArrayList<>(); // Represents all the frames which this frame was involved in

    private LinkedList<Value> locals = new LinkedList<>();
    private LinkedList<Value> stack = new LinkedList<>();
    private Value[] localsArr;
    private Value[] stackArr;

    public Frame(int opcode) {
        this.opcode = opcode;
    }

    public boolean isConstant() {
        return false;
    }

    public final int getOpcode() {
        return this.opcode;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[opcode=" + opcode + "]";
    }

    public List<Frame> getChildren() {
        return children;
    }

    public Value getLocalAt(int index) {
        if (localsArr == null) {
            localsArr = locals.toArray(new Value[locals.size()]);
        }
        return localsArr[index];
    }

    public Value getStackAt(int index) {
        if (stackArr == null) {
            stackArr = stack.toArray(new Value[stack.size()]);
        }
        return stackArr[index];
    }

    public void pushLocal(Value value) {
        this.locals.add(value);
        this.localsArr = null;
    }

    public void pushStack(Value value) {
        this.stack.add(value);
        this.stackArr = null;
    }
}
