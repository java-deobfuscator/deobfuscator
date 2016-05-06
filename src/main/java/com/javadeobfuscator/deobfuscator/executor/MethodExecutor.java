/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.executor;

import static com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes.*;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.google.common.base.Optional;
import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.exceptions.*;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class MethodExecutor {
    private static final boolean DEBUG;
    private static final boolean DEBUG_PRINT_EXCEPTIONS;
    private static final List<String> DEBUG_CLASSES;
    private static final List<String> DEBUG_METHODS_WITH_DESC;

    static {
        DEBUG = false;
        DEBUG_PRINT_EXCEPTIONS = false;
        DEBUG_CLASSES = Arrays.asList();
        DEBUG_METHODS_WITH_DESC = Arrays.asList();
    }

    public static <T> T execute(WrappedClassNode classNode, MethodNode method, List<StackObject> args, Object instance, Context context) {
        if (context == null)
            throw new IllegalArgumentException("Null context");
        List<StackObject> stack = new LinkedList<>();
        List<StackObject> locals = new LinkedList<>();
        if (!Modifier.isStatic(method.access)) {
            locals.add(new StackObject(Object.class, instance));
        }
        if (args != null) {
            for (StackObject arg : args) {
                if (arg.type == double.class || arg.type == long.class) {
                    locals.add(arg);
                }
                locals.add(arg);
            }
        }
        for (int i = 0; i < method.maxLocals + 50; i++) {
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

    /*
     * Main executor. This will go through each instruction and execute the instruction using a switch statement
     */
    private static <T> T execute(WrappedClassNode classNode, MethodNode method, AbstractInsnNode now, List<StackObject> stack, List<StackObject> locals, Context context) {
        context.push(classNode.classNode.name, method.name, classNode.constantPoolSize);
        if (DEBUG) {
            System.out.println("Executing " + classNode.classNode.name + " " + method.name + method.desc);
        }
        while (true) {
            try {
                if (DEBUG && (DEBUG_CLASSES.isEmpty() || DEBUG_CLASSES.contains(classNode.classNode.name)) && (DEBUG_METHODS_WITH_DESC.isEmpty() || DEBUG_METHODS_WITH_DESC.contains(method.name + method.desc))) {
                    System.out.println("\t" + stack);
                    System.out.println("\t" + locals);
                    System.out.println();
                    System.out.println(method.instructions.indexOf(now) + " " + Utils.prettyprint(now));
                }
                if (now == null) {
                    throw new FallingOffCodeException();
                }
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
                        Object load = cast.cst;
                        if (load instanceof Type) {
                            Type type = (Type) load;
                            load = new JavaClass(type.getInternalName().replace('/', '.'), context);
                        }
                        stack.add(0, new StackObject(Primitives.unwrap(cast.cst.getClass()), load));
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
                        doMath(stack, int.class, Float.class, (x, y) -> Float.isNaN(x) || Float.isNaN(y) ? -1 : x.compareTo(y));
                        break;
                    case FCMPG:
                        doMath(stack, int.class, Float.class, (x, y) -> Float.isNaN(x) || Float.isNaN(y) ? 1 : x.compareTo(y));
                        break;
                    case DCMPL:
                        doMath(stack, int.class, Double.class, (x, y) -> Double.isNaN(x) || Double.isNaN(y) ? -1 : x.compareTo(y));
                        break;
                    case DCMPG:
                        doMath(stack, int.class, Double.class, (x, y) -> Double.isNaN(x) || Double.isNaN(y) ? 1 : x.compareTo(y));
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
                        if (o.as(int.class) == 0) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IFNE: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        if (o.as(int.class) != 0) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IFLT: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        if (o.as(int.class) < 0) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IFGE: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        if (o.as(int.class) >= 0) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IFGT: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        if (o.as(int.class) > 0) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IFLE: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        if (o.as(int.class) <= 0) {
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
                        if (o.as(int.class) != o1.as(int.class)) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IF_ICMPLT: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        StackObject o1 = stack.remove(0);
                        if (o1.as(int.class) < o.as(int.class)) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IF_ICMPGE: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0).cast(int.class);
                        StackObject o1 = stack.remove(0).cast(int.class);
                        if (o1.as(int.class) >= o.as(int.class)) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IF_ICMPGT: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        StackObject o1 = stack.remove(0);
                        if (o1.as(int.class) > o.as(int.class)) {
                            now = cast.label;
                        }
                        break;
                    }
                    case IF_ICMPLE: {
                        JumpInsnNode cast = (JumpInsnNode) now;
                        StackObject o = stack.remove(0);
                        StackObject o1 = stack.remove(0);
                        if (o1.as(int.class) <= o.as(int.class)) {
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
                        int x = obj.as(int.class);
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
                        Integer x = obj.as(int.class);
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
                        int len = stack.remove(0).as(int.class);
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
                        int len = stack.remove(0).as(int.class);
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
                        if (throwable instanceof Throwable) {
                            Utils.sneakyThrow((Throwable) throwable);
                        } else {
                            throw new ExecutionException("Expected a throwable on stack");
                        }
                        context.pop();
                        return null;
                    }
                    case CHECKCAST: {
                        TypeInsnNode cast = (TypeInsnNode) now;
                        StackObject obj = stack.get(0);
                        if (obj.value != null) {
                            if (context.provider.canCheckcast(obj, Type.getType(cast.desc), context)) {
                                if (!context.provider.checkcast(obj, Type.getType(cast.desc), context)) {
                                    throw new ClassCastException(cast.desc);
                                }
                            } else {
                                throw new NoSuchComparisonHandlerException("No comparator found for " + cast.desc);
                            }
                        }
                        break;
                    }
                    case INSTANCEOF: {
                        TypeInsnNode cast = (TypeInsnNode) now;
                        StackObject obj = stack.remove(0);
                        if (context.provider.canCheckInstanceOf(obj, Type.getType(cast.desc), context)) {
                            boolean is = context.provider.instanceOf(obj, Type.getType(cast.desc), context);
                            stack.add(0, new StackObject(int.class, is ? 1 : 0));
                        } else {
                            throw new NoSuchComparisonHandlerException("No comparator found for " + cast.desc);
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
                            sizes.add(0, stack.remove(0).as(int.class));
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
                        throw new ExecutionException("Unknown opcode " + now.getClass().getSimpleName() + " " + now.getOpcode() + " ");
                    }
                }
                now = now.getNext();
            } catch (ExecutionException e) {
                throw e;
            } catch (Throwable t) {
                if (DEBUG_PRINT_EXCEPTIONS) {
                    t.printStackTrace(System.out);
                }
                if (method.tryCatchBlocks != null) {
                    /*
                     * Handlers appear to be executed in the order that they appear
                     * This means that the first valid one we encounter should be ok
                     */
                    for (TryCatchBlockNode tcbn : method.tryCatchBlocks) {
                        if (method.instructions.indexOf(tcbn.start) <= method.instructions.indexOf(now) && method.instructions.indexOf(now) < method.instructions.indexOf(tcbn.end)) {
                            if (tcbn.type == null || tcbn.type.equals("java/lang/Throwable")) {
                                List<StackObject> stackClone = new ArrayList<>();
                                stackClone.add(new StackObject(Object.class, t));
                                List<StackObject> localsClone = new ArrayList<>(locals);
                                context.pop();
                                return execute(classNode, method, tcbn.handler, stackClone, localsClone, context);
                            } else {
                                WrappedClassNode wr = context.dictionary.get(Type.getType(t.getClass()).getInternalName());
                                if (wr.classNode != null) {
                                    ClassNode cn = wr.classNode;
                                    boolean ok = false;
                                    while (cn != null) {
                                        if (cn.name.equals(tcbn.type)) {
                                            ok = true;
                                            break;
                                        }
                                        if (cn.superName == null) {
                                            break;
                                        }
                                        wr = context.dictionary.get(cn.superName);
                                        if (wr != null) {
                                            cn = wr.classNode;
                                        }
                                    }
                                    if (ok) {
                                        List<StackObject> stackClone = new ArrayList<>();
                                        stackClone.add(new StackObject(Object.class, t));
                                        List<StackObject> localsClone = new ArrayList<>(locals);
                                        context.pop();
                                        return execute(classNode, method, tcbn.handler, stackClone, localsClone, context);
                                    }
                                }
                            }
                        }
                    }
                }
                Utils.sneakyThrow(new ExecutionException(t));
                context.pop();
                return null;
            }
        }
    }

    /*
     * Actually I'm not sure what this is good for. Java's primitives are wonky
     */
    static <T> T castToPrimitive(Object start, Class<T> prim) {
        if (start == null) {
            throw new ExecutionException("Starting object should not be null");
        }
        if (start instanceof Boolean) {
            // Boolean does not extend Number
            // As booleans are stored as integers, this should be converted here
            // fixme maybe convert it at the root?
            start = ((Boolean) start) ? 1 : 0;
        } else if (start instanceof Character) {
            // Character does not extend Number
            start = (int) (Character) start;
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
                return (T) (Object) (((Number) start).intValue() != 0);
            default:
                throw new ExecutionException("Unknown primitive destination " + prim.getName());
        }
    }
}
