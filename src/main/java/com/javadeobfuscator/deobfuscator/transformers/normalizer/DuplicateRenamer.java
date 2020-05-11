/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;

@TransformerConfig.ConfigOptions(configClass = DuplicateRenamer.Config.class)
public class DuplicateRenamer extends AbstractNormalizer<DuplicateRenamer.Config>
{
	private static final String[] ILLEGAL_WINDOWS_CHARACTERS = {
		"aux", "con", "prn", "nul",
		"com0", "com1", "com2", "com3", 
		"com4", "com5", "com6", "com7",
		"com8", "com9", "lpt0", "lpt1",
		"lpt2", "lpt3", "lpt4", "lpt5",
		"lpt6", "lpt7", "lpt8", "lpt9",
	};
	private static final String[] ILLEGAL_JAVA_NAMES = {
		"abstract", "assert", "boolean", "break", 
		"byte", "case", "catch", "char", "class", 
		"const", "continue", "default", "do", 
		"double", "else", "enum", "extends", 
		"false", "final", "finally", "float", 
		"for", "goto", "if", "implements", 
		"import", "instanceof", "int", "interface", 
		"long", "native", "new", "null", 
		"package", "private", "protected", "public", 
		"return", "short", "static", "strictfp", 
		"super", "switch", "synchronized", "this", 
		"throw", "throws", "transient", "true", 
		"try", "void", "volatile", "while"};

	@Override
	public void remap(CustomRemapper remapper)
	{
		//We must load the entire class tree so subclasses are correctly counted
        classNodes().forEach(classNode -> {
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTry = new LinkedList<>();
            toTry.add(tree.thisClass);
            while (!toTry.isEmpty()) {
                String t = toTry.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    toTry.addAll(ct.parentClasses);
                    toTry.addAll(ct.subClasses);
                }
            }
        });
		// Aggressive class name obfuscation
		Map<String, AtomicInteger> names = new HashMap<>();
		classNodes().forEach(classNode -> {
			String classNodeName = classNode.name;
			if(!names.containsKey(classNodeName.toLowerCase(Locale.ROOT)))
			{
				names.put(classNodeName.toLowerCase(Locale.ROOT),
					new AtomicInteger());
				if(getConfig().renameIllegalNames())
				{
					String rawClassName = classNodeName;
					if(classNode.name.contains("/"))
						rawClassName = classNodeName.substring(classNode.name.lastIndexOf('/') + 1);
					boolean illegal = false;
					for(String s : ILLEGAL_WINDOWS_CHARACTERS)
						if(s.equals(rawClassName.toLowerCase(Locale.ROOT)))
						{
							illegal = true;
							break;
						}
					for(String s : ILLEGAL_JAVA_NAMES)
						if(s.equals(rawClassName))
						{
							illegal = true;
							break;
						}
					if(illegal)
					{
						String newName = classNodeName;
						do {
							newName = newName + "_"
								+ names.get(classNodeName.toLowerCase(Locale.ROOT))
								.getAndIncrement();
						} while (!remapper.map(classNode.name, newName));
					}
				}
			}else
			{
				String newName = classNodeName;
				do {
					newName = newName + "_"
						+ names.get(classNodeName.toLowerCase(Locale.ROOT))
						.getAndIncrement();
				} while (!remapper.map(classNode.name, newName));
			}
		});
		
