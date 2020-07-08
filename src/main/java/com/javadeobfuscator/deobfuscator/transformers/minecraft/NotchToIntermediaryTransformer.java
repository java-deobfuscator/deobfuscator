package com.javadeobfuscator.deobfuscator.transformers.minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.transformers.normalizer.AbstractNormalizer;
import com.javadeobfuscator.deobfuscator.transformers.normalizer.CustomRemapper;

@TransformerConfig.ConfigOptions(configClass = NotchToIntermediaryTransformer.Config.class)
public class NotchToIntermediaryTransformer extends AbstractNormalizer<NotchToIntermediaryTransformer.Config>
{
    @Override
    public void remap(CustomRemapper remapper)
    {
    	try
    	{
    		getDeobfuscator().assureLoaded("net/minecraft/client/main/Main");
    	}catch(NoClassInPathException e)
    	{
    		System.out.println("[NotchToIntermediaryTransformer] Obfuscated Minecraft jar not detected, put it as a library for best results!");
    	}
    	File dir = new File(System.getProperty("user.dir"));
    	File tiny = new File(dir, "mappings.tiny");
    	if(!tiny.exists())
    	{
    		System.out.println("[NotchToIntermediaryTransformer] You must put mappings.tiny in the same folder as deobfuscator.jar!");
    		return;
    	}
    	
    	Map<String, String> classMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> fieldMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> methodMappings = new HashMap<>();
    	
    	boolean rev = getConfig().isReverse();
    	//Read classes first
    	try(BufferedReader reader = new BufferedReader(new FileReader(tiny)))
    	{
    		reader.readLine();//Skip first line
    		String line;
    		while((line = reader.readLine()) != null)
    		{
    			if(line.startsWith("#"))
    				continue;
    			String[] map = line.split("\\s+");
    			if(map.length < 3)
    				throw new RuntimeException("Unexpected mapping");
        		if(map[0].equals("CLASS"))
        		{
        			if(map.length != 3)
        				throw new RuntimeException("Unexpected class mapping");
        			if(!map[1].equals(map[2]))
        				classMappings.put(rev ? map[2] : map[1], rev ? map[1] : map[2]);
        		}
    		}
    	}catch(IOException e)
		{
    		System.out.println("[NotchToIntermediaryTransformer] File read failed, are you sure the .tiny file has not been tampered?");
			e.printStackTrace();
			return;
		}
    	try(BufferedReader reader = new BufferedReader(new FileReader(tiny)))
    	{
    		reader.readLine();//Skip first line
    		String line;
    		while((line = reader.readLine()) != null)
    		{
    			if(line.startsWith("#"))
    				continue;
    			String[] map = line.split("\\s+");
    			if(map.length < 3)
    				throw new RuntimeException("Unexpected mapping");
        		String className;
        		switch(map[0])
        		{
        			case "CLASS":
        				break;
        			case "METHOD":
        				if(map.length != 5)
	        				throw new RuntimeException("Unexpected method mapping");
        				className = map[1];
        				if(rev)
        					for(Entry<String, String> entry : classMappings.entrySet())
        						if(entry.getValue().equals(className))
        						{
        							className = entry.getKey();
        							break;
        						}
        				String methodName = map[3];
        				String mappedMethod = map[4];
        				if(!methodName.equals(mappedMethod))
        				{
        					methodMappings.putIfAbsent(className, new HashMap<>());
        					String desc = map[2];
        					if(rev)
        					{
        						Type[] args = Type.getArgumentTypes(desc);
        	        			Type[] newArgs = new Type[args.length];
        	        			Type returnArg = Type.getReturnType(desc);
        	        			for(int i = 0; i < args.length; i++)
        	        			{
        	        				Type t = args[i];
        	        				newArgs[i] = getMappedType(t, classMappings);
        	        			}
        	        			returnArg = getMappedType(returnArg, classMappings);
        	        			desc = Type.getMethodDescriptor(returnArg, newArgs);
        					}
        					methodMappings.get(className).put(new NodeWrapper(rev ? mappedMethod : methodName, 
        						desc), rev ? methodName : mappedMethod);
        				}
        				break;
        			case "FIELD":
        				if(map.length != 5)
	        				throw new RuntimeException("Unexpected field mapping");
        				className = map[1];
        				if(rev)
        					for(Entry<String, String> entry : classMappings.entrySet())
        						if(entry.getValue().equals(className))
        						{
        							className = entry.getKey();
        							break;
        						}
        				String fieldName = map[3];
        				String mappedField = map[4];
        				String desc = map[2];
        				if(rev)
        					desc = getMappedType(Type.getType(desc), classMappings).toString();
        				if(!fieldName.equals(mappedField))
        				{
        					fieldMappings.putIfAbsent(className, new HashMap<>());
        					fieldMappings.get(className).put(new NodeWrapper(rev ? mappedField : fieldName, 
        						desc), rev ? fieldName : mappedField);
        				}
        				break;
        			default:
        				throw new RuntimeException("Unexpected mapping " + map[0]);
        		}
    		}
    	}catch(IOException e)
		{
    		System.out.println("[NotchToIntermediaryTransformer] File read failed, are you sure the .tiny file has not been tampered?");
			e.printStackTrace();
			return;
		}
    	//This is only useful when we are trying to map classes across packages
    	remapper.setIgnorePackages(true);
    	//Remap mixin shadow methods/fields
    	for(ClassNode classNode : classNodes())
    	{
    		List<String> mixinClasses = new ArrayList<>();
    		if(classNode.invisibleAnnotations != null)
    			for(AnnotationNode annot : classNode.invisibleAnnotations)
    				if(annot.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") && annot.values.size() >= 2)
    				{
    					int index = -1;
    					for(int i = 0; i < annot.values.size(); i++)
    					{
    						Object o = annot.values.get(i);
    						if("value".equals(o))
    						{
    							index = i + 1;
    							break;
    						}
    					}
    					List<?> list = (List<?>)annot.values.get(index);
    					for(Object o : list)
    					{
    						if(!(o instanceof Type))
    							continue;
    						mixinClasses.add(((Type)o).getInternalName());
    					}
    				}
    		
    		if(mixinClasses.isEmpty())
    			continue;
    		
    		List<ClassNode> superclasses = new ArrayList<>();
    		for(String mixinClass : mixinClasses)
    		{
    			//Convert back to Notch name
        		if(rev && classMappings.containsKey(mixinClass))
        			mixinClass = classMappings.get(mixinClass);
        		
        		ClassNode mixinClassNode = null;
        		try
        		{
        			mixinClassNode = getDeobfuscator().assureLoaded(mixinClass);
        		}catch(NoClassInPathException e)
        		{
        			System.out.println("[NotchToIntermediaryTransformer] Class not found: " + mixinClasses);
        		}
        		
        		for(ClassNode cn : getSuperClasses(mixinClassNode, rev, classMappings))
        			if(!superclasses.contains(cn))
        				superclasses.add(cn);
        		if(!superclasses.contains(mixinClassNode))
        			superclasses.add(mixinClassNode);
    		}
    		
    		for(ClassNode cn : getSuperClasses(classNode, rev, classMappings))
    			if(!superclasses.contains(cn))
    				superclasses.add(cn);
    		
    		for(MethodNode method : classNode.methods)
    		{
    			if(method.name.startsWith("<"))
    				continue;
    			boolean isShadow = false;
    			if(method.visibleAnnotations != null)
    				for(AnnotationNode annot : method.visibleAnnotations)
    					if(annot.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))
    					{
    						isShadow = true;
    						break;
    					}
    			if(isShadow)
    			{
    				String mapped = null;
					outer:
		    		for(ClassNode superclass : superclasses)
		    		{
		    			String name = superclass.name;
		    			if(rev)
		    				for(Entry<String, String> entry : classMappings.entrySet())
								if(entry.getValue().equals(name))
								{
									name = entry.getKey();
									break;
								}
		    			if(methodMappings.containsKey(name))
		    				for(Entry<NodeWrapper, String> entry : methodMappings.get(name).entrySet())
		    					if(entry.getKey().name.equals(method.name) && entry.getKey().desc.equals(method.desc))
    							{
		    						mapped = entry.getValue();
		    						break outer;
    							}
		    		}
					if(mapped != null)
					{
						methodMappings.putIfAbsent(classNode.name, new HashMap<>());
						methodMappings.get(classNode.name).put(new NodeWrapper(method.name, method.desc), mapped);
					}
    			}
    		}
    		for(FieldNode field : classNode.fields)
    		{
    			if(field.name.startsWith("<"))
    				continue;
    			boolean isShadow = false;
    			if(field.visibleAnnotations != null)
    				for(AnnotationNode annot : field.visibleAnnotations)
    					if(annot.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))
    					{
    						isShadow = true;
    						break;
    					}
    			if(isShadow)
    			{
    				String mapped = null;
					outer:
		    		for(ClassNode superclass : superclasses)
		    		{
		    			String name = superclass.name;
		    			if(rev)
		    				for(Entry<String, String> entry : classMappings.entrySet())
								if(entry.getValue().equals(name))
								{
									name = entry.getKey();
									break;
								}
		    			if(fieldMappings.containsKey(name))
		    				for(Entry<NodeWrapper, String> entry : fieldMappings.get(name).entrySet())
		    					if(entry.getKey().name.equals(field.name) && entry.getKey().desc.equals(field.desc))
    							{
		    						mapped = entry.getValue();
		    						break outer;
    							}
		    		}
					if(mapped != null)
					{
						fieldMappings.putIfAbsent(classNode.name, new HashMap<>());
						fieldMappings.get(classNode.name).put(new NodeWrapper(field.name, field.desc), mapped);
					}
    			}
    		}
    	}
    	//Remap subclass calls
    	for(ClassNode classNode : classNodes())
    		for(MethodNode method : classNode.methods)
    			for(AbstractInsnNode ain : method.instructions.toArray())
    				if(ain instanceof FieldInsnNode)
    				{
    					FieldInsnNode fieldInsn = (FieldInsnNode)ain;
    					boolean hasMapping = false;
    					if(fieldMappings.containsKey(fieldInsn.owner))
    						for(Entry<NodeWrapper, String> entry : fieldMappings.get(fieldInsn.owner).entrySet())
    							if(entry.getKey().name.equals(fieldInsn.name) && entry.getKey().desc.equals(fieldInsn.desc))
    							{
    								hasMapping = true;
    								break;
    							}
    					if(!hasMapping)
    					{
    						ClassNode owner;
    						try
    						{
    							if(rev && classMappings.containsKey(fieldInsn.owner))
    								owner = getDeobfuscator().assureLoaded(classMappings.get(fieldInsn.owner));
    							else
    								owner = getDeobfuscator().assureLoaded(fieldInsn.owner);
    						}catch(NoClassInPathException e)
    						{
    							String ownerName = rev ? classMappings.get(fieldInsn.owner) : fieldInsn.owner;
    							if(classMappings.containsKey(fieldInsn.owner))
    								System.out.println("[NotchToIntermediaryTransformer] Class not found: " + ownerName);
    							continue;
    						}
    						List<ClassNode> superclasses = getSuperClasses(owner, rev, classMappings);
    						String mapped = null;
    						outer:
    			    		for(ClassNode superclass : superclasses)
    			    		{
    			    			String name = superclass.name;
    			    			if(rev)
    			    				for(Entry<String, String> entry : classMappings.entrySet())
    									if(entry.getValue().equals(name))
    									{
    										name = entry.getKey();
    										break;
    									}
    			    			if(fieldMappings.containsKey(name))
    			    				for(Entry<NodeWrapper, String> entry : fieldMappings.get(name).entrySet())
    			    					if(entry.getKey().name.equals(fieldInsn.name) && entry.getKey().desc.equals(fieldInsn.desc))
    	    							{
    			    						mapped = entry.getValue();
    			    						break outer;
    	    							}
    			    		}
    						if(mapped != null)
    						{
    							fieldMappings.putIfAbsent(fieldInsn.owner, new HashMap<>());
    							fieldMappings.get(fieldInsn.owner).put(new NodeWrapper(fieldInsn.name, fieldInsn.desc), mapped);
    						}
    					}
    				}else if(ain instanceof MethodInsnNode && !((MethodInsnNode)ain).name.startsWith("<"))
    				{
    					MethodInsnNode methodInsn = (MethodInsnNode)ain;
    					boolean hasMapping = false;
    					if(methodMappings.containsKey(methodInsn.owner))
    						for(Entry<NodeWrapper, String> entry : methodMappings.get(methodInsn.owner).entrySet())
    							if(entry.getKey().name.equals(methodInsn.name) && entry.getKey().desc.equals(methodInsn.desc))
    							{
    								hasMapping = true;
    								break;
    							}
    					if(!hasMapping)
    					{
    						ClassNode owner;
    						try
    						{
    							if(rev && classMappings.containsKey(methodInsn.owner))
    								owner = getDeobfuscator().assureLoaded(classMappings.get(methodInsn.owner));
    							else
    								owner = getDeobfuscator().assureLoaded(methodInsn.owner);
    						}catch(NoClassInPathException e)
    						{
    							String ownerName = rev ? classMappings.get(methodInsn.owner) : methodInsn.owner;
    							if(classMappings.containsKey(methodInsn.owner))
    								System.out.println("[NotchToIntermediaryTransformer] Class not found: " + ownerName);
    							continue;
    						}
    						List<ClassNode> superclasses = getSuperClasses(owner, rev, classMappings);
    						String mapped = null;
    						outer:
    			    		for(ClassNode superclass : superclasses)
    			    		{
    			    			String name = superclass.name;
    			    			if(rev)
    			    				for(Entry<String, String> entry : classMappings.entrySet())
    									if(entry.getValue().equals(name))
    									{
    										name = entry.getKey();
    										break;
    									}
    			    			if(methodMappings.containsKey(name))
    			    				for(Entry<NodeWrapper, String> entry : methodMappings.get(name).entrySet())
    			    					if(entry.getKey().name.equals(methodInsn.name) && entry.getKey().desc.equals(methodInsn.desc))
    	    							{
    			    						mapped = entry.getValue();
    			    						break outer;
    	    							}
    			    		}
    						if(mapped != null)
    						{
    							methodMappings.putIfAbsent(methodInsn.owner, new HashMap<>());
    							methodMappings.get(methodInsn.owner).put(new NodeWrapper(methodInsn.name, methodInsn.desc), mapped);
    						}
    					}
    				}
    	for(Entry<String, String> entry : classMappings.entrySet())
    		remapper.map(entry.getKey(), entry.getValue());
    	for(Entry<String, Map<NodeWrapper, String>> entry : methodMappings.entrySet())
    		for(Entry<NodeWrapper, String> entry2 : entry.getValue().entrySet())
    			remapper.mapMethodName(entry.getKey(), entry2.getKey().name, entry2.getKey().desc, entry2.getValue(), true);
    	for(Entry<String, Map<NodeWrapper, String>> entry : fieldMappings.entrySet())
    		for(Entry<NodeWrapper, String> entry2 : entry.getValue().entrySet())
    			remapper.mapFieldName(entry.getKey(), entry2.getKey().name, entry2.getKey().desc, entry2.getValue(), true);
    	//Remap overrides
    	for(ClassNode classNode : classNodes())
    	{
    		List<ClassNode> superclasses = getSuperClasses(classNode, rev, classMappings);
    		for(ClassNode superclass : superclasses)
    		{
    			String name = superclass.name;
    			if(rev)
    				for(Entry<String, String> entry : classMappings.entrySet())
						if(entry.getValue().equals(name))
						{
							name = entry.getKey();
							break;
						}
    			if(methodMappings.containsKey(name))
    				for(Entry<NodeWrapper, String> entry : methodMappings.get(name).entrySet())
    					for(MethodNode method : classNode.methods)
    						if(method.name.equals(entry.getKey().name) && method.desc.equals(entry.getKey().desc))
    							remapper.mapMethodName(classNode.name, method.name, method.desc, entry.getValue(), true);
    		}
    	}
    }
    
    /**
     * Returns the accessible superclasses.
     */
	private List<ClassNode> getSuperClasses(ClassNode classNode, boolean rev, Map<String, String> classMappings)
    {
		List<ClassNode> list = new ArrayList<>();
		if(classNode == null)
			return list;
		if(classNode.superName != null)
		{
			String superName = classNode.superName;
			if(rev && classMappings.containsKey(superName))
				superName = classMappings.get(superName);
			try
			{
				ClassNode superClass = getDeobfuscator().assureLoaded(superName);
				if(superClass != null)
				{
					for(ClassNode cn : getSuperClasses(superClass, rev, classMappings))
						if(!list.contains(cn))
							list.add(cn);
					if(!list.contains(superClass))
						list.add(superClass);
				}
			}catch(NoClassInPathException e)
			{
				return list;
			}
		}
		for(String inf : classNode.interfaces)
		{
			if(rev && classMappings.containsKey(inf))
				inf = classMappings.get(inf);
			try
			{
				ClassNode superInterface = getDeobfuscator().assureLoaded(inf);
				if(superInterface != null)
				{
					for(ClassNode cn : getSuperClasses(superInterface, rev, classMappings))
						if(!list.contains(cn))
							list.add(cn);
					if(!list.contains(superInterface))
						list.add(superInterface);
				}
			}catch(NoClassInPathException e)
			{
				return list;
			}
		}
		return list;
    }
    
    private Type getMappedType(Type t, Map<String, String> classMappings)
    {
    	if(t.getSort() == Type.OBJECT)
		{
			for(Entry<String, String> mapping : classMappings.entrySet())
				if(mapping.getValue().equals(t.getInternalName()))
					return Type.getObjectType(mapping.getKey());
		}else if(t.getSort() == Type.ARRAY)
		{
			int layers = 1;
			Type element = t.getElementType();
			while(element.getSort() == Type.ARRAY)
			{
				element = element.getElementType();
				layers++;
			}
			if(element.getSort() == Type.OBJECT)
				for(Entry<String, String> mapping : classMappings.entrySet())
					if(mapping.getValue().equals(element.getInternalName()))
					{
						String beginning = "";
						for(int i = 0; i < layers; i++)
							beginning += "[";
						beginning += "L";
						return Type.getType(beginning + mapping.getKey() + ";");
					}
		}
		return t;
    }
    
    private class NodeWrapper
    {
    	public String name;
    	public String desc;
    	
    	public NodeWrapper(String name, String desc)
    	{
    		this.name = name;
    		this.desc = desc;
    	}
    }

	public static class Config extends AbstractNormalizer.Config 
	{
		/**
		 * If false, we map Notch names to Intermediary.
		 * If true, we map Intermediary to Notch names.
		 */
		private boolean reverse = true;
		
        public Config() 
        {
            super(NotchToIntermediaryTransformer.class);
        }
        
        public boolean isReverse() 
        {
            return reverse;
        }

        public void setReverse(boolean reverse) 
        {
            this.reverse = reverse;
        }
    }
}
