package com.javadeobfuscator.deobfuscator.executor;

import static com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.base.Optional;
import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.executor.exceptions.NoSuchMethodHandlerException;
import com.javadeobfuscator.deobfuscator.executor.providers.Provider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.FieldInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.IincInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.IntInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.JumpInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LdcInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LookupSwitchInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MultiANewArrayInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.TableSwitchInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.TryCatchBlockNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.TypeInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.VarInsnNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class MethodExecutor {
    public static <T> T execute(WrappedClassNode classNode, MethodNode method, List<StackObject> args, Object instance, Context context) {
        if (context == null)
            throw new IllegalArgumentException("Null context");
        List<StackObject> stack = new ArrayList<>();
        List<StackObject> locals = new ArrayList<>();
        if (!Modifier.isStatic(method.access)) {
            locals.add(new StackObject(Object.class, instance));
        }
        if (args != null) {
            for (StackObject arg : args) {
                locals.add(arg);
            }
        }
        for (int i = 0; i < method.maxLocals + 10; i++) {
            locals.add(null);
        }
        return execute(classNode, method, method.instructions.getFirst(), stack, locals, context);
    }

    private static void executeArrayLoad(List<StackObject> stack, Class<?> type) {
        int index = stack.remove(0).as(int.class);
        Object arr = stack.remove(0).value;
        stack.add(0, new StackObject(type, Array.get(arr, index)));
    }

    private static void executeArrayStore(List<StackObject> stack) {
        Object value = stack.remove(0).value;
        int index = stack.remove(0).as(int.class);
        Object arr = stack.remove(0).value;
        Type type = Type.getType(arr.getClass());
        Class<?> primitive = PrimitiveUtils.getPrimitiveByName(type.getElementType().getClassName());
        if (primitive != null) {
            value = castToPrimitive(value, primitive);
        }
        Array.set(arr, index, value);
    }

    @SuppressWarnings("unchecked")
    private static <T, P> void doMath(List<StackObject> stack, Class<P> prim, Class<T> t, Function<T, P> action) {
        Object obj1 = stack.remove(0).as(prim);
        stack.add(0, new StackObject(prim, action.apply((T) obj1)));
    }

    @SuppressWarnings("unchecked")
    private static <T, P> void doMath(List<StackObject> stack, Class<P> prim, Class<T> t, BiFunction<T, T, P> action) {
        Object obj1 = stack.remove(0).as(prim);
        Object obj2 = stack.remove(0).as(prim);
        stack.add(0, new StackObject(prim, action.apply((T) obj2, (T) obj1)));
    }

    @SuppressWarnings({
            "unchecked",
            "unused"
    })
    private static <T> T execute(WrappedClassNode classNode, MethodNode method, AbstractInsnNode now, List<StackObject> stack, List<StackObject> locals, Context context) {
        context.push(classNode.classNode.name, method.name, classNode.constantPoolSize);
        //System.out.println("Executing " + classNode.classNode.name + " " + method.name + method.desc);
        while (true) {
            try {
                //System.out.println("\t" + stack);
                //System.out.println("\t" + locals);
                //System.out.println();
                //System.out.println(method.instructions.indexOf(now) + " " + Utils.prettyprint(now));
                switch (now.getOpcode()) {
                case NOP:
                    break;
                case ACONST_NULL:
                    stack.add(0, StackObject.forPrimitive(Object.class));
                    break;
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                    stack.add(0, new StackObject(int.class, now.getOpcode() - 3));
                    break;
                case LCONST_0:
                case LCONST_1:
                    stack.add(0, new StackObject(long.class, (long) (now.getOpcode() - 9)));
                    break;
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                    stack.add(0, new StackObject(float.class, (float) (now.getOpcode() - 11)));
                    break;
                case DCONST_0:
                case DCONST_1:
                    stack.add(0, new StackObject(double.class, (double) (now.getOpcode() - 14)));
                    break;
                case BIPUSH: {
                    IntInsnNode cast = (IntInsnNode) now;
                    stack.add(0, new StackObject(byte.class, (byte) cast.operand));
                    break;
                }
                case SIPUSH: {
                    IntInsnNode cast = (IntInsnNode) now;
                    stack.add(0, new StackObject(short.class, (short) cast.operand));
                    break;
                }
                case LDC: {
                    LdcInsnNode cast = (LdcInsnNode) now;
                    stack.add(0, new StackObject(Primitives.unwrap(cast.cst.getClass()), cast.cst));
                    break;
                }
                case ILOAD:
                case LLOAD:
                case FLOAD:
                case DLOAD:
                case ALOAD: {
                    VarInsnNode cast = (VarInsnNode) now;
                    stack.add(0, locals.get(cast.var).copy());
                    break;
                }
                case IALOAD:
                    executeArrayLoad(stack, int.class);
                    break;
                case LALOAD:
                    executeArrayLoad(stack, long.class);
                    break;
                case FALOAD:
                    executeArrayLoad(stack, float.class);
                    break;
                case DALOAD:
                    executeArrayLoad(stack, double.class);
                    break;
                case AALOAD:
                    executeArrayLoad(stack, Object.class);
                    break;
                case BALOAD:
                    executeArrayLoad(stack, byte.class);
                    break;
                case CALOAD:
                    executeArrayLoad(stack, char.class);
                    break;
                case SALOAD:
                    executeArrayLoad(stack, short.class);
                    break;
                case ISTORE:
                case LSTORE:
                case FSTORE:
                case DSTORE:
                case ASTORE: {
                    VarInsnNode cast = (VarInsnNode) now;
                    locals.set(cast.var, stack.remove(0).copy());
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
                    executeArrayStore(stack);
                    break;
                case POP:
                    stack.remove(0);
                    break;
                case POP2: {
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        stack.remove(0);
                    } else {
                        stack.remove(0);
                        stack.remove(0);
                    }
                    break;
                }
                case DUP:
                    stack.add(0, stack.get(0));
                    break;
                case DUP_X1: {
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        throw new IllegalStateException();
                    }
                    if (obj.type == double.class || obj.type == long.class) {
                        throw new IllegalStateException();
                    }
                    stack.add(2, stack.get(0));
                    break;
                }
                case DUP_X2: {
                    StackObject obj = stack.get(1);
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
                    } else {
                        StackObject o1 = stack.get(1);
                        stack.add(2, o);
                        stack.add(3, o1);
                    }
                    break;
                }
                case DUP2_X1: {
                    StackObject o = stack.get(0);
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        stack.add(2, o);
                    } else {
                        StackObject o1 = stack.get(1);
                        stack.add(3, o);
                        stack.add(4, o1);
                    }
                    break;
                }
                case DUP2_X2: {
                    StackObject o = stack.get(0);
                    StackObject obj = stack.get(0);
                    if (obj.type == double.class || obj.type == long.class) {
                        obj = stack.get(1);
                        if (obj.type == double.class || obj.type == long.class) {
                            stack.add(2, o);
                        } else {
                            stack.add(3, o);
                        }
                    } else {
                        StackObject o1 = stack.get(1);
                        obj = stack.get(2);
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
                    stack.add(0, a);
                    stack.add(0, b);
                    break;
                }
                case IADD:
                    doMath(stack, int.class, Integer.class, (x, y) -> x + y);
                    break;
                case ISUB:
                    doMath(stack, int.class, Integer.class, (x, y) -> x - y);
                    break;
                case IMUL:
                    doMath(stack, int.class, Integer.class, (x, y) -> x * y);
                    break;
                case IDIV:
                    doMath(stack, int.class, Integer.class, (x, y) -> x / y);
                    break;
                case IREM:
                    doMath(stack, int.class, Integer.class, (x, y) -> x % y);
                    break;
                case ISHL:
                    doMath(stack, int.class, Integer.class, (x, y) -> x << y);
                    break;
                case ISHR:
                    doMath(stack, int.class, Integer.class, (x, y) -> x >> y);
                    break;
                case IUSHR:
                    doMath(stack, int.class, Integer.class, (x, y) -> x >>> y);
                    break;
                case IAND:
                    doMath(stack, int.class, Integer.class, (x, y) -> x & y);
                    break;
                case IOR:
                    doMath(stack, int.class, Integer.class, (x, y) -> x | y);
                    break;
                case IXOR:
                    doMath(stack, int.class, Integer.class, (x, y) -> x ^ y);
                    break;
                case LCMP:
                    doMath(stack, int.class, Long.class, (x, y) -> x.compareTo(y));
                    break;
                case FCMPL:
                    doMath(stack, int.class, Float.class, (x, y) -> Float.isNaN((Float) x) || Float.isNaN((Float) y) ? -1 : x.compareTo(y));
                    break;
                case FCMPG:
                    doMath(stack, int.class, Float.class, (x, y) -> Float.isNaN((Float) x) || Float.isNaN((Float) y) ? 1 : x.compareTo(y));
                    break;
                case DCMPL:
                    doMath(stack, int.class, Double.class, (x, y) -> Double.isNaN((Double) x) || Double.isNaN((Double) y) ? -1 : x.compareTo(y));
                    break;
                case DCMPG:
                    doMath(stack, int.class, Double.class, (x, y) -> Double.isNaN((Double) x) || Double.isNaN((Double) y) ? 1 : x.compareTo(y));
                    break;
                case LADD:
                    doMath(stack, long.class, Long.class, (x, y) -> x + y);
                    break;
                case LSUB:
                    doMath(stack, long.class, Long.class, (x, y) -> x - y);
                    break;
                case LMUL:
                    doMath(stack, long.class, Long.class, (x, y) -> x * y);
                    break;
                case LDIV:
                    doMath(stack, long.class, Long.class, (x, y) -> x / y);
                    break;
                case LREM:
                    doMath(stack, long.class, Long.class, (x, y) -> x % y);
                    break;
                case LSHL:
                    doMath(stack, long.class, Long.class, (x, y) -> x << y);
                    break;
                case LSHR:
                    doMath(stack, long.class, Long.class, (x, y) -> x >> y);
                    break;
                case LUSHR:
                    doMath(stack, long.class, Long.class, (x, y) -> x >>> y);
                    break;
                case LAND:
                    doMath(stack, long.class, Long.class, (x, y) -> x & y);
                    break;
                case LOR:
                    doMath(stack, long.class, Long.class, (x, y) -> x | y);
                    break;
                case LXOR:
                    doMath(stack, long.class, Long.class, (x, y) -> x ^ y);
                    break;
                case FADD:
                    doMath(stack, float.class, Float.class, (x, y) -> x + y);
                    break;
                case FSUB:
                    doMath(stack, float.class, Float.class, (x, y) -> x - y);
                    break;
                case FMUL:
                    doMath(stack, float.class, Float.class, (x, y) -> x * y);
                    break;
                case FDIV:
                    doMath(stack, float.class, Float.class, (x, y) -> x / y);
                    break;
                case FREM:
                    doMath(stack, float.class, Float.class, (x, y) -> x % y);
                    break;
                case DADD:
                    doMath(stack, double.class, Double.class, (x, y) -> x + y);
                    break;
                case DSUB:
                    doMath(stack, double.class, Double.class, (x, y) -> x - y);
                    break;
                case DMUL:
                    doMath(stack, double.class, Double.class, (x, y) -> x * y);
                    break;
                case DDIV:
                    doMath(stack, double.class, Double.class, (x, y) -> x / y);
                    break;
                case DREM:
                    doMath(stack, double.class, Double.class, (x, y) -> x % y);
                    break;
                case INEG:
                    doMath(stack, int.class, Integer.class, x -> -x);
                    break;
                case LNEG:
                    doMath(stack, long.class, Long.class, x -> -x);
                    break;
                case FNEG:
                    doMath(stack, float.class, Float.class, x -> -x);
                    break;
                case DNEG:
                    doMath(stack, double.class, Double.class, x -> -x);
                    break;
                case IINC: {
                    IincInsnNode cast = (IincInsnNode) now;
                    StackObject obj = locals.get(cast.var);
                    obj.value = obj.as(int.class) + cast.incr;
                    break;
                }
                case I2L: {
                    stack.add(0, stack.remove(0).cast(long.class));
                    break;
                }
                case I2F: {
                    stack.add(0, stack.remove(0).cast(float.class));
                    break;
                }
                case I2D: {
                    stack.add(0, stack.remove(0).cast(double.class));
                    break;
                }
                case L2I: {
                    stack.add(0, stack.remove(0).cast(int.class));
                    break;
                }
                case L2F: {
                    stack.add(0, stack.remove(0).cast(float.class));
                    break;
                }
                case L2D: {
                    stack.add(0, stack.remove(0).cast(double.class));
                    break;
                }
                case F2I: {
                    stack.add(0, stack.remove(0).cast(int.class));
                    break;
                }
                case F2L: {
                    stack.add(0, stack.remove(0).cast(long.class));
                    break;
                }
                case F2D: {
                    stack.add(0, stack.remove(0).cast(float.class));
                    break;
                }
                case D2I: {
                    stack.add(0, stack.remove(0).cast(int.class));
                    break;
                }
                case D2L: {
                    stack.add(0, stack.remove(0).cast(long.class));
                    break;
                }
                case D2F: {
                    stack.add(0, stack.remove(0).cast(float.class));
                    break;
                }
                case I2B: {
                    stack.add(0, stack.remove(0).cast(byte.class));
                    break;
                }
                case I2C: {
                    stack.add(0, stack.remove(0).cast(char.class));
                    break;
                }
                case I2S: {
                    stack.add(0, stack.remove(0).cast(short.class));
                    break;
                }
                case IFEQ: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) == 0) {
                        now = cast.label;
                    }
                    break;
                }
                case IFNE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) != 0) {
                        now = cast.label;
                    }
                    break;
                }
                case IFLT: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) < 0) {
                        now = cast.label;
                    }
                    break;
                }
                case IFGE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) >= 0) {
                        now = cast.label;
                    }
                    break;
                }
                case IFGT: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) > 0) {
                        now = cast.label;
                    }
                    break;
                }
                case IFLE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) <= 0) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ICMPEQ: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (o.as(int.class).intValue() == o1.as(int.class).intValue()) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ICMPNE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (castToPrimitive(o.value, int.class) != castToPrimitive(o1.value, int.class)) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ICMPLT: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (castToPrimitive(o1.value, int.class) < castToPrimitive(o.value, int.class)) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ICMPGE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0).cast(int.class);
                    StackObject o1 = stack.remove(0).cast(int.class);
                    if (castToPrimitive(o1.value, int.class) >= castToPrimitive(o.value, int.class)) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ICMPGT: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (castToPrimitive(o1.value, int.class) > castToPrimitive(o.value, int.class)) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ICMPLE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (castToPrimitive(o1.value, int.class) <= castToPrimitive(o.value, int.class)) {
                        now = cast.label;
                    }
                    break;
                }
                case IF_ACMPNE: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (context.provider.canCheckEquality(o, o1, context)) {
                        boolean eq = context.provider.checkEquality(o, o1, context);
                        if (!eq) {
                            now = cast.label;
                        }
                    } else {
                        throw new NoSuchMethodHandlerException("Could not find comparison for " + o.type + " " + o1.type);
                    }
                    break;
                }
                case IF_ACMPEQ: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject o = stack.remove(0);
                    StackObject o1 = stack.remove(0);
                    if (context.provider.canCheckEquality(o, o1, context)) {
                        boolean eq = context.provider.checkEquality(o, o1, context);
                        if (eq) {
                            now = cast.label;
                        }
                    } else {
                        throw new NoSuchMethodHandlerException("Could not find comparison for " + o.type + " " + o1.type);
                    }
                    break;
                }
                case GOTO: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    now = cast.label;
                    break;
                }
                case JSR: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    stack.add(0, new StackObject(Object.class, now));
                    now = cast.label;
                    break;
                }
                case RET: {
                    VarInsnNode cast = (VarInsnNode) now;
                    StackObject ret = locals.get(cast.var);
                    if (ret.value instanceof AbstractInsnNode) {
                        now = (AbstractInsnNode) ret.value;
                        break;
                    }
                    throw new IllegalArgumentException();
                }
                case TABLESWITCH: {
                    StackObject obj = stack.remove(0);
                    int x = castToPrimitive(obj.value, int.class);
                    TableSwitchInsnNode cast = (TableSwitchInsnNode) now;
                    if (x < cast.labels.size() && x >= 0) {
                        now = cast.labels.get(x);
                    } else {
                        now = cast.dflt;
                    }
                    break;
                }
                case LOOKUPSWITCH: {
                    StackObject obj = stack.remove(0);
                    Integer x = (Integer) castToPrimitive(obj.value, int.class);
                    LookupSwitchInsnNode cast = (LookupSwitchInsnNode) now;
                    if (cast.keys.indexOf(x) != -1) {
                        now = cast.labels.get(cast.keys.indexOf(x));
                    } else {
                        now = cast.dflt;
                    }
                    break;
                }
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN: {
                    context.pop();
                    return (T) stack.remove(0).value;
                }
                case RETURN: {
                    context.pop();
                    return (T) Optional.absent();
                }
                case GETSTATIC: {
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    stack.add(0, new StackObject(clazz == null ? Object.class : clazz, context.provider.getField(cast.owner, cast.name, cast.desc, null, context)));
                    break;
                }
                case PUTSTATIC: {
                    StackObject obj = stack.remove(0);
                    FieldInsnNode cast = (FieldInsnNode) now;
                    context.provider.setField(cast.owner, cast.name, cast.desc, null, obj.value, context);
                    break;
                }
                case GETFIELD: {
                    StackObject obj = stack.remove(0);
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    stack.add(0, new StackObject(clazz == null ? Object.class : clazz, context.provider.getField(cast.owner, cast.name, cast.desc, obj, context)));
                    break;
                }
                case PUTFIELD: {
                    StackObject obj = stack.remove(0);
                    StackObject instance = stack.remove(0);
                    FieldInsnNode cast = (FieldInsnNode) now;
                    context.provider.setField(cast.owner, cast.name, cast.desc, instance, obj.value, context);
                    break;
                }
                case INVOKEVIRTUAL: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<StackObject> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).copy());
                    }
                    args.add(stack.remove(0));
                    if (context.provider.canInvokeMethod(cast.owner, cast.name, cast.desc, args.get(args.size() - 1), args.subList(0, args.size() - 1), context)) {
                        Object provided = context.provider.invokeMethod(cast.owner, cast.name, cast.desc, args.get(args.size() - 1), args.subList(0, args.size() - 1), context);
                        if (type.getReturnType().getSort() != Type.VOID) {
                            stack.add(0, new StackObject(clazz == null ? Object.class : clazz, provided));
                        }
                    } else {
                        throw new NoSuchMethodHandlerException("Could not find invoker for " + cast.owner + " " + cast.name + cast.desc);
                    }
                    break;
                }
                case INVOKESPECIAL: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<StackObject> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).copy());
                    }
                    args.add(stack.remove(0));
                    if (context.provider.canInvokeMethod(cast.owner, cast.name, cast.desc, args.get(args.size() - 1), args.subList(0, args.size() - 1), context)) {
                        Object provided = context.provider.invokeMethod(cast.owner, cast.name, cast.desc, args.get(args.size() - 1), args.subList(0, args.size() - 1), context);
                        if (type.getReturnType().getSort() != Type.VOID) {
                            stack.add(0, new StackObject(clazz == null ? Object.class : clazz, provided));
                        }
                    } else {
                        throw new NoSuchMethodHandlerException("Could not find invoker for " + cast.owner + " " + cast.name + cast.desc);
                    }
                    break;
                }
                case INVOKESTATIC: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<StackObject> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).copy());
                    }
                    if (context.provider.canInvokeMethod(cast.owner, cast.name, cast.desc, null, args, context)) {
                        Object provided = context.provider.invokeMethod(cast.owner, cast.name, cast.desc, null, args, context);
                        if (type.getReturnType().getSort() != Type.VOID) {
                            stack.add(0, new StackObject(clazz == null ? Object.class : clazz, provided));
                        }
                    } else {
                        throw new NoSuchMethodHandlerException("Could not find invoker for " + cast.owner + " " + cast.name + cast.desc);
                    }
                    break;
                }
                case INVOKEINTERFACE: {
                    MethodInsnNode cast = (MethodInsnNode) now;
                    Type type = Type.getReturnType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    List<StackObject> args = new ArrayList<>();
                    for (Type t1 : Type.getArgumentTypes(cast.desc)) {
                        args.add(0, stack.remove(0).copy());
                    }
                    args.add(stack.remove(0));
                    if (context.provider.canInvokeMethod(cast.owner, cast.name, cast.desc, args.get(args.size() - 1), args.subList(0, args.size() - 1), context)) {
                        Object provided = context.provider.invokeMethod(cast.owner, cast.name, cast.desc, args.get(args.size() - 1), args.subList(0, args.size() - 1), context);
                        if (type.getReturnType().getSort() != Type.VOID) {
                            stack.add(0, new StackObject(clazz == null ? Object.class : clazz, provided));
                        }
                    } else {
                        throw new NoSuchMethodHandlerException("Could not find invoker for " + cast.owner + " " + cast.name + cast.desc);
                    }
                    break;
                }
                case INVOKEDYNAMIC: {
                    throw new UnsupportedOperationException();
                }
                case NEW: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    stack.add(0, new StackObject(Type.getType(cast.desc)));
                    break;
                }
                case NEWARRAY: {
                    int len = castToPrimitive(stack.remove(0).value, int.class);
                    IntInsnNode cast = (IntInsnNode) now;
                    Object add = null;
                    switch (cast.operand) {
                    case T_BOOLEAN:
                        add = new boolean[len];
                        break;
                    case T_CHAR:
                        add = new char[len];
                        break;
                    case T_FLOAT:
                        add = new float[len];
                        break;
                    case T_DOUBLE:
                        add = new double[len];
                        break;
                    case T_BYTE:
                        add = new byte[len];
                        break;
                    case T_SHORT:
                        add = new short[len];
                        break;
                    case T_INT:
                        add = new int[len];
                        break;
                    case T_LONG:
                        add = new long[len];
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown newarray type " + cast.operand);
                    }
                    stack.add(0, new StackObject(Object.class, add));
                    break;
                }
                case ANEWARRAY: {
                    int len = castToPrimitive(stack.remove(0).value, int.class);
                    stack.add(0, new StackObject(Object.class, new Object[len]));
                    break;
                }
                case ARRAYLENGTH: {
                    StackObject obj = stack.remove(0);
                    int len = Array.getLength(obj.value);
                    stack.add(0, new StackObject(int.class, len));
                    break;
                }
                case ATHROW: {
                    Object throwable = stack.remove(0).value;
                    Utils.sneakyThrow((Throwable) throwable);
                    return null;
                }
                case CHECKCAST: { //TODO Actually implement
                    break;
                }
                case INSTANCEOF: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    StackObject obj = stack.remove(0);
                    if (context.provider.canCheckInstanceOf(obj, Type.getType(cast.desc), context)) {
                        boolean is = context.provider.instanceOf(obj, Type.getType(cast.desc), context);
                        stack.add(0, new StackObject(int.class, is ? 1 : 0));
                    } else {
                        throw new IllegalArgumentException("No comparator found for " + cast.desc);
                    }
                    break;
                }
                case MONITORENTER: { //TODO Actually implement
                    stack.remove(0);
                    break;
                }
                case MONITOREXIT: {
                    stack.remove(0);
                    break;
                }
                case MULTIANEWARRAY: {
                    MultiANewArrayInsnNode cast = (MultiANewArrayInsnNode) now;
                    List<Integer> sizes = new ArrayList<>();
                    for (int i = 0; i < cast.dims; i++) {
                        sizes.add(0, castToPrimitive(stack.remove(0).value, int.class));
                    }
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    Class<?> create = clazz == null ? Object.class : clazz;
                    Object root = Array.newInstance(create, sizes.get(0));
                    List<Object> currentArray = new ArrayList<>();
                    currentArray.add(root);

                    for (int i = 0; i < sizes.size() - 1; i++) {
                        List<Object> temp = new ArrayList<>(currentArray);
                        currentArray.clear();
                        int size = sizes.get(i);
                        for (int s = 0; s < size; s++) {
                            for (Object o : temp) {
                                Object created = Array.newInstance(create, sizes.get(i + 1));
                                currentArray.add(created);
                                Array.set(o, s, created);
                            }
                        }
                    }
                    stack.add(0, new StackObject(Object.class, root));
                    break;
                }
                case IFNULL: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject obj = stack.remove(0);
                    if (obj.value == null) {
                        now = cast.label;
                    }
                    break;
                }
                case IFNONNULL: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject obj = stack.remove(0);
                    if (obj.value != null) {
                        now = cast.label;
                    }
                    break;
                }
                case -1: {
                    break;
                }
                default: {
                    throw new IllegalArgumentException(now.getOpcode() + " ");
                }
                }
                now = now.getNext();
            } catch (Throwable t) { //TODO Actually implement
                if (method.tryCatchBlocks != null) {
                    for (TryCatchBlockNode tcbn : method.tryCatchBlocks) {
                        if (method.instructions.indexOf(tcbn.start) <= method.instructions.indexOf(now) && method.instructions.indexOf(now) < method.instructions.indexOf(tcbn.end)) {
                            List<StackObject> stackClone = new ArrayList<>();
                            stackClone.add(new StackObject(Object.class, t));
                            List<StackObject> localsClone = new ArrayList<>(locals);
                            return execute(classNode, method, tcbn.handler, stackClone, localsClone, context);
                        }
                    }
                }
                Utils.sneakyThrow(t);
                return null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T castToPrimitive(Object start, Class<T> prim) {
        try {
            if (start == null) {
                return (T) PrimitiveUtils.getDefaultValue(Primitives.unwrap(prim));
            }
            if (start instanceof Boolean) {
                start = ((Boolean) start) ? 1 : 0;
            }
            if (start instanceof Character) {
                start = (int) ((Character) start).charValue();
            }
            if (prim == char.class) {
                return (T) Character.valueOf((char) ((Number) start).intValue());
            }
            switch (prim.getName()) {
            case "int":
                return (T) (Object) ((Number) start).intValue();
            case "long":
                return (T) (Object) ((Number) start).longValue();
            case "short":
                return (T) (Object) ((Number) start).shortValue();
            case "double":
                return (T) (Object) ((Number) start).doubleValue();
            case "float":
                return (T) (Object) ((Number) start).floatValue();
            case "byte":
                return (T) (Object) ((Number) start).byteValue();
            case "boolean":
                return (T) (Object) (((Number) start).intValue() != 0 ? true : false);
            default:
                throw new IllegalArgumentException(prim.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return (T) start;
    }

    public static class MultiBoolean {
        public List<AtomicBoolean> consider = new ArrayList<>();
        public List<MultiBoolean> considerOthers = new ArrayList<>();
        public AtomicBoolean thisBoolean = new AtomicBoolean(false);

        public MultiBoolean() {
            consider.add(thisBoolean);
        }

        public boolean and() {
            boolean result = true;
            for (AtomicBoolean b : consider) {
                result = result && b.get();
            }
            for (MultiBoolean b : considerOthers) {
                result = result && b.and();
            }
            return result;
        }

        public boolean or() {
            boolean result = false;
            for (AtomicBoolean b : consider) {
                result = result || b.get();
            }
            for (MultiBoolean b : considerOthers) {
                result = result || b.or();
            }
            return result;
        }
    }

    public static class StackObject {
        public Class<?> type;
        public Object value;

        public Type initType = null;
        public boolean isUninitialized = false;

        public boolean isldc = false;
        public AtomicReference<MultiBoolean> used = new AtomicReference<>(new MultiBoolean());

        public StackObject(Class<?> type, Object value) {
            if (Primitives.unwrap(type) != type) {
                throw new IllegalArgumentException();
            }
            this.type = type;
            this.value = value;
        }

        public StackObject(Class<?> type, Object value, boolean ldc) {
            if (Primitives.unwrap(type) != type) {
                throw new IllegalArgumentException();
            }
            this.type = type;
            this.value = value;
            this.isldc = ldc;
        }

        public StackObject(Type type) {
            this.isUninitialized = true;
            this.initType = type;
            this.value = this;
            this.type = Object.class;
        }

        public StackObject copy() {
            if (this.isUninitialized) {
                return this;
            }
            StackObject clone = new StackObject(type, value);
            clone.used.set(this.used.get());
            return clone;
        }

        public <T> T as(Class<T> prim) {
            if (Primitives.wrap(prim) != prim) {
                return castToPrimitive(value, prim);
            }
            if (value == null)
                return null;
            if (prim.isInstance(value)) {
                return prim.cast(value);
            } else {
                throw new IllegalArgumentException("Expected type " + prim.getCanonicalName() + " but got " + value.getClass().getCanonicalName());
            }
        }

        public StackObject cast(Class<?> prim) {
            if (value == null) {
                throw new NullPointerException();
            }
            try {
                Object start = value;
                if (start instanceof Boolean) {
                    start = ((Boolean) start) ? 1 : 0;
                }
                if (start instanceof Character) {
                    start = (int) ((Character) start).charValue();
                }
                if (prim == char.class) {
                    start = Character.valueOf((char) ((Number) start).intValue());
                }
                String type = prim.getName();
                Method unbox = start.getClass().getMethod(type + "Value");
                StackObject result = new StackObject(prim, unbox.invoke(start));
                result.used.set(this.used.get());
                return result;
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            throw new IllegalStateException();
        }

        public static StackObject forPrimitive(Class<?> prim) {
            if (prim == null) {
                throw new NullPointerException();
            }
            return new StackObject(prim, PrimitiveUtils.getDefaultValue(prim));
        }

        public String toString() {
            return initType != null ? "UninitType[" + initType + "]" : value == null ? "Null object" : value.toString();
        }

        public void initialize(Object value) {
            this.initType = null;
            this.isUninitialized = false;
            this.value = value;
        }
    }

    public static class Context {
        private List<StackTraceElement> context = new ArrayList<>();

        public Provider provider;
        public Map<String, WrappedClassNode> dictionary;

        public Context(Provider provider) {
            this.provider = provider;
        }

        public StackTraceElement at(int index) {
            return context.get(index);
        }

        public StackTraceElement pop() {
            return context.remove(0);
        }

        public void push(String clazz, String method, int constantPoolSize) {
            clazz = clazz.replace('/', '.');
            context.add(0, new StackTraceElement(clazz, method, null, constantPoolSize));
        }

        public int size() {
            return context.size();
        }

        public StackTraceElement[] getStackTrace() {
            StackTraceElement[] orig = new StackTraceElement[size()];
            for (int i = 0; i < size(); i++) {
                orig[i] = at(i);
            }
            return orig;
        }
    }
}
