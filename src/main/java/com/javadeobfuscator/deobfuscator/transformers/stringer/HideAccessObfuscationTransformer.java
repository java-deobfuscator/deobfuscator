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

import com.javadeobfuscator.deobfuscator.analyzer.ArgsAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.*;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

//TODO: Support Java6(50) and below (Reflection obfuscation)
public class HideAccessObfuscationTransformer extends Transformer<TransformerConfig> {
    private static final String[][] CLASS_TO_PRIMITIVE = {
            {"java/lang/Byte", "byte"},
            {"java/lang/Short", "short"},
            {"java/lang/Integer", "int"},
            {"java/lang/Long", "long"},
            {"java/lang/Float", "float"},
            {"java/lang/Double", "double"},
            {"java/lang/Character", "char"},
            {"java/lang/Boolean", "boolean"}
    };

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

            MethodExecutor.execute(decryptor, clinit, Collections.emptyList(), null, context);
        });

        String bootstrapDesc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";

        classNodes().forEach(classNode ->
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
                                args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup"));
                                args.add(JavaValue.valueOf(((InvokeDynamicInsnNode) insn).name));
                                args.add(new JavaObject(null, "java/lang/invoke/MethodType"));

                                JavaMethodHandle result = MethodExecutor.execute(classNode, bootstrapMethod, args, null, context);
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

        classNodes().stream().filter(classNode -> !decryptors.contains(classNode)).forEach(classNode ->
                classNode.methods.stream().filter(methodNode -> !methodNode.desc.equals(bootstrapDesc)).forEach(methodNode -> {
                    InsnList copy = Utils.copyInsnList(methodNode.instructions);
                    for (int i = 0; i < copy.size(); i++) {
                        AbstractInsnNode insn = copy.get(i);
                        if (insn instanceof MethodInsnNode && decryptors.stream().map(decryptor -> decryptor.name).collect(Collectors.toList()).contains(((MethodInsnNode) insn).owner)) {
                            String owner = ((MethodInsnNode) insn).owner;
                            String name = ((MethodInsnNode) insn).name;
                            String desc = ((MethodInsnNode) insn).desc;
                            switch (desc) {
                                case "(I[Ljava/lang/Object;)Ljava/lang/Object;": { // INVOKESTATIC and INVOKESPECIAL
                                    MethodNode hideAccessMethod = classes.get(owner).methods.stream().filter(m -> m.desc.equals(desc) && m.name.equals(name)).findFirst().orElse(null);
                                    if (hideAccessMethod != null && hideAccessMethod.instructions.size() > 2
                                            && hideAccessMethod.instructions.get(2).getOpcode() == Opcodes.LDC) {
                                        if (insn.getPrevious().getOpcode() == Opcodes.SWAP) {
                                            Integer value = (Integer) ((LdcInsnNode) insn.getPrevious().getPrevious()).cst;
                                            AbstractInsnNode returnInsert = null;
                                            //Invokespecial
                                            //Patches the hide access method first to return a constructor
                                            for (AbstractInsnNode ain : hideAccessMethod.instructions.toArray()) {
                                                if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                                                        && ((MethodInsnNode) ain).name.equals("newInstance")
                                                        && ((MethodInsnNode) ain).owner.equals("java/lang/reflect/Constructor")
                                                        && ain.getPrevious() != null
                                                        && ain.getPrevious().getOpcode() == Opcodes.ALOAD
                                                        && ain.getPrevious().getPrevious() != null
                                                        && ain.getPrevious().getPrevious().getOpcode() == Opcodes.ALOAD) {
                                                    hideAccessMethod.instructions.insert(ain.getPrevious().getPrevious(),
                                                            returnInsert = new InsnNode(Opcodes.ARETURN));
                                                    break;
                                                }
                                            }
                                            //Execute the method
                                            List<JavaValue> args = new ArrayList<>();
                                            args.add(new JavaInteger(value));
                                            args.add(new JavaObject(null, "java/lang/Object"));
                                            JavaConstructor result = MethodExecutor.execute(classes.get(owner), hideAccessMethod, Collections.singletonList(new JavaInteger(value)), null, context);
                                            hideAccessMethod.instructions.remove(returnInsert);
                                            //Remove the array of objects
                                            while (insn.getPrevious() != null) {
                                                if (insn.getPrevious().getOpcode() == Opcodes.ANEWARRAY) {
                                                    methodNode.instructions.remove(insn.getPrevious().getPrevious());
                                                    methodNode.instructions.remove(insn.getPrevious());
                                                    break;
                                                } else if (insn.getPrevious().getOpcode() == Opcodes.ACONST_NULL) {
                                                    methodNode.instructions.remove(insn.getPrevious());
                                                    break;
                                                }
                                                methodNode.instructions.remove(insn.getPrevious());
                                            }
                                            AbstractInsnNode firstArgInsn;
                                            if(Type.getArgumentTypes(result.getDesc()).length == 0)
                                            	firstArgInsn = insn;
                                            else
                                            	firstArgInsn = new ArgsAnalyzer(
                                            		insn.getPrevious(), Type.getArgumentTypes(result.getDesc()).length, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs().getFirstArgInsn();
                                            methodNode.instructions.insertBefore(firstArgInsn, new TypeInsnNode(Opcodes.NEW, result.getClassName()));
                                            methodNode.instructions.insertBefore(firstArgInsn, new InsnNode(Opcodes.DUP));
                                            //The constructor is used to write a desc
                                            methodNode.instructions.set(insn, new MethodInsnNode(
                                                    Opcodes.INVOKESPECIAL, result.getClassName(),
                                                    "<init>", result.getDesc(), false));
                                            count.getAndIncrement();
                                        }
                                        break;
                                    }
                                    //Invokestatic goes below
                                }
                                case "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;": { // INVOKEVIRTUAL
                                    MethodNode decryptMethod = classes.get(owner).methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Method;")).findFirst().orElse(null);
                                    if (insn.getPrevious().getOpcode() == Opcodes.SWAP) {
                                        Integer value = (Integer) ((LdcInsnNode) insn.getPrevious().getPrevious()).cst;
                                        JavaMethod result = MethodExecutor.execute(classes.get(owner), decryptMethod, Collections.singletonList(new JavaInteger(value)), null, context);
                                        //Remove the array of objects
                                        while (insn.getPrevious() != null) {
                                            if (insn.getPrevious().getOpcode() == Opcodes.ANEWARRAY) {
                                                methodNode.instructions.remove(insn.getPrevious().getPrevious());
                                                methodNode.instructions.remove(insn.getPrevious());
                                                break;
                                            } else if (insn.getPrevious().getOpcode() == Opcodes.ACONST_NULL) {
                                                methodNode.instructions.remove(insn.getPrevious());
                                                break;
                                            }
                                            methodNode.instructions.remove(insn.getPrevious());
                                        }
                                        //Remove extra pop
                                        if (Type.getReturnType(result.getDesc()).getSort() == Type.VOID &&
                                                insn.getNext() != null && insn.getNext().getOpcode() == Opcodes.POP)
                                            methodNode.instructions.remove(insn.getNext());
                                        //Removes the casts from a primitive to non primitive (doesn't solve the cast problem completely)
                                        if (insn.getNext() != null && insn.getNext().getOpcode() == Opcodes.CHECKCAST
                                                && insn.getNext().getNext() != null && insn.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL) {
                                            TypeInsnNode next = (TypeInsnNode) insn.getNext();
                                            if (isUnboxingMethod(next)
                                                    && Type.getReturnType(result.getDesc()).getClassName().equals(getPrimitiveFromClass(next.desc))) {
                                                methodNode.instructions.remove(next.getNext());
                                                methodNode.instructions.remove(next);
                                            }
                                        }
                                        //Uses invokeinterface if owner class is interface
                                        boolean useInterface = false;
                                        JavaClass clazz = new JavaClass(result.getOwner(), context);
                                        if ((clazz.getClassNode().access & Opcodes.ACC_INTERFACE) != 0)
                                            useInterface = true;
                                        int opcode = desc.equals("(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;") ? Opcodes.INVOKEVIRTUAL :
                                                Opcodes.INVOKESTATIC;
                                        if (opcode == Opcodes.INVOKEVIRTUAL && useInterface)
                                            opcode = Opcodes.INVOKEINTERFACE;
                                        methodNode.instructions.set(insn, new MethodInsnNode(
                                                opcode, result.getOwner(), result.getName(), result.getDesc(), opcode == Opcodes.INVOKEINTERFACE));
                                        count.getAndIncrement();
                                    }
                                    break;
                                }
                                case "(I)Ljava/lang/Object;": {  // GETSTATIC
                                    MethodNode decryptMethod = classes.get(owner).methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);
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
                                    MethodNode decryptMethod = classes.get(owner).methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);
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
                                    MethodNode decryptMethod = classes.get(owner).methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);
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
                                    MethodNode decryptMethod = classes.get(owner).methods.stream().filter(m -> m.desc.equals("(I)Ljava/lang/reflect/Field;")).findFirst().orElse(null);

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
        castFix(context);
        System.out.println("[Stringer] [HideAccessTransformer] Removed " + count.get() + " hide access");
        long cleanedup = cleanup(decryptors, decryptMethods);
        System.out.println("[Stringer] [HideAccessTransformer] Removed " + (cleanedup >> 32) + " decryptor classes");
        System.out.println("[Stringer] [HideAccessTransformer] Removed " + (int) cleanedup + " invokedynamic bootstrap methods");

        System.out.println("[Stringer] [HideAccessTransformer] Done");
        return true;
    }

    private void castFix(Context context) {
        classNodes().forEach(classNode -> classNode.methods.forEach(methodNode -> {
            InsnList copy = Utils.copyInsnList(methodNode.instructions);
            for (int i = 0; i < copy.size(); i++) {
                AbstractInsnNode insn = copy.get(i);

                if (insn.getOpcode() == Opcodes.CHECKCAST) {
                    TypeInsnNode checkcast = (TypeInsnNode) insn;
                    AbstractInsnNode previous = checkcast.getPrevious();

                    if (previous.getOpcode() == Opcodes.CHECKCAST) {
                        methodNode.instructions.remove(checkcast);
                    } else if (previous instanceof MethodInsnNode) {
                        Type type1 = getReturnType(((MethodInsnNode) previous).desc);
                        Type type2 = getReturnType(checkcast.desc);

                        if (type1.getClassName().equals(type2.getClassName())) {
                            methodNode.instructions.remove(checkcast);
                        } else {
                        	String class2 = type2.getClassName().replace(".", "/");
	                        class2 = convertPrimitiveToClassArray(class2, 1);
	                        String class1 = type1.getClassName().replace(".", "/");
	                        class1 = convertPrimitiveToClassArray(class1, 0);
	                        if(type1.getSort() == Type.ARRAY)
	                        	class1 = class1.substring(0, class1.length() - 2);
	                        if(type2.getSort() == Type.ARRAY)
	                        	class2 = class2.substring(0, class2.length() - 2);
	                        //Object[] to Object
	                        if(type2.getClassName().replace(".", "/").equals("java/lang/Object")
	                        	&& type2.getSort() == Type.ARRAY
	                        	&& type2.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))
	                        	methodNode.instructions.remove(checkcast);
	                        else if(!class1.equals("void") && !class1.equals("java/lang/Object")
	                        	&& (type1.getSort() != Type.ARRAY ||
	                        	!type1.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))) {
	                        	JavaClass clazz = new JavaClass(class1, context);
                                //Check if cast is a superclass/interface of the type
                                while (clazz != null) {
                                    if (isInterfaceOf(class2, clazz)) {
                                        methodNode.instructions.remove(checkcast);
                                        break;
                                    }
                                    clazz = clazz.getSuperclass();
                                }
                            }
                            //Invokespecial
                            if (((MethodInsnNode) previous).getOpcode() == Opcodes.INVOKESPECIAL) {
                                if (((MethodInsnNode) previous).owner.equals(type2.getClassName().replace(".", "/")))
                                    methodNode.instructions.remove(checkcast);
                                else {
                                    class1 = ((MethodInsnNode) previous).owner.replace(".", "/");
                                    if(!class1.equals("void") && !class1.equals("java/lang/Object")
	                        			&& (type1.getSort() != Type.ARRAY ||
	    	                        	!type1.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))) {
                                        JavaClass clazz = new JavaClass(class1, context);
                                        //Check if cast is a superclass/interface of the type
                                        while (clazz != null) {
                                            if (isInterfaceOf(class2, clazz)) {
                                                methodNode.instructions.remove(checkcast);
                                                break;
                                            }
                                            clazz = clazz.getSuperclass();
                                        }
                                    }
                                }
                            }
                        }
                    } else if (previous instanceof FieldInsnNode) {
                        Type type1 = getReturnType(((FieldInsnNode) previous).desc);
                        Type type2 = getReturnType(checkcast.desc);

                        if (type1.getClassName().equals(type2.getClassName())) {
                            methodNode.instructions.remove(checkcast);
                        } else if (isPrimitive(type1.getClassName()) && isObjectType(type2.getClassName())) {
                            AbstractInsnNode next = checkcast.getNext();

                            if (next instanceof MethodInsnNode && ((MethodInsnNode) next).name.endsWith("Value") && ((MethodInsnNode) next).desc.startsWith("()"))
                                methodNode.instructions.remove(next);

                            methodNode.instructions.remove(checkcast);
                        } else {
                        	String class2 = type2.getClassName().replace(".", "/");
	                        class2 = convertPrimitiveToClassArray(class2, 1);
	                        String class1 = type1.getClassName().replace(".", "/");
	                        class1 = convertPrimitiveToClassArray(class1, 0);
	                        if(type1.getSort() == Type.ARRAY)
	                        	class1 = class1.substring(0, class1.length() - 2);
	                        if(type2.getSort() == Type.ARRAY)
	                        	class2 = class2.substring(0, class2.length() - 2);
	                        //Object[] to Object
	                        if(type2.getClassName().replace(".", "/").equals("java/lang/Object")
	                        	&& type2.getSort() == Type.ARRAY
	                        	&& type2.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))
	                        	methodNode.instructions.remove(checkcast);
	                        else if(!class1.equals("void") && !class1.equals("Z") && !class1.equals("java/lang/Object")
	                        	&& (type1.getSort() != Type.ARRAY ||
	                        	!type1.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))) {
                                JavaClass clazz = new JavaClass(class1, context);
                                //Check if cast is a superclass/interface of the type
                                while (clazz != null) {
                                    if (isInterfaceOf(class2, clazz)) {
                                        methodNode.instructions.remove(checkcast);
                                        break;
                                    }
                                    clazz = clazz.getSuperclass();
                                }
                            }
                        }
                    } else if (previous.getOpcode() >= Opcodes.IALOAD && previous.getOpcode() <= Opcodes.SALOAD
                            && previous.getPrevious() != null && Utils.willPushToStack(previous.getPrevious().getOpcode())
                            && previous.getPrevious().getPrevious() != null
                            && (previous.getPrevious().getPrevious().getOpcode() == Opcodes.GETSTATIC
                            || previous.getPrevious().getPrevious().getOpcode() == Opcodes.GETFIELD)) {
                        FieldInsnNode fieldNode = (FieldInsnNode) previous.getPrevious().getPrevious();
                        Type type1 = getReturnType(fieldNode.desc);
                        Type type2 = getReturnType(checkcast.desc);
                        if (type1.getClassName().equals(type2.getClassName()))
                            methodNode.instructions.remove(checkcast);
                        String class2 = type2.getClassName().replace(".", "/");
                        class2 = convertPrimitiveToClassArray(class2, 1);
                        String class1 = type1.getClassName().replace(".", "/");
                        class1 = convertPrimitiveToClassArray(class1, 0);
                        if(type1.getSort() == Type.ARRAY)
                        	class1 = class1.substring(0, class1.length() - 2);
                        if(type2.getSort() == Type.ARRAY)
                        	class2 = class2.substring(0, class2.length() - 2);
                        //Object[] to Object
                        if(type2.getClassName().replace(".", "/").equals("java/lang/Object")
                        	&& type2.getSort() == Type.ARRAY
                        	&& type2.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))
                        	methodNode.instructions.remove(checkcast);
                        else if(!class1.equals("void") && !class1.equals("java/lang/Object")
                        	&& (type1.getSort() != Type.ARRAY ||
	                        	!type1.getElementType().getClassName().replace(".", "/").equals("java/lang/Object"))) {
                            JavaClass clazz = new JavaClass(class1, context);
                            //Check if cast is a superclass/interface of the type
                            while (clazz != null) {
                                if (isInterfaceOf(class2, clazz)) {
                                    methodNode.instructions.remove(checkcast);
                                    break;
                                }
                                clazz = clazz.getSuperclass();
                            }
                        }
                    }
                }
            }
        }));

        classNodes().forEach(classNode -> classNode.methods.forEach(methodNode -> {
            InsnList copy = Utils.copyInsnList(methodNode.instructions);
            for (int i = 0; i < copy.size(); i++) {
                AbstractInsnNode insn = copy.get(i);
                AbstractInsnNode previous = insn.getPrevious();
                if (insn instanceof FieldInsnNode && isValueOf(previous)) {
                    Type type1 = getReturnType(((MethodInsnNode) previous).desc);
                    Type type2 = getReturnType(((FieldInsnNode) insn).desc);

                    if (isObjectType(type1.getClassName()) && isPrimitive(type2.getClassName())) {
                        methodNode.instructions.remove(previous);
                    } /*else if (isPrimitive(type1.getClassName()) && isObjectType(type2.getClassName()) && isEqualType(type1.getClassName(), type2.getClassName())) {
                        String[] unboxing = getUnboxingMethod(type2.getClassName()).split(" ");
                        methodNode.instructions.insertBefore();
                    }*/
                }
            }
        }));
    }

    //XXX: Better detector
    private List<ClassNode> findDecryptClass() {
        List<ClassNode> decryptors = new ArrayList<>();
        classNodes().stream().filter(classNode -> classNode.version == 49 && Modifier.isFinal(classNode.access) && classNode.superName.equals("java/lang/Object")).forEach(possibleClass -> {
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

        classNodes().forEach(classNode -> {
            if (classNode.methods.removeIf(decryptMethods::contains)) {
                methodCount.getAndIncrement();
            }
        });

        Set<ClassNode> remove = classNodes().stream().filter(decryptClasses::contains).collect(Collectors.toSet());
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

    private Type getReturnType(String desc) {
        if (desc.startsWith("(")) {
            return Type.getReturnType(desc);
        } else if (desc.startsWith("L")) {
        	return Type.getReturnType("()" + desc);
        } else if (desc.startsWith("[")) {
            return Type.getReturnType("()[" + desc.substring(1, desc.length()));
        } else {
            return Type.getReturnType("()L" + desc + ";");
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

    private String getPrimitiveFromClass(String clazz) {
        for (String[] type : CLASS_TO_PRIMITIVE) {
            if (clazz.equals(type[0]))
                return type[1];
        }
        return null;
    }

    private String convertPrimitiveToClassArray(String array, int mode) {
        if (mode == 0) {
            String[][] types = {
                    {"byte[]", "java/lang/Byte[]"},
                    {"short[]", "java/lang/Short[]"},
                    {"int[]", "java/lang/Integer[]"},
                    {"long[]", "java/lang/Long[]"},
                    {"float[]", "java/lang/Float[]"},
                    {"double[]", "java/lang/Double[]"},
                    {"char[]", "java/lang/Character[]"},
                    {"boolean[]", "java/lang/Boolean[]"}
            };

            for (String[] type : types) {
                if (array.equals(type[0]))
                    return type[1];
            }
            return array;
        } else if (mode == 1) {
            String[][] types = {
                    {"[B", "java/lang/Byte[]"},
                    {"[S", "java/lang/Short[]"},
                    {"[I", "java/lang/Integer[]"},
                    {"[L", "java/lang/Long[]"},
                    {"[F", "java/lang/Float[]"},
                    {"[D", "java/lang/Double[]"},
                    {"[C", "java/lang/Character[]"},
                    {"[Z", "java/lang/Boolean[]"}
            };

            for (String[] type : types) {
                if (array.equals(type[0]))
                    return type[1];
            }
            return array;
        }
        return array;
    }

    /**
     * Determines if class1 is implemented at any point by class2.
     * This means that casting class2 to class1 would be casting from
     * more specific to less specific.
     *
     * @param class1 Should have slashes instead of dots
     */
    private boolean isInterfaceOf(String class1, JavaClass class2) {
        if (class1.equals(class2.getName().replace(".", "/")))
            return true;
        if (class2.getInterfaces() != null && class2.getInterfaces().length > 0)
            for (JavaClass clazz : class2.getInterfaces())
                if (isInterfaceOf(class1, clazz))
                    return true;
        return false;
    }
}
