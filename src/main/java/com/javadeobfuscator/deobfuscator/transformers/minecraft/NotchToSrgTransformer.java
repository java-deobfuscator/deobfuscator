package com.javadeobfuscator.deobfuscator.transformers.minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

@TransformerConfig.ConfigOptions(configClass = NotchToSrgTransformer.Config.class)
public class NotchToSrgTransformer extends AbstractNormalizer<NotchToSrgTransformer.Config>
{
    @Override
    public void remap(CustomRemapper remapper)
    {
    	try
    	{
    		getDeobfuscator().assureLoaded("net/minecraft/client/main/Main");
    	}catch(NoClassInPathException e)
    	{
    		System.out.println("[NotchToSrgTransformer] Obfuscated Minecraft jar not detected, put it as a library for best results!");
    	}
    	boolean tsrg = true;
    	File srg = new File("joined.tsrg");
    	if(!srg.exists())
    	{
    		srg = new File("joined.srg");
    		tsrg = false;
    	}
    	if(!srg.exists())
    	{
    		System.out.println("[NotchToSrgTransformer] You must put joined.srg or joined.tsrg next to deobfuscator.jar!");
    		return;
    	}
    	
    	Map<String, String> classMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> fieldMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> methodMappings = new HashMap<>();
    	
    	boolean rev = getConfig().isReverse();
    	try(BufferedReader reader = new BufferedReader(new FileReader(srg)))
    	{
    		String currentClass = null;
    		String line;
    		while((line = reader.readLine()) != null)
    		{
	        	if(tsrg)
	        		if(!line.startsWith("\t"))
	        		{
	        			String[] map = line.split(" ");
	        			if(map.length != 2)
	        				throw new RuntimeException("Unexpected class mapping");
	        			currentClass = rev ? map[1] :  map[0];
	        			if(!map[0].equals(map[1]))
	        				classMappings.put(rev ? map[1] : map[0], rev ? map[0] : map[1]);
	        		}else
	        		{
	        			String[] map = line.replace("\t", "").split(" ");
	        			if(currentClass == null)
	        				throw new RuntimeException("Method/field with unknown class found!");
	        			if(map.length == 3)
	        			{
	        				if(!map[0].equals(map[2]))
	        				{
	        					methodMappings.putIfAbsent(currentClass, new HashMap<>());
	        					methodMappings.get(currentClass).put(new NodeWrapper(rev ? map[2] : map[0], map[1]),
	        						rev ? map[0] : map[2]);
	        				}
	        			}else if(map.length == 2)
	        			{
	        				if(!map[0].equals(map[1]))
	        				{
	        					fieldMappings.putIfAbsent(currentClass, new HashMap<>());
	        					fieldMappings.get(currentClass).put(new NodeWrapper(rev ? map[1] : map[0], null),
	        						rev ? map[0] : map[1]);
	        				}
	        			}else
	        				throw new RuntimeException("Unexpected node mapping");
	        		}
	        	else
	        	{
	        		String[] beginning = line.split(" ", 2);
	        		if(beginning.length != 2)
	        			throw new RuntimeException("Unexpected srg mapping");
	        		line = beginning[1];
	        		String[] map = line.split(" ");
	        		int idx;
	        		String className;
	        		switch(beginning[0])
	        		{
	        			case "CL:":
		        			if(map.length != 2)
		        				throw new RuntimeException("Unexpected class mapping");
		        			if(!map[0].equals(map[1]))
		        				classMappings.put(rev ? map[1] : map[0], rev ? map[0] : map[1]);
	        				break;
	        			case "MD:":
	        				if(map.length != 4)
		        				throw new RuntimeException("Unexpected method mapping");
	        				idx = map[0].lastIndexOf('/');
	        				className = map[0].substring(0, idx);
	        				if(rev)
	        					for(Entry<String, String> entry : classMappings.entrySet())
	        						if(entry.getValue().equals(className))
	        						{
	        							className = entry.getKey();
	        							break;
	        						}
	        				String methodName = map[0].substring(idx + 1);
	        				String mappedMethod = map[2].substring(map[2].lastIndexOf('/') + 1);
	        				if(!methodName.equals(mappedMethod))
	        				{
	        					methodMappings.putIfAbsent(className, new HashMap<>());
	        					methodMappings.get(className).put(new NodeWrapper(rev ? mappedMethod : methodName, 
	        						rev ? map[3] : map[1]),
	        						rev ? methodName : mappedMethod);
	        				}
	        				break;
	        			case "FD:":
	        				if(map.length != 2)
		        				throw new RuntimeException("Unexpected field mapping");
	        				idx = map[0].lastIndexOf('/');
	        				className = map[0].substring(0, idx);
	        				if(rev)
	        					for(Entry<String, String> entry : classMappings.entrySet())
	        						if(entry.getValue().equals(className))
	        						{
	        							className = entry.getKey();
	        							break;
	        						}
	        				String fieldName = map[0].substring(idx + 1);
	        				String mappedField = map[1].substring(map[1].lastIndexOf('/') + 1);
	        				if(!fieldName.equals(mappedField))
	        				{
	        					fieldMappings.putIfAbsent(className, new HashMap<>());
	        					fieldMappings.get(className).put(new NodeWrapper(rev ? mappedField : fieldName, null),
	        						rev ? fieldName : mappedField);
	        				}
	        				break;
	        			case "PK:":
	        				//Package mappings are ignored
	        				break;
	        			default:
	        				throw new RuntimeException("Unexpected srg mapping " + beginning[0]);
	        		}
	        	}
    		}
    	}catch(IOException e)
		{
    		System.out.println("[NotchToSrgTransformer] File read failed, are you sure the (T)SRG file has not been tampered?");
			e.printStackTrace();
			return;
		}
    	//Fix descs for tsrg
    	if(tsrg && rev)
    		for(Entry<String, Map<NodeWrapper, String>> entry : methodMappings.entrySet())
        		for(Entry<NodeWrapper, String> entry2 : entry.getValue().entrySet())
        		{
        			String desc = entry2.getKey().desc;
        			Type[] args = Type.getArgumentTypes(desc);
        			Type[] newArgs = new Type[args.length];
        			Type returnArg = Type.getReturnType(desc);
        			for(int i = 0; i < args.length; i++)
        			{
        				Type t = args[i];
        				newArgs[i] = getMappedType(t, classMappings);
        			}
        			returnArg = getMappedType(returnArg, classMappings);
        			entry2.getKey().desc = Type.getMethodDescriptor(returnArg, newArgs);
        		}
    	//We are not provided field descs, so we need to search them
    	for(Iterator<Entry<String, Map<NodeWrapper, String>>> itr = fieldMappings.entrySet().iterator(); itr.hasNext();)
    	{
    		Entry<String, Map<NodeWrapper, String>> entry = itr.next();
    		ClassNode classNode;
    		String className = entry.getKey();
    		try
			{
    			if(rev && classMappings.containsKey(entry.getKey()))
    				className = classMappings.get(entry.getKey());
    			classNode = getDeobfuscator().assureLoaded(className);
			}catch(NoClassInPathException e)
			{
				System.out.println("[NotchToSrgTransformer] Class not found: " + className);
				itr.remove();
				continue;
			}
    		for(Iterator<Entry<NodeWrapper, String>> itr2 = entry.getValue().entrySet().iterator(); itr2.hasNext();)
    		{
    			//Note: There are NO duplicate fields, so this is safe
    			Entry<NodeWrapper, String> node = itr2.next();
    			FieldNode field = rev ? classNode.fields.stream().filter(f -> f.name.equals(node.getValue())).findFirst().orElse(null)
    				: classNode.fields.stream().filter(f -> f.name.equals(node.getKey().name)).findFirst().orElse(null);
    			if(field == null)
    			{
    				System.out.println("[NotchToSrgTransformer] Field not found: " + node.getKey().name + " @ " + entry.getKey());
    				itr2.remove();
    			}else
    			{
	    			if(rev)
	    				node.getKey().desc = getMappedType(Type.getType(field.desc), classMappings).toString();
	    			else
	    				node.getKey().desc = field.desc;
    			}
    		}
    	}
    	//This is only useful when we are trying to "collapse" the classes into one package (reobf)
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
    					boolean isValue = true;
    					for(int i = 0; i < annot.values.size(); i++)
    					{
    						Object o = annot.values.get(i);
    						if("value".equals(o))
    						{
    							index = i + 1;
    							break;
    						}
    						if("targets".equals(o))
    						{
    							index = i + 1;
    							isValue = false;
    							break;
    						}
    					}
    					List<?> list = (List<?>)annot.values.get(index);
    					for(Object o : list)
    					{
    						if(isValue && !(o instanceof Type))
    							continue;
    						if(!isValue && !(o instanceof String))
    							continue;
    						mixinClasses.add(isValue ? ((Type)o).getInternalName() : (String)o);
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
        			System.out.println("[NotchToSrgTransformer] Class not found: " + mixinClass);
        			continue;
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
    							if(rev)
    								owner = getDeobfuscator().assureLoaded(classMappings.get(fieldInsn.owner));
    							else
    								owner = getDeobfuscator().assureLoaded(fieldInsn.owner);
    						}catch(NoClassInPathException e)
    						{
    							String ownerName = rev ? classMappings.get(fieldInsn.owner) : fieldInsn.owner;
    							if(classMappings.containsKey(fieldInsn.owner))
    								System.out.println("[NotchToSrgTransformer] Class not found: " + ownerName);
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
    							if(rev)
    								owner = getDeobfuscator().assureLoaded(classMappings.get(methodInsn.owner));
    							else
    								owner = getDeobfuscator().assureLoaded(methodInsn.owner);
    						}catch(NoClassInPathException e)
    						{
    							String ownerName = rev ? classMappings.get(methodInsn.owner) : methodInsn.owner;
    							if(classMappings.containsKey(methodInsn.owner))
    								System.out.println("[NotchToSrgTransformer] Class not found: " + ownerName);
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
		 * If false, we map Notch names to SRG.
		 * If true, we map SRG to Notch names.
		 */
		private boolean reverse = false;
		
        public Config() 
        {
            super(NotchToSrgTransformer.class);
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
