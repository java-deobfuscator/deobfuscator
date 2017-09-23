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

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.Context;

import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class StringEncryptionTransformer extends Transformer {
    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() {
        System.out.println("[Stringer] [StringEncryptionTransformer] Starting");
        int count = count();
        System.out.println("[Stringer] [StringEncryptionTransformer] Found " + count + " encrypted strings");
        if (count > 0) {
            int decrypted = decrypt(count);
            System.out.println("[Stringer] [StringEncryptionTransformer] Decrypted " + decrypted + " encrypted strings");
            int cleanedup = cleanup();
            System.out.println("[Stringer] [StringEncryptionTransformer] Removed " + cleanedup + " decryption classes");
        }
        System.out.println("[Stringer] [StringEncryptionTransformer] Done");
    }

    private int cleanup() {
        AtomicInteger total = new AtomicInteger();
        Set<String> remove = new HashSet<>();
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            boolean method = false;
            boolean field = false;
            for (MethodNode node : classNode.methods) {
                if (node.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                    method = true;
                } else if (node.desc.equals("(Ljava/io/InputStream;)V")) { //Don't delete resource decryptors yet
                    method = false;
                    break;
                } else if ((node.desc.equals("(Ljava/lang/Object;I)Ljava/lang/String;") 
                	|| node.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;")) && classNode.superName.equals("java/lang/Thread")) {
                    method = true;
                }
            }
            for (FieldNode node : classNode.fields) {
                if (node.desc.equals("[Ljava/lang/Object;")) {
                    field = true;
                } else if (node.desc.equals("[Ljava/math/BigInteger")) {
                    field = true;
                }
            }
            if (method && field) {
                remove.add(classNode.name);
            }
        });
        remove.forEach(str -> {
            total.incrementAndGet();
            classes.remove(str);
            classpath.remove(str);
        });
        return total.get();
    }

    private int count() {
        AtomicInteger count = new AtomicInteger(0);
        for (WrappedClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                    	//Counts stringer 3.1.0+ encryption
                    	if(currentInsn instanceof MethodInsnNode && currentInsn.getPrevious() != null
                        	&& currentInsn.getPrevious().getOpcode() == Opcodes.IOR
                        	&& currentInsn.getPrevious().getPrevious() != null
                        	&& Utils.isNumber(currentInsn.getPrevious().getPrevious())
                        	&& currentInsn.getPrevious().getPrevious().getPrevious() != null
                        	&& currentInsn.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.ISHL
                        	&& currentInsn.getPrevious().getPrevious().getPrevious().getPrevious() != null
                        	&& Utils.isNumber(currentInsn.getPrevious().getPrevious().getPrevious().getPrevious())
                        	&& (methodInsns.get(methodInsns.indexOf(currentInsn) - 16).getOpcode() == Opcodes.LDC
                        	|| methodInsns.get(methodInsns.indexOf(currentInsn) - 16).getOpcode() == Opcodes.GETSTATIC))
                    	{
                    		MethodInsnNode m = (MethodInsnNode)currentInsn;
                    		 String strCl = m.owner;
                             Type type = Type.getType(m.desc);
                             if (type.getArgumentTypes().length == 2 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                 ClassNode innerClassNode = classes.get(strCl).classNode;
                                 FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);
                                 if (signature != null) {
                                     MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                     if (decrypterNode != null) {
                                         count.getAndIncrement();
                                     }
                                 }
                             }
                    	}
                        if (currentInsn instanceof LdcInsnNode && currentInsn.getNext() instanceof MethodInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) currentInsn;
                            MethodInsnNode m = (MethodInsnNode) ldc.getNext();
                            if (ldc.cst instanceof String) {
                                String strCl = m.owner;
                                Type type = Type.getType(m.desc);
                                if (type.getArgumentTypes().length == 1 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);
                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                            count.getAndIncrement();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return count.get();
    }

    private int decrypt(int expected) {
        AtomicInteger total = new AtomicInteger();
        final boolean[] alerted = new boolean[100];

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

        Map<AbstractInsnNode, String> enhanced = new HashMap<>();
        
        for (WrappedClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.classNode.methods) {
            	//Prevents static finals from being deobfuscated again
            	Map<AbstractInsnNode, Object> decryptedStaticFinals = new HashMap<>();
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                        //Latest Stringer (3.1.0+)
                    	if(currentInsn instanceof MethodInsnNode && currentInsn.getPrevious() != null
                        	&& currentInsn.getPrevious().getOpcode() == Opcodes.IOR
                        	&& currentInsn.getPrevious().getPrevious() != null
                        	&& Utils.isNumber(currentInsn.getPrevious().getPrevious())
                        	&& currentInsn.getPrevious().getPrevious().getPrevious() != null
                        	&& currentInsn.getPrevious().getPrevious().getPrevious().getOpcode() == Opcodes.ISHL
                        	&& currentInsn.getPrevious().getPrevious().getPrevious().getPrevious() != null
                        	&& Utils.isNumber(currentInsn.getPrevious().getPrevious().getPrevious().getPrevious())
                        	&& (methodInsns.get(methodInsns.indexOf(currentInsn) - 16).getOpcode() == Opcodes.LDC
                        	|| methodInsns.get(methodInsns.indexOf(currentInsn) - 16).getOpcode() == Opcodes.GETSTATIC))
                        {
                    		//If true, we deob the string at clinit 
                    		boolean isStaticFinal = false;
                    		FieldInsnNode fieldNode = null;
                    		MethodNode clinit = null;
                    		LdcInsnNode clinitLdc = null;
                			boolean alreadyDecrypted = false;
                    		if(methodInsns.get(methodInsns.indexOf(currentInsn) - 16).getOpcode() != Opcodes.LDC)
                    		{
                    			fieldNode = (FieldInsnNode)methodInsns.get(methodInsns.indexOf(currentInsn) - 16);
                    			String field = fieldNode.name;
                    			clinit =
                    				classNode.classNode.methods
											.stream()
											.filter(
												mn -> mn.name
													.equals(
														"<clinit>"))
											.findFirst()
											.orElse(null);
                    			if(clinit != null)
                    				for(AbstractInsnNode ain : clinit.instructions.toArray())
                    					if(ain.getOpcode() == Opcodes.PUTSTATIC
                    						&& ((FieldInsnNode)ain).name.equals(field)
                    						&& ain.getPrevious() != null && ain.getPrevious().getOpcode() == Opcodes.LDC)
                    					{
                    						if(decryptedStaticFinals.containsKey(ain))
                    						{
                    							alreadyDecrypted = true;
                    							break;
                    						}
                    						clinitLdc = (LdcInsnNode)ain.getPrevious();
                    						methodInsns.set(fieldNode, new LdcInsnNode(((LdcInsnNode)ain.getPrevious()).cst));
                    						decryptedStaticFinals.put(ain, ((LdcInsnNode)ain.getPrevious()).cst);
                    						isStaticFinal = true;
                    						break;
                    					}
                    		}
                    		if(alreadyDecrypted)
                    		{
                    			//String decryption things removed, getstatic stays
	                        	for(int i = insnIndex; i > insnIndex - 16; i--)
	                        		methodInsns.remove(methodInsns.get(i));
                    			continue;
                    		}
                    		AbstractInsnNode ldcNode = methodInsns.get(methodInsns.indexOf(currentInsn) - 16);
							MethodInsnNode m = (MethodInsnNode)currentInsn;
							//There are 17 instructions to keep before we get the result
							//Remove everything before and after
							List<AbstractInsnNode> before = new ArrayList<>();
							List<AbstractInsnNode> after = new ArrayList<>();
							//Added in reverse (both)
							//Remove before
                        	for(int i = insnIndex - 17; i >= 0; i--)
	                        	before.add(methodInsns.get(i));
                        	//Remove after
                        	for(int i = insnIndex + 1; i < methodInsns.size(); i++)
                        		after.add(methodInsns.get(i));
                        	for(AbstractInsnNode ain : before)
                        		methodInsns.remove(ain);
                        	for(AbstractInsnNode ain : after)
                        		methodInsns.remove(ain);
                        	Collections.reverse(before);
                        	Collections.reverse(after);
                        	//We execute the method and get the result
							String strCl = m.owner;
							Type type = Type.getType(m.desc);
							if(type.getArgumentTypes().length == 2
								&& type.getReturnType().getDescriptor()
									.equals("Ljava/lang/String;")
								&& classes.containsKey(strCl))
							{
								ClassNode innerClassNode =
									classes.get(strCl).classNode;
								FieldNode signature =
									innerClassNode.fields.stream()
										.filter(fn -> fn.desc
											.equals("[Ljava/lang/Object;"))
										.findFirst().orElse(null);

								if(signature != null)
								{
									MethodNode decrypterNode =
										innerClassNode.methods.stream()
											.filter(mn -> mn.name
												.equals(m.name)
												&& mn.desc.equals(m.desc)
												&& Modifier
													.isStatic(mn.access))
											.findFirst().orElse(null);
									if(decrypterNode != null)
									{
			                        	Context context = new Context(provider);
										context.dictionary = classpath;
										context.push(
											classNode.classNode.name
												.replace('/', '.'),
											methodNode.name,
											classNode.constantPoolSize);
										context.file =
											deobfuscator.getFile();
										// Stringer3
										if(innerClassNode.superName
											.equals("java/lang/Thread"))
										{
											MethodNode clinitMethod =
												classes.get(strCl)
													.getClassNode().methods
														.stream()
														.filter(
															mn -> mn.name
																.equals(
																	"<clinit>"))
														.findFirst()
														.orElse(null);
											if(clinitMethod != null)
											{
												//We don't want to run anything else (removes everything above it)
                                            	AbstractInsnNode firstVaildCode = null;
                                            	for(int i = 0; i < clinitMethod.instructions.size(); i++)
                                            	{
                                            		AbstractInsnNode ain = clinitMethod.instructions.get(i);
                                            		if(ain.getOpcode() == Opcodes.PUTSTATIC
                                            			&& ((FieldInsnNode)ain).desc.contains("BigInteger")
                            							&& ain.getPrevious() != null
                            							&& ain.getPrevious().getOpcode() == Opcodes.ANEWARRAY
                            							&& ain.getPrevious().getPrevious() != null
                            							&& Utils.isNumber(ain.getPrevious().getPrevious()))
                                            		{
                            							firstVaildCode = ain.getPrevious().getPrevious();
                            							break;
                                            		}
                                            	}
                                            	List<AbstractInsnNode> removed = new ArrayList<>();
                                            	while(firstVaildCode != null && firstVaildCode.getPrevious() != null)
                                            	{
                                            		//Added in reverse order
                                            		removed.add(firstVaildCode.getPrevious());
                                            		clinitMethod.instructions.remove(firstVaildCode.getPrevious());
                                            	}
                                                MethodExecutor.execute(classes.get(strCl), clinitMethod, Collections.emptyList(), null, context);
                                                //Placed in reverse order, so no need to reverse the list
                                                for(AbstractInsnNode ain : removed)
                                                	clinitMethod.instructions.insert(ain);
											}
										}
			                        	//Works for all methods, even those who aren't supposed to return value
			                        	AbstractInsnNode returnNode;
			                        	methodInsns.add(returnNode = new InsnNode(Opcodes.ARETURN));
										String result = MethodExecutor.execute(classNode, methodNode, Arrays.asList(), null, context);
			                        	//Add all the instructions back
			                        	for(AbstractInsnNode ain : before)
			                        		methodInsns.insertBefore(ldcNode, ain);
			                        	for(AbstractInsnNode ain : after)
			                        		methodInsns.insert(methodInsns.get(insnIndex), ain);
			                        	methodInsns.remove(returnNode);
			                        	//The result is added to the method (node with insnIndex is not removed, it is replaced)
			                        	for(int i = insnIndex; i > insnIndex - 16; i--)
			                        		methodInsns.remove(methodInsns.get(i));
			                        	LdcInsnNode resultNode;
			                        	methodInsns.set(ldcNode, resultNode = new LdcInsnNode(result));
			                        	if(isStaticFinal)
			                        	{
			                        		methodInsns.set(resultNode, fieldNode);
			                        		clinit.instructions.set(clinitLdc, resultNode);
			                        	}
			                        	//Sets it back
			                        	insnIndex -= 16;
			                        	total.incrementAndGet();
			                        	int x = (int) ((total.get() * 1.0d / expected) * 100);
                                        if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                            System.out.println("[Stringer] [StringEncryptionTransformer] Done " + x + "%");
                                            alerted[x - 1] = true;
                                        }
									}
								}
							}
                        }
                        if (currentInsn instanceof LdcInsnNode && currentInsn.getNext() instanceof MethodInsnNode) {
                            LdcInsnNode ldc = (LdcInsnNode) currentInsn;
                            MethodInsnNode m = (MethodInsnNode) ldc.getNext();
                            if (ldc.cst instanceof String) {
                                String strCl = m.owner;
                                Type type = Type.getType(m.desc);
                                if (type.getArgumentTypes().length == 1 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);

                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                            Context context = new Context(provider);
                                            context.dictionary = classpath;
                                            context.push(classNode.classNode.name.replace('/', '.'), methodNode.name, classNode.constantPoolSize);
                                            context.file = deobfuscator.getFile();

                                            // Stringer3
                                            if (innerClassNode.superName.equals("java/lang/Thread")) {
                                                MethodNode clinitMethod = classes.get(strCl).getClassNode().methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                                if (clinitMethod != null) {
                                                	//We don't want to run anything else (removes everything above it)
                                                	AbstractInsnNode firstVaildCode = null;
                                                	for(int i = 0; i < clinitMethod.instructions.size(); i++)
                                                	{
                                                		AbstractInsnNode ain = clinitMethod.instructions.get(i);
                                                		if(ain.getOpcode() == Opcodes.PUTSTATIC
                                                			&& ((FieldInsnNode)ain).desc.contains("BigInteger")
                                							&& ain.getPrevious() != null
                                							&& ain.getPrevious().getOpcode() == Opcodes.ANEWARRAY
                                							&& ain.getPrevious().getPrevious() != null
                                							&& Utils.isNumber(ain.getPrevious().getPrevious()))
                                                		{
                                							firstVaildCode = ain.getPrevious().getPrevious();
                                							break;
                                                		}
                                                	}
                                                	List<AbstractInsnNode> removed = new ArrayList<>();
                                                	while(firstVaildCode != null && firstVaildCode.getPrevious() != null)
                                                	{
                                                		//Added in reverse order
                                                		removed.add(firstVaildCode.getPrevious());
                                                		clinitMethod.instructions.remove(firstVaildCode.getPrevious());
                                                	}
                                                    MethodExecutor.execute(classes.get(strCl), clinitMethod, Collections.emptyList(), null, context);
                                                    //Placed in reverse order, so no need to reverse the list
                                                    for(AbstractInsnNode ain : removed)
                                                    	clinitMethod.instructions.insert(ain);
                                                }
                                            }

                                            Object o = null;
                                            try {
                                                o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Collections.singletonList(new JavaObject(ldc.cst, "java/lang/String")), null, context);
                                            } catch (ArrayIndexOutOfBoundsException e) {
                                                enhanced.put(ldc, classNode.classNode.name + " " + methodNode.name);
                                            }
                                            if (o != null) {
                                                ldc.cst = (String) o;
                                                methodNode.instructions.remove(ldc.getNext());
                                                total.incrementAndGet();
                                                int x = (int) ((total.get() * 1.0d / expected) * 100);
                                                if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                                    System.out.println("[Stringer] [StringEncryptionTransformer] Done " + x + "%");
                                                    alerted[x - 1] = true;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (WrappedClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) currentInsn;
                        WrappedClassNode targetClassNode = classes.get(m.owner);
                        if (targetClassNode != null) {
                            MethodNode targetMethodNode = null;
                            for (MethodNode tempMethodNode : targetClassNode.classNode.methods) {
                                if (tempMethodNode.name.equals(m.name) && tempMethodNode.desc.equals(m.desc)) {
                                    targetMethodNode = tempMethodNode;
                                    break;
                                }
                            }
                            if (targetMethodNode != null) {
                                InsnList innerMethodInsns = targetMethodNode.instructions;
                                for (int innerInsnIndex = 0; innerInsnIndex < innerMethodInsns.size(); innerInsnIndex++) {
                                    AbstractInsnNode innerCurrentInsn = innerMethodInsns.get(innerInsnIndex);
                                    if (innerCurrentInsn instanceof LdcInsnNode && innerCurrentInsn.getNext() instanceof MethodInsnNode) {
                                        LdcInsnNode innerLdc = (LdcInsnNode) innerCurrentInsn;
                                        MethodInsnNode innerMethod = (MethodInsnNode) innerLdc.getNext();
                                        if (innerLdc.cst instanceof String) {
                                            String strCl = innerMethod.owner;
                                            if (innerMethod.desc.endsWith(")Ljava/lang/String;")) {
                                                if (enhanced.remove(innerLdc) != null) {
                                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(innerMethod.name) && mn.desc.equals(innerMethod.desc)).findFirst().orElse(null);
                                                    Context context = new Context(provider);
                                                    context.push(classNode.classNode.name.replace('/', '.'), methodNode.name, classNode.constantPoolSize);
                                                    context.push(targetClassNode.classNode.name.replace('/', '.'), targetMethodNode.name, targetClassNode.constantPoolSize);
                                                    context.dictionary = classpath;
                                                    Object o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Arrays.asList(new JavaObject(innerLdc.cst, "java/lang/String")), null, context);
                                                    innerLdc.cst = o;
                                                    targetMethodNode.instructions.remove(innerLdc.getNext());
                                                    total.incrementAndGet();
                                                    int x = (int) ((total.get() * 1.0d / expected) * 100);
                                                    if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                                        System.out.println("[Stringer] [StringEncryptionTransformer] Done " + x + "%");
                                                        alerted[x - 1] = true;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return total.get();
    }
}
