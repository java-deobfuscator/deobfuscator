package com.javadeobfuscator.deobfuscator.transformers.smoke;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class NumberObfuscationTransformer extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        Map<String, Integer> numberMethods = new HashMap<>();
        System.out.println("[Smoke] [NumberObfuscationTransformer] Starting");
        
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		for(AbstractInsnNode ain : method.instructions.toArray())
        			if(ain instanceof MethodInsnNode &&
        				((MethodInsnNode) ain).owner.equals("java/lang/String") &&
        				((MethodInsnNode) ain).name.equals("length") &&
        				ain.getPrevious() instanceof LdcInsnNode && ((LdcInsnNode) ain.getPrevious()).cst instanceof String) {
        				AbstractInsnNode previous = ain.getPrevious();
        				method.instructions.set(ain, Utils.getIntInsn(((String)((LdcInsnNode)ain.getPrevious()).cst).length()));
        				method.instructions.remove(previous);
        				count.getAndIncrement();
        			}
        
        for(ClassNode classNode : classes.values())
            for(MethodNode method : classNode.methods)
            {
				Map<AbstractInsnNode, Frame<SourceValue>> frames = new HashMap<>();
				Map<AbstractInsnNode, AbstractInsnNode> replace = new LinkedHashMap<>();
    			try
    			{
    				Frame<SourceValue>[] fr = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
    				for(int i = 0; i < fr.length; i++)
    				{
    					Frame<SourceValue> f = fr[i];
    					frames.put(method.instructions.get(i), f);
    				}
    			}catch(AnalyzerException e)
    			{
    				oops("unexpected analyzer exception", e);
                    continue;
    			}
    			for(AbstractInsnNode ain : method.instructions.toArray())
    			{
    				if(ain.getOpcode() == Opcodes.IADD || ain.getOpcode() == Opcodes.ISUB || ain.getOpcode() == Opcodes.IMUL
    					|| ain.getOpcode() == Opcodes.IDIV || ain.getOpcode() == Opcodes.IREM || ain.getOpcode() == Opcodes.IXOR)
    				{
    					Frame<SourceValue> f = frames.get(ain);
    					SourceValue arg1 = f.getStack(f.getStackSize() - 1);
    					SourceValue arg2 = f.getStack(f.getStackSize() - 2);
    					if(arg1.insns.size() != 1 || arg2.insns.size() != 1)
    						continue;
    					AbstractInsnNode a1 = arg1.insns.iterator().next();
    					AbstractInsnNode a2 = arg2.insns.iterator().next();
    					for(Entry<AbstractInsnNode, AbstractInsnNode> entry : replace.entrySet())
    						if(entry.getKey() == a1)
    							a1 = entry.getValue();
    						else if(entry.getKey() == a2)
    							a2 = entry.getValue();
    					if(Utils.isInteger(a1) && Utils.isInteger(a2))
    					{
	    					Integer resultValue;
	                        if((resultValue = doMath(Utils.getIntValue(a1), Utils.getIntValue(a2), ain.getOpcode())) != null) {
	                        	AbstractInsnNode newValue = Utils.getIntInsn(resultValue);
	                        	replace.put(ain, newValue);
	                            method.instructions.set(ain, newValue);
	                            method.instructions.remove(a1);
	                            method.instructions.remove(a2);
	                            count.getAndAdd(2);
	                        }
    					}
    				}
    			}
            }
    
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		if (Modifier.isStatic(method.access) && method.desc.endsWith("()I")) {
                    int returnCount = Arrays.stream(method.instructions.toArray()).filter(insn -> insn.getOpcode() == Opcodes.IRETURN).collect(Collectors.toList()).size();
                    if (returnCount == 1) {
                        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                            if (insn.getOpcode() == Opcodes.IRETURN) {
                                if (Utils.isInteger(insn.getPrevious())) {
                                    numberMethods.put(classNode.name + method.name + method.desc, Utils.getIntValue(insn.getPrevious()));
                                }
                            }
                        }
                    }
                }
        for(ClassNode classNode : classNodes())
        	for(MethodNode method : classNode.methods)
        		for(int i = 0; i < method.instructions.size(); i++) {
        			AbstractInsnNode ain = method.instructions.get(i);
        			if (ain.getOpcode() == Opcodes.INVOKESTATIC) {
        				Integer number = numberMethods.get(((MethodInsnNode)ain).owner + ((MethodInsnNode)ain).name + ((MethodInsnNode)ain).desc);
        				if (number != null) {
        					method.instructions.set(ain, Utils.getIntInsn(number));
        					count.getAndIncrement();
        				}
        			}
        		}

        classNodes().forEach(classNode ->
        	classNode.methods = classNode.methods.stream().filter(methodNode -> !numberMethods.containsKey(classNode.name + methodNode.name + methodNode.desc)).collect(Collectors.toList()));
	
        System.out.println("[Smoke] [NumberObfuscationTransformer] Removed " + count.get() + " instructions");
        return true;
    }

    private Integer doMath(int value1, int value2, int opcode) {
        switch (opcode) {
            case IADD:
                return value2 + value1;
            case IDIV:
                return value2 / value1;
            case IREM:
                return value2 % value1;
            case ISUB:
                return value2 - value1;
            case IMUL:
                return value2 * value1;
            case IXOR:
                return value2 ^ value1;
        }

        return null;
    }
}
