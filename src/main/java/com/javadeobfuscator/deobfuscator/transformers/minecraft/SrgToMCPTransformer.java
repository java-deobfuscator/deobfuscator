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

@TransformerConfig.ConfigOptions(configClass = SrgToMCPTransformer.Config.class)
public class SrgToMCPTransformer extends AbstractNormalizer<SrgToMCPTransformer.Config>
{
    @Override
    public void remap(CustomRemapper remapper)
    {
    	try
    	{
    		getDeobfuscator().assureLoaded("net/minecraft/client/main/Main");
    	}catch(NoClassInPathException e)
    	{
    		System.out.println("[SrgToMCPTransformer] Obfuscated Minecraft jar not detected, put it as a library for best results!");
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
    		System.out.println("[SrgToMCPTransformer] You must put joined.srg or joined.tsrg next to deobfuscator.jar!");
    		return;
    	}
    	
    	File methods = new File("methods.csv");
    	File fields = new File("fields.csv");
    	
    	if(!methods.exists() || !fields.exists())
    	{
    		System.out.println("[SrgToMCPTransformer] You must put methods.csv and fields.csv next to deobfuscator.jar!");
    		return;
    	}
    	
    	Map<String, String> deobfFieldMappings = new HashMap<>();
    	Map<String, String> deobfMethodMappings = new HashMap<>();
    	
    	try(BufferedReader reader = new BufferedReader(new FileReader(fields)))
    	{
    		reader.readLine();//Skip first line
    		String line;
    		while((line = reader.readLine()) != null)
    		{
    			String[] split = line.split(",");
    			if(split.length < 3)
    				throw new RuntimeException("Invaild pattern in fields.csv");
    			deobfFieldMappings.put(split[0], split[1]);
    		}
    	}catch(IOException e)
		{
    		System.out.println("[SrgToMCPTransformer] File read failed, are you sure fields.csv has not been tampered?");
			e.printStackTrace();
			return;
		}
    	try(BufferedReader reader = new BufferedReader(new FileReader(methods)))
    	{
    		reader.readLine();//Skip first line
    		String line;
    		while((line = reader.readLine()) != null)
    		{
    			String[] split = line.split(",");
    			if(split.length < 3)
    				throw new RuntimeException("Invaild pattern in methods.csv");
    			deobfMethodMappings.put(split[0], split[1]);
    		}
    	}catch(IOException e)
		{
    		System.out.println("[SrgToMCPTransformer] File read failed, are you sure methods.csv has not been tampered?");
			e.printStackTrace();
			return;
		}
    	
    	Map<String, String> classMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> fieldMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> methodMappings = new HashMap<>();
    	
    	Map<String, Map<String, String>> fieldObfMappings = new HashMap<>();
    	
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
	        			currentClass = map[1];
	        			if(!map[0].equals(map[1]))
	        				classMappings.put(map[0], map[1]);
	        		}else
	        		{
	        			String[] map = line.replace("\t", "").split(" ");
	        			if(currentClass == null)
	        				throw new RuntimeException("Method/field with unknown class found!");
	        			if(map.length == 3)
	        			{
	        				if(deobfMethodMappings.containsKey(map[2]))
	        				{
	        					methodMappings.putIfAbsent(currentClass, new HashMap<>());
	        					methodMappings.get(currentClass).put(new NodeWrapper(rev ? deobfMethodMappings.get(map[2]) : map[2],
	        						map[1]), rev ? map[2] : deobfMethodMappings.get(map[2]));
	        				}
	        			}else if(map.length == 2)
	        			{
	        				if(deobfFieldMappings.containsKey(map[1]))
	        				{
	        					fieldMappings.putIfAbsent(currentClass, new HashMap<>());
	        					fieldMappings.get(currentClass).put(new NodeWrapper(rev ? deobfFieldMappings.get(map[1]) : map[1], null),
	        						rev ? map[1] : deobfFieldMappings.get(map[1]));
	        				}
	        				if(!map[0].equals(map[1]))
	        				{
	        					fieldObfMappings.putIfAbsent(currentClass, new HashMap<>());
	        					fieldObfMappings.get(currentClass).put(map[1], map[0]);
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
	        		String className;
	        		switch(beginning[0])
	        		{
	        			case "CL:":
		        			if(map.length != 2)
		        				throw new RuntimeException("Unexpected class mapping");
		        			if(!map[0].equals(map[1]))
		        				classMappings.put(map[0], map[1]);
	        				break;
	        			case "MD:":
	        				if(map.length != 4)
		        				throw new RuntimeException("Unexpected method mapping");
	        				className = map[0].substring(0, map[0].lastIndexOf('/'));
	        				if(classMappings.containsKey(className))
	        					className = classMappings.get(className);
	        				String methodName = map[2].substring(map[2].lastIndexOf('/') + 1);
	        				if(deobfMethodMappings.containsKey(methodName))
	        				{
	        					methodMappings.putIfAbsent(className, new HashMap<>());
	        					methodMappings.get(className).put(new NodeWrapper(rev ? deobfMethodMappings.get(methodName) : methodName,
	        						map[3]), rev ? methodName : deobfMethodMappings.get(methodName));
	        				}
	        				break;
	        			case "FD:":
	        				if(map.length != 2)
		        				throw new RuntimeException("Unexpected field mapping");
	        				className = map[0].substring(0, map[0].lastIndexOf('/'));
	        				if(classMappings.containsKey(className))
	        					className = classMappings.get(className);
	        				String fieldName = map[1].substring(map[1].lastIndexOf('/') + 1);
	        				if(deobfFieldMappings.containsKey(fieldName))
	        				{
	        					fieldMappings.putIfAbsent(className, new HashMap<>());
	        					fieldMappings.get(className).put(new NodeWrapper(rev ? deobfFieldMappings.get(fieldName) : fieldName,
	        						null), rev ? fieldName : deobfFieldMappings.get(fieldName));
	        				}
	        				String notchFieldName = map[0].substring(map[0].lastIndexOf('/') + 1);
	        				if(!notchFieldName.equals(fieldName))
	        				{
	        					fieldObfMappings.putIfAbsent(className, new HashMap<>());
	        					fieldObfMappings.get(className).put(fieldName, notchFieldName);
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
    		System.out.println("[SrgToMCPTransformer] File read failed, are you sure the (T)SRG file has not been tampered?");
			e.printStackTrace();
			return;
		}
    	//Fix descs for tsrg
    	if(tsrg)
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
    		for(Entry<String, String> mapping : classMappings.entrySet())
				if(mapping.getValue().equals(className))
				{
					className = mapping.getKey();
					break;
				}
    		try
			{
    			classNode = getDeobfuscator().assureLoaded(className);
			}catch(NoClassInPathException e)
			{
				System.out.println("[SrgToMCPTransformer] Class not found: " + className);
				itr.remove();
				continue;
			}
    		for(Iterator<Entry<NodeWrapper, String>> itr2 = entry.getValue().entrySet().iterator(); itr2.hasNext();)
    		{
    			//Note: There are NO duplicate fields, so this is safe
    			Entry<NodeWrapper, String> node = itr2.next();
    			String srgName = rev ? node.getValue() : node.getKey().name;
    			String notchName = fieldObfMappings.get(entry.getKey()).get(srgName);
    			if(notchName == null)
    				throw new RuntimeException("Could not find Notch name for SRG!");
    			FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(notchName)).findFirst().orElse(null);
    			if(field == null)
    			{
    				System.out.println("[SrgToMCPTransformer] Field not found: " + notchName + " @ " + classNode.name);
    				itr2.remove();
    			}else
    				node.getKey().desc = getMappedType(Type.getType(field.desc), classMappings).toString();
    		}
    	}
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
        		for(Entry<String, String> entry : classMappings.entrySet())
    				if(entry.getValue().equals(mixinClass))
    				{
    					mixinClass = entry.getKey();
    					break;
    				}
        		
        		ClassNode mixinClassNode = null;
        		try
        		{
        			mixinClassNode = getDeobfuscator().assureLoaded(mixinClass);
        		}catch(NoClassInPathException e)
        		{
        			System.out.println("[SrgToMCPTransformer] Class not found: " + mixinClass);
        			continue;
        		}
        		
        		for(ClassNode cn : getSuperClasses(mixinClassNode, classMappings))
        			if(!superclasses.contains(cn))
        				superclasses.add(cn);
        		if(!superclasses.contains(mixinClassNode))
        			superclasses.add(mixinClassNode);
    		}
    		
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
		    			if(classMappings.containsKey(name))
		    				name = classMappings.get(name);
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
		    			if(classMappings.containsKey(name))
		    				name = classMappings.get(name);
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
    					boolean hasReobfOwner = false;
    					FieldInsnNode fieldInsn = (FieldInsnNode)ain;
    					String reobfOwner = fieldInsn.owner;
    					for(Entry<String, String> mapping : classMappings.entrySet())
    						if(mapping.getValue().equals(fieldInsn.owner))
    						{
    							hasReobfOwner = true;
    							reobfOwner = mapping.getKey();
    							break;
    						}
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
    							owner = getDeobfuscator().assureLoaded(reobfOwner);
    						}catch(NoClassInPathException e)
    						{
    							if(hasReobfOwner)
    								System.out.println("[SrgToMCPTransformer] Class not found: " + reobfOwner);
    							continue;
    						}
    						List<ClassNode> superclasses = getSuperClasses(owner, classMappings);
    						String mapped = null;
    						outer:
    			    		for(ClassNode superclass : superclasses)
    			    		{
    			    			String name = superclass.name;
    			    			if(classMappings.containsKey(name))
    			    				name = classMappings.get(name);
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
    					boolean hasReobfOwner = false;
    					MethodInsnNode methodInsn = (MethodInsnNode)ain;
    					String reobfOwner = methodInsn.owner;
    					for(Entry<String, String> mapping : classMappings.entrySet())
    						if(mapping.getValue().equals(methodInsn.owner))
    						{
    							hasReobfOwner = true;
    							reobfOwner = mapping.getKey();
    							break;
    						}
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
    							owner = getDeobfuscator().assureLoaded(reobfOwner);
    						}catch(NoClassInPathException e)
    						{
    							if(hasReobfOwner)
    								System.out.println("[SrgToMCPTransformer] Class not found: " + reobfOwner);
    							continue;
    						}
    						List<ClassNode> superclasses = getSuperClasses(owner, classMappings);
    						String mapped = null;
    						outer:
    			    		for(ClassNode superclass : superclasses)
    			    		{
    			    			String name = superclass.name;
    			    			if(classMappings.containsKey(name))
    			    				name = classMappings.get(name);
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
    	for(Entry<String, Map<NodeWrapper, String>> entry : methodMappings.entrySet())
    		for(Entry<NodeWrapper, String> entry2 : entry.getValue().entrySet())
    			remapper.mapMethodName(entry.getKey(), entry2.getKey().name, entry2.getKey().desc, entry2.getValue(), true);
    	for(Entry<String, Map<NodeWrapper, String>> entry : fieldMappings.entrySet())
    		for(Entry<NodeWrapper, String> entry2 : entry.getValue().entrySet())
    			remapper.mapFieldName(entry.getKey(), entry2.getKey().name, entry2.getKey().desc, entry2.getValue(), true);
    	//Remap overrides
    	for(ClassNode classNode : classNodes())
    	{
    		List<ClassNode> superclasses = getSuperClasses(classNode, classMappings);
    		for(ClassNode superclass : superclasses)
    		{
    			String name = superclass.name;
    			if(classMappings.containsKey(name))
    				name = classMappings.get(name);
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
	private List<ClassNode> getSuperClasses(ClassNode classNode, Map<String, String> classMappings)
    {
		List<ClassNode> list = new ArrayList<>();
		if(classNode == null)
			return list;
		if(classNode.superName != null)
		{
			try
			{
				String superName = classNode.superName;
				for(Entry<String, String> entry : classMappings.entrySet())
					if(entry.getValue().equals(superName))
					{
						superName = entry.getKey();
						break;
					}
				ClassNode superClass = getDeobfuscator().assureLoaded(superName);
				if(superClass != null)
				{
					for(ClassNode cn : getSuperClasses(superClass, classMappings))
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
			for(Entry<String, String> entry : classMappings.entrySet())
				if(entry.getValue().equals(inf))
				{
					inf = entry.getKey();
					break;
				}
			try
			{
				ClassNode superInterface = getDeobfuscator().assureLoaded(inf);
				if(superInterface != null)
				{
					for(ClassNode cn : getSuperClasses(superInterface, classMappings))
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
    	if(t.getSort() == Type.OBJECT && classMappings.containsKey(t.getInternalName()))
    		return Type.getObjectType(classMappings.get(t.getInternalName()));
    	if(t.getSort() == Type.ARRAY)
		{
			int layers = 1;
			Type element = t.getElementType();
			while(element.getSort() == Type.ARRAY)
			{
				element = element.getElementType();
				layers++;
			}
			if(element.getSort() == Type.OBJECT && classMappings.containsKey(element.getInternalName()))
			{
				String beginning = "";
				for(int i = 0; i < layers; i++)
					beginning += "[";
				beginning += "L";
				return Type.getType(beginning + classMappings.get(element.getInternalName()) + ";");
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
		 * If false, we map SRG to deobbed names.
		 * If true, we map deobbed names to SRG.
		 */
		private boolean reverse = false;
		
        public Config() 
        {
            super(SrgToMCPTransformer.class);
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
