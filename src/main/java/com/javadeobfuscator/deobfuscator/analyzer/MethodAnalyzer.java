package com.javadeobfuscator.deobfuscator.analyzer;

import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ArgumentFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ArrayLengthFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ArrayLoadFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ArrayStoreFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.CheckCastFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.DupFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.FieldFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.InstanceofFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.JumpFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LocalFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MathFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MonitorFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MultiANewArrayFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.NewArrayFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.NewFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.PopFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ReturnFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.SwapFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.SwitchFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ThrowFrame;
import com.javadeobfuscator.deobfuscator.exceptions.PreventableStackOverflowError;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BALOAD;
import static org.objectweb.asm.Opcodes.BASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CALOAD;
import static org.objectweb.asm.Opcodes.CASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.D2F;
import static org.objectweb.asm.Opcodes.D2I;
import static org.objectweb.asm.Opcodes.D2L;
import static org.objectweb.asm.Opcodes.DADD;
import static org.objectweb.asm.Opcodes.DALOAD;
import static org.objectweb.asm.Opcodes.DASTORE;
import static org.objectweb.asm.Opcodes.DCMPG;
import static org.objectweb.asm.Opcodes.DCMPL;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.DDIV;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DMUL;
import static org.objectweb.asm.Opcodes.DNEG;
import static org.objectweb.asm.Opcodes.DREM;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DSUB;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2;
import static org.objectweb.asm.Opcodes.DUP2_X1;
import static org.objectweb.asm.Opcodes.DUP2_X2;
import static org.objectweb.asm.Opcodes.DUP_X1;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.F2D;
import static org.objectweb.asm.Opcodes.F2I;
import static org.objectweb.asm.Opcodes.F2L;
import static org.objectweb.asm.Opcodes.FADD;
import static org.objectweb.asm.Opcodes.FALOAD;
import static org.objectweb.asm.Opcodes.FASTORE;
import static org.objectweb.asm.Opcodes.FCMPG;
import static org.objectweb.asm.Opcodes.FCMPL;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_1;
import static org.objectweb.asm.Opcodes.FCONST_2;
import static org.objectweb.asm.Opcodes.FDIV;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FMUL;
import static org.objectweb.asm.Opcodes.FNEG;
import static org.objectweb.asm.Opcodes.FREM;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.FSUB;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.I2B;
import static org.objectweb.asm.Opcodes.I2C;
import static org.objectweb.asm.Opcodes.I2D;
import static org.objectweb.asm.Opcodes.I2F;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.I2S;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.IALOAD;
import static org.objectweb.asm.Opcodes.IAND;
import static org.objectweb.asm.Opcodes.IASTORE;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.ICONST_3;
import static org.objectweb.asm.Opcodes.ICONST_4;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.IDIV;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLE;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.objectweb.asm.Opcodes.IINC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IMUL;
import static org.objectweb.asm.Opcodes.INEG;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKEDYNAMIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IOR;
import static org.objectweb.asm.Opcodes.IREM;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISHL;
import static org.objectweb.asm.Opcodes.ISHR;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.ISUB;
import static org.objectweb.asm.Opcodes.IUSHR;
import static org.objectweb.asm.Opcodes.IXOR;
import static org.objectweb.asm.Opcodes.JSR;
import static org.objectweb.asm.Opcodes.L2D;
import static org.objectweb.asm.Opcodes.L2F;
import static org.objectweb.asm.Opcodes.L2I;
import static org.objectweb.asm.Opcodes.LADD;
import static org.objectweb.asm.Opcodes.LALOAD;
import static org.objectweb.asm.Opcodes.LAND;
import static org.objectweb.asm.Opcodes.LASTORE;
import static org.objectweb.asm.Opcodes.LCMP;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.LDC;
import static org.objectweb.asm.Opcodes.LDIV;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LMUL;
import static org.objectweb.asm.Opcodes.LNEG;
import static org.objectweb.asm.Opcodes.LOOKUPSWITCH;
import static org.objectweb.asm.Opcodes.LOR;
import static org.objectweb.asm.Opcodes.LREM;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSHL;
import static org.objectweb.asm.Opcodes.LSHR;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.LSUB;
import static org.objectweb.asm.Opcodes.LUSHR;
import static org.objectweb.asm.Opcodes.LXOR;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.MULTIANEWARRAY;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.NOP;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RET;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SALOAD;
import static org.objectweb.asm.Opcodes.SASTORE;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.TABLESWITCH;

