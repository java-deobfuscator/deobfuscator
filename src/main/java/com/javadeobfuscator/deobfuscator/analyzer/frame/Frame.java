package com.javadeobfuscator.deobfuscator.analyzer.frame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.javadeobfuscator.deobfuscator.analyzer.Value;
import com.javadeobfuscator.deobfuscator.utils.RuntimeTypeAdapterFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Frame {
    protected int opcode;

    protected transient List<Frame> parents = new ArrayList<>(); // Represents all the frames which contributed to creating this frame
    protected transient List<Frame> children = new ArrayList<>(); // Represents all the frames which this frame was involved in

    private transient LinkedList<Value> locals = new LinkedList<>();
    private transient LinkedList<Value> stack = new LinkedList<>();
    private transient Value[] localsArr;
    private transient Value[] stackArr;
    
    private Boolean isConstant;

    public Frame(int opcode) {
        this.opcode = opcode;
    }

    public final int getOpcode() {
        return this.opcode;
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
    
    public boolean isConstant() {
        if (isConstant == null) {
            calculateConstant();
        }
        return isConstant;
    }
    
    private void calculateConstant() {
        boolean constant = true;
        for (Frame frame : parents) {
            constant &= frame.isConstant();
        }
        this.isConstant = constant; 
    }
    
    @Override
    public String toString() {
        isConstant();
        return GSON.toJson(this, Frame.class);    
    }

    private static final Gson GSON;
    
    static {
        RuntimeTypeAdapterFactory<Frame> typeAdapterFactory = RuntimeTypeAdapterFactory.of(Frame.class);
        typeAdapterFactory.registerSubtype(ArgumentFrame.class)
                .registerSubtype(ArrayLengthFrame.class)
                .registerSubtype(ArrayLoadFrame.class)
                .registerSubtype(ArrayStoreFrame.class)
                .registerSubtype(CheckCastFrame.class)
                .registerSubtype(DupFrame.class)
                .registerSubtype(FieldFrame.class)
                .registerSubtype(InstanceofFrame.class)
                .registerSubtype(JumpFrame.class)
                .registerSubtype(LdcFrame.class)
                .registerSubtype(LocalFrame.class)
                .registerSubtype(MathFrame.class)
                .registerSubtype(MethodFrame.class)
                .registerSubtype(MonitorFrame.class)
                .registerSubtype(MultiANewArrayFrame.class)
                .registerSubtype(NewArrayFrame.class)
                .registerSubtype(NewFrame.class)
                .registerSubtype(PopFrame.class)
                .registerSubtype(ReturnFrame.class)
                .registerSubtype(SwapFrame.class)
                .registerSubtype(SwitchFrame.class)
                .registerSubtype(ThrowFrame.class)
                .registerSubtype(Frame.class);
        GSON = new GsonBuilder().registerTypeAdapterFactory(typeAdapterFactory).serializeNulls().create();
    }
}
