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

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

@TransformerConfig.ConfigOptions(configClass = DuplicateRenamer.Config.class)
public class DuplicateRenamer extends Transformer<TransformerConfig>
{
	@Override
	public boolean transform() throws Throwable
	{
		// Aggressive class name obfuscation
		CustomRemapper remapper = new CustomRemapper();
		Map<String, AtomicInteger> names = new HashMap<>();
		List<String> renamed = new ArrayList<>();
		classNodes().stream().map(WrappedClassNode::getClassNode)
			.forEach(classNode -> {
				String classNodeName = classNode.name;
				if(!names.containsKey(classNodeName.toLowerCase(Locale.ROOT)))
					names.put(classNodeName.toLowerCase(Locale.ROOT),
						new AtomicInteger());
				else
					while(true)
					{
						String newName = classNodeName + "_"
							+ names.get(classNodeName.toLowerCase(Locale.ROOT))
								.getAndIncrement();
						if(remapper.map(classNode.name, newName))
						{
							renamed.add(classNode.name);
							break;
						}
					}
			});
		
		Map<String, WrappedClassNode> updated = new HashMap<>();
		Set<String> removed = new HashSet<>();
		
		classNodes().forEach(wr -> {
			ClassNode newNode = new ClassNode();
			RemappingClassAdapter remap =
				new RemappingClassAdapter(newNode, remapper);
			if(renamed.contains(wr.classNode.name))
				removed.add(wr.classNode.name);
			wr.classNode.accept(remap);
			wr.classNode = newNode;
			updated.put(newNode.name, wr);
		});

		classes.putAll(updated);
		classpath.putAll(updated);
		removed.forEach(classes::remove);
		removed.forEach(classpath::remove);
		getDeobfuscator().resetHierachy();
		getDeobfuscator().loadHierachy();
		List<MethodArgs> methodNames = new ArrayList<>();
		// Aggressive method name obfuscation (same method name + same params)
		AtomicInteger methodIdNow = new AtomicInteger();
		classNodes().stream().map(WrappedClassNode::getClassNode)
			.forEach(classNode -> {
				Set<String> allClasses = new HashSet<>();
				ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
				Set<String> tried = new HashSet<>();
				LinkedList<String> toTry = new LinkedList<>();
				toTry.add(tree.thisClass);
				while(!toTry.isEmpty())
				{
					String t = toTry.poll();
					if(tried.add(t) && !t.equals("java/lang/Object"))
					{
						this.getDeobfuscator().assureLoaded(t);
						ClassTree ct = this.getDeobfuscator().getClassTree(t);
						allClasses.add(t);
						allClasses.addAll(ct.parentClasses);
						toTry.addAll(ct.parentClasses);
						allClasses.addAll(ct.subClasses);
						toTry.addAll(ct.subClasses);
					}
				}
				allClasses.remove(tree.thisClass);

				for(MethodNode methodNode : new ArrayList<>(classNode.methods))
				{
					if(methodNode.name.startsWith("<"))
						continue;
					if(methodNode.name.equals("main"))
						continue;
					final Map<Map.Entry<ClassNode, MethodNode>, Boolean> allMethodNodes =
						new HashMap<>();
					final Type methodType = Type.getReturnType(methodNode.desc);
					final AtomicBoolean isLibrary = new AtomicBoolean(false);
					if(methodType.getSort() != Type.OBJECT
						&& methodType.getSort() != Type.ARRAY)
					{
						if(methodType.getSort() == Type.METHOD)
						{
							throw new IllegalArgumentException(
								"Did not expect method");
						}
						allClasses.stream()
							.map(name -> this.getDeobfuscator().assureLoaded(name))
							.forEach(node -> {
								boolean foundSimilar = false;
								boolean equals = false;
								MethodNode equalsMethod = null;
								for(MethodNode method : node.methods)
								{
									Type thisType =
										Type.getMethodType(methodNode.desc);
									Type otherType =
										Type.getMethodType(method.desc);
									if(methodNode.name.equals(method.name)
										&& Arrays.equals(
											thisType.getArgumentTypes(),
											otherType.getArgumentTypes()))
									{
										foundSimilar = true;
										if(thisType.getReturnType()
											.getSort() == otherType
												.getReturnType().getSort())
										{
											equals = true;
											equalsMethod = method;
										}
									}
								}
								if(foundSimilar)
								{
									if(equals)
									{
										allMethodNodes.put(
											new AbstractMap.SimpleEntry<>(node,
												equalsMethod),
											true);
									}
								}else
								{
									allMethodNodes
										.put(new AbstractMap.SimpleEntry<>(node,
											methodNode), false);
								}
							});
					}else if(methodType.getSort() == Type.ARRAY)
					{
						Type elementType = methodType.getElementType();
						if(elementType.getSort() == Type.OBJECT)
						{
							String parent = elementType.getInternalName();
							allClasses.stream().map(
								name -> this.getDeobfuscator().assureLoaded(name))
								.forEach(node -> {
									boolean foundSimilar = false;
									boolean equals = false;
									MethodNode equalsMethod = null;
									for(MethodNode method : node.methods)
									{
										Type thisType =
											Type.getMethodType(methodNode.desc);
										Type otherType =
											Type.getMethodType(method.desc);
										if(methodNode.name.equals(method.name)
											&& Arrays.equals(
												thisType.getArgumentTypes(),
												otherType.getArgumentTypes()))
										{
											if(otherType.getReturnType()
												.getSort() == Type.OBJECT)
											{
												foundSimilar = true;
												String child =
													otherType.getReturnType()
														.getInternalName();
												this.getDeobfuscator()
													.assureLoaded(parent);
												this.getDeobfuscator()
													.assureLoaded(child);
												if(this.getDeobfuscator()
													.isSubclass(parent, child)
													|| this.getDeobfuscator()
														.isSubclass(child,
															parent))
												{
													equals = true;
													equalsMethod = method;
												}
											}
										}
									}
									if(foundSimilar)
									{
										if(equals)
										{
											allMethodNodes.put(
												new AbstractMap.SimpleEntry<>(
													node, equalsMethod),
												true);
										}
									}else
									{
										allMethodNodes.put(
											new AbstractMap.SimpleEntry<>(node,
												methodNode),
											false);
									}
								});
						}else
						{
							allClasses.stream().map(
								name -> this.getDeobfuscator().assureLoaded(name))
								.forEach(node -> {
									boolean foundSimilar = false;
									boolean equals = false;
									MethodNode equalsMethod = null;
									for(MethodNode method : node.methods)
									{
										Type thisType =
											Type.getMethodType(methodNode.desc);
										Type otherType =
											Type.getMethodType(method.desc);
										if(methodNode.name.equals(method.name)
											&& Arrays.equals(
												thisType.getArgumentTypes(),
												otherType.getArgumentTypes()))
										{
											foundSimilar = true;
											if(thisType.getReturnType()
												.getSort() == otherType
													.getReturnType().getSort())
											{
												equals = true;
												equalsMethod = method;
											}
										}
									}
									if(foundSimilar)
									{
										if(equals)
										{
											allMethodNodes.put(
												new AbstractMap.SimpleEntry<>(
													node, equalsMethod),
												true);
										}
									}else
									{
										allMethodNodes.put(
											new AbstractMap.SimpleEntry<>(node,
												methodNode),
											false);
									}
								});
						}
					}else if(methodType.getSort() == Type.OBJECT)
					{
						String parent = methodType.getInternalName();
						allClasses.stream()
							.map(name -> this.getDeobfuscator().assureLoaded(name))
							.forEach(node -> {
								boolean foundSimilar = false;
								boolean equals = false;
								MethodNode equalsMethod = null;
								for(MethodNode method : node.methods)
								{
									Type thisType =
										Type.getMethodType(methodNode.desc);
									Type otherType =
										Type.getMethodType(method.desc);
									if(methodNode.name.equals(method.name)
										&& Arrays.equals(
											thisType.getArgumentTypes(),
											otherType.getArgumentTypes()))
									{
										if(otherType.getReturnType()
											.getSort() == Type.OBJECT)
										{
											foundSimilar = true;
											String child =
												otherType.getReturnType()
													.getInternalName();
											this.getDeobfuscator()
												.assureLoaded(parent);
											this.getDeobfuscator()
												.assureLoaded(child);
											if(this.getDeobfuscator()
												.isSubclass(parent, child)
												|| this.getDeobfuscator()
													.isSubclass(child, parent))
											{
												equals = true;
												equalsMethod = method;
											}
										}
									}
								}
								if(foundSimilar)
								{
									if(equals)
									{
										allMethodNodes.put(
											new AbstractMap.SimpleEntry<>(node,
												equalsMethod),
											true);
									}else
									{
										allMethodNodes.put(
											new AbstractMap.SimpleEntry<>(node,
												methodNode),
											false);
									}
								}else
								{
									allMethodNodes
										.put(new AbstractMap.SimpleEntry<>(node,
											methodNode), false);
								}
							});
					}

					allMethodNodes.entrySet().forEach(ent -> {
	                    if (getDeobfuscator().isLibrary(ent.getKey().getKey()) && ent.getValue()) {
	                        isLibrary.set(true);
	                    }
	                });

					if(!isLibrary.get())
					{
						if(!methodNames.contains(
							new MethodArgs(classNode.name, methodNode.name, Type.getArgumentTypes(methodNode.desc))))
							methodNames.add(new MethodArgs(classNode.name, 
								methodNode.name, Type.getArgumentTypes(methodNode.desc)));
						else if(!remapper.methodMappingExists(classNode.name,
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

		classNodes().forEach(wr -> {
			ClassNode newNode = new ClassNode();
			RemappingClassAdapter remap =
				new RemappingClassAdapter(newNode, remapper);
			wr.classNode.accept(remap);
			wr.classNode = newNode;
		});
		// Aggressive field name obfuscation (same field name)
		classNodes().stream().map(WrappedClassNode::getClassNode)
			.forEach(classNode -> {
				names.clear();
				ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
				Set<String> allClasses = new HashSet<>();
				Set<String> tried = new HashSet<>();
				LinkedList<String> toTry = new LinkedList<>();
				toTry.add(tree.thisClass);
				while(!toTry.isEmpty())
				{
					String t = toTry.poll();
					if(tried.add(t) && !t.equals("java/lang/Object"))
					{
						ClassTree ct = this.getDeobfuscator().getClassTree(t);
						allClasses.add(t);
						allClasses.addAll(ct.parentClasses);
						allClasses.addAll(ct.subClasses);
						toTry.addAll(ct.parentClasses);
						toTry.addAll(ct.subClasses);
					}
				}
				for(FieldNode fieldNode : classNode.fields)
				{
					List<String> references = new ArrayList<>();
					for(String possibleClass : allClasses)
					{
						ClassNode otherNode =
							this.getDeobfuscator().assureLoaded(possibleClass);
						boolean found = false;
						for(FieldNode otherField : otherNode.fields)
						{
							if(otherField.name.equals(fieldNode.name)
								&& otherField.desc.equals(fieldNode.desc))
							{
								found = true;
							}
						}
						if(!found)
						{
							references.add(possibleClass);
						}
					}
					if(!names
						.containsKey(fieldNode.name))
						names.put(fieldNode.name,
							new AtomicInteger());
					else if(!remapper.fieldMappingExists(classNode.name,
						fieldNode.name, fieldNode.desc))
					{
						while(true)
						{
							String newName = fieldNode.name + "_"
								+ names
									.get(
										fieldNode.name)
									.getAndIncrement();
							if(remapper.mapFieldName(classNode.name,
								fieldNode.name, fieldNode.desc, newName, false))
							{
								for(String s : references)
								{
									remapper.mapFieldName(s, fieldNode.name,
										fieldNode.desc, newName, true);
								}
								break;
							}
						}
					}
				}
			});
		
		classNodes().forEach(wr -> {
			ClassNode newNode = new ClassNode();
			RemappingClassAdapter remap =
				new RemappingClassAdapter(newNode, remapper);
			wr.classNode.accept(remap);
			wr.classNode = newNode;
		});
		return true;
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
	
	public static class Config extends TransformerConfig {
        @JsonProperty(value = "mapping-file")
        private File mappingFile;

        public Config() {
            super(DuplicateRenamer.class);
        }

        public File getMappingFile() {
            return mappingFile;
        }

        public void setMappingFile(File mappingFile) {
            this.mappingFile = mappingFile;
        }
    }
}