// todo this is seriously broken. right now any jump which share the same end and start (i.e. insn 5 jumps into insn 8) will only be run once
// however this was based on a flawed assumption that a jump over the same gap would produce the same results every time
// for example, a -> b -> c -> d and a -> c -> d both do a jump from c -> d, but clearly the behaviour at c will be different
public class MethodAnalyzer {
    private static final boolean DEBUG = false;

    public static AnalyzerResult analyze(ClassNode classNode, MethodNode method) {
        if (Modifier.isAbstract(method.access) || Modifier.isNative(method.access)) {
            return AnalyzerResult.EMPTY_RESULT;
        }
        AnalyzerResult result = new AnalyzerResult();
        result.frames = new HashMap<>();

        List<StackObject> stack = new ArrayList<>();
        List<StackObject> locals = new ArrayList<>();
        
        int counter = 0;
        if (!Modifier.isStatic(method.access)) {
            locals.add(new StackObject(new ArgumentFrame(Opcodes.ASTORE, 0), classNode.name));
            counter++;
        }

        for (Type type : Type.getArgumentTypes(method.desc)) {
            Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
            int opcode;
            if(clazz == int.class)
            	opcode = Opcodes.ISTORE;
            else if(clazz == long.class)
            	opcode = Opcodes.LSTORE;
            else if(clazz == double.class)
            	opcode = Opcodes.DSTORE;
            else if(clazz == float.class)
            	opcode = Opcodes.FSTORE;
            else
            	opcode = Opcodes.ASTORE;
            ArgumentFrame frame = new ArgumentFrame(opcode, counter);
            counter++;
            if (clazz == null) {
                locals.add(new StackObject(Object.class, frame, type.getInternalName()));
            } else {
                locals.add(new StackObject(clazz, frame));
            }
            if (clazz == double.class || clazz == long.class) {
                locals.add(new StackObject(clazz, frame));
            }
        }

        Map<AbstractInsnNode, List<TryCatchBlockNode>> handlers = new HashMap<>();
        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode node : method.tryCatchBlocks) {
                AbstractInsnNode start = node.start;
                while (start != node.end) {
                    handlers.computeIfAbsent(start, k -> new ArrayList<>()).add(node);
                    start = start.getNext();
                }
            }
        }

        try {
            execute(classNode, method, method.instructions.getFirst(), stack, locals, handlers, result, new HashSet<>());
        } catch (StackOverflowError e) {
            if (Boolean.getBoolean("com.javadeobfuscator.MethodAnalyzer.debug") || DEBUG) {
                throw e;
            }
            throw new PreventableStackOverflowError("Ran out of stack space while analyzing a method");
        }
        return result;
    }

    private static Frame executeArrayLoad(int opcode, List<StackObject> stack, Class<?> type) {
        Frame index = stack.remove(0).value;
        Frame array = stack.remove(0).value;

        ArrayLoadFrame currentFrame = new ArrayLoadFrame(opcode, index, array);

        if (type == Object.class) {
            stack.add(0, new StackObject(type, currentFrame, "java/lang/Object")); //todo ugh
        } else {
            stack.add(0, new StackObject(type, currentFrame));
        }
        return currentFrame;
    }

    private static Frame executeArrayStore(int opcode, List<StackObject> stack) {
        Frame value = stack.remove(0).value;
        Frame index = stack.remove(0).value;
        Frame arr = stack.remove(0).value;
        return new ArrayStoreFrame(opcode, value, index, arr);
    }

    private static Frame doCast(int opcode, List<StackObject> stack, Class<?> prim) {
        Frame value = stack.remove(0).value;
        Frame currentFrame = new MathFrame(opcode, value);
        stack.add(0, new StackObject(prim, currentFrame));
        return currentFrame;
    }

    @SuppressWarnings("unchecked")
    private static <P> Frame doUnaryMath(int opcode, List<StackObject> stack, Class<P> prim) {
        Frame target = stack.remove(0).value;
        Frame currentFrame = new MathFrame(opcode, target);
        stack.add(0, new StackObject(prim, currentFrame));
        return currentFrame;

    }

    @SuppressWarnings("unchecked")
    private static <P> Frame doBinaryMath(int opcode, List<StackObject> stack, Class<P> prim) {
        Frame obj1 = stack.remove(0).value;
        Frame obj2 = stack.remove(0).value;
        Frame currentFrame = new MathFrame(opcode, obj1, obj2);
        stack.add(0, new StackObject(prim, currentFrame));
        return currentFrame;
    }

    private static void assureSize(List<StackObject> list, int size) {
        while (list.size() <= size) {
            list.add(null);
        }
    }

    @SuppressWarnings({
            "unchecked",
            "unused"
    })
    private static void execute(ClassNode classNode, MethodNode method, AbstractInsnNode now, List<StackObject> stack, List<StackObject> locals, Map<AbstractInsnNode, List<TryCatchBlockNode>> handlers, AnalyzerResult result, Set<Map.Entry<AbstractInsnNode, AbstractInsnNode>> jumped) {
//        System.out.println("Executing " + classNode.name + " " + method.name + method.desc + " " + method.instructions.indexOf(now) + " " + Utils.prettyprint(now));
        boolean done = false;
        Frame currentFrame;
        List<AbstractInsnNode> successors = new ArrayList<>();
        while (true) {
            switch (now.getOpcode()) {
                case NOP:
                    currentFrame = new Frame(NOP);
                    break;
                case ACONST_NULL:
                    currentFrame = new LdcFrame(now.getOpcode(), null);
                    stack.add(0, new StackObject(Object.class, currentFrame, "java/lang/Object"));
                    break;
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                    currentFrame = new LdcFrame(now.getOpcode(), now.getOpcode() - 3);
                    stack.add(0, new StackObject(int.class, currentFrame));
                    break;
                case LCONST_0:
                case LCONST_1:
                    currentFrame = new LdcFrame(now.getOpcode(), now.getOpcode() - 9);
                    stack.add(0, new StackObject(long.class, currentFrame));
                    break;
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                    currentFrame = new LdcFrame(now.getOpcode(), now.getOpcode() - 11);
                    stack.add(0, new StackObject(float.class, currentFrame));
                    break;
                case DCONST_0:
                case DCONST_1:
                    currentFrame = new LdcFrame(now.getOpcode(), now.getOpcode() - 14);
                    stack.add(0, new StackObject(double.class, currentFrame));
                    break;
                case BIPUSH: {
                    IntInsnNode cast = (IntInsnNode) now;
                    currentFrame = new LdcFrame(now.getOpcode(), (byte) cast.operand);
                    stack.add(0, new StackObject(byte.class, currentFrame));
                    break;
                }
                case SIPUSH: {
                    IntInsnNode cast = (IntInsnNode) now;
                    currentFrame = new LdcFrame(now.getOpcode(), (short) cast.operand);
                    stack.add(0, new StackObject(short.class, currentFrame));
                    break;
                }
                case LDC: {
                    LdcInsnNode cast = (LdcInsnNode) now;
                    currentFrame = new LdcFrame(now.getOpcode(), cast.cst);
                    Class<?> unwrapped = Primitives.unwrap(cast.cst.getClass());
                    if (unwrapped == cast.cst.getClass()) {
                        if (cast.cst instanceof Type) {
                            unwrapped = Class.class;
                        } else {
                            unwrapped = cast.cst.getClass();
                        }
                        stack.add(0, new StackObject(Object.class, currentFrame, Type.getType(unwrapped).getInternalName()));
                    } else {
                        stack.add(0, new StackObject(unwrapped, currentFrame));
                    }
                    break;
                }
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD: {
                    VarInsnNode cast = (VarInsnNode) now;
                    assureSize(locals, cast.var);
                    StackObject stackObject = locals.get(cast.var);
                    currentFrame = new LocalFrame(now.getOpcode(), cast.var, stackObject.value);
                    stack.add(0, stackObject);
                    break;
                }
                case IALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, int.class);
                    break;
                case LALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, long.class);
                    break;
                case FALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, float.class);
                    break;
                case DALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, double.class);
                    break;
                case AALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, Object.class);
                    break;
                case BALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, byte.class);
                    break;
                case CALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, char.class);
                    break;
                case SALOAD:
                    currentFrame = executeArrayLoad(now.getOpcode(), stack, short.class);
                    break;
                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE: {
                    VarInsnNode cast = (VarInsnNode) now;
                    StackObject stackObject = stack.remove(0);
                    currentFrame = new LocalFrame(now.getOpcode(), cast.var, stackObject.value);
                    assureSize(locals, cast.var);
                    locals.set(cast.var, new StackObject(stackObject.type, currentFrame, stackObject.initType));
                    break;
                }
                case IASTORE:
                case LASTORE:
                case FASTORE:
                case DASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                case AASTORE:
                    currentFrame = executeArrayStore(now.getOpcode(), stack);
                    break;
                case POP: {
                    StackObject stackObject = stack.remove(0);
                    currentFrame = new PopFrame(now.getOpcode(), stackObject.value);
                    break;
                }
                case POP2: {
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        stack.remove(0);
                        currentFrame = new PopFrame(now.getOpcode(), obj.value);
                    } else {
                        stack.remove(0);
                        StackObject next = stack.remove(0);
                        currentFrame = new PopFrame(now.getOpcode(), obj.value, next.value);
                    }
                    break;
                }
                case DUP: {
                    StackObject stackObject = stack.get(0);
                    currentFrame = new DupFrame(now.getOpcode(), stackObject.value);
                    stack.add(0, stackObject);
                    break;
                }
                case DUP_X1: {
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        throw new IllegalStateException();
                    }
                    if (obj.type == double.class || obj.type == long.class) {
                        throw new IllegalStateException();
                    }
                    stack.add(2, obj);
                    currentFrame = new DupFrame(now.getOpcode(), obj.value);
                    break;
                }
                case DUP_X2: {
                    StackObject obj = stack.get(1);
                    StackObject zeroth = stack.get(0);
                    currentFrame = new DupFrame(now.getOpcode(), zeroth.value);
                    if (obj.type == double.class || obj.type == long.class) {
                        stack.add(2, stack.get(0));
                    } else {
                        stack.add(3, stack.get(0));
                    }
                    break;
                }
                case DUP2: {
                    StackObject o = stack.get(0);
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        stack.add(1, o);
                        currentFrame = new DupFrame(now.getOpcode(), o.value);
                    } else {
                        StackObject o1 = stack.get(1);
                        stack.add(2, o);
                        stack.add(3, o1);
                        currentFrame = new DupFrame(now.getOpcode(), o.value, o1.value);
                    }
                    break;
                }
                case DUP2_X1: {
                    StackObject o = stack.get(0);
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        stack.add(2, o);
                        currentFrame = new DupFrame(now.getOpcode(), o.value);
                    } else {
                        StackObject o1 = stack.get(1);
                        stack.add(3, o);
                        stack.add(4, o1);
                        currentFrame = new DupFrame(now.getOpcode(), o.value, o1.value);
                    }
                    break;
                }
                case DUP2_X2: {
                    StackObject o = stack.get(0);
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        obj = stack.get(1);
                        currentFrame = new DupFrame(now.getOpcode(), o.value);
                        if (obj.type == double.class || obj.type == long.class) {
                            stack.add(2, o);
                        } else {
                            stack.add(3, o);
                        }
                    } else {
                        StackObject o1 = stack.get(1);
                        obj = stack.get(2);
                        currentFrame = new DupFrame(now.getOpcode(), o.value, o1.value);
                        if (obj.type == double.class || obj.type == long.class) {
                            stack.add(3, o);
                            stack.add(4, o1);
                        } else {
                            stack.add(4, o);
                            stack.add(5, o1);
                        }
                    }
                    break;
                }
                case SWAP: {
                    StackObject a = stack.remove(0);
                    StackObject b = stack.remove(0);
                    currentFrame = new SwapFrame(a.value, b.value);
                    stack.add(0, a);
                    stack.add(0, b);
                    break;
                }
                case IADD:
                case ISUB:
                case IMUL:
                case IDIV:
                case IREM:
                case ISHL:
                case ISHR:
                case IUSHR:
                case IAND:
                case IOR:
                case IXOR:
                case LCMP:
                case FCMPL:
                case FCMPG:
                case DCMPL:
                case DCMPG:
                    currentFrame = doBinaryMath(now.getOpcode(), stack, int.class);
                    break;
                case LADD:
                case LSUB:
                case LMUL:
                case LDIV:
                case LREM:
                case LSHL:
                case LSHR:
                case LUSHR:
                case LAND:
                case LOR:
                case LXOR:
                    currentFrame = doBinaryMath(now.getOpcode(), stack, long.class);
                    break;
                case FADD:
                case FSUB:
                case FMUL:
                case FDIV:
                case FREM:
                    currentFrame = doBinaryMath(now.getOpcode(), stack, float.class);
                    break;
                case DADD:
                case DSUB:
                case DMUL:
                case DDIV:
                case DREM:
                    currentFrame = doBinaryMath(now.getOpcode(), stack, double.class);
                    break;
                case INEG:
                    currentFrame = doUnaryMath(now.getOpcode(), stack, int.class);
                    break;
                case LNEG:
                    currentFrame = doUnaryMath(now.getOpcode(), stack, long.class);
                    break;
                case FNEG:
                    currentFrame = doUnaryMath(now.getOpcode(), stack, float.class);
                    break;
                case DNEG:
                    currentFrame = doUnaryMath(now.getOpcode(), stack, double.class);
                    break;
                case IINC: {
                    IincInsnNode cast = (IincInsnNode) now;
                    assureSize(locals, cast.var);
                    StackObject obj = locals.get(cast.var);
                    currentFrame = new LocalFrame(now.getOpcode(), cast.var, obj.value);
                    break;
                }
                case I2L:
                case F2L:
                case D2L: {
                    currentFrame = doCast(now.getOpcode(), stack, long.class);
                    break;
                }
                case I2F:
                case L2F:
                case D2F: {
                    currentFrame = doCast(now.getOpcode(), stack, float.class);
                    break;
                }
                case I2D:
                case L2D:
                case F2D: {
                    currentFrame = doCast(now.getOpcode(), stack, double.class);
                    break;
                }
                case L2I:
                case D2I:
                case F2I: {
                    currentFrame = doCast(now.getOpcode(), stack, int.class);
                    break;
                }
                case I2B: {
                    currentFrame = doCast(now.getOpcode(), stack, byte.class);
                    break;
                }
                case I2C: {
                    currentFrame = doCast(now.getOpcode(), stack, char.class);
                    break;
                }
                case I2S: {
                    currentFrame = doCast(now.getOpcode(), stack, short.class);
                    break;
                }
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IFNULL:
                case IFNONNULL: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    Frame o = stack.remove(0).value;
                    successors.add(cast.label);
                    successors.add(now.getNext());
                    currentFrame = new JumpFrame(now.getOpcode(), Collections.singletonList(o), cast.label, now.getNext());
                    break;
                }
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGT:
                case IF_ICMPGE:
                case IF_ICMPLE:
                case IF_ACMPNE:
                case IF_ACMPEQ: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    Frame o = stack.remove(0).value;
                    Frame o1 = stack.remove(0).value;
                    successors.add(cast.label);
                    successors.add(now.getNext());
                    currentFrame = new JumpFrame(now.getOpcode(), Arrays.asList(o, o1), cast.label, now.getNext());
                    break;
                }
                case GOTO: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    successors.add(cast.label);
                    currentFrame = new JumpFrame(now.getOpcode(), Collections.emptyList(), cast.label);
                    break;
                }
                case JSR: {
                    //                    JumpInsnNode cast = (JumpInsnNode) now;
                    //                    stack.add(0, new StackObject(Object.class, now));
                    //                    now = cast.label;
                    //                    break;
                    throw new UnsupportedOperationException();
                }
                case RET: {
                    //                    VarInsnNode cast = (VarInsnNode) now;
                    //                    StackObject ret = locals.get(cast.var);
                    //                    if (ret.value instanceof AbstractInsnNode) {
                    //                        now = (AbstractInsnNode) ret.value;
                    //                        break;
                    //                    }
                    //                    throw new IllegalArgumentException();
                    throw new UnsupportedOperationException();
                }
                case TABLESWITCH: {
                    Frame frame = stack.remove(0).value;
                    TableSwitchInsnNode cast = (TableSwitchInsnNode) now;
                    currentFrame = new SwitchFrame(now.getOpcode(), frame, cast.labels, cast.dflt);
                    successors.addAll(cast.labels);
                    successors.add(cast.dflt);
                    break;
                }
                case LOOKUPSWITCH: {
                    Frame frame = stack.remove(0).value;
                    LookupSwitchInsnNode cast = (LookupSwitchInsnNode) now;
                    currentFrame = new SwitchFrame(now.getOpcode(), frame, cast.labels, cast.dflt);
                    successors.addAll(cast.labels);
                    successors.add(cast.dflt);
                    break;
                }
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN: {
                    currentFrame = new ReturnFrame(now.getOpcode(), stack.remove(0).value);
                    done = true;
                    break;
                }
                case RETURN: {
                    currentFrame = new ReturnFrame(now.getOpcode(), null);
                    done = true;
                    break;
                }
                case GETSTATIC: {
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    currentFrame = new FieldFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, null, null);
                    if (clazz == null) {
                        stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                    } else {
                        stack.add(0, new StackObject(clazz, currentFrame));
                    }
                    break;
                }
                case PUTSTATIC: {
                    FieldInsnNode cast = (FieldInsnNode) now;
                    currentFrame = new FieldFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, null, stack.remove(0).value);
                    break;
                }
                case GETFIELD: {
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    currentFrame = new FieldFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, stack.remove(0).value, null);

                    if (clazz == null) {
                        stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                    } else {
                        stack.add(0, new StackObject(clazz, currentFrame));
                    }
                    break;
                }
                case PUTFIELD: {
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Frame obj = stack.remove(0).value;
                    Frame instance = stack.remove(0).value;
                    currentFrame = new FieldFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, instance, obj);
                    break;
                }
                case INVOKEVIRTUAL: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<Frame> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).value);
                    }
                    currentFrame = new MethodFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, stack.remove(0).value, args);
                    if (type.getSort() != Type.VOID) {
                        if (clazz == null) {
                            stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                        } else {
                            stack.add(0, new StackObject(clazz, currentFrame));
                        }
                    }
                    break;
                }
                case INVOKESPECIAL: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<Frame> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).value);
                    }
                    StackObject instance = stack.remove(0);
                    //                if (instance.isInitialized && cast.name.equals("<init>"))
                    //                    throw new IllegalArgumentException("Already initialized");
                    instance.initialize();
                    currentFrame = new MethodFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, instance.value, args);
                    if (type.getSort() != Type.VOID) {
                        if (clazz == null) {
                            stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                        } else {
                            stack.add(0, new StackObject(clazz, currentFrame));
                        }
                    }
                    break;
                }
                case INVOKESTATIC: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<Frame> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).value);
                    }
                    currentFrame = new MethodFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, null, args);
                    if (type.getSort() != Type.VOID) {
                        if (clazz == null) {
                            stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                        } else {
                            stack.add(0, new StackObject(clazz, currentFrame));
                        }
                    }
                    break;
                }
                case INVOKEINTERFACE: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<Frame> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).value);
                    }
                    currentFrame = new MethodFrame(now.getOpcode(), cast.owner, cast.name, cast.desc, stack.remove(0).value, args);
                    if (type.getSort() != Type.VOID) {
                        if (clazz == null) {
                            stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                        } else {
                            stack.add(0, new StackObject(clazz, currentFrame));
                        }
                    }
                    break;
                }
                case INVOKEDYNAMIC: {
                    InvokeDynamicInsnNode cast = (InvokeDynamicInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<Frame> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).value);
                    }
                    currentFrame = new MethodFrame(now.getOpcode(), "", cast.name, cast.desc, null, args);
                    if (type.getSort() != Type.VOID) {
                        if (clazz == null) {
                            stack.add(0, new StackObject(Object.class, currentFrame, type.getInternalName()));
                        } else {
                            stack.add(0, new StackObject(clazz, currentFrame));
                        }
                    }
                    break;
                    //                    throw new UnsupportedOperationException();
                }
                case NEW: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    currentFrame = new NewFrame(cast.desc);
                    stack.add(0, new StackObject((NewFrame) currentFrame));
                    break;
                }
                case NEWARRAY: {
                    Frame len = stack.remove(0).value;
                    IntInsnNode cast = (IntInsnNode) now;
                    currentFrame = new NewArrayFrame(now.getOpcode(), PrimitiveUtils.getPrimitiveByNewArrayId(cast.operand).getSimpleName(), len);
                    String desc = "[" + Type.getType(PrimitiveUtils.getPrimitiveByNewArrayId(cast.operand)).getDescriptor();
                    stack.add(0, new StackObject(Object.class, currentFrame, desc));
                    break;
                }
                case ANEWARRAY: {
                    Frame len = stack.remove(0).value;
                    TypeInsnNode cast = (TypeInsnNode) now;
                    currentFrame = new NewArrayFrame(now.getOpcode(), cast.desc, len);
                    String desc = null;
                    Type type;
                    try {
                        // if array is multidimensional, type is a descriptor
                        type = Type.getType(cast.desc);
                    } catch (Throwable ignored) {
                        // however, if array is single dimensional, type is an object type
                        // this is especially bad if the object type is something like "LongHashMap" because it starts with an "L"
                        type = Type.getObjectType(cast.desc);
                    }
                    if (type.getSort() == Type.ARRAY) {
                        desc = type.getDescriptor();
                    } else {
                        desc = "[" + type.getDescriptor() + ";";
                    }
                    stack.add(0, new StackObject(Object.class, currentFrame, desc));
                    break;
                }
                case ARRAYLENGTH: {
                    Frame obj = stack.remove(0).value;
                    currentFrame = new ArrayLengthFrame(obj);
                    stack.add(0, new StackObject(int.class, currentFrame));
                    break;
                }
                case ATHROW: {
                    Frame throwable = stack.remove(0).value;
                    currentFrame = new ThrowFrame(throwable);
                    done = true;
                    break;
                }
                case CHECKCAST: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    StackObject obj = new StackObject(Object.class, stack.get(0).value, cast.desc);
                    stack.remove(0);
                    stack.add(0, obj);
                    currentFrame = new CheckCastFrame(obj.value);
                    break;
                }
                case INSTANCEOF: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    currentFrame = new InstanceofFrame(stack.remove(0).value);
                    stack.add(0, new StackObject(int.class, currentFrame));
                    break;
                }
                case MONITORENTER:
                case MONITOREXIT: {
                    currentFrame = new MonitorFrame(now.getOpcode(), stack.remove(0).value);
                    break;
                }
                case MULTIANEWARRAY: {
                    MultiANewArrayInsnNode cast = (MultiANewArrayInsnNode) now;
                    List<Frame> sizes = new ArrayList<>();
                    for (int i = 0; i < cast.dims; i++) {
                        sizes.add(0, stack.remove(0).value);
                    }
                    currentFrame = new MultiANewArrayFrame(sizes);
                    String desc = cast.desc;
                    for (int i = 0; i < cast.dims; i++) {
                        desc = "[" + desc;
                    }
                    stack.add(0, new StackObject(Object.class, currentFrame, desc));
                    break;
                }
                case -1: {
                    currentFrame = null;
                    break;
                }
                default: {
                    throw new IllegalArgumentException(now.getOpcode() + " ");
                }
            }
