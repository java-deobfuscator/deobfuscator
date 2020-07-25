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

package com.javadeobfuscator.deobfuscator.utils;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.objectweb.asm.Opcodes.*;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import sun.misc.Unsafe;

public class Utils {
    public static boolean isInstruction(AbstractInsnNode node) {
        return !(node instanceof LineNumberNode) && !(node instanceof FrameNode) && !(node instanceof LabelNode);
    }

    public static boolean notAbstractOrNative(MethodNode methodNode) {
        return !Modifier.isNative(methodNode.access) && !Modifier.isAbstract(methodNode.access);
    }

    public static AbstractInsnNode getNextFollowGoto(AbstractInsnNode node) {
        AbstractInsnNode next = node.getNext();
        while (next instanceof LabelNode || next instanceof LineNumberNode || next instanceof FrameNode) {
            next = next.getNext();
        }
        if (next.getOpcode() == GOTO) {
            JumpInsnNode cast = (JumpInsnNode) next;
            next = cast.label;
            while (!Utils.isInstruction(next)) {
                next = next.getNext();
            }
        }
        return next;
    }

    public static AbstractInsnNode getNext(AbstractInsnNode node, int amount) {
        for (int i = 0; i < amount; i++) {
            node = getNext(node);
        }
        return node;
    }
    
    public static AbstractInsnNode getNext(AbstractInsnNode node) {
        AbstractInsnNode next = node.getNext();
        while (!Utils.isInstruction(next)) {
            next = next.getNext();
        }
        return next;
    }

    public static AbstractInsnNode getPrevious(AbstractInsnNode node, int amount) {
        for (int i = 0; i < amount; i++) {
            node = getPrevious(node);
        }
        return node;
    }

    public static AbstractInsnNode getPrevious(AbstractInsnNode node) {
        AbstractInsnNode prev = node.getPrevious();
        while (!Utils.isInstruction(prev)) {
            prev = prev.getPrevious();
        }
        return prev;
    }

    public static int iconstToInt(int opcode) {
        int operand = Integer.MIN_VALUE;
        switch (opcode) {
            case ICONST_0:
                operand = 0;
                break;
            case ICONST_1:
                operand = 1;
                break;
            case ICONST_2:
                operand = 2;
                break;
            case ICONST_3:
                operand = 3;
                break;
            case ICONST_4:
                operand = 4;
                break;
            case ICONST_5:
                operand = 5;
                break;
            case ICONST_M1:
                operand = -1;
                break;
        }
        return operand;
    }

    public static MethodNode getMethodNode(ClassNode start, String methodName, String methodDesc, Map<String, ClassNode> dictionary) {
        MethodNode targetMethod = null;

        LinkedList<ClassNode> haystack = new LinkedList<>();
        haystack.add(start);
        while (targetMethod == null && !haystack.isEmpty()) {
            ClassNode needle = haystack.poll();
            targetMethod = needle.methods.stream().filter(imn -> imn.name.equals(methodName) && imn.desc.equals(methodDesc)).findFirst().orElse(null);
            if (targetMethod == null) {
                if (!needle.name.equals("java/lang/Object")) {
                    for (String intf : needle.interfaces) {
                        ClassNode intfNode = dictionary.get(intf);
                        if (intfNode == null) {
                            throw new IllegalArgumentException("Class not found: " + intf);
                        }
                        haystack.add(intfNode);
                    }
                    String superName = needle.superName;
                    needle = dictionary.get(needle.superName);
                    if (needle == null) {
                        throw new IllegalArgumentException("Class not found: " + superName);
                    }
                    haystack.add(needle);
                }
            }
        }

        return targetMethod;
    }

    public static long copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[4096];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public static String descFromTypes(Type[] types) {
        StringBuilder descBuilder = new StringBuilder("(");
        for (Type type : types) {
            descBuilder.append(type.getDescriptor());
        }
        descBuilder.append(")");
        return descBuilder.toString();
    }

