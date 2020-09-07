package com.javadeobfuscator.deobfuscator.transformers.smoke; 
 
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
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.util.Arrays;
import java.util.HashSet;
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

        for(ClassNode classNode : classes.values())
            for(MethodNode method : classNode.methods)
            {
            	InstructionModifier modifier = new InstructionModifier();
                Frame<SourceValue>[] frames;
                try 
                {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
                }catch(AnalyzerException e) 
                {
                    oops("unexpected analyzer exception", e);
                    continue;
                }

                for(AbstractInsnNode ain : TransformerHelper.instructionIterator(method))
                	if(ain instanceof MethodInsnNode) 
                	{
                        MethodInsnNode m = (MethodInsnNode)ain;
                        String strCl = m.owner;
                        if(m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) 
                        {
                        	Frame<SourceValue> f = frames[method.instructions.indexOf(m)];
                        	if(f.getStack(f.getStackSize() - 2).insns.size() != 1
                        		|| f.getStack(f.getStackSize() - 1).insns.size() != 1)
                        		continue;
                        	AbstractInsnNode a1 = f.getStack(f.getStackSize() - 2).insns.iterator().next();
							AbstractInsnNode a2 = f.getStack(f.getStackSize() - 1).insns.iterator().next();
							if(a1.getOpcode() != Opcodes.LDC || !Utils.isInteger(a2))
								continue;
							Object obfString = ((LdcInsnNode)a1).cst;
							int number = Utils.getIntValue(a2);
    						Context context = new Context(provider);
    						if(classes.containsKey(strCl)) 
    						{
    							ClassNode innerClassNode = classes.get(strCl);
    							MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);
    							if(isSmokeMethod(decrypterNode))
    							{
        							String value = MethodExecutor.execute(classNode, decrypterNode, Arrays.asList(JavaValue.valueOf(obfString), new JavaInteger(number)), null, context);
        							modifier.remove(a2);
        							modifier.remove(a1);
        							modifier.replace(m, new LdcInsnNode(value));
                                    decryptor.add(decrypterNode);
                                    count.getAndIncrement();
    							}
    						}
                        }
                	}
                modifier.apply(method);
            }
        System.out.println("[Smoke] [StringEncryptionTransformer] Decrypted " + count + " encrypted strings");
        System.out.println("[Smoke] [StringEncryptionTransformer] Removed " + cleanup(decryptor) + " decryption methods");
        System.out.println("[Smoke] [StringEncryptionTransformer] Done");
		return count.get() > 0;
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
