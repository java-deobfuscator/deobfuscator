package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

@TransformerConfig.ConfigOptions(configClass = EnumNormalizer.Config.class)
public class EnumNormalizer extends AbstractNormalizer<EnumNormalizer.Config>
{
    @Override
    public void remap(CustomRemapper remapper)
    {
        for(ClassNode classNode : classNodes())
        {
        	if(!classNode.superName.equals("java/lang/Enum"))
        		continue;
        	MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
        	if(clinit != null && clinit.instructions != null && clinit.instructions.getFirst() != null)
        	{
        		//Fix order
        		LinkedHashMap<LabelNode, List<AbstractInsnNode>> result = new FlowAnalyzer(clinit).analyze(clinit.instructions.getFirst(),
        			new ArrayList<>(), new HashMap<>(), false, true);
        		List<FieldNode> order = new ArrayList<>();
        		FieldNode valuesArr = null;
        		boolean hasDuplicate = false;
        		for(List<AbstractInsnNode> insns : result.values())
        			for(AbstractInsnNode ain : insns)
        				if(ain.getOpcode() == Opcodes.PUTSTATIC
        					&& Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.OBJECT
                    		&& Type.getType(((FieldInsnNode)ain).desc).getInternalName().equals(classNode.name))
        				{
        					FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
        						&& Type.getType(f.desc).getInternalName().equals(classNode.name)).findFirst().orElse(null);
        					if(field != null && Modifier.isStatic(field.access))
        					{
        						order.add(field);
        						classNode.fields.remove(field);
        					}
        				}else if(!hasDuplicate && ain.getOpcode() == Opcodes.PUTSTATIC
        					&& Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.ARRAY
                    		&& Type.getType(((FieldInsnNode)ain).desc).getElementType().getSort() == Type.OBJECT
                    		&& Type.getType(((FieldInsnNode)ain).desc).getElementType().getInternalName().equals(classNode.name))
        				{
        					FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
        						&& Type.getType(f.desc).getElementType().getInternalName().equals(classNode.name)).findFirst().orElse(null);
        					if(field != null && Modifier.isStatic(field.access))
        					{
        						if(valuesArr != null)
        							hasDuplicate = true;
        						else
        							valuesArr = field;
        					}
        				}
        		if(valuesArr != null)
        		{
        			valuesArr.access |= Opcodes.ACC_SYNTHETIC;
        			classNode.fields.remove(valuesArr);
        			order.add(valuesArr);
        		}
        		Collections.reverse(order);
        		for(FieldNode field : order)
        			classNode.fields.add(0, field);
        		//Fix names
        		Frame<SourceValue>[] frames;
                try 
                {
                    frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, clinit);
                }catch(AnalyzerException e) 
                {
                    oops("unexpected analyzer exception", e);
                    continue;
                }
                for(List<AbstractInsnNode> insns : result.values())
                	for(AbstractInsnNode ain : insns)
	                	if(ain.getOpcode() == Opcodes.PUTSTATIC
	    					&& Type.getType(((FieldInsnNode)ain).desc).getSort() == Type.OBJECT
	                		&& Type.getType(((FieldInsnNode)ain).desc).getInternalName().equals(classNode.name))
	    				{
	                		FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
	    						&& Type.getType(f.desc).getInternalName().equals(classNode.name)).findFirst().orElse(null);
	                		if(field == null || !Modifier.isStatic(field.access))
	                			continue;
	                		//Find invokespecial
	                		Frame<SourceValue> frame = frames[clinit.instructions.indexOf(ain)];
    						if(frame.getStack(frame.getStackSize() - 1).insns.size() == 1)
    						{
    							AbstractInsnNode pusher = frame.getStack(frame.getStackSize() - 1).insns.iterator().next();
    							while(pusher.getOpcode() == Opcodes.DUP)
    							{
    								frame = frames[clinit.instructions.indexOf(pusher)];
    								if(frame.getStack(frame.getStackSize() - 1).insns.size() != 1)
    									break;
    								pusher = frame.getStack(frame.getStackSize() - 1).insns.iterator().next();
    							}
    							if(pusher.getOpcode() != Opcodes.NEW)
    								continue;
    							if(!((TypeInsnNode)pusher).desc.equals(classNode.name))
    								continue;
    							LinkedHashMap<LabelNode, List<AbstractInsnNode>> passed = 
    								new FlowAnalyzer(clinit).analyze(pusher, Collections.singletonList(ain), new HashMap<>(),
    								false, false);
    							MethodInsnNode invokeSpecial = null;
    							for(Entry<LabelNode, List<AbstractInsnNode>> entry : passed.entrySet())
    								for(AbstractInsnNode pass : entry.getValue())
    									if(pass.getOpcode() == Opcodes.INVOKESPECIAL
    										&& ((MethodInsnNode)pass).owner.equals(classNode.name)
    										&& ((MethodInsnNode)pass).name.equals("<init>"))
    									{
    										if(invokeSpecial != null)
    										{
    											invokeSpecial = null;
    											break;
    										}
    										invokeSpecial = (MethodInsnNode)pass;
    									}
    							if(invokeSpecial == null)
    								continue;
    							int argLen = Type.getArgumentTypes(invokeSpecial.desc).length;
	    						Frame<SourceValue> invokeFrame = frames[clinit.instructions.indexOf(invokeSpecial)];
	    						if(invokeFrame.getStack(invokeFrame.getStackSize() - argLen).insns.size() == 1
	    							&& invokeFrame.getStack(invokeFrame.getStackSize() - argLen).insns.iterator().next().getOpcode() == Opcodes.LDC)
	    						{
	    							String value = (String)((LdcInsnNode)invokeFrame.getStack(invokeFrame.getStackSize() -
	    								argLen).insns.iterator().next()).cst;
	    							if(!field.name.equals(value))
	    								remapper.mapFieldName(classNode.name, field.name, field.desc, value, false);
	    						}
    						}
	    				}
        	}
        }
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(EnumNormalizer.class);
        }
    }
}
