package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class RedundantGotoRemover extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        AtomicInteger counter = new AtomicInteger();
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		for(int i = 0; i < method.instructions.size(); i++) 
                {
                    AbstractInsnNode node = method.instructions.get(i);
                    if(node.getOpcode() == Opcodes.GOTO) 
                    {
                        AbstractInsnNode a = Utils.getNext(node);
                        AbstractInsnNode b = Utils.getNext(((JumpInsnNode)node).label);
                        if(a == b) 
                        {
                        	method.instructions.remove(node);
                        	counter.incrementAndGet();
                        }else if(b.getOpcode() == Opcodes.GOTO && Utils.getNext(((JumpInsnNode)b).label) == a)
                        {
                        	boolean used = false;
    						loop:
    						for(AbstractInsnNode ain1 : method.instructions.toArray())
    							if(ain1 instanceof JumpInsnNode && ain1 != node
    							&& ((JumpInsnNode)ain1).label == ((JumpInsnNode)node).label)
    							{
    								used = true;
    								break loop;
    							}else if(ain1 instanceof TableSwitchInsnNode 
    								&& (((TableSwitchInsnNode)ain1).labels.contains(((JumpInsnNode)node).label)
    								|| ((TableSwitchInsnNode)ain1).dflt == ((JumpInsnNode)node).label))
    							{
    								used = true;
    								break loop;
    							}else if(ain1 instanceof LookupSwitchInsnNode 
    								&& (((LookupSwitchInsnNode)ain1).labels.contains(((JumpInsnNode)node).label)
    								|| ((LookupSwitchInsnNode)ain1).dflt == ((JumpInsnNode)node).label))
    							{
    								used = true;
    								break loop;
    							}
    						if(!used)
    							loop:
    							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
    								if(tcbn.start == ((JumpInsnNode)node).label || tcbn.end == ((JumpInsnNode)node).label 
    								|| tcbn.handler == ((JumpInsnNode)node).label)
    								{
    									used = true;
    									break loop;
    								}
    						if(!used)
    						{
    							AbstractInsnNode prev = Utils.getPrevious(((JumpInsnNode)node).label);
    							method.instructions.remove(b);
    							method.instructions.remove(((JumpInsnNode)node).label);
    							method.instructions.remove(node);
    							loop:
	    						for(AbstractInsnNode ain1 : method.instructions.toArray())
	    							if(ain1 instanceof JumpInsnNode && ((JumpInsnNode)ain1).label == ((JumpInsnNode)b).label)
	    							{
	    								used = true;
	    								break loop;
	    							}else if(ain1 instanceof TableSwitchInsnNode 
	    								&& (((TableSwitchInsnNode)ain1).labels.contains(((JumpInsnNode)b).label)
	    								|| ((TableSwitchInsnNode)ain1).dflt == ((JumpInsnNode)b).label))
	    							{
	    								used = true;
	    								break loop;
	    							}else if(ain1 instanceof LookupSwitchInsnNode 
	    								&& (((LookupSwitchInsnNode)ain1).labels.contains(((JumpInsnNode)b).label)
	    								|| ((LookupSwitchInsnNode)ain1).dflt == ((JumpInsnNode)b).label))
	    							{
	    								used = true;
	    								break loop;
	    							}
	    						if(!used)
	    							loop:
	    							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
	    								if(tcbn.start == ((JumpInsnNode)b).label || tcbn.end == ((JumpInsnNode)b).label
	    								|| tcbn.handler == ((JumpInsnNode)b).label)
	    								{
	    									used = true;
	    									break loop;
	    								}
	    						if(!used)
	    							method.instructions.remove(((JumpInsnNode)b).label);
	    						if(prev.getOpcode() == Opcodes.GOTO)
	    						{
	    							AbstractInsnNode a2 = Utils.getNext(prev);
	    							AbstractInsnNode b2 = Utils.getNext(((JumpInsnNode)prev).label);
	    							if(a2 == b2) 
	    							{
	    								method.instructions.remove(prev);
	    								used = false;
	    								loop:
	    	    						for(AbstractInsnNode ain1 : method.instructions.toArray())
	    	    							if(ain1 instanceof JumpInsnNode && ((JumpInsnNode)ain1).label == ((JumpInsnNode)prev).label)
	    	    							{
	    	    								used = true;
	    	    								break loop;
	    	    							}else if(ain1 instanceof TableSwitchInsnNode 
	    	    								&& (((TableSwitchInsnNode)ain1).labels.contains(((JumpInsnNode)prev).label)
	    	    								|| ((TableSwitchInsnNode)ain1).dflt == ((JumpInsnNode)prev).label))
	    	    							{
	    	    								used = true;
	    	    								break loop;
	    	    							}else if(ain1 instanceof LookupSwitchInsnNode 
	    	    								&& (((LookupSwitchInsnNode)ain1).labels.contains(((JumpInsnNode)prev).label)
	    	    								|| ((LookupSwitchInsnNode)ain1).dflt == ((JumpInsnNode)prev).label))
	    	    							{
	    	    								used = true;
	    	    								break loop;
	    	    							}
	    	    						if(!used)
	    	    							loop:
	    	    							for(TryCatchBlockNode tcbn : method.tryCatchBlocks)
	    	    								if(tcbn.start == ((JumpInsnNode)prev).label || tcbn.end == ((JumpInsnNode)prev).label
	    	    								|| tcbn.handler == ((JumpInsnNode)prev).label)
	    	    								{
	    	    									used = true;
	    	    									break loop;
	    	    								}
	    	    						if(!used)
	    	    							method.instructions.remove(((JumpInsnNode)prev).label);
	    							}
	    						}
    						}else
    							((JumpInsnNode)node).label = ((JumpInsnNode)b).label;
    						counter.incrementAndGet();
                        }
                    }
                }
        System.out.println("Removed " + counter.get() + " redundant gotos");
		return counter.get() > 0;
    }
}
