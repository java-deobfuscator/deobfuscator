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
import com.javadeobfuscator.deobfuscator.matcher.InstructionMatcher;
import com.javadeobfuscator.deobfuscator.matcher.InstructionPattern;
import com.javadeobfuscator.deobfuscator.matcher.InvocationStep;
import com.javadeobfuscator.deobfuscator.matcher.LoadIntStep;
import com.javadeobfuscator.deobfuscator.matcher.OpcodeStep;
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
    public static final InstructionPattern DECRYPT_PATTERN_LEGACY = new InstructionPattern(
        new OpcodeStep(LDC),
        new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/String;)Ljava/lang/String;", false)
    );
	public static final InstructionPattern DECRYPT_PATTERNV_3 = new InstructionPattern(
        new OpcodeStep(LDC),
        new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;)Ljava/lang/String;", false)
    );
    public static final InstructionPattern DECRYPT_PATTERNV_31 = new InstructionPattern(
        new OpcodeStep(LDC),
        new InvocationStep(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false),
        new OpcodeStep(DUP),
        new OpcodeStep(DUP),
        new LoadIntStep(),
        new OpcodeStep(DUP_X1),
        new OpcodeStep(CALOAD),
        new LoadIntStep(),
        new OpcodeStep(IXOR),
        new OpcodeStep(I2C),
        new OpcodeStep(CASTORE),
        new LoadIntStep(),
        new LoadIntStep(),
        new OpcodeStep(ISHL),
        new LoadIntStep(),
        new OpcodeStep(IOR),
        new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;I)Ljava/lang/String;", false)
    );
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
                        //Stringer 3.1
                        InstructionMatcher matcher = DECRYPT_PATTERNV_31.matcher(currentInsn);
                        if (matcher.find() && matcher.getCapturedInstructions("all").get(0) == currentInsn) {
                            MethodInsnNode m = (MethodInsnNode) matcher.getCapturedInstructions("all").get(16);
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
                        InstructionMatcher matcher1 = DECRYPT_PATTERNV_3.matcher(currentInsn);
                        boolean switched = false;
                        if(!matcher1.find())
                        {
                        	matcher1 = DECRYPT_PATTERN_LEGACY.matcher(currentInsn);
                        	switched = true;
                        }
                        if ((!switched || matcher1.find()) && matcher1.getCapturedInstructions("all").get(0) == currentInsn) {
                            LdcInsnNode ldc = (LdcInsnNode) matcher1.getCapturedInstructions("all").get(0);
                            MethodInsnNode m = (MethodInsnNode) matcher1.getCapturedInstructions("all").get(1);
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
                Map<LabelNode, LabelNode> cloneMap = Utils.generateCloneMap(methodNode.instructions);
                for (int insnIndex = 0; insnIndex < methodInsns.size(); insnIndex++) {
                    AbstractInsnNode currentInsn = methodInsns.get(insnIndex);
                    if (currentInsn != null) {
                        //Stringer 3.1
                    	InstructionMatcher matcher = DECRYPT_PATTERNV_31.matcher(currentInsn);
                        if (matcher.find() && matcher.getCapturedInstructions("all").get(0) == currentInsn) {
                    		MethodInsnNode m = (MethodInsnNode) matcher.getCapturedInstructions("all").get(16);
                            String strCl = m.owner;
                            Type type = Type.getType(m.desc);
                            if (type.getArgumentTypes().length == 2 && type.getReturnType().getDescriptor().equals("Ljava/lang/String;") && classes.containsKey(strCl)) {
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
                                        MethodNode decryptorMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodNode.name, "()Ljava/lang/String;", null, null);
                                        for (AbstractInsnNode matched : matcher.getCapturedInstructions("all")) {
                                            decryptorMethod.instructions.add(matched.clone(cloneMap));
                                        }
                                        decryptorMethod.instructions.add(new InsnNode(ARETURN));
                                        String result = MethodExecutor.execute(classNode, decryptorMethod, Arrays.asList(), null, context);
                                        for(int i = 0; i < matcher.getCapturedInstructions("all").size() - 1; i++)
                                        	methodNode.instructions.remove(matcher.getCapturedInstructions("all").get(i));
                                        methodNode.instructions.set(matcher.getCapturedInstructions("all").get(
                                        	matcher.getCapturedInstructions("all").size() - 1), new LdcInsnNode(result));
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
                        InstructionMatcher matcher1 = DECRYPT_PATTERNV_3.matcher(currentInsn);
                        boolean switched = false;
                        if(!matcher1.find())
                        {
                        	matcher1 = DECRYPT_PATTERN_LEGACY.matcher(currentInsn);
                        	switched = true;
                        }
                        if ((!switched || matcher1.find()) && matcher1.getCapturedInstructions("all").get(0) == currentInsn) {
                            LdcInsnNode ldc = (LdcInsnNode) matcher1.getCapturedInstructions("all").get(0);
                            MethodInsnNode m = (MethodInsnNode) matcher1.getCapturedInstructions("all").get(1);
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
                                    InstructionMatcher matcher = DECRYPT_PATTERNV_3.matcher(innerCurrentInsn);
                                    boolean switched = false;
                                    if(!matcher.find())
                                    {
                                    	matcher = DECRYPT_PATTERN_LEGACY.matcher(innerCurrentInsn);
                                    	switched = true;
                                    }
                                    if ((!switched || matcher.find()) && matcher.getCapturedInstructions("all").get(0) == innerCurrentInsn) {
                                        LdcInsnNode innerLdc = (LdcInsnNode) matcher.getCapturedInstructions("all").get(0);
                                        MethodInsnNode innerMethod = (MethodInsnNode) matcher.getCapturedInstructions("all").get(1);
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
