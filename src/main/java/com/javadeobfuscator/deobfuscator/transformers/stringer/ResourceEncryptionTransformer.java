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

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaArray;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class ResourceEncryptionTransformer extends Transformer<TransformerConfig> {
	@Override
    public boolean transform() {
        System.out.println("[Stringer] [ResourceEncryptionTransformer] Starting");
        int processed = process();
        System.out.println("[Stringer] [ResourceEncryptionTransformer] Processed " + processed + " resources");
        System.out.println("[Stringer] [ResourceEncryptionTransformer] Done");
        return processed > 0;
    }

    private int process() {
        AtomicInteger total = new AtomicInteger();

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
        ClassNode decryptor = null;
        MethodNode init = null;
        for(ClassNode classNode : classNodes()) {
        	MethodNode initTemp = null;
        	boolean hasInputStream = false;
        	boolean hasObjectArr = false;
        	for(FieldNode f : classNode.fields)
        		if(f.desc.equals("[Ljava/lang/Object;"))
        			hasObjectArr = true;
        	List<ClassNode> calls = new ArrayList<>();
        	if(hasObjectArr)
        		for(MethodNode m : classNode.methods)
        			if(m.name.equals("<init>") && m.desc.equals("(Ljava/io/InputStream;)V"))
        			{
        				for(AbstractInsnNode ain : m.instructions.toArray())
							if(ain.getOpcode() == Opcodes.NEW
								&& classes.containsKey(((TypeInsnNode)ain).desc))
								calls.add(classes.get(((TypeInsnNode)ain).desc));
        				initTemp = m;
        				hasInputStream = true;
        			}
        	boolean extendsFilter = classNode.superName.equals("java/io/FilterInputStream");
        	if(hasInputStream && hasObjectArr && extendsFilter
        		&& calls.size() == 2 && calls.get(0).superName.equals("java/util/zip/Inflater")
        		&& calls.get(1).superName.equals("java/io/FilterInputStream"))
        	{
        		init = initTemp;
        		decryptor = classNode;
        	}
        }
        if(decryptor != null)
        {
        	Context context = new Context(provider);
    		context.dictionary = classpath;
    		String inflaterClass = null;
    		//Patch
    		for(AbstractInsnNode ain : init.instructions.toArray())
    			if(ain.getOpcode() == Opcodes.INVOKESPECIAL
    				&& ((MethodInsnNode)ain).owner.equals("java/io/PushbackInputStream")
    				&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.INVOKESPECIAL)
    			{
    				init.instructions.insert(ain.getNext(),
    					new FieldInsnNode(Opcodes.PUTFIELD, decryptor.name, "in", "Ljava/io/InputStream;"));
    				init.instructions.insert(ain.getNext(), new InsnNode(Opcodes.SWAP));
    				init.instructions.insert(ain.getNext(), new VarInsnNode(Opcodes.ALOAD, 0));
    				init.instructions.insert(ain, new InsnNode(Opcodes.DUP_X1));
    			}else if(ain.getOpcode() == Opcodes.NEW
    				&& ((TypeInsnNode)ain).desc.equals("java/util/zip/InflaterInputStream"))
    				inflaterClass = ((TypeInsnNode)ain.getNext().getNext().getNext().getNext()).desc;
    		final String inflaterClassF = inflaterClass;
			JavaObject.patchClasses.put(inflaterClass, (r) ->
			{
				CustomInflater inflater = new CustomInflater(r);
				inflater.context = context;
				inflater.inflaterClass = inflaterClassF;
				return inflater;
			});
        	for(Entry<String, byte[]> entry : getDeobfuscator().getInputPassthrough().entrySet())
        	{
        		JavaValue arg = JavaValue.valueOf(new ByteArrayInputStream(entry.getValue()));
        		JavaObject instance = new JavaObject(decryptor.name);
        		MethodExecutor.execute(decryptor, init, Arrays.asList(arg), instance, context);
        		Object in = context.provider.getField(decryptor.name, "in", "Ljava/io/InputStream;", 
        			instance, context);
    			try
    			{
	        		if(!(in instanceof PushbackInputStream) && in instanceof InflaterInputStream)
	        		{
	        			byte[] buffer = new byte[4096];
	    				ByteArrayOutputStream output = new ByteArrayOutputStream();
	        			while(true)
	    				{
	        				int n = (int)context.provider.invokeMethod("java/util/zip/InflaterInputStream", 
	        					"read", "([B)I", new JavaObject(in, "java/util/zip/InflaterInputStream"), 
	        					Arrays.asList(JavaValue.valueOf(buffer)), context);
	        				if(n == -1)
	        					break;
	        				output.write(buffer, 0, n);
	    				}
	    				entry.setValue(output.toByteArray());
	    				output.close();
	    				total.incrementAndGet();
	        		}else if(!(in instanceof PushbackInputStream) && in instanceof FilterInputStream)
	        		{
	        			for(AbstractInsnNode ain : init.instructions.toArray())
        					if(ain.getPrevious() != null && ain.getPrevious().getOpcode() == Opcodes.ALOAD
        					&& ain.getOpcode() == Opcodes.NEW && classes.containsKey(((TypeInsnNode)ain).desc)
        					&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.DUP
        					&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.ALOAD)
        					{
		        				byte[] buffer = new byte[4096];
		        				ByteArrayOutputStream output = new ByteArrayOutputStream();
		        				ClassNode classNode = classes.get(((TypeInsnNode)ain).desc);
		        				MethodNode initclass = classNode.methods.stream().filter(m -> m.name.equals("<init>"))
		        					.findFirst().orElse(null);
		        				JavaObject inst = new JavaObject(classNode.name);
		        				MethodExecutor.execute(classNode, initclass, Arrays.asList(JavaValue.valueOf(in),
		        					instance), inst, context);
		        				MethodNode read = classNode.methods.stream().filter(m -> m.desc.equals("([B)I") && m.name.equals("read"))
		        					.findFirst().orElse(null);
		        				while(true)
		        				{
			        				int n = MethodExecutor.execute(classNode, read, Arrays.asList(JavaValue.valueOf(buffer)), 
			        					inst, context);
			        				if(n == -1)
			        					break;
			        				output.write(buffer, 0, n);
		        				}
		        				entry.setValue(output.toByteArray());
		        				output.close();
		        				total.incrementAndGet();
		        				break;
        					}
	        		}
    			}catch(IOException e)
    			{
    				Utils.sneakyThrow(e);
    			}
        	}
        	JavaObject.patchClasses.remove(inflaterClass);
        	//Cleanup
        	Set<String> remove = new HashSet<>();
        	for(AbstractInsnNode ain : init.instructions.toArray())
        		if(ain.getOpcode() == Opcodes.NEW && classes.get(((TypeInsnNode)ain).desc) != null)
        		remove.add(((TypeInsnNode)ain).desc);
        	Map<ClassNode, Set<MethodNode>> getResource = new HashMap<>();
        	List<MethodNode> toRemove = new ArrayList<>();
        	for(ClassNode classNode : classNodes())
        		if(classNode != decryptor)
        		{
        			for(MethodNode methodNode : classNode.methods)
        			{
        				AbstractInsnNode ain = methodNode.instructions.getFirst();
        				if(methodNode.desc.equals("(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;")
        					&& ain != null && ain.getOpcode() == Opcodes.NEW
        					&& ((TypeInsnNode)ain).desc.equals(decryptor.name))
        				{
        					getResource.putIfAbsent(classNode, new HashSet<>());
        					getResource.get(classNode).add(methodNode);
        				}
        				else if(methodNode.desc.equals("(Ljava/lang/Class;Ljava/lang/String;)Ljava/net/URL;")
        					&& ain != null && ain.getOpcode() == Opcodes.INVOKESTATIC
        					&& methodNode.instructions.getLast().getPrevious() != null
        					&& methodNode.instructions.getLast().getPrevious().getOpcode() == Opcodes.INVOKESPECIAL
        					&& ((MethodInsnNode)methodNode.instructions.getLast().getPrevious()).owner.equals("java/net/URL"))
        				{
        					MethodInsnNode methodInsn = (MethodInsnNode)ain;
        					MethodNode node = classNode.methods.stream().filter(m -> m.name.equals(methodInsn.name)
        						&& m.desc.equals(methodInsn.desc)).findFirst().orElse(null);
        					if(node != null && !toRemove.contains(node))
        						toRemove.add(node);
        					getResource.putIfAbsent(classNode, new HashSet<>());
        					getResource.get(classNode).add(methodNode);
        				}
        			}
        			toRemove.forEach(m -> classNode.methods.remove(m));
        		}
        	for(Entry<ClassNode, Set<MethodNode>> entry : getResource.entrySet())
        		for(MethodNode method : entry.getValue())
	        		if(method.desc.equals("(Ljava/lang/Class;Ljava/lang/String;)Ljava/net/URL;"))
	        			for(AbstractInsnNode ain : method.instructions.toArray())
	        				if(ain.getOpcode() == Opcodes.NEW && classes.containsKey(((TypeInsnNode)ain).desc))
	        				remove.add(((TypeInsnNode)ain).desc);
        	Set<String> add = new HashSet<>();
        	for(String className : remove)
        		for(MethodNode method : classes.get(className).methods)
        			for(AbstractInsnNode ain : method.instructions.toArray())
        				if(ain.getOpcode() == Opcodes.NEW && classes.containsKey(((TypeInsnNode)ain).desc))
        					add.add(((TypeInsnNode)ain).desc);
        	remove.addAll(add);
        	for(ClassNode classNode : classNodes())
        		if(getResource.containsKey(classNode))
        			for(MethodNode methodNode : classNode.methods)
        				for(AbstractInsnNode ain : methodNode.instructions.toArray())
        					if(ain.getOpcode() == Opcodes.INVOKESTATIC
        						&& ((MethodInsnNode)ain).owner.equals(classNode.name))
        					{
        						MethodNode node = getResource.get(classNode).stream().filter(m -> 
        						m.name.equals(((MethodInsnNode)ain).name)
        						&& m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
        						if(node != null)
        						{
        							String name;
        							String desc;
        							if(node.desc.equals("(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;"))
        							{
        								name = "getResourceAsStream";
        								desc = "(Ljava/lang/String;)Ljava/io/InputStream;";
        							}else
        							{
        								name = "getResource";
        								desc = "(Ljava/lang/String;)Ljava/net/URL;";
        							}
        							methodNode.instructions.set(ain, new MethodInsnNode(
        								Opcodes.INVOKEVIRTUAL, "java/lang/Class", name, desc, false));
        						}
        					}
        	remove.add(decryptor.name);
        	remove.forEach(s -> classes.remove(s));
        	for(Entry<ClassNode, Set<MethodNode>> entry : getResource.entrySet())
        		for(MethodNode method : entry.getValue())
        			entry.getKey().methods.remove(method);
        }
        return total.get();
    }
    
    public class CustomInflater extends Inflater
    {
    	public String inflaterClass;
    	public Context context;
    	public Object instance;
    	
    	public CustomInflater(Object instance)
    	{
    		this.instance = instance;
    	}
    	
    	@Override
		public void setInput(byte[] b, int off, int len) 
		{
    		if(Thread.currentThread().getStackTrace()[2].getClassName().startsWith(
    			"com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider"))
    			super.setInput(b, off, len);
    		else
    			MethodExecutor.execute(classes.get(inflaterClass), 
    				classes.get(inflaterClass).methods.stream().filter(m -> m.name.equals("setInput")).findFirst().orElse(null),
    				Arrays.asList(new JavaArray(b), new JavaInteger(off), new JavaInteger(len)), new JavaObject(this, inflaterClass), context);
		}
    }
}