		List<MethodArgs> methodNames = new ArrayList<>();
		// Aggressive method name obfuscation (same method name + same params)
		AtomicInteger methodIdNow = new AtomicInteger();
        classNodes().forEach(classNode -> {
            Set<String> allClasses = new HashSet<>();
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTry = new LinkedList<>();
            toTry.add(tree.thisClass);
            while (!toTry.isEmpty()) {
                String t = toTry.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.parentClasses);
                    toTry.addAll(ct.parentClasses);
                    allClasses.addAll(ct.subClasses);
                    toTry.addAll(ct.subClasses);
                }
            }
            LinkedList<String> toTryParent = new LinkedList<>();
            LinkedList<String> toTryChild = new LinkedList<>();
            toTryParent.addAll(tree.parentClasses);
            toTryChild.addAll(tree.subClasses);
            while (!toTryParent.isEmpty()) {
                String t = toTryParent.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.parentClasses);
                    toTryParent.addAll(ct.parentClasses);
                }
            }
            while (!toTryChild.isEmpty()) {
                String t = toTryChild.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.subClasses);
                    toTryChild.addAll(ct.subClasses);
                }
            }
            allClasses.remove(tree.thisClass);

            for (MethodNode methodNode : new ArrayList<>(classNode.methods)) {
                if (methodNode.name.startsWith("<"))
                    continue;
                if (methodNode.name.equals("main"))
                    continue;
                final Map<Map.Entry<ClassNode, MethodNode>, Boolean> allMethodNodes = new HashMap<>();
                final Type methodType = Type.getReturnType(methodNode.desc);
                final AtomicBoolean isLibrary = new AtomicBoolean(false);
                if (methodType.getSort() != Type.OBJECT && methodType.getSort() != Type.ARRAY) {
                    if (methodType.getSort() == Type.METHOD) {
                        throw new IllegalArgumentException("Did not expect method");
                    }
                    allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
                        boolean foundSimilar = false;
                        boolean equals = false;
                        MethodNode equalsMethod = null;
                        for (MethodNode method : node.methods) {
                            Type thisType = Type.getMethodType(methodNode.desc);
                            Type otherType = Type.getMethodType(method.desc);
                            if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
                                foundSimilar = true;
                                if (thisType.getReturnType().getSort() == otherType.getReturnType().getSort()) {
                                    equals = true;
                                    equalsMethod = method;
                                }
                            }
                        }
                        if (foundSimilar && equals) {
                        	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
                        } else {
                            allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                        }
                    });
                } else if (methodType.getSort() == Type.ARRAY) {
                    Type elementType = methodType.getElementType();
                    int layers = 1;
                    AtomicBoolean passed = new AtomicBoolean();
                    while(true)
                    {
                    	if(passed.get())
                    	{
                    		layers++;
                    		passed.set(false);
                    	}
	                    if (elementType.getSort() == Type.OBJECT) {
	                        String parent = elementType.getInternalName();
	                        final int layersF = layers;
	                        allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
	                            boolean foundSimilar = false;
	                            boolean equals = false;
	                            MethodNode equalsMethod = null;
	                            for (MethodNode method : node.methods) {
	                                Type thisType = Type.getMethodType(methodNode.desc);
	                                Type otherType = Type.getMethodType(method.desc);
	                                if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
	                                	Type otherEleType = otherType.getReturnType();
	                                	if(toTryParent.contains(node.name) && otherEleType.getSort() == Type.OBJECT
	                                    	&& otherEleType.getInternalName().equals("java/lang/Object"))
	                                    {
	                                    	//Passed (superclass has Object return)
	                                    	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
	                                    	break;
	                                    }
	                                	if(otherEleType.getSort() != Type.ARRAY || otherEleType.getDimensions() < layersF)
	                                		break;
	                                	for(int i = 0; i < layersF; i++)
	                                		otherEleType = otherEleType.getElementType();
	                                    if (otherEleType.getSort() == Type.OBJECT) {
	                                        foundSimilar = true;
	                                        String child = otherEleType.getInternalName();
	                                        this.getDeobfuscator().assureLoaded(parent);
	                                        this.getDeobfuscator().assureLoaded(child);
	                                        if ((toTryChild.contains(node.name) && this.getDeobfuscator().isSubclass(parent, child))
	                                        	|| (toTryParent.contains(node.name) && this.getDeobfuscator().isSubclass(child, parent))
	                                        	|| child.equals(parent)) {
	                                            equals = true;
	                                            equalsMethod = method;
	                                        }
	                                    }
	                                }
	                            }
	                            if (foundSimilar && equals) {
	                            	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
	                            } else {
	                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
	                            }
	                        });
	                        break;
	                    } else if (elementType.getSort() != Type.ARRAY) {
	                    	final int layersF = layers;
	                        allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
	                            boolean foundSimilar = false;
	                            boolean equals = false;
	                            MethodNode equalsMethod = null;
	                            for (MethodNode method : node.methods) {
	                                Type thisType = Type.getMethodType(methodNode.desc);
	                                Type otherType = Type.getMethodType(method.desc);
	                                if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
	                                    foundSimilar = true;
	                                    Type otherEleType = otherType.getReturnType();
	                                    if(toTryParent.contains(node.name) && otherEleType.getSort() == Type.OBJECT
	                                    	&& otherEleType.getInternalName().equals("java/lang/Object"))
	                                    {
	                                    	//Passed (superclass has Object return)
	                                    	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
	                                    	break;
	                                    }
	                                	if(otherEleType.getSort() != Type.ARRAY || otherEleType.getDimensions() < layersF)
	                                		break;
	                                	for(int i = 0; i < layersF; i++)
	                                		otherEleType = otherEleType.getElementType();
	                                    if (elementType.getSort() == otherEleType.getSort()) {
	                                        equals = true;
	                                        equalsMethod = method;
	                                    }
	                                }
	                            }
	                            if (foundSimilar && equals) {
	                            	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
	                            } else {
	                                allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
	                            }
	                        });
	                        break;
	                    } else {
	                    	int layersF = layers;
	                    	allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
	                            MethodNode equalsMethod = null;
	                            for (MethodNode method : node.methods) {
	                                Type thisType = Type.getMethodType(methodNode.desc);
	                                Type otherType = Type.getMethodType(method.desc);
	                                if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
	                                    Type otherEleType = otherType.getReturnType();
	                                	for(int i = 0; i < layersF; i++)
	                                		otherEleType = otherEleType.getElementType();
	                                    if (otherEleType.getSort() == Type.ARRAY)
	                                    {
	                                    	//Continue checking element
	                                    	passed.set(true);
	                                    	continue;
	                                    }else if(toTryParent.contains(node.name) && otherEleType.getSort() == Type.OBJECT
	                                    	&& otherEleType.getInternalName().equals("java/lang/Object"))
	                                    {
	                                    	//Passed (superclass has Object return)
	                                    	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
	                                    	break;
	                                    }else
	                                    	//Fail
	                                    	break;
	                                }
	                            }
	                        });
	                    }
                    }
                } else if (methodType.getSort() == Type.OBJECT) {
                    String parent = methodType.getInternalName();
                    allClasses.stream().map(name -> this.getDeobfuscator().assureLoaded(name)).forEach(node -> {
                        boolean foundSimilar = false;
                        boolean equals = false;
                        MethodNode equalsMethod = null;
                        for (MethodNode method : node.methods) {
                            Type thisType = Type.getMethodType(methodNode.desc);
                            Type otherType = Type.getMethodType(method.desc);
                            if (methodNode.name.equals(method.name) && Arrays.equals(thisType.getArgumentTypes(), otherType.getArgumentTypes())) {
                                if (otherType.getReturnType().getSort() == Type.OBJECT) {
                                    foundSimilar = true;
                                    String child = otherType.getReturnType().getInternalName();
                                    this.getDeobfuscator().assureLoaded(parent);
                                    this.getDeobfuscator().assureLoaded(child);
                                    if ((toTryChild.contains(node.name) && this.getDeobfuscator().isSubclass(parent, child))
                                    	|| (toTryParent.contains(node.name) && this.getDeobfuscator().isSubclass(child, parent))
                                    	|| child.equals(parent)) {
                                        equals = true;
                                        equalsMethod = method;
                                    }
                                }else if (toTryParent.contains(node.name) && otherType.getSort() == Type.ARRAY)
                                {
                                	//Arrays extend object
                                	foundSimilar = true;
                                	equals = true;
                                	equalsMethod = method;
                                }
                            }
                        }
                        if (foundSimilar && equals) {
                        	allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, equalsMethod), true);
                        } else {
                            allMethodNodes.put(new AbstractMap.SimpleEntry<>(node, methodNode), false);
                        }
                    });
                }

                allMethodNodes.forEach((key, value) -> {
                    if (getDeobfuscator().isLibrary(key.getKey()) && value) {
                        isLibrary.set(true);
                    }
                });

                if (!isLibrary.get()) {
                	if(!methodNames.contains(
						new MethodArgs(classNode.name, methodNode.name, Type.getArgumentTypes(methodNode.desc))))
                	{
						methodNames.add(new MethodArgs(classNode.name, 
							methodNode.name, Type.getArgumentTypes(methodNode.desc)));
						if(getConfig().renameIllegalNames())
						{
							boolean illegal = false;
							for(String s : ILLEGAL_JAVA_NAMES)
								if(s.equals(methodNode.name))
								{
									illegal = true;
									break;
								}
							if(illegal)
								while(true)
								{
									String name = methodNode.name + "_"
										+ methodIdNow.getAndIncrement();
									if(remapper.mapMethodName(classNode.name,
										methodNode.name, methodNode.desc, name,
										false))
									{
										allMethodNodes.keySet().forEach(ent -> {
											remapper.mapMethodName(
												ent.getKey().name,
												ent.getValue().name,
												ent.getValue().desc, name, true);
										});
										break;
									}
								}
						}
                	}else if(!remapper.methodMappingExists(classNode.name,
						methodNode.name, methodNode.desc))
					{
						while(true)
						{
							String name = methodNode.name + "_"
								+ methodIdNow.getAndIncrement();
							if(remapper.mapMethodName(classNode.name,
								methodNode.name, methodNode.desc, name,
								false))
							{
								allMethodNodes.keySet().forEach(ent -> {
									remapper.mapMethodName(
										ent.getKey().name,
										ent.getValue().name,
										ent.getValue().desc, name, true);
								});
								break;
							}
						}
					}
					//Check for superclass/interface method clashes
					Context context = new Context(new DelegatingProvider());
					context.dictionary = classpath;
					JavaClass clazz = new JavaClass(classNode.name, context);
					List<JavaMethod> conflicters = new ArrayList<>();
					while(clazz != null && !getDeobfuscator().isLibrary(clazz.getClassNode()))
                	{
						for(JavaMethod method : clazz.getDeclaredMethods())
							if(method.getName().equals(methodNode.name))
							{
								Type[] types = Type.getArgumentTypes(method.getDesc());
								Type[] types2 = Type.getArgumentTypes(methodNode.desc);
								boolean typesEqual = true;
								boolean returnTypesEqual = false;
								if(types.length == types2.length)
								{
									for(int i = 0; i < types.length; i++)
										if(types[i].getSort() == Type.OBJECT && types2[i].getSort() == Type.OBJECT 
										&& !types[i].getInternalName().equals(types2[i].getInternalName()))
											typesEqual = false;
										else if(!types2[i].equals(types[i]))
											typesEqual = false;
								}else
									typesEqual = false;
								String returnType = 
									method.getDesc().substring(method.getDesc().indexOf(')') + 1, method.getDesc().length());
								String returnType2 = 
									methodNode.desc.substring(methodNode.desc.indexOf(')') + 1, methodNode.desc.length());
								if(returnType.equals(returnType2))
									returnTypesEqual = true;
								if(typesEqual && !returnTypesEqual && !remapper.methodMappingExists(classNode.name,
									methodNode.name, methodNode.desc))
									//Adds the conflicter to a mapping
									conflicters.add(method);
							}
                		clazz = clazz.getSuperclass();
                	}
					conflicters.addAll(getInterfaceConflicters(new JavaMethod(new JavaClass(classNode.name, context), methodNode), new JavaClass(classNode.name, context), remapper));
					if(conflicters.size() > 0)
					{
						int id = methodIdNow.getAndIncrement();
						while(true)
						{
							String name = methodNode.name + "_" + id;
							if(remapper.mapMethodName(classNode.name,
								methodNode.name, methodNode.desc, name,
								false))
							{
								allMethodNodes.keySet().forEach(ent -> {
									remapper.mapMethodName(
										ent.getKey().name,
										ent.getValue().name,
										ent.getValue().desc, name, true);
								});
								break;
							}
						}
					}
                }
            }
        });
		// Aggressive field name obfuscation (same field name)
        classNodes().forEach(classNode -> {
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> allClasses = new HashSet<>();
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTry = new LinkedList<>();
            toTry.add(tree.thisClass);
            while (!toTry.isEmpty()) {
                String t = toTry.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.parentClasses);
                    allClasses.addAll(ct.subClasses);
                    toTry.addAll(ct.parentClasses);
                    toTry.addAll(ct.subClasses);
                }
            }
            for (FieldNode fieldNode : classNode.fields) {
                List<String> references = new ArrayList<>();
                for (String possibleClass : allClasses) {
                    ClassNode otherNode = this.getDeobfuscator().assureLoaded(possibleClass);
                    boolean found = false;
                    for (FieldNode otherField : otherNode.fields) {
                        if (otherField.name.equals(fieldNode.name) && otherField.desc.equals(fieldNode.desc)) {
                            found = true;
                        }
                    }
                    if (!found) {
                        references.add(possibleClass);
                    }
                }
                if(!names.containsKey(classNode.name + fieldNode.name))
                {
					names.put(classNode.name + fieldNode.name, new AtomicInteger());
					if(getConfig().renameIllegalNames())
					{
						boolean illegal = false;
						for(String s : ILLEGAL_JAVA_NAMES)
							if(s.equals(fieldNode.name))
							{
								illegal = true;
								break;
							}
						if(illegal)
							while(true)
							{
								String newName = fieldNode.name + "_"
									+ names.get(
										classNode.name + fieldNode.name)
										.getAndIncrement();
								if(remapper.mapFieldName(classNode.name,
									fieldNode.name, fieldNode.desc, newName, false))
								{
									for(String s : references)
										remapper.mapFieldName(s, fieldNode.name,
											fieldNode.desc, newName, true);
									break;
								}
							}
					}
                }else if(!remapper.fieldMappingExists(classNode.name,
					fieldNode.name, fieldNode.desc))
				{
					while(true)
					{
						String newName = fieldNode.name + "_"
							+ names.get(
								classNode.name + fieldNode.name)
							.getAndIncrement();
						if(remapper.mapFieldName(classNode.name,
							fieldNode.name, fieldNode.desc, newName, false))
						{
							for(String s : references)
								remapper.mapFieldName(s, fieldNode.name,
									fieldNode.desc, newName, true);
							break;
						}
					}
				}
            }
        });
	}
	
	private Collection<JavaMethod> getInterfaceConflicters(JavaMethod method, JavaClass clazz, CustomRemapper remapper)
	{
		Collection<JavaMethod> toReturn = new ArrayList<>();
		if(getDeobfuscator().isLibrary(clazz.getClassNode()))
			return toReturn;
		for(JavaClass interf : clazz.getInterfaces())
			if(!getDeobfuscator().isLibrary(interf.getClassNode()))
			{
				for(JavaMethod m : interf.getDeclaredMethods())
					if(m.getName().equals(method.getName()))
					{
						Type[] types = Type.getArgumentTypes(m.getDesc());
						Type[] types2 = Type.getArgumentTypes(method.getDesc());
						boolean typesEqual = true;
						boolean returnTypesEqual = false;
						if(types.length == types2.length)
						{
							for(int i = 0; i < types.length; i++)
								if(types[i].getSort() == Type.OBJECT && types2[i].getSort() == Type.OBJECT 
								&& !types[i].getInternalName().equals(types2[i].getInternalName()))
									typesEqual = false;
								else if(!types2[i].equals(types[i]))
									typesEqual = false;
						}else
							typesEqual = false;
						String returnType = 
							m.getDesc().substring(m.getDesc().indexOf(')') + 1, m.getDesc().length());
						String returnType2 = 
							method.getDesc().substring(method.getDesc().indexOf(')') + 1, method.getDesc().length());
						if(returnType.equals(returnType2))
							returnTypesEqual = true;
						if(typesEqual && !returnTypesEqual && !remapper.methodMappingExists(method.getOwner(),
							method.getName(), method.getDesc()))
							//Adds the conflicter to a mapping
							toReturn.add(m);
					}
				if(interf.getInterfaces() != null && interf.getInterfaces().length > 0)
					toReturn.addAll(getInterfaceConflicters(method, interf, remapper));
			}
		return toReturn;
	}
	
	private class MethodArgs
	{
		public final String clazz;
		public final String methodName;
		public final Type[] args;
		
		public MethodArgs(String clazz, String methodName, Type[] args)
		{
			this.clazz = clazz;
			this.methodName = methodName;
			this.args = args;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if(!(o instanceof MethodArgs))
				return false;
			MethodArgs other = (MethodArgs)o;
			if(!clazz.equals(other.clazz))
				return false;
			if(!methodName.equals(other.methodName))
				return false;
			if(other.args.length != args.length)
				return false;
			for(int i = 0; i < other.args.length; i++)
			{
				Type t = other.args[i];
				if(t.getSort() == Type.OBJECT && args[i].getSort() == Type.OBJECT 
					&& !t.getInternalName().equals(args[i].getInternalName()))
					return false;
				else if(!t.equals(args[i]))
					return false;
			}
			return true;
		}
		
		@Override
		public int hashCode()
		{
			int code = methodName.hashCode();
			code += clazz.hashCode();
			for(Type t : args)
				code += t.hashCode();
			return code;
		}
	}
	
	public static class Config extends AbstractNormalizer.Config 
	{
		private boolean renameIllegalNames = false;
		
        public Config() 
        {
            super(DuplicateRenamer.class);
        }
        
        public boolean renameIllegalNames() 
        {
            return renameIllegalNames;
        }

        public void setRenameIllegalNames(boolean renameIllegalNames) 
        {
            this.renameIllegalNames = renameIllegalNames;
        }
    }
}
