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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

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
                                            AbstractInsnNode firstArgInsn = null;
                                            if(Type.getArgumentTypes(result.getDesc()).length == 0)
                                            	firstArgInsn = insn;
                                            else
                                            {
                                            	int length = 0;
                                            	for(Type t : Type.getArgumentTypes(result.getDesc()))
                                            		if(t.getSort() == Type.LONG || t.getSort() == Type.DOUBLE)
                                            			length += 2;
                                            		else
                                            			length++;
                                            	ArgsAnalyzer.Result res = new ArgsAnalyzer(insn.getPrevious(), length, ArgsAnalyzer.Mode.BACKWARDS).lookupArgs();
                                            	if(res instanceof ArgsAnalyzer.FailedResult)
                                            	{
                                            		boolean passed = false;
                                            		AbstractInsnNode replace;
                                            		methodNode.instructions.set(insn, replace = new MethodInsnNode(
                                            			Opcodes.INVOKESPECIAL, result.getClassName(),
                                            			"<init>", result.getDesc(), false));
                                            		AbstractInsnNode newInsn = new TypeInsnNode(Opcodes.NEW, result.getClassName());
                                            		AbstractInsnNode dupInsn = new InsnNode(Opcodes.DUP);
                                            		for(int i1 = methodNode.instructions.indexOf(replace); i1 >= 0; i1--)
                                            		{
                                            			AbstractInsnNode a = methodNode.instructions.get(i1);
                                            			if(!Utils.isInstruction(a) || a.getOpcode() == Opcodes.IINC)
                                            				continue;
                                            			methodNode.instructions.insertBefore(a, newInsn);
                                            			methodNode.instructions.insertBefore(a, dupInsn);
                                            			Frame<SourceValue>[] tempFrames;
                                            			try {
                                            				tempFrames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
                                            			} catch (AnalyzerException e) {
                                            				methodNode.instructions.remove(newInsn);
                                            				methodNode.instructions.remove(dupInsn);
                                                         	continue;
                                            			}
                                            			Frame<SourceValue> currentFrame = tempFrames[methodNode.instructions.indexOf(replace)];
                                            			Set<AbstractInsnNode> insns = new HashSet<>(currentFrame.getStack(currentFrame.getStackSize() -
                                            				Type.getArgumentTypes(result.getDesc()).length - 2).insns);
                                            			if(insns.size() == 1)
                                            			{
                                            				AbstractInsnNode singleton = null;
                                            				for(AbstractInsnNode ain1 : insns)
                                            					singleton = ain1;
                                            				if(singleton == newInsn)
                                            				{
                                            					passed = true;
                                            					break;
                                            				}
                                            			}
                                            			methodNode.instructions.remove(newInsn);
                                        				methodNode.instructions.remove(dupInsn);
                                            		}
                                            		if(!passed)
                                            			for(int i1 = methodNode.instructions.indexOf(replace); i1 < methodNode.instructions.size(); i1++)
                                                		{
                                                			AbstractInsnNode a = methodNode.instructions.get(i1);
                                                			if(!Utils.isInstruction(a) || a.getOpcode() == Opcodes.IINC)
                                                				continue;
                                                			methodNode.instructions.insertBefore(a, newInsn);
                                                			methodNode.instructions.insertBefore(a, dupInsn);
                                                			Frame<SourceValue>[] tempFrames;
                                                			try {
                                                				tempFrames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, methodNode);
                                                			} catch (AnalyzerException e) {
                                                				methodNode.instructions.remove(newInsn);
                                                				methodNode.instructions.remove(dupInsn);
                                                             	continue;
                                                			}
                                                			Frame<SourceValue> currentFrame = tempFrames[methodNode.instructions.indexOf(replace)];
                                                			Set<AbstractInsnNode> insns = new HashSet<>(currentFrame.getStack(currentFrame.getStackSize() -
                                                				Type.getArgumentTypes(result.getDesc()).length - 1).insns);
                                                			if(insns.size() == 1)
                                                			{
                                                				AbstractInsnNode singleton = null;
                                                				for(AbstractInsnNode ain1 : insns)
                                                					singleton = ain1;
                                                				if(singleton == newInsn)
                                                				{
                                                					passed = true;
                                                					break;
                                                				}
                                                			}
                                                			methodNode.instructions.remove(newInsn);
                                            				methodNode.instructions.remove(dupInsn);
                                                		}
                                            		if(!passed)
                                            			throw new RuntimeException("Could not insert constructor!");
                                            		count.getAndIncrement();
                                            		break;
                                            	}else
                                            		firstArgInsn = res.getFirstArgInsn();
                                            }
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

                                    if (insn.getNext().getOpcode() == Opcodes.CHECKCAST && isUnboxingMethod((TypeInsnNode)insn.getNext())
                                    	&& Type.getType(result.getDesc()).getClassName().equals(getPrimitiveFromClass(((TypeInsnNode)insn.getNext()).desc))) {
                                    	methodNode.instructions.remove(insn.getNext().getNext());
                                    	methodNode.instructions.remove(insn.getNext());
                                    }
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

                                        if (isValueOf(insn.getPrevious())
                                        	&& Type.getType(result.getDesc()).getClassName().equals(getPrimitiveFromClass(((MethodInsnNode)insn.getPrevious()).owner)))
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

                                    if (insn.getNext().getOpcode() == Opcodes.CHECKCAST && isUnboxingMethod((TypeInsnNode)insn.getNext())
                                    	&& Type.getType(result.getDesc()).getClassName().equals(getPrimitiveFromClass(((TypeInsnNode)insn.getNext()).desc))) {
                                    	methodNode.instructions.remove(insn.getNext().getNext());
                                    	methodNode.instructions.remove(insn.getNext());
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

                                        if (isValueOf(insn.getPrevious())
                                        	&& Type.getType(result.getDesc()).getClassName().equals(getPrimitiveFromClass(((MethodInsnNode)insn.getPrevious()).owner)))
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
        classNodes().forEach(classNode -> {
        	for(MethodNode mn : classNode.methods)
			{
        		boolean modified;
        		do
        		{
        			modified = false;
					Set<AbstractInsnNode> set = new HashSet<>();
					Frame<BasicValue>[] frames;
	                try 
	                {
	                    frames = new Analyzer<>(new MyInterpreter()).analyze(classNode.name, mn);
	                }catch(AnalyzerException e) 
	                {
	                    oops("unexpected analyzer exception", e);
	                    continue;
	                }
	                for(int i = 0; i < mn.instructions.size(); i++)
	                {
	                	AbstractInsnNode ain = mn.instructions.get(i);
	                	if(ain.getOpcode() == Opcodes.CHECKCAST && frames[i] != null)
	                	{
	                		Type typeBefore = frames[i].getStack(frames[i].getStackSize() - 1).getType();
	                		Type typeNow = Type.getObjectType(((TypeInsnNode)ain).desc);
	                		while(true)
	                		{
		                		if(typeBefore.equals(typeNow))
		                			set.add(ain);
		                		else if(typeBefore.getSort() == Type.OBJECT && typeBefore.getInternalName().equals("null"))
		                			set.add(ain);
		                		else if(typeNow.getSort() == Type.OBJECT && typeNow.getInternalName().equals("java/lang/Object")
		                			&& typeBefore.getSort() == Type.ARRAY)
		                			set.add(ain);
		                		else if(typeNow.getSort() == Type.OBJECT && typeBefore.getSort() == Type.OBJECT)
		                			try
		                			{
		                				if(getDeobfuscator().isSubclass(typeNow.getInternalName(), typeBefore.getInternalName()))
		                					set.add(ain);
		                			}catch(Exception e) {}	
		                		else if(typeNow.getSort() == Type.ARRAY && typeBefore.getSort() == Type.ARRAY)
		                		{
		                			typeBefore = typeBefore.getElementType();
		                			typeNow = typeNow.getElementType();
		                			continue;
		                		}
		                		break;
	                		}
	                	}
	                }
	                for(AbstractInsnNode ain : set)
	                	mn.instructions.remove(ain);
	                modified = !set.isEmpty();
        		}while(modified);
			}
        });
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

    private String getPrimitiveFromClass(String clazz) {
        for (String[] type : CLASS_TO_PRIMITIVE) {
            if (clazz.equals(type[0]))
                return type[1];
        }
        return null;
    }

    private class MyInterpreter extends BasicInterpreter
    {
    	public MyInterpreter()
    	{
    		super(Opcodes.ASM8);
    	}
    	
    	@Override
    	public BasicValue newValue(final Type type)
    	{
    		if(type == null)
    			return new BasicValue(Type.getType("Ljava/lang/Object;"));
    		switch(type.getSort())
    		{
    			case Type.VOID:
    				return null;
    			case Type.BOOLEAN:
    			case Type.CHAR:
    			case Type.BYTE:
    			case Type.SHORT:
    			case Type.INT:
    				return BasicValue.INT_VALUE;
    			case Type.FLOAT:
    				return BasicValue.FLOAT_VALUE;
    			case Type.LONG:
    				return BasicValue.LONG_VALUE;
    			case Type.DOUBLE:
    				return BasicValue.DOUBLE_VALUE;
    			case Type.ARRAY:
    			case Type.OBJECT:
    				return new BasicValue(type);
    			default:
    				throw new Error("Internal error");
    		}
    	}
    	
    	@Override
    	public BasicValue binaryOperation(final AbstractInsnNode insn,
    		final BasicValue value1, final BasicValue value2)
    			throws AnalyzerException
    	{
    		if(insn.getOpcode() == Opcodes.AALOAD)
    			return new BasicValue(value1.getType().getElementType());
    		return super.binaryOperation(insn, value1, value2);
    	}
    	
    	@Override
    	public BasicValue merge(final BasicValue v, final BasicValue w)
    	{
    		if(!v.equals(w))
    			return new BasicValue(Type.getType("Ljava/lang/Object;"));
    		return v;
    	}
    }
}
