package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import com.javadeobfuscator.deobfuscator.analyzer.FlowAnalyzer;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.utils.Utils;

import org.assertj.core.internal.asm.Opcodes;
import org.assertj.core.internal.asm.Type;
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
	                		&& Type.getType(((FieldInsnNode)ain).desc).getInternalName().equals(classNode.name)
	                		&& Utils.getPrevious(ain) != null && Utils.getPrevious(ain).getOpcode() == Opcodes.INVOKESPECIAL)
	    				{
	    					FieldNode field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode)ain).name)
	    						&& Type.getType(f.desc).getInternalName().equals(classNode.name)).findFirst().orElse(null);
	    					if(field != null && Modifier.isStatic(field.access))
	    					{
	    						int argLen = Type.getArgumentTypes(((MethodInsnNode)Utils.getPrevious(ain)).desc).length;
	    						Frame<SourceValue> frame = frames[clinit.instructions.indexOf(Utils.getPrevious(ain))];
	    						if(frame.getStack(frame.getStackSize() - argLen).insns.size() == 1
	    							&& frame.getStack(frame.getStackSize() - argLen).insns.iterator().next().getOpcode() == Opcodes.LDC)
	    						{
	    							String value = (String)((LdcInsnNode)frame.getStack(frame.getStackSize() - argLen).insns.iterator().next()).cst;
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
