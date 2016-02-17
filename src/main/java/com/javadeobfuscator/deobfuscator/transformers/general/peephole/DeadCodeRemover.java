/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.ObjDoubleConsumer;

import static com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes.*;

public class DeadCodeRemover extends Transformer {
    public DeadCodeRemover(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        AtomicInteger deadInstructions = new AtomicInteger();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode -> methodNode.instructions.getFirst() != null).forEach(methodNode -> {
                if (methodNode.localVariables != null) {
                    methodNode.localVariables.clear();
                }
                List<AbstractInsnNode> protectedNodes = new ArrayList<>();
                if (methodNode.tryCatchBlocks != null) {
                    methodNode.tryCatchBlocks.forEach(tryCatchBlockNode -> {
                        protectedNodes.add(tryCatchBlockNode.start);
                        protectedNodes.add(tryCatchBlockNode.end);
                        protectedNodes.add(tryCatchBlockNode.handler);
                    });
                }

                Type type = Type.getType(methodNode.desc);
                List<StackObject> stack = new ArrayList<>();
                List<StackObject> locals = new ArrayList<>();
                if ((methodNode.access & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) {
                    locals.add(StackObject.forPrimitive(Object.class));
                }

                for (Type t : type.getArgumentTypes()) {
                    Class<?> prim = PrimitiveUtils.getPrimitiveByName(t.getClassName());
                    if (prim != null) {
                        StackObject asdf = StackObject.forPrimitive(prim);
                        locals.add(asdf);
                        if (prim == double.class || prim == long.class) {
                            locals.add(asdf);
                        }
                    } else {
                        locals.add(StackObject.forPrimitive(Object.class));
                    }
                }
                for (int i = 0; i < 50; i++) {
                    locals.add(new StackObject(Object.class, null));
                }

                for (int i = 0; i < methodNode.maxLocals + 10; i++) locals.add(StackObject.forPrimitive(Object.class));

//                System.out.println("Executing " + classNode.name + " " + methodNode.name + methodNode.desc);
                execute(classes.get(classNode.name), methodNode, methodNode.instructions.getFirst(), stack, locals, protectedNodes, new HashSet<>());

                Iterator<AbstractInsnNode> it = methodNode.instructions.iterator();
                while (it.hasNext()) {
                    if (!protectedNodes.contains(it.next())) {
                        it.remove();
                        deadInstructions.getAndIncrement();
                    }
                }
            });
        });
        System.out.println("Removed " + deadInstructions.get() + " dead instructions");
    }

    private void walk(AbstractInsnNode now, List<AbstractInsnNode> notDead, Set<AbstractInsnNode> jumped) {
        while (now != null) {
            notDead.add(now);
            if (now instanceof JumpInsnNode) {
                JumpInsnNode cast = (JumpInsnNode) now;
                if (jumped.add(cast.label)) {
                    walk(cast.label, notDead, jumped);
                }
            } else if (now instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode cast = (TableSwitchInsnNode) now;
                for (LabelNode label : cast.labels) {
                    if (jumped.add(label))
                        walk(label, notDead, jumped);
                }
                if (jumped.add(cast.dflt)) {
                    walk(cast.dflt, notDead, jumped);
                }
            } else if (now instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode cast = (LookupSwitchInsnNode) now;
                for (LabelNode label : cast.labels) {
                    if (jumped.add(label))
                        walk(label, notDead, jumped);
                }
                if (jumped.add(cast.dflt)) {
                    walk(cast.dflt, notDead, jumped);
                }
            }
            switch (now.getOpcode()) {
                case JSR:
                case RET:
                    throw new IllegalArgumentException();
                case GOTO:
                case TABLESWITCH:
                case LOOKUPSWITCH:
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN:
                case ATHROW:
                    return;
                default:
                    break;
            }
            now = now.getNext();
        }
    }


    private static void executeArrayLoad(List<StackObject> stack, Class<?> type) {
        int index = stack.remove(0).as(int.class);
        Object arr = stack.remove(0).value;
        stack.add(0, StackObject.forPrimitive(type));
    }

    private static void executeArrayStore(List<StackObject> stack) {
        Object value = stack.remove(0).value;
        int index = stack.remove(0).as(int.class);
        Object arr = stack.remove(0).value;
    }

    @SuppressWarnings("unchecked")
    private static <T, P> void doMath(List<StackObject> stack, Class<P> prim, Class<T> t, java.util.function.Function<T, P> action) {
        Object obj1 = stack.remove(0);
        stack.add(0, StackObject.forPrimitive(prim));
    }

    @SuppressWarnings("unchecked")
    private static <T, P> void doMath(List<StackObject> stack, Class<P> prim, Class<T> t, BiFunction<T, T, P> action) {
        Object obj1 = stack.remove(0);
        Object obj2 = stack.remove(0);
        stack.add(0, StackObject.forPrimitive(prim));
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

    private static <T> T execute(WrappedClassNode classNode, MethodNode method, AbstractInsnNode now, List<StackObject> stack, List<StackObject> locals, List<AbstractInsnNode> notDead, Set<AbstractInsnNode> jumped) {
//        System.out.println("Executing " + classNode.classNode.name + " " + method.name + method.desc);
        Map<AbstractInsnNode, List<AbstractInsnNode>> ends = new HashMap<>();
        if (method.tryCatchBlocks != null) {
            method.tryCatchBlocks.forEach(tryCatchBlockNode -> {
                List<AbstractInsnNode> handlers = ends.get(tryCatchBlockNode.start);
                if (handlers == null) {
                    handlers = new ArrayList<>();
                    ends.put(tryCatchBlockNode.start, handlers);
                }
                handlers.add(tryCatchBlockNode.handler);
            });
        }

        while (true) {
            if (now == null) {
                return null;
            }
            if (false) {
                System.out.println();
                System.out.println(method.instructions.indexOf(now) + " " + Utils.prettyprint(now));
            }
            if (ends.containsKey(now)) {
                for (AbstractInsnNode handler : ends.get(now)) {
                    if (jumped.add(handler)) {
                        List<StackObject> stackClone = new ArrayList<>();
                        stackClone.add(StackObject.forPrimitive(Object.class));
                        List<StackObject> localsClone = new ArrayList<>(locals);
                        execute(classNode, method, handler, stackClone, localsClone, notDead, jumped);
                    }
                }
            }
            if (!notDead.add(now)) {
                return null;
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
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case IFNULL:
                case IFNONNULL: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    StackObject obj = stack.remove(0);
                    if (jumped.add(cast.label)) {
                        execute(classNode, method, cast.label, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    if (!jumped.add(now.getNext())) {
                        return null;
                    }
                    break;
                }
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                case IF_ACMPNE:
                case IF_ACMPEQ: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    if (jumped.add(cast.label)) {
                        execute(classNode, method, cast.label, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    if (!jumped.add(now.getNext())) {
                        return null;
                    }
                    break;
                }
                case GOTO: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    if (jumped.add(cast.label)) {
                        execute(classNode, method, cast.label, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    return null;
                }
                case JSR: {
                    JumpInsnNode cast = (JumpInsnNode) now;
                    stack.add(0, new StackObject(Object.class, now.getNext()));
                    if (jumped.add(cast.label)) {
                        execute(classNode, method, cast.label, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    return null;
                }
                case RET: {
                    VarInsnNode cast = (VarInsnNode) now;
                    StackObject ret = locals.get(cast.var);
                    if (ret.value instanceof AbstractInsnNode) {
                        if (jumped.add(((AbstractInsnNode) ret.value))) {
                            execute(classNode, method, (AbstractInsnNode) ret.value, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                        }
                        return null;
                    }
                    throw new IllegalArgumentException();
                }
                case TABLESWITCH: {
                    TableSwitchInsnNode cast = (TableSwitchInsnNode) now;
                    for (LabelNode label : cast.labels) {
                        if (jumped.add(label))
                            execute(classNode, method, label, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    if (jumped.add(cast.dflt)) {
                        execute(classNode, method, cast.dflt, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    return null;
                }
                case LOOKUPSWITCH: {
                    LookupSwitchInsnNode cast = (LookupSwitchInsnNode) now;
                    for (LabelNode label : cast.labels) {
                        if (jumped.add(label))
                            execute(classNode, method, label, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    if (jumped.add(cast.dflt)) {
                        execute(classNode, method, cast.dflt, new ArrayList<>(stack), new ArrayList<>(locals), notDead, jumped);
                    }
                    return null;
                }
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN: {
                    return null;
                }
                case RETURN: {
                    return null;
                }
                case GETSTATIC: {
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    stack.add(0, StackObject.forPrimitive(clazz == null ? Object.class : clazz));
                    break;
                }
                case PUTSTATIC: {
                    StackObject obj = stack.remove(0);
                    FieldInsnNode cast = (FieldInsnNode) now;
                    break;
                }
                case GETFIELD: {
                    StackObject obj = stack.remove(0);
                    FieldInsnNode cast = (FieldInsnNode) now;
                    Type type = Type.getType(cast.desc);
                    Class<?> clazz = PrimitiveUtils.getPrimitiveByName(type.getClassName());
                    stack.add(0, StackObject.forPrimitive(clazz == null ? Object.class : clazz));
                    break;
                }
                case PUTFIELD: {
                    StackObject obj = stack.remove(0);
                    StackObject instance = stack.remove(0);
                    FieldInsnNode cast = (FieldInsnNode) now;
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
                    if (type.getReturnType().getSort() != Type.VOID) {
                        stack.add(0, StackObject.forPrimitive(clazz == null ? Object.class : clazz));
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
                    if (type.getReturnType().getSort() != Type.VOID) {
                        stack.add(0, StackObject.forPrimitive(clazz == null ? Object.class : clazz));
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
                    if (type.getReturnType().getSort() != Type.VOID) {
                        stack.add(0, StackObject.forPrimitive(clazz == null ? Object.class : clazz));
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
                    if (type.getReturnType().getSort() != Type.VOID) {
                        stack.add(0, StackObject.forPrimitive(clazz == null ? Object.class : clazz));
                    }
                    break;
                }
                case INVOKEDYNAMIC: {
                    throw new UnsupportedOperationException();
                }
                case NEW: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    stack.add(0, new StackObject(Object.class, null, false));
                    break;
                }
                case NEWARRAY: {
                    int len = castToPrimitive(stack.remove(0).value, int.class);
                    IntInsnNode cast = (IntInsnNode) now;
                    stack.add(0, new StackObject(Object.class, null));
                    break;
                }
                case ANEWARRAY: {
                    int len = castToPrimitive(stack.remove(0).value, int.class);
                    stack.add(0, new StackObject(Object.class, new Object[len]));
                    break;
                }
                case ARRAYLENGTH: {
                    StackObject obj = stack.remove(0);
                    stack.add(0, new StackObject(int.class, 0));
                    break;
                }
                case ATHROW: {
                    Object throwable = stack.remove(0).value;
                    return null;
                }
                case CHECKCAST: { //TODO Actually implement
                    break;
                }
                case INSTANCEOF: {
                    TypeInsnNode cast = (TypeInsnNode) now;
                    StackObject obj = stack.remove(0);
                    stack.add(0, new StackObject(int.class, 0));
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
                    stack.add(0, new StackObject(Object.class, null));
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
        }
    }
}
