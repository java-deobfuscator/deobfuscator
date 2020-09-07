package com.javadeobfuscator.deobfuscator.transformers.allatori;

import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class LightFlowObfuscationTransformer extends Transformer<TransformerConfig> 
{
	@Override 
    public boolean transform() throws Throwable {
    	DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new MappedFieldProvider());

        AtomicInteger fixed = new AtomicInteger();

        System.out.println("[Allatori] [LightFlowObfuscationTransformer] Starting");
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        	{
        		boolean modified;
        		do
        		{
        			modified = false;
	        		for(AbstractInsnNode ain : method.instructions.toArray())
	        			if((willPush(ain) || ain.getOpcode() == Opcodes.DUP) && ain.getNext() != null 
	        			&& (willPush(ain.getNext()) || ain.getNext().getOpcode() == Opcodes.DUP)
	        				&& ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.POP2)
	        			{
	        				method.instructions.remove(ain.getNext().getNext());
	        				method.instructions.remove(ain.getNext());
	        				method.instructions.remove(ain);
	        				modified = true;
	        				fixed.incrementAndGet();
	        			}
        		}while(modified);
        	}
        System.out.println("[Allatori] [LightFlowObfuscationTransformer] Removed " + fixed + " dead instructions");
        System.out.println("[Allatori] [LightFlowObfuscationTransformer] Done");
        return fixed.get() > 0;
	}
	
	private boolean willPush(AbstractInsnNode ain)
    {
    	if(ain.getOpcode() == Opcodes.LDC && (((LdcInsnNode)ain).cst instanceof Long || ((LdcInsnNode)ain).cst instanceof Double))
    		return false;
    	return Utils.willPushToStack(ain.getOpcode()) && ain.getOpcode() != Opcodes.GETSTATIC
    		&& ain.getOpcode() != Opcodes.LLOAD && ain.getOpcode() != Opcodes.DLOAD;
    }
}