    public static void sneakyThrow(Throwable t) {
        Utils.<Error>sneakyThrow0(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow0(Throwable t) throws T {
        throw (T) t;
    }

    private static final Printer printer = new Textifier();
    private static final TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);

    public static String prettyprint(AbstractInsnNode insnNode) {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }

    public static boolean isTerminating(AbstractInsnNode next) {
        switch (next.getOpcode()) {
            case RETURN:
            case ARETURN:
            case IRETURN:
            case FRETURN:
            case DRETURN:
            case LRETURN:
            case ATHROW:
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case GOTO:
                return true;
        }
        return false;
    }

    public static boolean willPushToStack(int opcode) {
        switch (opcode) {
            case ACONST_NULL:
            case ICONST_M1:
            case ICONST_0:
            case ICONST_1:
            case ICONST_2:
            case ICONST_3:
            case ICONST_4:
            case ICONST_5:
            case FCONST_0:
            case FCONST_1:
            case FCONST_2:
            case BIPUSH:
            case SIPUSH:
            case LDC:
            case GETSTATIC:
            case ILOAD:
            case LLOAD:
            case FLOAD:
            case DLOAD:
            case ALOAD: {
                return true;
            }
        }
        return false;
    }

    public static Unsafe getUnsafe() {
        try {
            initializeUnsafe();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return unsafe;
    }

    public static <T> T allocateInstance(Class<T> t) {
        try {
            return (T) getUnsafe().allocateInstance(t);
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Unsafe unsafe;

    private static void initializeUnsafe() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        if (unsafe == null) {
            Constructor<Unsafe> ctor = Unsafe.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            unsafe = ctor.newInstance();
        }
    }

    public static boolean isNumber(String type) {
        switch (type) {
            case "I":
            case "S":
            case "B":
            case "J":
            case "D":
            case "F":
                return true;
            default:
                return false;
        }
    }

    public static boolean canReturnDigit(String type) {
        switch (type) {
            case "I":
            case "S":
            case "B":
            case "J":
            case "Z":
            case "C":
                return true;
            default:
                return false;
        }
    }

    public static boolean isFloat(String type) {
        switch (type) {
            case "F":
            case "D":
                return true;
            default:
                return false;
        }
    }

    public static InsnList copyInsnList(InsnList original) {
        InsnList newInsnList = new InsnList();

        for (AbstractInsnNode insn = original.getFirst(); insn != null; insn = insn.getNext()) {
            newInsnList.add(insn);
        }

        return newInsnList;
    }

    public static InsnList cloneInsnList(InsnList original) {
        InsnList newInsnList = new InsnList();
        Map<LabelNode, LabelNode> labels = new HashMap<>();

        for (AbstractInsnNode insn = original.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LabelNode) {
                labels.put((LabelNode)insn, new LabelNode());
            }
        }

        for (AbstractInsnNode insn = original.getFirst(); insn != null; insn = insn.getNext()) {
            newInsnList.add(insn.clone(labels));
        }

        return newInsnList;
    }

    public static AbstractInsnNode getIntInsn(int number) {
    	if (number >= -1 && number <= 5)
    		return new InsnNode(number + 3);
    	else if (number >= -128 && number <= 127)
    		return new IntInsnNode(Opcodes.BIPUSH, number);
    	else if (number >= -32768 && number <= 32767)
    		return new IntInsnNode(Opcodes.SIPUSH, number);
    	else
    		return new LdcInsnNode(number);
    }
    
    public static AbstractInsnNode getLongInsn(long number) {
        if (number >= 0 && number <= 1)
            return new InsnNode((int) (number + 9));
        else
            return new LdcInsnNode(number);
    }

    public static AbstractInsnNode getFloatInsn(float number) {
        if (number >= 0 && number <= 2) {
            return new InsnNode((int) (number + 11));
        } else {
            return new LdcInsnNode(number);
        }
    }

    public static AbstractInsnNode getDoubleInsn(double number) {
        if (number >= 0 && number <= 1)
            return new InsnNode((int) (number + 14));
        else
            return new LdcInsnNode(number);
    }
    
    public static void printClass(ClassNode classNode) { 
        System.out.println(classNode.name + '\n'); 
        classNode.methods.forEach(methodNode -> { 
            System.out.println(methodNode.name + " " + methodNode.desc); 
            for (int i = 0; i < methodNode.instructions.size(); i++) { 
                System.out.printf("%s:   %s \n", i, prettyprint(methodNode.instructions.get(i))); 
            } 
        }); 
    } 

    public static boolean isInteger(AbstractInsnNode ain)
	{
    	if (ain == null) return false;
		if((ain.getOpcode() >= Opcodes.ICONST_M1
			&& ain.getOpcode() <= Opcodes.ICONST_5)
			|| ain.getOpcode() == Opcodes.SIPUSH 
			|| ain.getOpcode() == Opcodes.BIPUSH)
			return true;
		if(ain instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)ain;
			if(ldc.cst instanceof Integer)
				return true;
		}
		return false;
	}

	public static int getIntValue(AbstractInsnNode node)
	{
		if(node.getOpcode() >= Opcodes.ICONST_M1
			&& node.getOpcode() <= Opcodes.ICONST_5)
			return node.getOpcode() - 3;
		if(node.getOpcode() == Opcodes.SIPUSH
			|| node.getOpcode() == Opcodes.BIPUSH)
			return ((IntInsnNode)node).operand;
		if(node instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)node;
			if(ldc.cst instanceof Integer)
				return (int)ldc.cst;
		}
		return 0;
	}

