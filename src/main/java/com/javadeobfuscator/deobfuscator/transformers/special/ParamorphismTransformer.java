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

package com.javadeobfuscator.deobfuscator.transformers.special;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.assertj.core.internal.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class ParamorphismTransformer extends Transformer<TransformerConfig>
{
	public static boolean OVERRIDE = true;
	
	@Override
	public boolean transform()
	{
		DelegatingProvider provider = new DelegatingProvider();
		provider.register(new PrimitiveFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new MappedFieldProvider());
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
            	if(target.type().equals("Ljava/lang/Class;")
            		&& type.getDescriptor().equals("Ljava/lang/Class;"))
            		return true;
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                if (type.getDescriptor().equals("[C")) {
                    if (!(target.value() instanceof char[])) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
            	if(type.getDescriptor().equals("Ljava/lang/Class;"))
            		return true;
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
		System.out.println("[Special] [ParamorphismTransformer] Starting");
		if(getDeobfuscator().invaildClasses.isEmpty() && !OVERRIDE)
		{
			System.out.println("[Special] [ParamorphismTransformer] Paramorphism not found or option not enabled. Exiting");
			return false;
		}
		AtomicInteger annotRemoved = new AtomicInteger();
		AtomicInteger flow = new AtomicInteger();
		AtomicInteger methodCalls = new AtomicInteger();
		AtomicInteger fieldCalls = new AtomicInteger();
		AtomicInteger stringCalls = new AtomicInteger();
        //Remove annotations
		for(ClassNode classNode : classNodes())
		{
			if(classNode.invisibleAnnotations != null)
			{
				Iterator<AnnotationNode> itr = classNode.invisibleAnnotations.iterator();
				while(itr.hasNext())
				{
					AnnotationNode next = itr.next();
					if(next.desc != null && next.desc.length() > 50)
					{
						itr.remove();
						annotRemoved.incrementAndGet();
					}						
				}
			}
			for(FieldNode field : classNode.fields)
				if(field.invisibleAnnotations != null)
				{
					Iterator<AnnotationNode> itr = field.invisibleAnnotations.iterator();
					while(itr.hasNext())
					{
						AnnotationNode next = itr.next();
						if(next.desc != null && next.desc.length() > 50)
						{
							itr.remove();
							annotRemoved.incrementAndGet();
						}
					}
				}
			for(MethodNode method : classNode.methods)
				if(method.invisibleAnnotations != null)
				{
					Iterator<AnnotationNode> itr = method.invisibleAnnotations.iterator();
					while(itr.hasNext())
					{
						AnnotationNode next = itr.next();
						if(next.desc != null && next.desc.length() > 50)
						{
							itr.remove();
							annotRemoved.incrementAndGet();
						}
					}
				}
		}
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
				for(AbstractInsnNode ain : method.instructions.toArray())
					if((Modifier.isStatic(method.access) || method.name.equals("<init>")) && ain.getOpcode() == Opcodes.NEW
					&& ((TypeInsnNode)ain).desc.equals("java/lang/Object")
					&& ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.DUP
					&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.INVOKESPECIAL
					&& ((MethodInsnNode)ain.getNext().getNext()).owner.equals("java/lang/Object")
					&& ((MethodInsnNode)ain.getNext().getNext()).name.equals("<init>")
					&& ain.getNext().getNext().getNext() != null
					&& ain.getNext().getNext().getNext().getOpcode() == Opcodes.DUP
					&& ain.getNext().getNext().getNext().getNext() != null
					&& ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IFNONNULL)
					{
						AbstractInsnNode next = ain.getNext().getNext().getNext().getNext().getNext();
						while(next.getNext() != null && next.getNext().getOpcode() == Opcodes.INVOKEINTERFACE)
							next = next.getNext();
						if(next.getNext() == null || !((next.getNext().getOpcode() >= Opcodes.IRETURN &&
							next.getNext().getOpcode() <= Opcodes.RETURN) || next.getNext().getOpcode() == Opcodes.ATHROW))
							continue;
						AbstractInsnNode end = next.getNext();
						LabelNode lbl = ((JumpInsnNode)ain.getNext().getNext().getNext().getNext()).label;
						method.instructions.remove(lbl.getNext());
						while(ain.getNext() != end)
							method.instructions.remove(ain.getNext());
						method.instructions.remove(end);
						method.instructions.remove(ain);
						if(method.instructions.contains(lbl))
							method.instructions.remove(lbl);
						flow.incrementAndGet();
					}else if(!Modifier.isStatic(method.access) && !method.name.equals("<init>") && ain.getOpcode() == Opcodes.ALOAD
						&& ((VarInsnNode)ain).var == 0 && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.DUP
						&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.IFNONNULL)
					{
						AbstractInsnNode next = ain.getNext().getNext().getNext();
						while(next.getNext() != null && next.getNext().getOpcode() == Opcodes.INVOKEINTERFACE)
							next = next.getNext();
						if(next.getNext() == null || !((next.getNext().getOpcode() >= Opcodes.IRETURN &&
							next.getNext().getOpcode() <= Opcodes.RETURN) || next.getNext().getOpcode() == Opcodes.ATHROW))
							continue;
						AbstractInsnNode end = next.getNext();
						LabelNode lbl = ((JumpInsnNode)ain.getNext().getNext()).label;
						method.instructions.remove(lbl.getNext());
						while(ain.getNext() != end)
							method.instructions.remove(ain.getNext());
						method.instructions.remove(end);
						method.instructions.remove(ain);
						if(method.instructions.contains(lbl))
							method.instructions.remove(lbl);
						flow.incrementAndGet();
					}
		//Remove goto switchup
		for(ClassNode classNode : classNodes())
			for(MethodNode method : classNode.methods)
			{
				boolean modified = false;
				for(AbstractInsnNode ain : method.instructions.toArray())
				{
					boolean passed = false;
					List<LabelNode> labels = new ArrayList<>();
					while((passed ? ain.getOpcode() == Opcodes.GOTO : ain instanceof JumpInsnNode)
						&& ((JumpInsnNode)ain).label.getNext() != null && ((JumpInsnNode)ain).label.getNext().getOpcode() == Opcodes.GOTO)
					{
						if(labels.contains(((JumpInsnNode)ain).label))
							break;
						labels.add(((JumpInsnNode)ain).label);
						((JumpInsnNode)ain).label = ((JumpInsnNode)((JumpInsnNode)ain).label.getNext()).label;
						flow.incrementAndGet();
						passed = true;
						modified = true;
					}
				}
				if(modified)
				{
					InstructionModifier modifier = new InstructionModifier();

	                Frame<BasicValue>[] frames;
					try
					{
						frames = new Analyzer<>(new BasicInterpreter()).analyze(classNode.name, method);
					}catch(AnalyzerException e)
					{
						throw new RuntimeException(e);
					}
	                for(int i = 0; i < method.instructions.size(); i++)
	                {
	                    if(!Utils.isInstruction(method.instructions.get(i))) continue;
	                    if(frames[i] != null) continue;

	                    modifier.remove(method.instructions.get(i));
	                }

	                modifier.apply(method);

	                // empty try catch nodes are illegal
	                if(method.tryCatchBlocks != null)
	                	method.tryCatchBlocks.removeIf(tryCatchBlockNode -> Utils.getNext(tryCatchBlockNode.start) ==
	                	Utils.getNext(tryCatchBlockNode.end));
					for(AbstractInsnNode node : method.instructions.toArray())
						 if(node.getOpcode() == Opcodes.GOTO) 
		                    {
		                        AbstractInsnNode a = Utils.getNext(node);
		                        AbstractInsnNode b = Utils.getNext(((JumpInsnNode)node).label);
		                        if(a == b) 
		                        	method.instructions.remove(node);
		                    }
				}
			}
		//Decrypt classes
		Set<String> toRemove = new HashSet<>();
		for(ClassNode classNode : classNodes())
		{
			MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
			AbstractInsnNode first = clinit == null ? null : clinit.instructions.getFirst();
			if(first != null && first.getOpcode() == Opcodes.NEW
				&& first.getNext() != null && first.getNext().getOpcode() == Opcodes.DUP
				&& first.getNext().getNext() != null && first.getNext().getNext().getOpcode() == Opcodes.LDC
				&& ((LdcInsnNode)first.getNext().getNext()).cst instanceof Type
				&& first.getNext().getNext().getNext() != null
				&& first.getNext().getNext().getNext().getOpcode() == Opcodes.INVOKEVIRTUAL
				&& ((MethodInsnNode)first.getNext().getNext().getNext()).name.equals("getClassLoader")
				&& ((MethodInsnNode)first.getNext().getNext().getNext()).owner.equals("java/lang/Class")
				&& first.getNext().getNext().getNext().getNext() != null
				&& first.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.LDC
				&& first.getNext().getNext().getNext().getNext().getNext() != null
				&& Utils.isInteger(first.getNext().getNext().getNext().getNext().getNext()))
			{
				int len = Utils.getIntValue(first.getNext().getNext().getNext().getNext().getNext());
				byte[] encrypted = getDeobfuscator().getInputPassthrough().get(((LdcInsnNode)first.getNext().getNext().getNext().getNext()).cst);
				if(encrypted == null)
				{
					System.out.println("Encrypted file not found for " + classNode.name);
					continue;
				}
				byte[] decrypted = new byte[len];
				try
				{
					DataInputStream dataStream = new DataInputStream(new ByteArrayInputStream(encrypted));
					dataStream.readFully(decrypted, 0, dataStream.available());
					for(int i = 7; i >= 0; i--)
			            decrypted[7 - i] = (byte)((int)(2272919233031569408L >> 8 * i & 255L));
			         GZIPInputStream gzipStream = new GZIPInputStream(new ByteArrayInputStream(decrypted.clone()));
			         dataStream = new DataInputStream(gzipStream);
			         dataStream.readFully(decrypted);
			         gzipStream.close();
			         getDeobfuscator().getInputPassthrough().remove(((LdcInsnNode)first.getNext().getNext().getNext().getNext()).cst);
			         toRemove.add(((TypeInsnNode)first).desc);//Loader
			         toRemove.add(classNode.name);//Interface impl
			         ClassReader reader = new ClassReader(decrypted);
			         ClassNode node = new ClassNode();
			         reader.accept(node, ClassReader.SKIP_FRAMES);
			         toRemove.add(node.interfaces.get(0));//Interface
			         //Decrypt all
			         Map<MethodNode, AbstractInsnNode> replacements = new HashMap<>();
			         JavaValue instance = JavaValue.valueOf(new Object());
			         for(MethodNode method : node.methods)
			         {
			        	 AbstractInsnNode mode = null;
			        	 for(AbstractInsnNode ain : method.instructions.toArray())
			        		 if(ain instanceof FieldInsnNode || ain instanceof MethodInsnNode)
			        		 {
			        			 mode = ain;
			        			 break;
			        		 }else if(ain instanceof LdcInsnNode && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.ARETURN)
			        		 {
			        			 mode = ain;
			        			 break;
			        		 }
			        	 if(mode == null)
			        		 throw new IllegalStateException("Could not find decryptor for method");
			        	 Context context = new Context(provider);
			        	 if(mode.getOpcode() == Opcodes.GETFIELD && ((FieldInsnNode)mode).owner.equals(node.name))
			        	 {
			        		 String res = MethodExecutor.execute(node, method, Arrays.asList(), instance, context);
			        		 replacements.put(method, new LdcInsnNode(res));
			        	 }else if(mode instanceof FieldInsnNode || mode instanceof MethodInsnNode)
			        		 replacements.put(method, mode.clone(null));
			        	 else if(mode instanceof LdcInsnNode)
			        		 replacements.put(method, mode.clone(null));
			         }
			         for(ClassNode cn : classNodes())
			        	 for(MethodNode mn : cn.methods)
			        		 for(AbstractInsnNode ain : mn.instructions.toArray())
			        			 if(ain instanceof MethodInsnNode && ((MethodInsnNode)ain).owner.equals(classNode.name))
			        			 {
			        				 MethodNode caller = node.methods.stream().filter(m -> m.name.equals(((MethodInsnNode)ain).name)
			        					 && m.desc.equals(((MethodInsnNode)ain).desc)).findFirst().orElse(null);
			        				 mn.instructions.set(ain, replacements.get(caller).clone(null));
			        				 if(replacements.get(caller) instanceof LdcInsnNode)
			        					 stringCalls.incrementAndGet();
			        				 else if(replacements.get(caller) instanceof MethodInsnNode)
			        					 methodCalls.incrementAndGet();
			        				 else if(replacements.get(caller) instanceof FieldInsnNode)
			        					 fieldCalls.incrementAndGet();
			        			 }
				}catch(Exception e)
				{
					e.printStackTrace();
					System.out.println("Decryption failed for " + classNode.name);
					continue;
				}
			}
		}
		for(String s : toRemove)
		{
			classes.remove(s);
			classpath.remove(s);
		}
		System.out.println("[Special] [ParamorphismTransformer] Removed " + annotRemoved + " annotations");
		System.out.println("[Special] [ParamorphismTransformer] Removed " + flow + " flow obfuscations");
		System.out.println("[Special] [ParamorphismTransformer] Decrypted " + methodCalls + " method calls");
		System.out.println("[Special] [ParamorphismTransformer] Decrypted " + fieldCalls + " field calls");
		System.out.println("[Special] [ParamorphismTransformer] Decrypted " + stringCalls + " strings");
		return methodCalls.get() > 0 || fieldCalls.get() > 0 || stringCalls.get() > 0;
	}
}
