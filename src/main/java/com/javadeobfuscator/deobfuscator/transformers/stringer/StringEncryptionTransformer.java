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
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@TransformerConfig.ConfigOptions(configClass = StringEncryptionTransformer.Config.class)
public class StringEncryptionTransformer extends Transformer<StringEncryptionTransformer.Config> {

	private List<ClassNode> decryptors = new ArrayList<>();
	
    @Override
    public boolean transform() {
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
        return true;
    }

    private int cleanup() {
        AtomicInteger total = new AtomicInteger();
        Set<String> remove = new HashSet<>();
        if(getConfig().shouldRemoveAllStringerClasses())
	        classNodes().forEach(classNode -> {
	            boolean method = false;
	            boolean field = false;
	            for (MethodNode node : classNode.methods) {
	                if (node.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
	                    method = true;
	                } else if (node.desc.equals("(Ljava/io/InputStream;)V")) { //Don't delete resource decryptors yet
	                    method = false;
	                    break;
	                } else if ((node.desc.equals("(Ljava/lang/Object;I)Ljava/lang/String;") || node.desc.equals("(Ljava/lang/Object;)Ljava/lang/String;")) && classNode.superName.equals("java/lang/Thread")) {
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
        decryptors.forEach(classNode ->
        {
        	boolean resource = false;
        	for(MethodNode node : classNode.methods)
        		if(node.desc.equals("(Ljava/io/InputStream;)V")) //Don't delete resource decryptors yet
        		{
        			resource = true;
        			break;
        		}
        	if(!resource)
        		remove.add(classNode.name);
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
        for (ClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                        //Counts stringer 3.1.0+ encryption
                        if (getStringerInsns(currentInsn) != null) {
                            MethodInsnNode m = (MethodInsnNode) currentInsn;
                            String strCl = m.owner;
                            Type type = Type.getType(m.desc);
                            if (type.getArgumentTypes().length == 2 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                ClassNode innerClassNode = classes.get(strCl);
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
                                    ClassNode innerClassNode = classes.get(strCl);
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);
                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                            count.getAndIncrement();
                                        }
                                    }
                                }
                            }
                        }else if (currentInsn.getOpcode() == Opcodes.GETSTATIC && currentInsn.getNext() instanceof MethodInsnNode) {
                        	FieldInsnNode fieldNode = (FieldInsnNode) currentInsn;
                        	boolean containsput = false;
                        	MethodNode clinit = classNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                        	if (clinit != null) 
                        		for (AbstractInsnNode ain : clinit.instructions.toArray())
                        			if (ain.getOpcode() == Opcodes.PUTSTATIC && ((FieldInsnNode) ain).name.equals(fieldNode.name) && ((FieldInsnNode) ain).desc.equals(fieldNode.desc) && ain.getPrevious() != null && ain.getPrevious().getOpcode() == Opcodes.LDC) {
                        				containsput = true;
                        				break;
                        			}
                        	if(containsput) {
	                        	MethodInsnNode m = (MethodInsnNode) currentInsn.getNext();
	                        	String strCl = m.owner;
	                            Type type = Type.getType(m.desc);
	                            if (type.getArgumentTypes().length == 1 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
	                                ClassNode innerClassNode = classes.get(strCl);
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

        for (ClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                        //Latest Stringer (3.1.0+)
                    	if (getStringerInsns(currentInsn) != null) {
                    		MethodInsnNode m = (MethodInsnNode) currentInsn;
                            String strCl = m.owner;
                            Type type = Type.getType(m.desc);
                            if (type.getArgumentTypes().length == 2 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                ClassNode innerClassNode = classes.get(strCl);
                                FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);

                                if (signature != null) {
                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                    List<AbstractInsnNode> stringerInsns = getStringerInsns(currentInsn);
                                    AbstractInsnNode ldcNode = stringerInsns.get(16);
                                    //There are 17 instructions to keep before we get the result
                                    //Remove everything before and after
                                    List<AbstractInsnNode> before = new ArrayList<>();
                                    List<AbstractInsnNode> after = new ArrayList<>();
                                    //Added in reverse (both)
                                    //Remove before
                                    for (int i = methodInsns.indexOf(ldcNode) - 1; i >= 0; i--)
                                        before.add(methodInsns.get(i));
                                    //Remove after
                                    for (int i = methodInsns.indexOf(stringerInsns.get(0)) + 1; i < methodInsns.size(); i++)
                                        after.add(methodInsns.get(i));
                                    for (AbstractInsnNode ain : before)
                                        methodInsns.remove(ain);
                                    for (AbstractInsnNode ain : after)
                                        methodInsns.remove(ain);
                                    Collections.reverse(before);
                                    Collections.reverse(after);
                                    if (decrypterNode != null) {
                                    	decryptors.add(innerClassNode);
                                        Context context = new Context(provider);
                                        context.dictionary = classpath;
                                        context.constantPools = getDeobfuscator().getConstantPools();
                                        context.push(classNode.name.replace('/', '.'), methodNode.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                        context.file = getDeobfuscator().getConfig().getInput();
                                        // Stringer3
                                        if (innerClassNode.superName.equals("java/lang/Thread")) {
                                            MethodNode clinitMethod = classes.get(strCl).methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                            if (clinitMethod != null) {
                                                MethodExecutor.execute(classes.get(strCl), clinitMethod, Collections.emptyList(), null, context);
                                            }
                                        }
                                        //Works for all methods, even those who aren't supposed to return value
                                        AbstractInsnNode returnNode;
                                        methodInsns.add(returnNode = new InsnNode(Opcodes.ARETURN));
                                        String result = MethodExecutor.execute(classNode, methodNode, Arrays.asList(), null, context);
                                        //Add all the instructions back
                                        for (AbstractInsnNode ain : before)
                                            methodInsns.insertBefore(ldcNode, ain);
                                        for (AbstractInsnNode ain : after)
                                            methodInsns.insert(methodInsns.get(insnIndex), ain);
                                        methodInsns.remove(returnNode);
                                        //The result is added to the method (node with insnIndex is not removed, it is replaced)
                                        for (int i = 0; i < stringerInsns.size() - 1; i++)
                                            methodInsns.remove(stringerInsns.get(i));
                                        LdcInsnNode resultNode;
                                        methodInsns.set(ldcNode, resultNode = new LdcInsnNode(result));
                                        //Sets it back
                                        insnIndex = methodInsns.indexOf(resultNode);
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
                    	if (getLegacyStringerInsns(currentInsn) != null) {
                            LdcInsnNode ldc = (LdcInsnNode) currentInsn;
                            MethodInsnNode m = (MethodInsnNode) getLegacyStringerInsns(currentInsn).get(1);
                            if (ldc.cst instanceof String) {
                                String strCl = m.owner;
                                Type type = Type.getType(m.desc);
                                if (type.getArgumentTypes().length == 1 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
                                    ClassNode innerClassNode = classes.get(strCl);
                                    FieldNode signature = innerClassNode.fields.stream().filter(fn -> fn.desc.equals("[Ljava/lang/Object;")).findFirst().orElse(null);

                                    if (signature != null) {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc) && Modifier.isStatic(mn.access)).findFirst().orElse(null);
                                        if (decrypterNode != null) {
                                        	decryptors.add(innerClassNode);
                                            Context context = new Context(provider);
                                            context.dictionary = classpath;
                                            context.constantPools = getDeobfuscator().getConstantPools();
                                            context.push(classNode.name.replace('/', '.'), methodNode.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                            context.file = getDeobfuscator().getConfig().getInput();

                                            // Stringer3
                                            if (innerClassNode.superName.equals("java/lang/Thread")) {
                                                MethodNode clinitMethod = classes.get(strCl).methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                                if (clinitMethod != null) {
                                                    MethodExecutor.execute(classes.get(strCl), clinitMethod, Collections.emptyList(), null, context);
                                                }
                                            }

                                            Object o = null;
                                            try {
                                                o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Collections.singletonList(new JavaObject(ldc.cst, "java/lang/String")), null, context);
                                            } catch (ArrayIndexOutOfBoundsException e) {
                                                enhanced.put(ldc, classNode.name + " " + methodNode.name);
                                            }
                                            if (o != null) {
                                                ldc.cst = (String) o;
                                                methodNode.instructions.remove(m);
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
        for (ClassNode classNode : classNodes()) {
            for (MethodNode methodNode : classNode.methods) {
                InsnList methodInsns = methodNode.instructions;
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) currentInsn;
                        ClassNode targetClassNode = classes.get(m.owner);
                        if (targetClassNode != null) {
                            MethodNode targetMethodNode = null;
                            for (MethodNode tempMethodNode : targetClassNode.methods) {
                                if (tempMethodNode.name.equals(m.name) && tempMethodNode.desc.equals(m.desc)) {
                                    targetMethodNode = tempMethodNode;
                                    break;
                                }
                            }
                            if (targetMethodNode != null) {
                                InsnList innerMethodInsns = targetMethodNode.instructions;
                                for (int innerInsnIndex = 0; innerInsnIndex < innerMethodInsns.size(); innerInsnIndex++) {
                                    AbstractInsnNode innerCurrentInsn = innerMethodInsns.get(innerInsnIndex);
                                    if (getLegacyStringerInsns(innerCurrentInsn) != null) {
                                        LdcInsnNode innerLdc = (LdcInsnNode) innerCurrentInsn;
                                        MethodInsnNode innerMethod = (MethodInsnNode) getLegacyStringerInsns(innerCurrentInsn).get(1);
                                        if (innerLdc.cst instanceof String) {
                                            String strCl = innerMethod.owner;
                                            if (innerMethod.desc.endsWith(")Ljava/lang/String;")) {
                                                if (enhanced.remove(innerLdc) != null) {
                                                    ClassNode innerClassNode = classes.get(strCl);
                                                    decryptors.add(innerClassNode);
                                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(innerMethod.name) && mn.desc.equals(innerMethod.desc)).findFirst().orElse(null);
                                                    Context context = new Context(provider);
                                                    context.push(classNode.name.replace('/', '.'), methodNode.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                                    context.push(targetClassNode.name.replace('/', '.'), targetMethodNode.name, getDeobfuscator().getConstantPool(targetClassNode).getSize());
                                                    context.dictionary = classpath;
                                                    context.constantPools = getDeobfuscator().getConstantPools();
                                                    Object o = MethodExecutor.execute(classes.get(strCl), decrypterNode, Arrays.asList(new JavaObject(innerLdc.cst, "java/lang/String")), null, context);
                                                    innerLdc.cst = o;
                                                    targetMethodNode.instructions.remove(innerMethod);
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
    
    private List<AbstractInsnNode> getStringerInsns(AbstractInsnNode now)
    {
    	List<AbstractInsnNode> insns = new ArrayList<>();
    	if(now.getOpcode() != Opcodes.INVOKESTATIC)
    		return null;
    	AbstractInsnNode previous = now;
    	while(previous != null)
    	{
    		if(Utils.isInstruction(previous))
    		{
    			if(previous.getOpcode() == Opcodes.GOTO
    				&& ((JumpInsnNode)previous).label == previous.getNext())
    			{
    				previous = previous.getPrevious();
    				continue;
    			}
    			insns.add(previous);
    			if(insns.size() >= 17)
    				break;
    		}
    		previous = previous.getPrevious();
    	}
    	if(insns.size() < 17)
    		return null;
    	if(insns.get(1).getOpcode() == Opcodes.IOR
    		&& Utils.isInteger(insns.get(2))
    		&& insns.get(3).getOpcode() == Opcodes.ISHL
    		&& Utils.isInteger(insns.get(4))
    		&& Utils.isInteger(insns.get(5))
    		&& insns.get(6).getOpcode() == Opcodes.CASTORE
    		&& insns.get(7).getOpcode() == Opcodes.I2C
    		&& insns.get(8).getOpcode() == Opcodes.IXOR
    		&& Utils.isInteger(insns.get(9))
    		&& insns.get(10).getOpcode() == Opcodes.CALOAD
    		&& insns.get(11).getOpcode() == Opcodes.DUP_X1
    		&& Utils.isInteger(insns.get(12))
    		&& insns.get(13).getOpcode() == Opcodes.DUP
    		&& insns.get(14).getOpcode() == Opcodes.DUP
    		&& insns.get(15).getOpcode() == Opcodes.INVOKEVIRTUAL
    		&& ((MethodInsnNode)insns.get(15)).name.equals("toCharArray")
    		&& ((MethodInsnNode)insns.get(15)).owner.equals("java/lang/String")
    		&& insns.get(16).getOpcode() == Opcodes.LDC)
    		return insns;
    	return null;
    }
    
    private List<AbstractInsnNode> getLegacyStringerInsns(AbstractInsnNode now)
    {
    	List<AbstractInsnNode> insns = new ArrayList<>();
    	if(now.getOpcode() != Opcodes.LDC)
    		return null;
    	AbstractInsnNode next = now;
    	while(next != null)
    	{
    		if(Utils.isInstruction(next))
    		{
    			if(next.getOpcode() == Opcodes.GOTO
    				&& ((JumpInsnNode)next).label == next.getNext())
    			{
    				next = next.getNext();
    				continue;
    			}
    			insns.add(next);
    			if(insns.size() >= 2)
    				break;
    		}
    		next = next.getNext();
    	}
    	if(insns.size() < 2)
    		return null;
    	if(insns.get(1).getOpcode() == Opcodes.INVOKESTATIC)
    		return insns;
    	return null;
    }
    
    public static class Config extends TransformerConfig 
	{
		/**
		 * Should we remove all Stringer classes, regardless if they
		 * are called or not?
		 * If you are dealing with multiple layers of stringer, it is best to keep this off.
		 */
        private boolean removeAllStringerClasses = true;

        public Config() 
        {
            super(StringEncryptionTransformer.class);
        }

        public boolean shouldRemoveAllStringerClasses() 
        {
            return removeAllStringerClasses;
        }

        public void setRemoveAllStringerClasses(boolean removeAllStringerClasses) 
        {
            this.removeAllStringerClasses = removeAllStringerClasses;
        }
    }
}