//            System.out.println(method.instructions.indexOf(now) + " " + Utils.prettyprint(now).trim());
//            System.out.println("\t Stack: " + stack); 
//            System.out.println("\t Locals: " + locals); 
//            System.out.println();
            if (currentFrame != null) {
                List<Frame> thisFrame = result.frames.computeIfAbsent(now, k -> new ArrayList<>());
                thisFrame.add(currentFrame);
                result.mapping = null;
                result.maxLocals = Math.max(result.maxLocals, locals.size());
                result.maxStack = Math.max(result.maxStack, stack.size());

                for (int i = 0; i < locals.size(); i++) {
                    StackObject object = locals.get(i);
                    if (object == null) {
                        currentFrame.pushLocal(new Value(ValueType.NULL));
                    } else {
                        ValueType type = object.getType();
                        String desc = type == ValueType.UNINITIALIZED_THIS ? classNode.name : object.initType;
                        currentFrame.pushLocal(new Value(type, desc));
                    }
                }

                for (int i = 0; i < stack.size(); i++) {
                    StackObject object = stack.get(i);
                    if (object == null) {
                        throw new IllegalArgumentException();
                    }
                    ValueType type = object.getType();
                    String desc = type == ValueType.UNINITIALIZED_THIS ? classNode.name : object.initType;
                    currentFrame.pushStack(new Value(type, desc));
                }

            }
            List<TryCatchBlockNode> handler = handlers.get(now);
            if (handler != null) {
                for (TryCatchBlockNode tcbn : handler) {
                    if (jumped.add(new AbstractMap.SimpleEntry<>(now, tcbn.handler))) {
                        List<StackObject> newStack = new ArrayList<>();
                        newStack.add(0, new StackObject(new ArgumentFrame(-1, -1), tcbn.type == null ? "java/lang/Throwable" : tcbn.type));
                        List<StackObject> newLocals = new ArrayList<>();
                        newLocals.addAll(locals);
                        execute(classNode, method, tcbn.handler, newStack, newLocals, handlers, result, jumped);
                    }
                }
            }
            if (done) {
                return;
            }
            if (!successors.isEmpty()) {
                for (AbstractInsnNode successor : successors) {
                    if (jumped.add(new AbstractMap.SimpleEntry<>(now, successor))) {
                        List<StackObject> newStack = new ArrayList<>();
                        newStack.addAll(stack);
                        List<StackObject> newLocals = new ArrayList<>();
                        newLocals.addAll(locals);
                        execute(classNode, method, successor, newStack, newLocals, handlers, result, jumped);
                    }
                }
                return;
            } else {
                now = now.getNext();
            }
        }
    }

    public static class StackObject {
        public Class<?> type;
        public Frame value;
        public boolean isInitialized = true;
        public boolean isThis = false;
        public String initType;

        public StackObject(Class<?> type, Frame value) {
            this(type, value, null);
        }

        public StackObject(Class<?> type, Frame value, String desc) {
            if (Primitives.unwrap(type) != type) {
                throw new IllegalArgumentException();
            }
            this.type = type;
            this.value = value;
            this.initType = desc;
            if (type == Object.class && desc == null) {
                throw new IllegalArgumentException();
            }
        }

        public StackObject(NewFrame frame) {
            this.isInitialized = false;
            this.type = Object.class;
            this.initType = frame.getType();
            this.value = frame;
            if (this.initType == null) {
                throw new IllegalArgumentException("No inittype");
            }
        }

        public StackObject(ArgumentFrame frame, String type) {
            this.isInitialized = false;
            this.isThis = true;
            this.type = Object.class;
            this.value = frame;
            this.initType = type;
            if (this.initType == null) {
                throw new IllegalArgumentException("No inittype");
            }
        }

        public void initialize() {
            this.isInitialized = true;
        }

        public String toString() {
            return (value == null ? "null" : value.toString()) + " t=" + initType;
        }

        public ValueType getType() {
            if (value == null)
                return ValueType.NULL;
            if (isThis && !isInitialized)
                return ValueType.UNINITIALIZED_THIS;
            if (!isInitialized)
                return ValueType.UNINITIALIZED;
            if (type == int.class)
                return ValueType.INTEGER;
            if (type == double.class)
                return ValueType.DOUBLE;
            if (type == float.class)
                return ValueType.FLOAT;
            if (type == long.class)
                return ValueType.LONG;
            if (type == Object.class)
                return ValueType.OBJECT;
            if (type == boolean.class)
                return ValueType.INTEGER;
            if (type == short.class)
                return ValueType.INTEGER;
            if (type == byte.class)
                return ValueType.INTEGER;
            if (type == char.class)
                return ValueType.INTEGER;
            throw new IllegalArgumentException(type + " " + toString());
        }
    }
}
