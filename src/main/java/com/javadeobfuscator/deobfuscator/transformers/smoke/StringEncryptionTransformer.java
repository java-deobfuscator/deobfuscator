package com.javadeobfuscator.deobfuscator.transformers.smoke; 
 
import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger; 
 
public class StringEncryptionTransformer extends Transformer<TransformerConfig> {
 
    @Override 
    public boolean transform() throws Throwable {
    	DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new MappedFieldProvider());

        AtomicInteger count = new AtomicInteger();
        Set<MethodNode> decryptor = new HashSet<>();

        System.out.println("[Smoke] [StringEncryptionTransformer] Starting");

        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode m = (MethodInsnNode) current;
                        String strCl = m.owner;
                        if (m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) {
        					if (m.getPrevious() != null && Utils.isInteger(m.getPrevious()) 
        						&& m.getPrevious().getPrevious() != null 
        						&& m.getPrevious().getPrevious() instanceof LdcInsnNode
        						&& ((LdcInsnNode)m.getPrevious().getPrevious()).cst instanceof String) {
        						int number = Utils.getIntValue(m.getPrevious());
        						String obfString = (String)((LdcInsnNode)m.getPrevious().getPrevious()).cst;
        						Context context = new Context(provider);
        						if (classes.containsKey(strCl)) {
        							ClassNode innerClassNode = classes.get(strCl);
        							MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
        							if(isSmokeMethod(decrypterNode))
        							{
        								context.push(classNode.name, methodNode.name, getDeobfuscator().getConstantPool(classNode).getSize());
	        							String value = MethodExecutor.execute(classNode, decrypterNode, Arrays.asList(JavaValue.valueOf(obfString), new JavaInteger(number)), null, context);
	                                    methodNode.instructions.remove(m.getPrevious().getPrevious());
	                                    methodNode.instructions.remove(m.getPrevious());
	                                    methodNode.instructions.set(m, new LdcInsnNode(value));
	                                    count.getAndIncrement();
        							}
        						}
        					}else
        					{
        						//Reverse bytecode to try to get the previous args
        						AnalyzerResult result = MethodAnalyzer.analyze(classNode, methodNode);
        						List<AbstractInsnNode> args = new ArrayList<>();
        						for(Frame arg : ((MethodFrame)result.getFrames().get(m).get(0)).getArgs())
        							args.add(result.getInsnNode(arg));
        						if(args.get(0).getOpcode() == Opcodes.LDC
        							&& ((LdcInsnNode)args.get(0)).cst instanceof String
        							&& Utils.isInteger(args.get(1)))
        						{
        							int number = Utils.getIntValue(args.get(1));
            						String obfString = (String)((LdcInsnNode)args.get(0)).cst;
            						Context context = new Context(provider);
            						if(classes.containsKey(strCl)) 
            						{
            							ClassNode innerClassNode = classes.get(strCl);
            							MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
            							if(isSmokeMethod(decrypterNode))
            							{
    	        							String value = MethodExecutor.execute(classNode, decrypterNode, Arrays.asList(JavaValue.valueOf(obfString), new JavaInteger(number)), null, context);
    	                                    methodNode.instructions.remove(args.get(1));
    	                                    methodNode.instructions.remove(args.get(0));
    	                                    methodNode.instructions.set(m, new LdcInsnNode(value));
    	                                    count.getAndIncrement();
            							}
            						}
        						}
        					}
        				}
                    }
                }
            });
        });
        System.out.println("[Smoke] [StringEncryptionTransformer] Decrypted " + count + " encrypted strings");
        System.out.println("[Smoke] [StringEncryptionTransformer] Removed " + cleanup(decryptor) + " decryption methods");
        System.out.println("[Smoke] [StringEncryptionTransformer] Done");
		return true;
    }
    
    private int cleanup(Set<MethodNode> methods)
	{
    	AtomicInteger count = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
            if (classNode.methods.removeIf(methods::contains)) {
                count.getAndIncrement();
            }
        });
        return count.get();
	}

	private boolean isSmokeMethod(MethodNode method)
    {
		boolean containsArray = false;
		int putstatic = 0;
		int getstatic = 0;
    	for(AbstractInsnNode ain : method.instructions.toArray())
    		if(ain.getOpcode() == Opcodes.ANEWARRAY)
    			containsArray = true;
    		else if(ain.getOpcode() == Opcodes.PUTSTATIC || ain.getOpcode() == Opcodes.GETSTATIC)
    			if(((FieldInsnNode)ain).desc.equals("[Ljava/lang/String;"))
    			{
    				if(ain.getOpcode() == Opcodes.PUTSTATIC)
    					putstatic++;
    				else
    					getstatic++;
    			}
    			
    	return containsArray && putstatic == 2 && getstatic == 2;
    }
} 
