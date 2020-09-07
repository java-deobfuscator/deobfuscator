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

package com.javadeobfuscator.deobfuscator.transformers.zelix;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.*;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaField;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaFieldHandle;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaHandle;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.values.JavaLong;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public class ReflectionObfuscationTransformer extends Transformer<TransformerConfig> {
    static Map<String, String> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put("boolean", "java/lang/Boolean");
        PRIMITIVES.put("byte", "java/lang/Byte");
        PRIMITIVES.put("char", "java/lang/Character");
        PRIMITIVES.put("short", "java/lang/Short");
        PRIMITIVES.put("int", "java/lang/Integer");
        PRIMITIVES.put("float", "java/lang/Float");
        PRIMITIVES.put("double", "java/lang/Double");
        PRIMITIVES.put("long", "java/lang/Long");
    }

    @Override
    public boolean transform() throws Throwable {
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Starting");
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Finding reflection obfuscation");
        int count = findReflectionObfuscation();
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Found " + count + " reflection obfuscation instructions");
        int amount = 0;
        if (count > 0) {
            amount = inlineReflection(count);
            System.out.println("[Zelix] [ReflectionObfuscationTransformer] Inlined " + amount + " reflection obfuscation instructions");
        }
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Done");
        return amount > 0;
    }

    public int inlineReflection(int expected) throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        final boolean[] alerted = new boolean[100];

        DelegatingProvider provider = new DelegatingProvider();

        provider.register(new PrimitiveFieldProvider());
        provider.register(new MappedFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return type.getDescriptor().equals("Ljava/lang/String;") && target.value() instanceof String;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                if (type.getInternalName().equals("java/lang/String")) {
                    return target.value() instanceof String;
                } else if (type.getInternalName().equals("java/lang/Class")) {
                    return target.value() instanceof JavaClass || target.value() instanceof Type; //TODO consolidate types
                } else if (type.getInternalName().equals("java/lang/reflect/Method")) {
                    return target.value() instanceof JavaMethod;
                } else if (type.getInternalName().equals("java/lang/reflect/Field")) {
                    return target.value() instanceof JavaField;
                } else if (type.getInternalName().equals("[Ljava/lang/reflect/Method;")) {
                    return target.value() instanceof Object[];
                } else if (type.getInternalName().equals("[Ljava/lang/Class;")) {
                    return target.value() instanceof Object[];
                }
                return false;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type, Context context) {
                return type.getInternalName().equals("java/lang/String")
                        || type.getInternalName().equals("java/lang/Class")
                        || type.getInternalName().equals("java/lang/reflect/Method")
                        || type.getInternalName().equals("java/lang/reflect/Field")
                        || type.getInternalName().equals("[Ljava/lang/reflect/Method;")
                        || type.getInternalName().equals("[Ljava/lang/Class;");
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        Set<ClassNode> initted = new HashSet<>();
        Set<ClassNode> reflectionClasses = new HashSet<>();
        Map<ClassNode, Set<MethodNode>> indyReflectionMethods = new HashMap<>();
        Map<ClassNode, Set<MethodNode>> argsReflectionMethods = new HashMap<>();
        Map<ClassNode, Set<MethodNode>> initReflectionMethod = new HashMap<>();
        Map<ClassNode, MethodNode> fieldReflectionMethod = new HashMap<>();
        Map<ClassNode, MethodNode> methodReflectionMethod = new HashMap<>();
        for(ClassNode classNode : classNodes())
        	for(MethodNode methodNode : classNode.methods)
        		for(AbstractInsnNode current : methodNode.instructions.toArray())
        		{
        			if (current instanceof MethodInsnNode
                    	&& !methodNode.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;"
                    		+ "Ljava/lang/String;Ljava/lang/invoke/MethodType;J)Ljava/lang/invoke/MethodHandle;")
                    	&& !methodNode.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;"
                    		+ "Ljava/lang/String;Ljava/lang/invoke/MethodType;JJ)Ljava/lang/invoke/MethodHandle;")) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                        if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Method;")) {
                            long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                            String strCl = methodInsnNode.owner;
                            ClassNode innerClassNode = classpath.get(strCl);
                            if (initted.add(innerClassNode)) {
                            	try 
    							{
                                	List<MethodNode> init = new ArrayList<>();
                                	MethodNode decryptorNode = innerClassNode.methods.stream().filter(mn -> mn.desc.equals("()V") && isInitMethod(innerClassNode, mn)).findFirst().orElse(null);
                                	FieldInsnNode fieldInsn = null;
                                	List<AbstractInsnNode> removed = new ArrayList<>();
                                	if(decryptorNode != null)
                                	{
                                    	init.add(decryptorNode);
                                    	fieldInsn = (FieldInsnNode)getObjectList(decryptorNode);
                                	}else
                                    {
                                    	MethodNode clinit = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                    	for(AbstractInsnNode ain : clinit.instructions.toArray())
                                    	{
                                    		if(Utils.isInteger(ain) && ain.getNext() != null
                                    			&& (ain.getNext().getOpcode() == Opcodes.NEWARRAY || 
                                    			(ain.getNext().getOpcode() == Opcodes.ANEWARRAY && ((TypeInsnNode)ain.getNext()).desc.equals("java/lang/Object")))
                                    			&& ain.getNext().getNext() != null
                                        		&& ain.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)ain.getNext().getNext()).owner.equals(innerClassNode.name)
                                        		&& ain.getNext().getNext().getNext() != null
                                        		&& Utils.isInteger(ain.getNext().getNext().getNext()) 
                                        		&& Utils.getIntValue(ain.getNext().getNext().getNext()) == Utils.getIntValue(ain)
                                        		&& ain.getNext().getNext().getNext().getNext() != null
                                        		&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ANEWARRAY 
                                        		&& ((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc.equals("java/lang/String")
                                        		&& ain.getNext().getNext().getNext().getNext().getNext() != null
                                        		&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC 
                                        		&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).owner.equals(innerClassNode.name))
                                    		{
                                    			((TypeInsnNode)ain.getNext()).desc = "java/lang/String";
                                    			((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc = "java/lang/Object";
                                    			((FieldInsnNode)ain.getNext().getNext()).desc = "[Ljava/lang/String;";
                                    			((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).desc = "[Ljava/lang/Object;";
                                       			String temp = ((FieldInsnNode)ain.getNext().getNext()).name;
                                       			((FieldInsnNode)ain.getNext().getNext()).name = ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name;
                                       			((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name = temp;
                                    			fieldInsn = (FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext();
                                               	while(ain.getNext() != fieldInsn)
                                               	{
                                               		removed.add(ain.getNext());
                                            		clinit.instructions.remove(ain.getNext());
                                               	}
                                               	removed.add(fieldInsn);
                                               	clinit.instructions.remove(fieldInsn);
                                               	removed.add(0, ain);
                                               	clinit.instructions.remove(ain);
                                    			break;
                                    		}
                                    	}
                                    }
                                	FieldInsnNode fieldInsn1 = fieldInsn;
                                    Object[] otherInit = innerClassNode.methods.stream().filter(mn -> mn.desc.equals("()V") && isOtherInitMethod(innerClassNode, mn, fieldInsn1)).toArray();
                                    if(decryptorNode == null)
                                    {
                                    	MethodNode firstinit = (MethodNode)Arrays.stream(otherInit).filter(mn -> 
                                    	Utils.isInteger(getFirstIndex((MethodNode)mn))
                                    		&& Utils.getIntValue(getFirstIndex((MethodNode)mn)) == 0).findFirst().orElse(null);
                                    	firstinit.instructions.remove(firstinit.instructions.getFirst());
                                    	Collections.reverse(removed);
                                    	boolean first = false;
                                    	for(AbstractInsnNode ain : removed)
                                    	{
                                    		firstinit.instructions.insert(ain);
                                    		if(!first)
                                    		{
                                    			firstinit.instructions.insert(new InsnNode(Opcodes.DUP));
                                    			first = true;
                                    		}
                                    	}
                                    }
                                    for(Object o : otherInit)
                                		init.add((MethodNode)o);
                                    if(!innerClassNode.equals(classNode))
                                    	reflectionClasses.add(innerClassNode);
                                    else
                                    {
                                    	argsReflectionMethods.put(innerClassNode, new HashSet<>());
                                    	initReflectionMethod.put(innerClassNode, new HashSet<>());
                                    	for(MethodNode method : init)
                                    		initReflectionMethod.get(innerClassNode).add(method);
                                    }
                                	Context context = new Context(provider);
                                    context.dictionary = this.classpath;
                                    for(MethodNode method1 : init)
                                    	MethodExecutor.execute(innerClassNode, method1, Collections.emptyList(), null, context);
                                }catch(Throwable t) 
    							{
                                    System.out.println("Error while fully initializing " + classNode.name);
                                    t.printStackTrace(System.out);
                                }
                            }
                            MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            Context ctx = new Context(provider);
                            ctx.dictionary = classpath;
                            JavaMethod javaMethod = MethodExecutor.execute(innerClassNode, decrypterNode, Arrays.asList(new JavaLong(ldc)), null, ctx);
                            if(!methodReflectionMethod.containsKey(innerClassNode))
                            	methodReflectionMethod.put(innerClassNode, decrypterNode);
                            MethodInsnNode methodInsn = null;
                            if(current.getPrevious().getPrevious().getOpcode() == Opcodes.CHECKCAST)
                            {
                            	methodInsn = (MethodInsnNode)current.getPrevious().getPrevious().getPrevious();
                            	methodNode.instructions.remove(current.getPrevious().getPrevious().getPrevious());
                            }
                            if(methodInsn == null)
                            	methodInsn = (MethodInsnNode)current.getPrevious().getPrevious();
                            methodNode.instructions.remove(current.getPrevious().getPrevious());
                            if(argsReflectionMethods.containsKey(innerClassNode))
                            {
                            	MethodInsnNode finalMethodInsn = methodInsn;
                            	MethodNode method = innerClassNode.methods.stream().filter(
                            		m -> m.name.equals(finalMethodInsn.name) && m.desc.equals(finalMethodInsn.desc)).findFirst().orElse(null);
                            	argsReflectionMethods.get(innerClassNode).add(method);
                            }
                            methodNode.instructions.remove(current.getPrevious());
                            int opcode = -1;
                            if(current.getNext().getNext().getOpcode() == Opcodes.ACONST_NULL)
                            	opcode = Opcodes.INVOKESTATIC;
                            else if((classpath.get(javaMethod.getOwner()).access & Opcodes.ACC_INTERFACE) != 0)
                            	opcode = Opcodes.INVOKEINTERFACE;
                            else
                            	opcode = Opcodes.INVOKEVIRTUAL;
                            LabelNode label = null;
                            while(current.getNext() != null)
                            {
                            	if(current.getNext() instanceof LabelNode)
                            	{
                            		methodNode.instructions.remove(label = (LabelNode)current.getNext());
                            		methodNode.instructions.remove(current.getNext());
                            		break;
                            	}
                            	methodNode.instructions.remove(current.getNext());
                            }
                            while(!(current.getNext() instanceof LabelNode))
                            	methodNode.instructions.remove(current.getNext());
                            LabelNode nextLabel = (LabelNode)current.getNext();
                            methodNode.instructions.set(current, new MethodInsnNode(opcode, javaMethod.getOwner(), javaMethod.getName(), javaMethod.getDesc(), opcode == Opcodes.INVOKEINTERFACE));
                            //Remove exception thing
                            List<TryCatchBlockNode> beginTryCatch = new ArrayList<>();
                            List<TryCatchBlockNode> additionalRemove = new ArrayList<>();
                            Iterator<TryCatchBlockNode> itr = methodNode.tryCatchBlocks.iterator();
                            while(itr.hasNext())
                            {
                            	TryCatchBlockNode trycatch = itr.next();
                            	if(trycatch.start.equals(label) && trycatch.end.equals(nextLabel))
                            	{
                            		LabelNode begin = trycatch.handler;
        							while(begin.getNext() != null && !(begin.getNext() instanceof LabelNode))
        								methodNode.instructions.remove(begin.getNext());
        							//Find all trycatch nodes that begin with handler
        							for(TryCatchBlockNode tc : methodNode.tryCatchBlocks)
        								if(tc != trycatch && tc.end == begin)
        								{
        									beginTryCatch.add(tc);
        									tc.end = (LabelNode)begin.getNext();
        								}
        							//Find all trycatch nodes that try-catch exception block
        							for(TryCatchBlockNode tc : methodNode.tryCatchBlocks)
        								if(tc.start == begin && tc.end == begin.getNext())
        									additionalRemove.add(tc);
        							//Find all trycatch nodes that is a continuation of beginTryCatch
        							for(TryCatchBlockNode tc : methodNode.tryCatchBlocks)
        								if(tc.start == begin.getNext())
        								{
        									TryCatchBlockNode before = null;
        									for(TryCatchBlockNode tc2 : beginTryCatch)
        										if(tc2.end == begin.getNext() && tc2.type.equals(tc.type))
        										{
        											before = tc2;
        											break;
        										}
        									if(before != null)
        									{
        										additionalRemove.add(before);
        										tc.start = before.start;
        									}
        								}
        							methodNode.instructions.remove(begin);
        							itr.remove();
        							break;
                            	}
                            }
                            for(TryCatchBlockNode trycatch : additionalRemove)
                            	methodNode.tryCatchBlocks.remove(trycatch);
                            count.incrementAndGet();
                            int x = (int) ((count.get() * 1.0d / expected) * 100);
                            if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                alerted[x - 1] = true;
                            }
                        } else if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Field;")) {
                            long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                            String strCl = methodInsnNode.owner;
                            ClassNode innerClassNode = classpath.get(strCl);
                            if (initted.add(innerClassNode)) {
                            	try 
    							{
                                	List<MethodNode> init = new ArrayList<>();
                                	MethodNode decryptorNode = innerClassNode.methods.stream().filter(mn -> mn.desc.equals("()V") && isInitMethod(innerClassNode, mn)).findFirst().orElse(null);
                                	FieldInsnNode fieldInsn = null;
                                	List<AbstractInsnNode> removed = new ArrayList<>();
                                	if(decryptorNode != null)
                                	{
                                    	init.add(decryptorNode);
                                    	fieldInsn = (FieldInsnNode)getObjectList(decryptorNode);
                                	}else
                                    {
                                    	MethodNode clinit = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                    	for(AbstractInsnNode ain : clinit.instructions.toArray())
                                    	{
                                    		if(Utils.isInteger(ain) && ain.getNext() != null
                                    			&& (ain.getNext().getOpcode() == Opcodes.NEWARRAY || 
                                    			(ain.getNext().getOpcode() == Opcodes.ANEWARRAY && ((TypeInsnNode)ain.getNext()).desc.equals("java/lang/Object")))
                                    			&& ain.getNext().getNext() != null
                                        		&& ain.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)ain.getNext().getNext()).owner.equals(innerClassNode.name)
                                        		&& ain.getNext().getNext().getNext() != null
                                        		&& Utils.isInteger(ain.getNext().getNext().getNext()) 
                                        		&& Utils.getIntValue(ain.getNext().getNext().getNext()) == Utils.getIntValue(ain)
                                        		&& ain.getNext().getNext().getNext().getNext() != null
                                        		&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ANEWARRAY 
                                        		&& ((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc.equals("java/lang/String")
                                        		&& ain.getNext().getNext().getNext().getNext().getNext() != null
                                        		&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC 
                                        		&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).owner.equals(innerClassNode.name))
                                    		{
                                    			((TypeInsnNode)ain.getNext()).desc = "java/lang/String";
                                    			((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc = "java/lang/Object";
                                    			((FieldInsnNode)ain.getNext().getNext()).desc = "[Ljava/lang/String;";
                                    			((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).desc = "[Ljava/lang/Object;";
                                       			String temp = ((FieldInsnNode)ain.getNext().getNext()).name;
                                       			((FieldInsnNode)ain.getNext().getNext()).name = ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name;
                                       			((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name = temp;
                                    			fieldInsn = (FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext();
                                               	while(ain.getNext() != fieldInsn)
                                               	{
                                               		removed.add(ain.getNext());
                                            		clinit.instructions.remove(ain.getNext());
                                               	}
                                               	removed.add(fieldInsn);
                                               	clinit.instructions.remove(fieldInsn);
                                               	removed.add(0, ain);
                                               	clinit.instructions.remove(ain);
                                    			break;
                                    		}
                                    	}
                                    }
                                	FieldInsnNode fieldInsn1 = fieldInsn;
                                    Object[] otherInit = innerClassNode.methods.stream().filter(mn -> mn.desc.equals("()V") && isOtherInitMethod(innerClassNode, mn, fieldInsn1)).toArray();
                                    if(decryptorNode == null)
                                    {
                                    	MethodNode firstinit = (MethodNode)Arrays.stream(otherInit).filter(mn -> 
                                    	Utils.isInteger(getFirstIndex((MethodNode)mn))
                                    		&& Utils.getIntValue(getFirstIndex((MethodNode)mn)) == 0).findFirst().orElse(null);
                                    	firstinit.instructions.remove(firstinit.instructions.getFirst());
                                    	Collections.reverse(removed);
                                    	boolean first = false;
                                    	for(AbstractInsnNode ain : removed)
                                    	{
                                    		firstinit.instructions.insert(ain);
                                    		if(!first)
                                    		{
                                    			firstinit.instructions.insert(new InsnNode(Opcodes.DUP));
                                    			first = true;
                                    		}
                                    	}
                                    }
                                    for(Object o : otherInit)
                                		init.add((MethodNode)o);
                                    if(!innerClassNode.equals(classNode))
                                    	reflectionClasses.add(innerClassNode);
                                    else
                                    {
                                    	argsReflectionMethods.put(innerClassNode, new HashSet<>());
                                    	initReflectionMethod.put(innerClassNode, new HashSet<>());
                                    	for(MethodNode method : init)
                                    		initReflectionMethod.get(innerClassNode).add(method);
                                    }
                                	Context context = new Context(provider);
                                    context.dictionary = this.classpath;
                                    for(MethodNode method1 : init)
                                    	MethodExecutor.execute(innerClassNode, method1, Collections.emptyList(), null, context);
                                }catch(Throwable t) 
    							{
                                    System.out.println("Error while fully initializing " + classNode.name);
                                    t.printStackTrace(System.out);
                                }
                            }
                            MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                            Context ctx = new Context(provider);
                            ctx.dictionary = classpath;
                            JavaField javaField = MethodExecutor.execute(innerClassNode, decrypterNode, Collections.singletonList(new JavaLong(ldc)), null, ctx);
                            if(!fieldReflectionMethod.containsKey(innerClassNode))
                            	fieldReflectionMethod.put(innerClassNode, decrypterNode);
                            methodNode.instructions.remove(current.getPrevious());
                            if(current.getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL)
                            {
                            	//Getstatic/field
                            	int opcode = -1;
                            	if(current.getNext().getOpcode() == Opcodes.SWAP)
                            		opcode = Opcodes.GETFIELD;
                            	else
                            		opcode = Opcodes.GETSTATIC;
                            	if(current.getNext().getNext().getNext() != null
                            		&& current.getNext().getNext().getNext().getOpcode() == Opcodes.CHECKCAST)
                            		methodNode.instructions.remove(current.getNext().getNext().getNext());
                            	methodNode.instructions.remove(current.getNext().getNext());
                            	methodNode.instructions.remove(current.getNext());
                            	methodNode.instructions.set(current, new FieldInsnNode(
                            		opcode, javaField.getClassName(), javaField.getName(), javaField.getDesc()));
                            }else
                            {
                            	//Putstatic/field
                            	int opcode = -1;
                            	if(current.getNext().getOpcode() == Opcodes.ACONST_NULL)
                            		opcode = Opcodes.PUTSTATIC;
                            	else
                            		opcode = Opcodes.PUTFIELD;
                            	if(opcode == Opcodes.PUTSTATIC)
                            		methodNode.instructions.remove(current.getNext());
                            	else if(current.getNext().getOpcode() == Opcodes.SWAP)
                            	{
                            		//Long/Double
                            		methodNode.instructions.remove(current.getNext());
                            		methodNode.instructions.remove(current.getPrevious().getPrevious());
                            		methodNode.instructions.remove(current.getPrevious());
                            	}
                            	methodNode.instructions.remove(current.getNext().getNext().getNext());
                        		methodNode.instructions.remove(current.getNext().getNext());
                        		methodNode.instructions.remove(current.getNext());
                            	methodNode.instructions.set(current, new FieldInsnNode(
                            		opcode, javaField.getClassName(), javaField.getName(), javaField.getDesc()));
                            }
                            count.incrementAndGet();
                            int x = (int) ((count.get() * 1.0d / expected) * 100);
                            if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                alerted[x - 1] = true;
                            }
                        }
                    }else if (current instanceof InvokeDynamicInsnNode) {
                    	InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) current;
                    	if (invokeDynamicInsnNode.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                    		long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                            String strCl = invokeDynamicInsnNode.bsm.getOwner();
                            ClassNode innerClassNode = classpath.get(strCl);
                            if (initted.add(innerClassNode)) {
                            	try 
    							{
                                	List<MethodNode> init = new ArrayList<>();
                                	MethodNode decryptorNode = innerClassNode.methods.stream().filter(mn -> mn.desc.equals("()V") && isInitMethod(innerClassNode, mn)).findFirst().orElse(null);
                                	FieldInsnNode fieldInsn = null;
                                	List<AbstractInsnNode> removed = new ArrayList<>();
                                	if(decryptorNode != null)
                                	{
                                    	init.add(decryptorNode);
                                    	fieldInsn = (FieldInsnNode)getObjectList(decryptorNode);
                                	}else
                                    {
                                    	MethodNode clinit = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                    	for(AbstractInsnNode ain : clinit.instructions.toArray())
                                    	{
                                    		if(Utils.isInteger(ain) && ain.getNext() != null
                                    			&& (ain.getNext().getOpcode() == Opcodes.NEWARRAY || 
                                    			(ain.getNext().getOpcode() == Opcodes.ANEWARRAY && ((TypeInsnNode)ain.getNext()).desc.equals("java/lang/Object")))
                                    			&& ain.getNext().getNext() != null
                                        		&& ain.getNext().getNext().getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)ain.getNext().getNext()).owner.equals(innerClassNode.name)
                                        		&& ain.getNext().getNext().getNext() != null
                                        		&& Utils.isInteger(ain.getNext().getNext().getNext()) 
                                        		&& Utils.getIntValue(ain.getNext().getNext().getNext()) == Utils.getIntValue(ain)
                                        		&& ain.getNext().getNext().getNext().getNext() != null
                                        		&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ANEWARRAY 
                                        		&& ((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc.equals("java/lang/String")
                                        		&& ain.getNext().getNext().getNext().getNext().getNext() != null
                                        		&& ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.PUTSTATIC 
                                        		&& ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).owner.equals(innerClassNode.name))
                                    		{
                                    			((TypeInsnNode)ain.getNext()).desc = "java/lang/String";
                                    			((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc = "java/lang/Object";
                                    			((FieldInsnNode)ain.getNext().getNext()).desc = "[Ljava/lang/String;";
                                    			((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).desc = "[Ljava/lang/Object;";
                                       			String temp = ((FieldInsnNode)ain.getNext().getNext()).name;
                                       			((FieldInsnNode)ain.getNext().getNext()).name = ((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name;
                                       			((FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext()).name = temp;
                                    			fieldInsn = (FieldInsnNode)ain.getNext().getNext().getNext().getNext().getNext();
                                               	while(ain.getNext() != fieldInsn)
                                               	{
                                               		removed.add(ain.getNext());
                                            		clinit.instructions.remove(ain.getNext());
                                               	}
                                               	removed.add(fieldInsn);
                                               	clinit.instructions.remove(fieldInsn);
                                               	removed.add(0, ain);
                                               	clinit.instructions.remove(ain);
                                    			break;
                                    		}
                                    	}
                                    }
                                	FieldInsnNode fieldInsn1 = fieldInsn;
                                    Object[] otherInit = innerClassNode.methods.stream().filter(mn -> mn.desc.equals("()V") && isOtherInitMethod(innerClassNode, mn, fieldInsn1)).toArray();
                                    if(decryptorNode == null)
                                    {
                                    	MethodNode firstinit = (MethodNode)Arrays.stream(otherInit).filter(mn -> 
                                    	Utils.isInteger(getFirstIndex((MethodNode)mn))
                                    		&& Utils.getIntValue(getFirstIndex((MethodNode)mn)) == 0).findFirst().orElse(null);
                                    	firstinit.instructions.remove(firstinit.instructions.getFirst());
                                    	Collections.reverse(removed);
                                    	boolean first = false;
                                    	for(AbstractInsnNode ain : removed)
                                    	{
                                    		firstinit.instructions.insert(ain);
                                    		if(!first)
                                    		{
                                    			firstinit.instructions.insert(new InsnNode(Opcodes.DUP));
                                    			first = true;
                                    		}
                                    	}
                                    }
                                    for(Object o : otherInit)
                                		init.add((MethodNode)o);
                                    if(!innerClassNode.equals(classNode))
                                    	reflectionClasses.add(innerClassNode);
                                    else
                                    {
                                    	indyReflectionMethods.put(innerClassNode, new HashSet<>());
                                    	initReflectionMethod.put(innerClassNode, new HashSet<>());
                                    	for(MethodNode method : init)
                                    		initReflectionMethod.get(innerClassNode).add(method);
                                    }
                                	Context context = new Context(provider);
                                    context.dictionary = this.classpath;
                                    for(MethodNode method1 : init)
                                    	MethodExecutor.execute(innerClassNode, method1, Collections.emptyList(), null, context);
                                }catch(Throwable t) 
    							{
                                    System.out.println("Error while fully initializing " + classNode.name);
                                    t.printStackTrace(System.out);
                                }
                            }
                            MethodNode indyNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(invokeDynamicInsnNode.bsm.getName()) 
                            	&& mn.desc.equals(invokeDynamicInsnNode.bsm.getDesc())).findFirst().orElse(null);
                            MethodNode indyNode2 = null;
                            for(AbstractInsnNode ain : indyNode.instructions.toArray())
                            	if(ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode)ain).cst instanceof Handle)
                            	{
                            		Handle handle = (Handle)((LdcInsnNode)ain).cst;
                            		indyNode2 = innerClassNode.methods.stream().filter(mn -> mn.name.equals(handle.getName()) 
                            			&& mn.desc.equals(handle.getDesc())).findFirst().orElse(null);
                            		break;
                            	}
                            MethodNode indyNode3 = null;
                            for(AbstractInsnNode ain : indyNode2.instructions.toArray())
                            	if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(innerClassNode.name))
                            	{
                            		indyNode3 = innerClassNode.methods.stream().filter(mn -> mn.name.equals(((MethodInsnNode)ain).name) 
                            			&& mn.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
                            		break;
                            	}
                            List<JavaValue> args = new ArrayList<>();
                            args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
                            args.add(new JavaObject(null, "java/lang/invoke/MutableCallSite")); //CallSite
                            args.add(JavaValue.valueOf(invokeDynamicInsnNode.name)); //dyn method name
                            args.add(new JavaObject(invokeDynamicInsnNode.desc, "java/lang/invoke/MethodType")); //dyn method type
                            args.add(new JavaLong(ldc));
                            Context ctx = new Context(provider);
                            ctx.dictionary = classpath;
                            JavaHandle handle = MethodExecutor.execute(innerClassNode, indyNode3, args, null, ctx);
                            if(indyReflectionMethods.containsKey(innerClassNode))
                            {
                            	indyReflectionMethods.get(innerClassNode).add(indyNode);
                            	indyReflectionMethods.get(innerClassNode).add(indyNode2);
                            	indyReflectionMethods.get(innerClassNode).add(indyNode3);
                            	if(!fieldReflectionMethod.containsKey(innerClassNode))
                            		for(AbstractInsnNode ain : indyNode3.instructions.toArray())
                            			if(ain instanceof MethodInsnNode && ((MethodInsnNode)ain).desc.equals("(J)Ljava/lang/reflect/Field;"))
                            			{
                            				MethodNode refMethod = innerClassNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)ain).name)
                            					&& m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
                            				fieldReflectionMethod.put(innerClassNode, refMethod);
                            			}
                            	if(!methodReflectionMethod.containsKey(innerClassNode))
                            		for(AbstractInsnNode ain : indyNode3.instructions.toArray())
                            			if(ain instanceof MethodInsnNode && ((MethodInsnNode)ain).desc.equals("(J)Ljava/lang/reflect/Method;"))
                            			{
                            				MethodNode refMethod = innerClassNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)ain).name)
                            					&& m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
                            				methodReflectionMethod.put(innerClassNode, refMethod);
                            			}
                            }
                            methodNode.instructions.remove(current.getPrevious());
                            AbstractInsnNode replacement = null;
                            if(handle instanceof JavaMethodHandle)
                            {
                            	JavaMethodHandle jmh = (JavaMethodHandle)handle;
                                String clazz = jmh.clazz.replace('.', '/');
                                switch (jmh.type) {
                                    case "virtual":
                                        replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ? 
                                        	 Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, jmh.name, jmh.desc,
                                        	 (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
                                        break;
                                    case "static":
                                        replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, jmh.name, jmh.desc, false);
                                        break;
                                    case "special":
                                        replacement = new MethodInsnNode(Opcodes.INVOKESPECIAL, clazz, jmh.name, jmh.desc, false);
                                        break;
                                }
                            }else
                            {
                            	JavaFieldHandle jfh = (JavaFieldHandle)handle;
                                String clazz = jfh.clazz.replace('.', '/');
                                switch (jfh.type) {
                                    case "virtual":
                                        replacement = new FieldInsnNode(jfh.setter ? 
                                        	 Opcodes.PUTFIELD : Opcodes.GETFIELD, clazz, jfh.name, jfh.desc);
                                        break;
                                    case "static":
                                        replacement = new FieldInsnNode(jfh.setter ? 
                                        	Opcodes.PUTSTATIC : Opcodes.GETSTATIC, clazz, jfh.name, jfh.desc);
                                        break;
                                }
                            }
                            methodNode.instructions.set(current, replacement);
                            count.incrementAndGet();
                            int x = (int) ((count.get() * 1.0d / expected) * 100);
                            if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                alerted[x - 1] = true;
                            }
                        }
                    }
        		}
        //Remove all decryption class/methods
        Set<ClassNode> remove = classNodes().stream().filter(classNode -> reflectionClasses.contains(classNode)).collect(Collectors.toSet());
        classNodes().removeAll(remove);
        for(Entry<ClassNode, Set<MethodNode>> entry : argsReflectionMethods.entrySet())
        	for(MethodNode method : entry.getValue())
        		entry.getKey().methods.remove(method);
        for(Entry<ClassNode, Set<MethodNode>> entry : indyReflectionMethods.entrySet())
        	for(MethodNode method : entry.getValue())
        		entry.getKey().methods.remove(method);
        for(Entry<ClassNode, Set<MethodNode>> entry : initReflectionMethod.entrySet())
        {
        	List<MethodNode> list = new ArrayList<>(entry.getValue());
        	for(MethodNode method : list)
        	{
	        	int fieldCount = 0;
	        	if(list.indexOf(method) == 0)
		        	for(AbstractInsnNode ain : method.instructions.toArray())
		        	{
		        		if(ain.getOpcode() == Opcodes.PUTSTATIC)
		        		{
		        			FieldInsnNode fieldInsn = (FieldInsnNode)ain;
		        			FieldNode field = entry.getKey().fields.stream().filter(f -> 
		        			f.name.equals(fieldInsn.name) && f.desc.equals(fieldInsn.desc)).findFirst().orElse(null);
		        			entry.getKey().fields.remove(field);
		        		}
		        		if(fieldCount >= 2)
		        			break;
		        	}
	        	MethodNode clinit = entry.getKey().methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
	        	if(clinit != null)
		        	for(AbstractInsnNode ain : clinit.instructions.toArray())
		        		if(ain.getOpcode() == Opcodes.INVOKESTATIC)
		        		{
		        			MethodInsnNode methodInsn = (MethodInsnNode)ain;
		        			if(methodInsn.desc.equals(method.desc) && methodInsn.name.equals(method.name))
		        			{
		        				clinit.instructions.remove(methodInsn);
		        				break;
		        			}
		        		}
	        	entry.getKey().methods.remove(method);
        	}
        }
        for(Entry<ClassNode, MethodNode> entry : methodReflectionMethod.entrySet())
        {
        	List<MethodNode> reflectionReferences = new ArrayList<>();
        	for(AbstractInsnNode ain : entry.getValue().instructions.toArray())
        	{
        		if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(entry.getKey().name))
        		{
        			MethodInsnNode methodInsn = (MethodInsnNode)ain;
        			MethodNode method = entry.getKey().methods.stream().filter(
        				m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElse(null);
        			if(!reflectionReferences.contains(method))
        				reflectionReferences.add(method);
        		}
        		if(reflectionReferences.size() >= 4)
        			break;
        	}
        	for(int i = 0; i < reflectionReferences.size(); i++)
        	{
        		MethodNode method = reflectionReferences.get(i);
        		if(i == 2)
        			for(AbstractInsnNode ain : method.instructions.toArray())
                		if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(entry.getKey().name))
                		{
                			MethodInsnNode methodInsn = (MethodInsnNode)ain;
                			MethodNode method1 = entry.getKey().methods.stream().filter(
                				m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElse(null);
                			if(method1 != null)
                				entry.getKey().methods.remove(method1);
                		}
        		entry.getKey().methods.remove(method);
        	}
        	entry.getKey().methods.remove(entry.getValue());
        }
        for(Entry<ClassNode, MethodNode> entry : fieldReflectionMethod.entrySet())
        {
        	List<MethodNode> reflectionReferences = new ArrayList<>();
        	for(AbstractInsnNode ain : entry.getValue().instructions.toArray())
        	{
        		if(ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode)ain).owner.equals(entry.getKey().name))
        		{
        			MethodInsnNode methodInsn = (MethodInsnNode)ain;
        			MethodNode method = entry.getKey().methods.stream().filter(
        				m -> m.name.equals(methodInsn.name) && m.desc.equals(methodInsn.desc)).findFirst().orElse(null);
        			if(method != null && !reflectionReferences.contains(method))
        				reflectionReferences.add(method);
        		}
        		if(reflectionReferences.size() >= 4)
        			break;
        	}
        	for(MethodNode method : reflectionReferences)
        		entry.getKey().methods.remove(method);
        	entry.getKey().methods.remove(entry.getValue());
        }
        return count.get();
    }

	public static boolean isInitMethod(ClassNode classNode, MethodNode method)
	{
    	List<AbstractInsnNode> instrs = new ArrayList<>();
    	for(AbstractInsnNode ain : method.instructions.toArray())
    	{
    		if(ain.getOpcode() == -1)
    			continue;
    		instrs.add(ain);
    		if(instrs.size() >= 7)
    			break;
    	}
    	if(instrs.size() < 7)
    		return false;
    	int firstNum =  -1;
    	if(Utils.isInteger(instrs.get(0)))
    		firstNum = Utils.getIntValue(instrs.get(0));
    	if(firstNum == -1)
    		return false;
    	if(instrs.get(1).getOpcode() == Opcodes.ANEWARRAY && ((TypeInsnNode)instrs.get(1)).desc.equals("java/lang/String")
    		&& instrs.get(2).getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)instrs.get(2)).owner.equals(classNode.name)
    		&& Utils.isInteger(instrs.get(3)) && Utils.getIntValue(instrs.get(3)) == firstNum
    		&& instrs.get(4).getOpcode() == Opcodes.ANEWARRAY && ((TypeInsnNode)instrs.get(4)).desc.equals("java/lang/Object")
    		&& instrs.get(5).getOpcode() == Opcodes.DUP
    		&& instrs.get(6).getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)instrs.get(6)).owner.equals(classNode.name))
    		return true;
    	if(instrs.get(1).getOpcode() == Opcodes.NEWARRAY
    		&& instrs.get(2).getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)instrs.get(2)).owner.equals(classNode.name)
    		&& Utils.isInteger(instrs.get(3)) && Utils.getIntValue(instrs.get(3)) == firstNum
    		&& instrs.get(4).getOpcode() == Opcodes.ANEWARRAY && ((TypeInsnNode)instrs.get(4)).desc.equals("java/lang/Object")
    		&& instrs.get(5).getOpcode() == Opcodes.DUP
    		&& instrs.get(6).getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode)instrs.get(6)).owner.equals(classNode.name))
    		return true;
    	return false;
	}
    
    public static AbstractInsnNode getObjectList(MethodNode method)
	{
    	List<AbstractInsnNode> instrs = new ArrayList<>();
    	for(AbstractInsnNode ain : method.instructions.toArray())
    	{
    		if(ain.getOpcode() == -1)
    			continue;
    		instrs.add(ain);
    		if(instrs.size() >= 7)
    			break;
    	}
    	return instrs.get(6);
	}
    
    public static boolean isOtherInitMethod(ClassNode classNode, MethodNode method, FieldInsnNode fieldInsn)
	{
    	List<AbstractInsnNode> instrs = new ArrayList<>();
    	for(AbstractInsnNode ain : method.instructions.toArray())
    	{
    		if(ain.getOpcode() == -1)
    			continue;
    		instrs.add(ain);
    		if(instrs.size() >= 3)
    			break;
    	}
    	if(instrs.size() < 3)
    		return false;
    	if(instrs.get(0).getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode)instrs.get(0)).desc.equals(fieldInsn.desc)
    		 && ((FieldInsnNode)instrs.get(0)).name.equals(fieldInsn.name) 
    		 && ((FieldInsnNode)instrs.get(0)).owner.equals(fieldInsn.owner)
    		 && instrs.get(1).getOpcode() == Opcodes.DUP
    		 && Utils.isInteger(instrs.get(2)))
    		return true;
    	return false;
	}

    public static AbstractInsnNode getFirstIndex(MethodNode method)
	{
    	List<AbstractInsnNode> instrs = new ArrayList<>();
    	for(AbstractInsnNode ain : method.instructions.toArray())
    	{
    		if(ain.getOpcode() == -1)
    			continue;
    		instrs.add(ain);
    		if(instrs.size() >= 3)
    			break;
    	}
    	return instrs.get(2);
	}
    
	private int findReflectionObfuscation() throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        classNodes().stream().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode current = methodNode.instructions.get(i);
                    if (current instanceof MethodInsnNode 
                    	&& !methodNode.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;"
                    		+ "Ljava/lang/String;Ljava/lang/invoke/MethodType;J)Ljava/lang/invoke/MethodHandle;")
                    	&& !methodNode.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MutableCallSite;"
                    		+ "Ljava/lang/String;Ljava/lang/invoke/MethodType;JJ)Ljava/lang/invoke/MethodHandle;")) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                        if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Method;") || methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Field;")) {
                            count.incrementAndGet();
                        }
                    }else if (current instanceof InvokeDynamicInsnNode) {
                    	InvokeDynamicInsnNode invokeDynamicInsnNode = (InvokeDynamicInsnNode) current;
                    	if (invokeDynamicInsnNode.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
                    		count.incrementAndGet();
                    	}
                    }
                }
            });
        });
        return count.get();
    }
}