    public static boolean isLong(AbstractInsnNode ain)
	{
    	if (ain == null) return false;
    	if(ain.getOpcode() == Opcodes.LCONST_0
    		|| ain.getOpcode() == Opcodes.LCONST_1)
    		return true;
    	if(ain instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)ain;
			if(ldc.cst instanceof Long)
				return true;
		}
		return false;
	}
    
	public static long getLongValue(AbstractInsnNode node)
	{
		if(node.getOpcode() >= Opcodes.LCONST_0
			&& node.getOpcode() <= Opcodes.LCONST_1)
			return node.getOpcode() - 9;
		if(node instanceof LdcInsnNode)
		{
			LdcInsnNode ldc = (LdcInsnNode)node;
			if(ldc.cst instanceof Long)
				return (long)ldc.cst;
		}
		return 0;
	}
	
    public static List<byte[]> loadBytes(File input) {
        List<byte[]> result = new ArrayList<>();

        if (input.getName().endsWith(".jar")) {
            try (ZipFile zipIn = new ZipFile(input)) {
                Enumeration<? extends ZipEntry> e = zipIn.entries();
                while (e.hasMoreElements()) {
                    ZipEntry next = e.nextElement();
                    if (next.getName().endsWith(".class")) {
                        try (InputStream in = zipIn.getInputStream(next)) {
                            result.add(IOUtils.toByteArray(in));
                        } catch (IllegalArgumentException x) {
                            System.out.println("Could not parse " + next.getName() + " (is it a class?)");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(System.out);
            }
        } else if (input.getName().endsWith(".class")) {
            try (InputStream in = new FileInputStream(input)) {
                result.add(IOUtils.toByteArray(in));
            } catch (Throwable x) {
                System.out.println("Could not parse " + input.getName() + " (is it a class?)");
            }
        }

        return result;
    }

    public static Map<LabelNode, LabelNode> generateCloneMap(InsnList list) {
        Map<LabelNode, LabelNode> result = new HashMap<>();
        list.iterator().forEachRemaining(insn -> {
            if (insn instanceof LabelNode) {
                result.put((LabelNode) insn, new LabelNode());
            }
        });
        return result;
    }
    
    public static int getPullValue(AbstractInsnNode ain, boolean includeShifts)
    {
    	switch(ain.getOpcode())
    	{
    	    case IALOAD:
    	    case LALOAD:
    	    case FALOAD:
    	    case DALOAD:
    	    case AALOAD:
    	    case BALOAD:
    	    case CALOAD:
    	    case SALOAD:
    	    	return 2;
    	    case ISTORE:
    	    case FSTORE:
    	    case ASTORE:
    	    	return 1;
    	    case LSTORE:
    	    case DSTORE:
    	    	return 2;
    	    case IASTORE:
    	    case FASTORE:
    	    case AASTORE:
    	    case BASTORE:
    	    case CASTORE:
    	    case SASTORE:
    	    	return 3;
    	    case LASTORE:
    	    case DASTORE:
    	    	return 4;
    	    case POP:
    	    	return 1;
    	    case POP2:
    	    	return 2;
    	    case DUP:
    	    	if(includeShifts)
    	    		return 1;
    	    	break;
    	    case DUP_X1:
    	    	if(includeShifts)
    	    		return 1;
    	    	break;
    	    case DUP_X2:
    	    	if(includeShifts)
    	    		return 1;
    	    	break;
    	    case DUP2:
    	    	if(includeShifts)
    	    		return 2;
    	    	break;
    	    case DUP2_X1:
    	    	if(includeShifts)
    	    		return 2;
    	    	break;
    	    case DUP2_X2:
    	    	if(includeShifts)
    	    		return 2;
    	    	break;
    	    case SWAP:
    	    	if(includeShifts)
    	    		return 2;
    	    	break;
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
    	    	return 2;
    	    case LADD:
    	    case LSUB:
    	    case LMUL:
    	    case LDIV:
    	    case LREM:
    	    case LAND:
    	    case LOR:
    	    case LXOR:
    	    case LCMP:
    	    	return 4;
    	    case LSHL:
    	    case LSHR:
    	    case LUSHR:
    	    	return 3;
    	    case FADD:
    	    case FSUB:
    	    case FMUL:
    	    case FDIV:
    	    case FREM:
    	    case FCMPL:
    	    case FCMPG:
    	    	return 2;
    	    case DADD:
    	    case DSUB:
    	    case DMUL:
    	    case DDIV:
    	    case DREM:
    	    case DCMPL:
    	    case DCMPG:
    	    	return 4;
    	    case INEG:
    	    case FNEG:
    	    	return 1;
    	    case DNEG:
    	    case LNEG:
    	    	return 2;
    	    case I2L:
    	    case I2D:
    	    case I2F:
    	    	return 1;
    	    case L2I:
    	    case L2D:
    	    case L2F:
    	    	return 2;
    	    case F2I:
    	    case F2D:
    	    case F2L:
    	    	return 1;
    	    case D2F:
    	    case D2L:
    	    case D2I:
    	    	return 2;
    	    case IFNE:
    	    case IFEQ:
    	    case IFLT:
    	    case IFGE:
    	    case IFGT:
    	    case IFLE:
    	    	return 1;
    	    case IF_ICMPEQ:
    	    case IF_ICMPNE:
    	    case IF_ICMPLT:
    	    case IF_ICMPGE:
    	    case IF_ICMPGT:
    	    case IF_ICMPLE:
    	    case IF_ACMPNE:
    	    case IF_ACMPEQ:
    	    	return 2;
    	    case TABLESWITCH:
    	    case LOOKUPSWITCH:
    	    	return 1;
    	    case IRETURN:
    	    case FRETURN:
    	    case ARETURN:
    	    	return 1;
    	    case LRETURN:
    	    case DRETURN:
    	    	return 2;
    	    case PUTSTATIC:
    	    	if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG
    	    	|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE)
    	    		return 2;
    	    	return 1;
    	    case GETFIELD:
    	    	return 1;
    	    case PUTFIELD:
    	    	if(Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.LONG
    	    	|| Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.DOUBLE)
    	    		return 3;
    	    	return 2;
    	    case INVOKESTATIC:
    	    case INVOKEVIRTUAL:
    	    case INVOKEINTERFACE:
    	    case INVOKESPECIAL:
    	    	int args = 0;
    	    	if(ain.getOpcode() != Opcodes.INVOKESTATIC)
    	    		args++;
    	    	for(Type t : Type.getArgumentTypes((((MethodInsnNode)ain).desc)))
    	    		if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
    	    			args += 2;
    	    		else
    	    			args++;
    	    	return args;
    	    case INVOKEDYNAMIC:
    	    	int args1 = 0;
    	    	for(Type t : Type.getArgumentTypes((((InvokeDynamicInsnNode)ain).desc)))
    	    		if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
    	    			args1 += 2;
    	    		else
    	    			args1++;
    	    	return args1;
    	    case NEWARRAY:
    	    case ANEWARRAY:
    	    case ARRAYLENGTH:
    	    case ATHROW:
    	    case CHECKCAST:
    	    case INSTANCEOF:
    	    case MONITORENTER:
    	    case MONITOREXIT:
    	    case IFNULL:
    	    case IFNONNULL:
    	    	return 1;
    	    case MULTIANEWARRAY:
    	    	return ((MultiANewArrayInsnNode)ain).dims;
    	}
    	return 0;
    }
}
