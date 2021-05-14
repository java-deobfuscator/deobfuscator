package com.javadeobfuscator.deobfuscator.transformers.minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
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

@TransformerConfig.ConfigOptions(configClass = IntermediaryToYarnTransformer.Config.class)
public class IntermediaryToYarnTransformer extends AbstractNormalizer<IntermediaryToYarnTransformer.Config>
{
    @Override
    public void remap(CustomRemapper remapper)
    {
    	try
    	{
    		getDeobfuscator().assureLoaded("net/minecraft/client/main/Main");
    	}catch(NoClassInPathException e)
    	{
    		System.out.println("[IntermediaryToYarnTransformer] Obfuscated Minecraft jar not detected, put it as a library for best results!");
    	}
    	File mappings = new File("mappings");
    	if(!mappings.exists())
    	{
    		System.out.println("[NotchToIntermediaryTransformer] You must put the mappings folder next to deobfuscator.jar!");
    		return;
    	}
    	
    	File tiny = new File("mappings.tiny");
    	if(!tiny.exists())
    	{
    		System.out.println("[NotchToIntermediaryTransformer] You must put mappings.tiny next to deobfuscator.jar!");
    		return;
    	}
    	
    	Map<String, String> notchToIntClass = new HashMap<>();
    	
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
        		switch(map[0])
        		{
        			case "CLASS":
        				if(map.length != 3)
            				throw new RuntimeException("Unexpected class mapping");
            			if(!map[1].equals(map[2]))
            				notchToIntClass.put(map[1], map[2]);
        				break;
        			case "METHOD":
        			case "FIELD":
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
    	
    	Map<String, String> classMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> fieldMappings = new HashMap<>();
    	Map<String, Map<NodeWrapper, String>> methodMappings = new HashMap<>();
    	
    	boolean rev = getConfig().isReverse();
    	Collection<File> files = FileUtils.listFiles(mappings, new RegexFileFilter("^(.*?)"), 
    		  TrueFileFilter.INSTANCE);
    	for(File file : files)
	    	try(BufferedReader reader = new BufferedReader(new FileReader(file)))
	    	{
	    		String firstLine = reader.readLine();
	    		String[] firstLineSplit = firstLine.split("\\s+");
	    		if(firstLineSplit.length != 2 && firstLineSplit.length != 3)
	    			throw new RuntimeException("Invaild class mapping");
	    		if(!firstLineSplit[0].equals("CLASS"))
	    			throw new RuntimeException("Invaild class mapping");
	    		String obfName = firstLineSplit[1];
	    		String deobfName = firstLineSplit.length == 2 ? firstLineSplit[1] : firstLineSplit[2];
	    		if(!deobfName.equals(obfName))
	    			classMappings.put(rev ? deobfName : obfName, rev ? obfName : deobfName);
	    		String line;
	    		int prevTab = 0;
	    		while((line = reader.readLine()) != null)
	    		{
	    			int tabs = line.length();
	    			line = line.trim();
	    			tabs = tabs - line.length();
	    			String[] split = line.split("\\s+");
	    			switch(split[0])
	    			{
	    				case "METHOD":
	    					if(split.length != 3 && split.length != 4)
	    						throw new RuntimeException("Invaild method mapping");
	    					if(tabs != prevTab + 1)
	    						throw new RuntimeException("Invaild whitespace");
	    					if(split.length == 4)
	    					{
	    						String name = rev ? deobfName : obfName;
	    						methodMappings.putIfAbsent(name, new HashMap<>());
	    						methodMappings.get(name).put(new NodeWrapper(rev ? split[2] : split[1], split[3]),
	    							rev ? split[1] : split[2]);
	    					}
	    					break;
	    				case "FIELD":
	    					if(split.length != 3 && split.length != 4)
	    						throw new RuntimeException("Invaild method mapping");
	    					if(tabs != prevTab + 1)
	    						throw new RuntimeException("Invaild whitespace");
	    					if(split.length == 4)
	    					{
	    						String name = rev ? deobfName : obfName;
	    						String desc = split[3];
	    						if(rev)
	    						{
	    							Type type = Type.getType(desc);
	    							desc = getMappedType(type, classMappings).toString();
	    						}
	    						fieldMappings.putIfAbsent(name, new HashMap<>());
	    						fieldMappings.get(name).put(new NodeWrapper(rev ? split[2] : split[1], desc),
	    							rev ? split[1] : split[2]);
	    					}
	    					break;
	    				case "CLASS":
	    					//Handle subclasses
	    					if(split.length != 2 && split.length != 3)
	    						throw new RuntimeException("Invaild class mapping");
	    					if(prevTab < tabs - 1)
	    						throw new RuntimeException("Invaild class whitespace");
	    					int indexesBack = prevTab - tabs + 1;
	    					obfName = obfName.substring(0, lastIndexPos(indexesBack, obfName));
	    					deobfName = deobfName.substring(0, lastIndexPos(indexesBack, deobfName)); 
	    					String obfInnerName = split[1];
    			    		String deobfInnerName = split.length == 2 ? split[1] : split[2];
    			    		obfName += "$" + obfInnerName;
    			    		deobfName += "$" + deobfInnerName;
    			    		if(!obfName.equals(deobfName))
    			    			classMappings.put(rev ? deobfName : obfName, rev ? obfName : deobfName);
	    					prevTab = tabs;
	    					break;
	    				case "ARG":
	    				case "COMMENT":
	    					break;
	    				default:
	    					throw new RuntimeException("Unexpected mapping type " + split[0]);
	    			}
	    		}
	    	}catch(IOException e)
			{
	    		System.out.println("[IntermediaryToYarnTransformer] File read failed, are you sure the mapping files have not been tampered?");
				e.printStackTrace();
				return;
			}
    	//Fix descs when we are reversing
    	if(rev)
    	{
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
    		for(Entry<String, Map<NodeWrapper, String>> entry : fieldMappings.entrySet())
	    		for(Entry<NodeWrapper, String> entry2 : entry.getValue().entrySet())
	    			entry2.getKey().desc = getMappedType(Type.getType(entry2.getKey().desc), classMappings).toString();
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
    			//Convert back to intermediate name (rev)
    			if(rev && classMappings.containsKey(mixinClass))
    				mixinClass = classMappings.get(mixinClass);
    			//Convert back to Notch name
        		for(Entry<String, String> entry : notchToIntClass.entrySet())
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
        			System.out.println("[IntermediaryToYarnTransformer] Class not found: " + mixinClass);
        		}
        		
        		for(ClassNode cn : getSuperClasses(mixinClassNode, rev, classMappings, notchToIntClass))
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
		    			if(notchToIntClass.containsKey(name))
		    				name = notchToIntClass.get(name);
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
		    			if(notchToIntClass.containsKey(name))
		    				name = notchToIntClass.get(name);
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
    					boolean hasReobfOwner = false;
    					FieldInsnNode fieldInsn = (FieldInsnNode)ain;
    					String reobfOwner = fieldInsn.owner;
    					//Convert back to intermediate name (rev)
    	    			if(rev && classMappings.containsKey(reobfOwner))
    	    				reobfOwner = classMappings.get(reobfOwner);
    	    			//Convert back to Notch name
    	        		for(Entry<String, String> entry : notchToIntClass.entrySet())
    	    				if(entry.getValue().equals(reobfOwner))
    	    				{
    	    					hasReobfOwner = true;
    	    					reobfOwner = entry.getKey();
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
    								System.out.println("[IntermediaryToYarnTransformer] Class not found: " + reobfOwner);
    							continue;
    						}
    						List<ClassNode> superclasses = getSuperClasses(owner, rev, classMappings, notchToIntClass);
    						String mapped = null;
    						outer:
    			    		for(ClassNode superclass : superclasses)
    			    		{
    			    			String name = superclass.name;
    			    			if(notchToIntClass.containsKey(name))
    			    				name = notchToIntClass.get(name);
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
    					boolean hasReobfOwner = false;
    					MethodInsnNode methodInsn = (MethodInsnNode)ain;
    					String reobfOwner = methodInsn.owner;
    					//Convert back to intermediate name (rev)
    	    			if(rev && classMappings.containsKey(reobfOwner))
    	    				reobfOwner = classMappings.get(reobfOwner);
    	    			//Convert back to Notch name
    	        		for(Entry<String, String> entry : notchToIntClass.entrySet())
    	    				if(entry.getValue().equals(reobfOwner))
    	    				{
    	    					hasReobfOwner = true;
    	    					reobfOwner = entry.getKey();
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
    								System.out.println("[IntermediaryToYarnTransformer] Class not found: " + reobfOwner);
    							continue;
    						}
    						List<ClassNode> superclasses = getSuperClasses(owner, rev, classMappings, notchToIntClass);
    						String mapped = null;
    						outer:
    			    		for(ClassNode superclass : superclasses)
    			    		{
    			    			String name = superclass.name;
    			    			if(notchToIntClass.containsKey(name))
    			    				name = notchToIntClass.get(name);
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
    		List<ClassNode> superclasses = getSuperClasses(classNode, rev, classMappings, notchToIntClass);
    		for(ClassNode superclass : superclasses)
    		{
    			String name = superclass.name;
    			if(notchToIntClass.containsKey(name))
    				name = notchToIntClass.get(name);
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
    
    private int lastIndexPos(int pos, String s)
    {
        if(pos <= 0)
        	return s.length();
        return lastIndexPos(--pos, s.substring(0, s.lastIndexOf("$")));
    }
    
    /**
     * Returns the accessible superclasses.
     */
	private List<ClassNode> getSuperClasses(ClassNode classNode, boolean rev, Map<String, String> classMappings,
		Map<String, String> notchToIntClass)
    {
		List<ClassNode> list = new ArrayList<>();
		if(classNode == null)
			return list;
		if(classNode.superName != null)
		{
			try
			{
				String superName = classNode.superName;
				//Convert back to intermediate name (rev)
    			if(rev && classMappings.containsKey(superName))
    				superName = classMappings.get(superName);
    			//Convert back to Notch name
        		for(Entry<String, String> entry : notchToIntClass.entrySet())
    				if(entry.getValue().equals(superName))
    				{
    					superName = entry.getKey();
    					break;
    				}
				ClassNode superClass = getDeobfuscator().assureLoaded(superName);
				if(superClass != null)
				{
					for(ClassNode cn : getSuperClasses(superClass, rev, classMappings, notchToIntClass))
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
			//Convert back to intermediate name (rev)
			if(rev && classMappings.containsKey(inf))
				inf = classMappings.get(inf);
			//Convert back to Notch name
    		for(Entry<String, String> entry : notchToIntClass.entrySet())
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
					for(ClassNode cn : getSuperClasses(superInterface, rev, classMappings, notchToIntClass))
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
		 * If false, we map Intermediary to Yarn.
		 * If true, we map Yarn to Intermediary.
		 */
		private boolean reverse = false;
		
        public Config() 
        {
            super(IntermediaryToYarnTransformer.class);
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
