/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.utils;

import com.google.common.base.*;
import com.google.common.primitives.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.javavm.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.tree.analysis.Frame;
import org.slf4j.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

import static com.javadeobfuscator.deobfuscator.utils.Utils.*;

public class TransformerHelper implements Opcodes {
    public static boolean isInvokeVirtual(AbstractInsnNode insn, String owner, String name, String desc) {
        if (insn.getOpcode() != INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
        return (owner == null || methodInsnNode.owner.equals(owner)) &&
                (name == null || methodInsnNode.name.equals(name)) &&
                (desc == null || methodInsnNode.desc.equals(desc));
    }

    public static boolean isInvokeStatic(AbstractInsnNode insn, String owner, String name, String desc) {
        if (insn.getOpcode() != INVOKESTATIC) {
            return false;
        }
        MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
        return (owner == null || methodInsnNode.owner.equals(owner)) &&
                (name == null || methodInsnNode.name.equals(name)) &&
                (desc == null || methodInsnNode.desc.equals(desc));
    }

    public static boolean isPutStatic(AbstractInsnNode insn, String owner, String name, String desc) {
        if (insn.getOpcode() != PUTSTATIC) {
            return false;
        }
        FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;
        return (owner == null || fieldInsnNode.owner.equals(owner)) &&
                (name == null || fieldInsnNode.name.equals(name)) &&
                (desc == null || fieldInsnNode.desc.equals(desc));
    }

    public static MethodNode findClinit(ClassNode classNode) {
        return findMethodNode(classNode, "<clinit>", "()V");
    }

    public static MethodNode findMethodNode(ClassNode classNode, String name, String desc) {
        return findMethodNode(classNode, name, desc, false);
    }

    public static MethodNode findMethodNode(ClassNode classNode, String name, String desc, boolean basic) {
        List<MethodNode> methods = findMethodNodes(classNode, name, desc, basic);
        if (methods.isEmpty() || methods.size() > 1) {
            return null;
        }
        return methods.get(0);
    }

    public static List<MethodNode> findMethodNodes(ClassNode classNode, String name, String desc, boolean basic) {
        List<MethodNode> found = new ArrayList<>();
        for (MethodNode m : classNode.methods) {
            boolean nameMatches = name == null || m.name.equals(name);
            boolean descMatches = desc == null || (basic ? TransformerHelper.basicType(m.desc) : m.desc).equals(desc);
            if (nameMatches && descMatches) {
                found.add(m);
            }
        }
        return found;
    }

    public static FieldNode findFieldNode(ClassNode classNode, String name, String desc) {
        List<FieldNode> fields = findFieldNodes(classNode, name, desc);
        if (fields.isEmpty() || fields.size() > 1) {
            return null;
        }
        return fields.get(0);
    }

    public static List<FieldNode> findFieldNodes(ClassNode classNode, String name, String desc) {
        List<FieldNode> fields = new ArrayList<>();
        for (FieldNode f : classNode.fields) {
            if ((name == null || f.name.equals(name)) &&
                    (desc == null || f.desc.equals(desc))) {
                fields.add(f);
            }
        }
        return fields;
    }

    public static boolean containsInvokeStatic(MethodNode methodNode, String owner, String name, String desc) {
        for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insn = it.next();
            if (isInvokeStatic(insn, owner, name, desc)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsInvokeVirtual(MethodNode methodNode, String owner, String name, String desc) {
        for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insn = it.next();
            if (isInvokeVirtual(insn, owner, name, desc)) {
                return true;
            }
        }
        return false;
    }

    public static int countOccurencesOf(MethodNode methodNode, int opcode) {
        int i = 0;
        for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
            AbstractInsnNode insnNode = it.next();
            if (insnNode.getOpcode() == opcode) {
                i++;
            }
        }
        return i;
    }


    public static File javaLib(String name) {
        return new File(System.getProperty("java.home") + File.separator + "lib" + File.separator + name);
    }

    public static File javaLibExt(String name) {
        return new File(System.getProperty("java.home") + File.separator + "lib" + File.separator + "ext" + File.separator + name);
    }

    public static File javaLibSecurity(String name) {
        return new File(System.getProperty("java.home") + File.separator + "lib" + File.separator + "security" + File.separator + name);
    }

    public static VirtualMachine newVirtualMachine(Transformer<?> transformer) {
        List<byte[]> jvmFiles = new ArrayList<>();
        jvmFiles.addAll(loadBytes(javaLib("rt.jar")));
        jvmFiles.addAll(loadBytes(javaLib("jce.jar")));
        jvmFiles.addAll(loadBytes(javaLib("jsse.jar")));
//        jvmFiles.addAll(loadBytes(javaLibExt("sunjce_provider.jar")));
//        jvmFiles.addAll(loadBytes(javaLibExt("sunec.jar")));
//        jvmFiles.addAll(loadBytes(javaLibExt("sunmscapi.jar")));

        VirtualMachine vm = new VirtualMachine(jvmFiles);
        vm.fullInitialization();
        vm.getFilesystem().map(new File("\\C:\\java_home_dir\\lib\\security\\java.security"), javaLibSecurity("java.security"));
        vm.getFilesystem().map(new File("\\C:\\java_home_dir\\lib\\jce.jar"), javaLib("jce.jar"));
        vm.getFilesystem().map(new File("\\C:\\java_home_dir\\lib\\security\\US_export_policy.jar"), javaLibSecurity("US_export_policy.jar"));
        vm.getFilesystem().map(new File("\\C:\\java_home_dir\\lib\\security\\local_policy.jar"), javaLibSecurity("local_policy.jar"));
        vm.getFilesystem().map(new File("\\currentjar.zip"), transformer.getDeobfuscator().getConfig().getInput());
        vm.classpath(transformer.getDeobfuscator().getClasses().values());
        vm.classpath(transformer.getDeobfuscator().getLibraries().values());
        transformer.getDeobfuscator().getReaders().forEach((k, v) -> vm.registerClass(v, k));
        if (transformer.getConfig().getVmModifiers() != null) {
            transformer.getConfig().getVmModifiers().forEach(c -> c.accept(vm));
        }
        return vm;
    }

    public static String basicType(String type) {
        return basicType(Type.getType(type)).getDescriptor();
    }

    public static Type basicType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return Type.INT_TYPE;
            case Type.METHOD: {
                Type[] args = type.getArgumentTypes();
                Type ret = type.getReturnType();
                for (int i = 0; i < args.length; i++) {
                    args[i] = basicType(args[i]);
                }
                ret = basicType(ret);
                return Type.getMethodType(ret, args);
            }
            case Type.OBJECT:
            case Type.ARRAY: {
                return Type.getType("Ljava/lang/Object;");
            }
            default:
                return type;
        }
    }

    public static AbstractInsnNode unbox(Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                throw new RuntimeException();
            case Type.BOOLEAN:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
            case Type.CHAR:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Character", "characterValue", "()C", false);
            case Type.BYTE:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
            case Type.SHORT:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
            case Type.INT:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
            case Type.FLOAT:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
            case Type.LONG:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
            case Type.DOUBLE:
                return new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
            default:
                return new InsnNode(Opcodes.NOP);
        }
    }

    public static AbstractInsnNode box(Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                return new InsnNode(Opcodes.ACONST_NULL);
            case Type.BOOLEAN:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
            case Type.CHAR:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
            case Type.BYTE:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
            case Type.SHORT:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
            case Type.INT:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            case Type.FLOAT:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
            case Type.LONG:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
            case Type.DOUBLE:
                return new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
            default:
                return new InsnNode(Opcodes.NOP);
        }
    }

    public static AbstractInsnNode zero(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
                return new InsnNode(ICONST_0);
            case Type.BYTE:
                return new IntInsnNode(BIPUSH, 0);
            case Type.SHORT:
                return new IntInsnNode(SIPUSH, 0);
            case Type.INT:
                return new InsnNode(ICONST_0);
            case Type.FLOAT:
                return new InsnNode(FCONST_0);
            case Type.LONG:
                return new InsnNode(LCONST_0);
            case Type.DOUBLE:
                return new InsnNode(DCONST_0);
            case Type.OBJECT:
            case Type.ARRAY:
                return new InsnNode(ACONST_NULL);
            default:
                throw new RuntimeException("Unsupported type " + type);
        }
    }

    public static InsnList store(Type[] types) {
        return store(types, 0);
    }

    public static InsnList store(Type[] types, int startingSlot) {
        InsnList result = new InsnList();
        for (Type type : types) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    result.add(new VarInsnNode(ISTORE, startingSlot));
                    startingSlot++;
                    break;
                case Type.FLOAT:
                    result.add(new VarInsnNode(FSTORE, startingSlot));
                    startingSlot++;
                    break;
                case Type.LONG:
                    result.add(new VarInsnNode(LSTORE, startingSlot));
                    startingSlot += 2;
                    break;
                case Type.DOUBLE:
                    result.add(new VarInsnNode(DSTORE, startingSlot));
                    startingSlot += 2;
                    break;
                case Type.OBJECT:
                case Type.ARRAY:
                    result.add(new VarInsnNode(ASTORE, startingSlot));
                    startingSlot++;
                    break;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
        }
        return result;
    }

    public static InsnList load(Type[] types) {
        return load(types, 0, true);
    }

    public static InsnList load(Type[] types, int startingSlot, boolean up) {
        InsnList result = new InsnList();
        for (Type type : types) {
            switch (type.getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    result.add(new VarInsnNode(ILOAD, startingSlot));
                    break;
                case Type.FLOAT:
                    result.add(new VarInsnNode(FLOAD, startingSlot));
                    break;
                case Type.LONG:
                    result.add(new VarInsnNode(LLOAD, startingSlot));
                    break;
                case Type.DOUBLE:
                    result.add(new VarInsnNode(DLOAD, startingSlot));
                    break;
                case Type.OBJECT:
                case Type.ARRAY:
                    result.add(new VarInsnNode(ALOAD, startingSlot));
                    break;
                default:
                    throw new RuntimeException("Unsupported type " + type);
            }
            startingSlot += (up ? type.getSize() : -type.getSize());
        }
        return result;
    }

    public static int size(Type... types) {
        int size = 0;
        for (Type type : types) {
            size += type.getSize();
        }
        return size;
    }

    public static void dumpMethod(MethodNode methodNode) {
        dumpMethod(methodNode, methodNode.instructions.size());
    }

    public static void dumpMethod(MethodNode methodNode, int amount) {
        for (int i = 0; i < Math.min(methodNode.instructions.size(), amount); i++) {
            System.out.println(Utils.prettyprint(methodNode.instructions.get(i)));
        }
    }

    public static String getPackage(String className) {
        int lastIndex = className.lastIndexOf("/");
        if (lastIndex == -1) {
            return "";
        }
        return className.substring(0, lastIndex);
    }

    public static String getFullClassName(String className) {
        int lastIndex = className.lastIndexOf("/");
        if (lastIndex == -1) {
            return className;
        }
        return className.substring(lastIndex + 1);
    }

    public static String getOuterClassName(String className) {
        className = getFullClassName(className);
        int lastIndex = className.lastIndexOf("$");
        if (lastIndex == -1) {
            return className;
        }
        return className.substring(0, lastIndex);
    }

    public static String getInnerClassName(String className) {
        className = getFullClassName(className);
        int lastIndex = className.lastIndexOf("$");
        if (lastIndex == -1) {
            return className;
        }
        return className.substring(lastIndex + 1);
    }

    public static Iterable<AbstractInsnNode> instructionIterator(MethodNode methodNode) {
        Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator();
        return () -> new Iterator<AbstractInsnNode>() {
            AbstractInsnNode next = null;

            @Override
            public boolean hasNext() {
                while (iterator.hasNext()) {
                    next = iterator.next();
                    if (Utils.isInstruction(next)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public AbstractInsnNode next() {
                if (this.next == null) {
                    throw new NoSuchElementException();
                }
                AbstractInsnNode next = this.next;
                this.next = null;
                return next;
            }
        };
    }

    public static boolean hasArgumentTypes(Type[] types, Type... want) {
        boolean[] found = new boolean[want.length];
        for (Type t : types) {
            for (int i = 0; i < want.length; i++) {
                if (!found[i] && t.equals(want[i])) {
                    found[i] = true;
                }
            }
        }
        return Booleans.countTrue(found) == found.length;
    }

    public static boolean hasArgumentTypesOtherThan(Type[] types, Type... want) {
    	loop:
        for (Type t : types) {
            for (int i = 0; i < want.length; i++) {
                if (t.equals(want[i])) {
                	continue loop;
                }
            }
            return true;
        }
    	return false;
    }
    
    public static boolean isConstantInt(AbstractInsnNode insn) {
        if (insn.getOpcode() >= ICONST_M1 && insn.getOpcode() <= ICONST_5) return true;
        if (insn.getOpcode() == BIPUSH || insn.getOpcode() == SIPUSH) return true;
        if (insn.getOpcode() == LDC) return ((LdcInsnNode) insn).cst instanceof Integer;
        return false;
    }

    public static String insnToString(AbstractInsnNode insn) {
        return Utils.prettyprint(insn);
    }

    public static String insnsToString(Collection<AbstractInsnNode> insns) {
        return insns.stream().map(Utils::prettyprint).collect(Collectors.joining(",", "[", "]"));
    }

    private static final Supplier<ExecutorService> ASYNC_SERVICE = Suppliers.memoize(Executors::newCachedThreadPool);

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformerHelper.class);

    public static Frame<SourceValue>[] analyze(ClassNode classNode, MethodNode methodNode) {
        Future<Frame<SourceValue>[]> future = ASYNC_SERVICE.get().submit(() -> new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode));
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            LOGGER.debug("timed out while analyzing {} {}{}", classNode.name, methodNode.name, methodNode.desc);
            return null;
        } catch (ExecutionException e) {
            LOGGER.debug("exception while analyzing {} {}{}", classNode.name, methodNode.name, methodNode.desc, e);
            return null;
        }
    }
}
