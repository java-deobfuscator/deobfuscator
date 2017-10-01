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

package com.javadeobfuscator.deobfuscator.transformers.stringer;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaField;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Handle;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

//TODO: Support Java6(50) and below (Reflection obfuscation)
public class HideAccessObfuscationTransformer extends Transformer {
    public HideAccessObfuscationTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public boolean transform() throws Throwable {
        List<ClassNode> decryptors = findDecryptClass();
        List<MethodNode> decryptMethods = new ArrayList<>();

        AtomicInteger count = new AtomicInteger(0);

        System.out.println("[Stringer] [HideAccessTransformer] Starting");
        System.out.println("[Stringer] [HideAccessTransformer] Found " + decryptors.size() + " decryptors");

        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new MappedFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        Context context = new Context(provider);
        context.dictionary = this.classpath;

        decryptors.forEach(decryptor -> {
            MethodNode clinit = decryptor.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
            if (clinit == null) throw new RuntimeException("Could not find a method <clinit> in " + decryptor.name);

            MethodExecutor.execute(classes.get(decryptor.name), clinit, Collections.emptyList(), null, context);
        });

        String bootstrapDesc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode ->
                classNode.methods.stream().filter(methodNode -> !methodNode.desc.equals(bootstrapDesc)).forEach(methodNode -> {
            InsnList copy = Utils.copyInsnList(methodNode.instructions);
            for (int i = 0; i < copy.size(); i++) {
                AbstractInsnNode insn = copy.get(i);
                if (insn instanceof InvokeDynamicInsnNode) {
                    Handle bootstrap = ((InvokeDynamicInsnNode) insn).bsm;

                    if (bootstrap.getDesc().equals(bootstrapDesc)) {
                        MethodNode bootstrapMethod = classNode.methods.stream().filter(m -> m.name.equals(bootstrap.getName()) && m.desc.equals(bootstrap.getDesc())).findFirst().orElse(null);

                        if (bootstrapMethod == null) throw new RuntimeException("Could not find bootstrap");
                        decryptMethods.add(bootstrapMethod);

                        List<JavaValue> args = new ArrayList<>();
                        args.add(JavaValue.valueOf(null));
                        args.add(JavaValue.valueOf(((InvokeDynamicInsnNode) insn).name));
                        args.add(JavaValue.valueOf(null));

                        JavaMethodHandle result = MethodExecutor.execute(classes.get(classNode.name), bootstrapMethod, args, null, context);
                        switch (result.type) {
                            case "virtual":
                                methodNode.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, result.clazz, result.name, result.desc, false));
                                break;
                            case "static":
                                methodNode.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, result.clazz, result.name, result.desc, false));
                                break;
                            default:
                                throw new RuntimeException("Unknown type");
                        }
                        count.getAndIncrement();
                    }
                }
            }
        }));

        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).filter(classNode -> !decryptors.contains(classNode)).forEach(classNode ->
                classNode.methods.stream().filter(methodNode -> !methodNode.desc.equals(bootstrapDesc)).forEach(methodNode -> {
            InsnList copy = Utils.copyInsnList(methodNode.instructions);
            for (int i = 0; i < copy.size(); i++) {
                AbstractInsnNode insn = copy.get(i);
                if (insn instanceof MethodInsnNode && decryptors.stream().map(decryptor -> decryptor.name).collect(Collectors.toList()).contains(((MethodInsnNode) insn).owner)) {
                    String owner = ((MethodInsnNode) insn).owner;
                    String name = ((MethodInsnNode) insn).name;
                    String desc = ((MethodInsnNode) insn).desc;
                    switch (desc) {
                        case "(I)Ljava/lang/Object;": {  // GETSTATIC
                            MethodNode decryptMethod = classes.get(owner).getClassNode().methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);
                            Integer value = (Integer) ((LdcInsnNode) insn.getPrevious()).cst;
                            JavaField result = MethodExecutor.execute(classes.get(owner), decryptMethod, Collections.singletonList(new JavaInteger(value)), null, context);

                            if (isValueOf(insn.getPrevious().getPrevious()))
                                methodNode.instructions.remove(insn.getPrevious().getPrevious());

                            methodNode.instructions.remove(insn.getPrevious());
                            methodNode.instructions.set(insn, new FieldInsnNode(Opcodes.GETSTATIC, result.getClassName(), result.getName(), result.getDesc()));
                            count.getAndIncrement();
                            break;
                        }
                        case "(ILjava/lang/Object;)V": { // PUTSTATIC
                            MethodNode decryptMethod = classes.get(owner).getClassNode().methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);
                            if (insn.getPrevious().getOpcode() == Opcodes.SWAP) {
                                Integer value = (Integer) ((LdcInsnNode) insn.getPrevious().getPrevious()).cst;
                                JavaField result = MethodExecutor.execute(classes.get(owner), decryptMethod, Collections.singletonList(new JavaInteger(value)), null, context);

                                methodNode.instructions.remove(insn.getPrevious().getPrevious());
                                methodNode.instructions.remove(insn.getPrevious());

                                methodNode.instructions.set(insn, new FieldInsnNode(Opcodes.PUTSTATIC, result.getClassName(), result.getName(), result.getDesc()));
                                count.getAndIncrement();
                            }
                            break;
                        }
                        case "(Ljava/lang/Object;I)Ljava/lang/Object;": { // GETFIELD
                            MethodNode decryptMethod = classes.get(owner).getClassNode().methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);
                            Integer value = (Integer) ((LdcInsnNode) insn.getPrevious()).cst;
                            JavaField result = MethodExecutor.execute(classes.get(owner), decryptMethod, Collections.singletonList(new JavaInteger(value)), null, context);

                            if (isValueOf(insn.getPrevious().getPrevious())) {
                                methodNode.instructions.remove(insn.getPrevious().getPrevious());
                            }

                            methodNode.instructions.remove(insn.getPrevious());
                            methodNode.instructions.set(insn, new FieldInsnNode(Opcodes.GETFIELD, result.getClassName(), result.getName(), result.getDesc()));
                            count.getAndIncrement();
                            break;
                        }
                        case "(Ljava/lang/Object;ILjava/lang/Object;)V": { // PUTFIELD
                            MethodNode decryptMethod = classes.get(owner).getClassNode().methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);

                            if (insn.getPrevious().getOpcode() == Opcodes.SWAP) {
                                Integer value = (Integer) ((LdcInsnNode) insn.getPrevious().getPrevious()).cst;
                                JavaField result = MethodExecutor.execute(classes.get(owner), decryptMethod, Collections.singletonList(new JavaInteger(value)), null, context);

                                methodNode.instructions.remove(insn.getPrevious().getPrevious());
                                methodNode.instructions.remove(insn.getPrevious());

                                methodNode.instructions.set(insn, new FieldInsnNode(Opcodes.PUTFIELD, result.getClassName(), result.getName(), result.getDesc()));
                                count.getAndIncrement();
                            }
                            break;
                        }
                    }
                }
            }
        }));

        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> classNode.methods.forEach(methodNode -> {
            InsnList copy = Utils.copyInsnList(methodNode.instructions);
            for (int i = 0; i < copy.size(); i++) {
                AbstractInsnNode insn = copy.get(i);

                if (insn.getOpcode() == Opcodes.CHECKCAST) {
                    TypeInsnNode checkcast = (TypeInsnNode) insn;
                    AbstractInsnNode previous = checkcast.getPrevious();

                    if (previous.getOpcode() == Opcodes.CHECKCAST) {
                        methodNode.instructions.remove(checkcast);
                    } else if (previous instanceof MethodInsnNode) {
                        Type type1 = getType(((MethodInsnNode) previous).desc).getReturnType();
                        Type type2 = getType(checkcast.desc).getReturnType();

                        if (type1.getClassName().equals(type2.getClassName())) {
                            methodNode.instructions.remove(checkcast);
                        }
                    } else if (previous instanceof FieldInsnNode) {
                        Type type1 = getType(((FieldInsnNode) previous).desc).getReturnType();
                        Type type2 = getType(checkcast.desc).getReturnType();

                        if (type1.getClassName().equals(type2.getClassName())) {
                            methodNode.instructions.remove(checkcast);
                        } else if (isPrimitive(type1.getClassName()) && isObjectType(type2.getClassName())) {
                            AbstractInsnNode next = checkcast.getNext();

                            if (next instanceof MethodInsnNode && ((MethodInsnNode) next).name.endsWith("Value") && ((MethodInsnNode) next).desc.startsWith("()"))
                                methodNode.instructions.remove(next);

                            methodNode.instructions.remove(checkcast);
                        }
                    }
                }
            }
        }));

        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> classNode.methods.forEach(methodNode -> {
            InsnList copy = Utils.copyInsnList(methodNode.instructions);
            for (int i = 0; i < copy.size(); i++) {
                AbstractInsnNode insn = copy.get(i);
                AbstractInsnNode previous = insn.getPrevious();
                if (insn instanceof FieldInsnNode && isValueOf(previous)) {
                    Type type1 = getType(((MethodInsnNode) previous).desc).getReturnType();
                    Type type2 = getType(((FieldInsnNode) insn).desc).getReturnType();

                    if (isObjectType(type1.getClassName()) && isPrimitive(type2.getClassName())) {
                        methodNode.instructions.remove(previous);
                    } /*else if (isPrimitive(type1.getClassName()) && isObjectType(type2.getClassName()) && isEqualType(type1.getClassName(), type2.getClassName())) {
                        String[] unboxing = getUnboxingMethod(type2.getClassName()).split(" ");
                        methodNode.instructions.insertBefore();
                    }*/
                }
            }
        }));

        System.out.println("[Stringer] [HideAccessTransformer] Removed " + count.get() + " hide access");
        long cleanedup = cleanup(decryptors, decryptMethods);
        System.out.println("[Stringer] [HideAccessTransformer] Removed " + (cleanedup >> 32) + " decryptor classes");
        System.out.println("[Stringer] [HideAccessTransformer] Removed " + (int) cleanedup + " invokedynamic bootstrap methods");

        System.out.println("[Stringer] [HideAccessTransformer] Done");
        return true;
    }

    //XXX: Better detector
    private List<ClassNode> findDecryptClass() {
        List<ClassNode> decryptors = new ArrayList<>();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).filter(classNode -> classNode.version == 49 && Modifier.isFinal(classNode.access) && classNode.superName.equals("java/lang/Object")).forEach(possibleClass -> {
            List<String> methodsDesc = possibleClass.methods.stream().map(m -> m.desc).collect(Collectors.toList());
            List<String> fieldsDesc = possibleClass.fields.stream().map(f -> f.desc).collect(Collectors.toList());

            if (fieldsDesc.contains("[Ljava/lang/Object;") &&
                    fieldsDesc.contains("[Ljava/lang/Class;") &&
                    methodsDesc.contains("(II)Ljava/lang/Class;") &&
                    methodsDesc.contains("(I)Ljava/lang/reflect/Method;") &&
                    methodsDesc.contains("(I)Ljava/lang/reflect/Field;")) decryptors.add(possibleClass);

        });
        return decryptors;
    }

    private long cleanup(List<ClassNode> decryptClasses, List<MethodNode> decryptMethods) {
        int classCount;
        AtomicInteger methodCount = new AtomicInteger();

        classNodes().forEach(wrappedClassNode -> {
            if (wrappedClassNode.classNode.methods.removeIf(decryptMethods::contains)) {
                methodCount.getAndIncrement();
            }
        });

        Set<WrappedClassNode> remove = classNodes().stream().filter(wrappedClassNode -> decryptClasses.contains(wrappedClassNode.classNode)).collect(Collectors.toSet());
        classCount = remove.size();
        classNodes().removeAll(remove);

        return (((long) classCount) << 32) | (methodCount.get() & 0xFFFFFFFFL);
    }

    private boolean isObjectType(String type) {
        List<String> objectType = new ArrayList<String>() {{
            add("java/lang/Byte");
            add("java/lang/Short");
            add("java/lang/Integer");
            add("java/lang/Long");
            add("java/lang/Float");
            add("java/lang/Double");
            add("java/lang/Character");
            add("java/lang/Boolean");
        }};
        return objectType.contains(type.replace(".", "/"));
    }

    private boolean isPrimitive(String type) {
        List<String> primitiveType = new ArrayList<String>() {{
            add("B");
            add("S");
            add("I");
            add("J");
            add("F");
            add("D");
            add("C");
            add("Z");
            add("boolean");
            add("char");
            add("byte");
            add("short");
            add("int");
            add("float");
            add("long");
            add("double");
        }};
        return primitiveType.contains(type);
    }

    private boolean isUnboxingMethod(TypeInsnNode checkcast) {
        if (checkcast.getNext() instanceof MethodInsnNode) {

            String[][] objectType = {
                    {"java/lang/Byte", "byteValue", "()B"},
                    {"java/lang/Short", "shortValue", "()S"},
                    {"java/lang/Integer", "intValue", "()I"},
                    {"java/lang/Long", "longValue", "()J"},
                    {"java/lang/Float", "floatValue", "()F"},
                    {"java/lang/Double", "doubleValue", "()D"},
                    {"java/lang/Character", "charValue", "()C"},
                    {"java/lang/Boolean", "booleanValue", "()Z"}
            };

            MethodInsnNode next = (MethodInsnNode) checkcast.getNext();
            for (String[] type : objectType) {
                if (checkcast.desc.equals(type[0]) && next.owner.equals(type[0]) && next.name.equals(type[1]) && next.desc.equals(type[2])) {
                    return true;
                }
            }
        }

        return false;
    }

    private String getUnboxingMethod(String type) {
        String[][] objectType = {
                {"java/lang/Byte", "byteValue", "()B"},
                {"java/lang/Short", "shortValue", "()S"},
                {"java/lang/Integer", "intValue", "()I"},
                {"java/lang/Long", "longValue", "()J"},
                {"java/lang/Float", "floatValue", "()F"},
                {"java/lang/Double", "doubleValue", "()D"},
                {"java/lang/Character", "charValue", "()C"},
                {"java/lang/Boolean", "booleanValue", "()Z"}
        };

        for (String[] t : objectType) {
            if (type.replace(".", "/").equals(t[0]))
                return String.format("%s %s %s", t[0], t[1], t[2]);
        }

        return null;
    }

    private boolean isValueOf(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode) {
            MethodInsnNode cast = (MethodInsnNode) insn;
            String[] objectType = {
                    "java/lang/Byte",
                    "java/lang/Short",
                    "java/lang/Integer",
                    "java/lang/Long",
                    "java/lang/Float",
                    "java/lang/Double",
                    "java/lang/Character",
                    "java/lang/Boolean"
            };

            for (String type : objectType) {
                if (cast.owner.equals(type) && cast.name.equals("valueOf") && cast.desc.endsWith(String.format(")L%s;", type))) {
                    return true;
                }
            }
        }

        return false;
    }

    private Type getType(String desc) {
        if (desc.startsWith("(") || desc.startsWith("L")) {
            return Type.getType(desc);
        } else if (desc.startsWith("[") && !isPrimitive(desc.substring(1, desc.length()))) {
            return Type.getType("()[L" + desc.substring(1, desc.length()));
        } else {
            return Type.getType("L" + desc + ";");
        }
    }

    private boolean isEqualType(String type1, String type2) {
        String[][] types = {
                {"java/lang/Byte", "byte", "B"},
                {"java/lang/Short", "short", "S"},
                {"java/lang/Integer", "int", "I"},
                {"java/lang/Long", "long", "J"},
                {"java/lang/Float", "float", "F"},
                {"java/lang/Double", "double", "D"},
                {"java/lang/Character", "char", "C"},
                {"java/lang/Boolean", "boolean", "Z"}
        };

        for (String[] type : types) {
            boolean flag1 = false;
            boolean flag2 = false;
            for (String t : type) {
                if (t.equals(type1.replace(".", "/"))) flag1 = true;
                else if (t.equals(type2.replace(".", "/"))) flag2 = true;
            }

            if (flag1 && flag2) return true;
        }
        return false;
    }
}
