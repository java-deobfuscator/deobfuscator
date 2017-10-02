package com.javadeobfuscator.deobfuscator.analyzer.frame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.javadeobfuscator.deobfuscator.analyzer.Value;
import com.javadeobfuscator.deobfuscator.utils.RuntimeTypeAdapterFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Frame {
    private static final String ILLEGAL_OPCODE = "<illegal opcode>";

    private static final String[] OPCODE_NAMES = {
            "nop", "aconst_null", "iconst_m1", "iconst_0", "iconst_1",
            "iconst_2", "iconst_3", "iconst_4", "iconst_5", "lconst_0",
            "lconst_1", "fconst_0", "fconst_1", "fconst_2", "dconst_0",
            "dconst_1", "bipush", "sipush", "ldc", "ldc_w", "ldc2_w", "iload",
            "lload", "fload", "dload", "aload", "iload_0", "iload_1", "iload_2",
            "iload_3", "lload_0", "lload_1", "lload_2", "lload_3", "fload_0",
            "fload_1", "fload_2", "fload_3", "dload_0", "dload_1", "dload_2",
            "dload_3", "aload_0", "aload_1", "aload_2", "aload_3", "iaload",
            "laload", "faload", "daload", "aaload", "baload", "caload", "saload",
            "istore", "lstore", "fstore", "dstore", "astore", "istore_0",
            "istore_1", "istore_2", "istore_3", "lstore_0", "lstore_1",
            "lstore_2", "lstore_3", "fstore_0", "fstore_1", "fstore_2",
            "fstore_3", "dstore_0", "dstore_1", "dstore_2", "dstore_3",
            "astore_0", "astore_1", "astore_2", "astore_3", "iastore", "lastore",
            "fastore", "dastore", "aastore", "bastore", "castore", "sastore",
            "pop", "pop2", "dup", "dup_x1", "dup_x2", "dup2", "dup2_x1",
            "dup2_x2", "swap", "iadd", "ladd", "fadd", "dadd", "isub", "lsub",
            "fsub", "dsub", "imul", "lmul", "fmul", "dmul", "idiv", "ldiv",
            "fdiv", "ddiv", "irem", "lrem", "frem", "drem", "ineg", "lneg",
            "fneg", "dneg", "ishl", "lshl", "ishr", "lshr", "iushr", "lushr",
            "iand", "land", "ior", "lor", "ixor", "lxor", "iinc", "i2l", "i2f",
            "i2d", "l2i", "l2f", "l2d", "f2i", "f2l", "f2d", "d2i", "d2l", "d2f",
            "i2b", "i2c", "i2s", "lcmp", "fcmpl", "fcmpg",
            "dcmpl", "dcmpg", "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle",
            "if_icmpeq", "if_icmpne", "if_icmplt", "if_icmpge", "if_icmpgt",
            "if_icmple", "if_acmpeq", "if_acmpne", "goto", "jsr", "ret",
            "tableswitch", "lookupswitch", "ireturn", "lreturn", "freturn",
            "dreturn", "areturn", "return", "getstatic", "putstatic", "getfield",
            "putfield", "invokevirtual", "invokespecial", "invokestatic",
            "invokeinterface", "invokedynamic", "new", "newarray", "anewarray",
            "arraylength", "athrow", "checkcast", "instanceof", "monitorenter",
            "monitorexit", "wide", "multianewarray", "ifnull", "ifnonnull",
            "goto_w", "jsr_w", "breakpoint", ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE, ILLEGAL_OPCODE,
            ILLEGAL_OPCODE, "impdep1", "impdep2"
    };

    protected transient int opcode;
    protected String mnemonic;

    protected transient List<Frame> parents = new ArrayList<>(); // Represents all the frames which contributed to creating this frame
    protected transient List<Frame> children = new ArrayList<>(); // Represents all the frames which this frame was involved in

//    private transient LinkedList<Value> locals = new LinkedList<>();
//    private transient LinkedList<Value> stack = new LinkedList<>();
    private transient Value[] localsArr;
    private transient Value[] stackArr;
    
    private Boolean isConstant;

    public Frame(int opcode) {
        this.opcode = opcode;
        if (opcode == -1) {
            this.mnemonic = "-1";
        } else {
            this.mnemonic = OPCODE_NAMES[opcode];
        }
    }

    public final int getOpcode() {
        return this.opcode;
    }

    public List<Frame> getChildren() {
        return children;
    }

//    public Value getLocalAt(int index) {
//        if (localsArr == null) {
//            localsArr = locals.toArray(new Value[locals.size()]);
//        }
//        return localsArr[index];
//    }

//    public Value getStackAt(int index) {
//        if (stackArr == null) {
//            stackArr = stack.toArray(new Value[stack.size()]);
//        }
//        return stackArr[index];
//    }

    public void pushLocal(Value value) {
//        this.locals.add(value);
//        this.localsArr = null;
    }

    public void pushStack(Value value) {
//        this.stack.add(value);
//        this.stackArr = null;
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
