package com.javadeobfuscator.deobfuscator.transformers.dasho;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.internal.asm.Opcodes;
import org.objectweb.asm.tree.*;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class FlowObfuscationTransformer extends Transformer<TransformerConfig>
{
	 @Override
	 public boolean transform() throws Throwable {
		 System.out.println("[DashO] [FlowObfuscationTransformer] Starting");
		 AtomicInteger counter = new AtomicInteger();
		 
		 for(ClassNode classNode : classNodes())
			 for(MethodNode method : classNode.methods)
				 loop:
				 for(AbstractInsnNode ain : method.instructions.toArray())
					 if(Utils.getIntValue(ain) == -1 && ain.getNext() != null
						 && ain.getNext().getOpcode() == Opcodes.ISTORE
						 && ain.getNext().getNext() != null
						 && ain.getNext().getNext().getOpcode() == Opcodes.LDC
						 && ((LdcInsnNode)ain.getNext().getNext()).cst.equals("0")
						 && ain.getNext().getNext().getNext() != null
						 && ain.getNext().getNext().getNext().getOpcode() == Opcodes.IINC
						 && ((IincInsnNode)ain.getNext().getNext().getNext()).incr == 1
						 && ((IincInsnNode)ain.getNext().getNext().getNext()).var == ((VarInsnNode)ain.getNext()).var
						 && ain.getNext().getNext().getNext().getNext() != null
						 && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ASTORE)
					 {
						 List<AbstractInsnNode> remove = new ArrayList<>();
						 Map<AbstractInsnNode, AbstractInsnNode> replace = new HashMap<>();
						 remove.add(ain);
						 remove.add(ain.getNext());
						 remove.add(ain.getNext().getNext());
						 remove.add(ain.getNext().getNext().getNext());
						 remove.add(ain.getNext().getNext().getNext().getNext());
						 int var1Index = ((VarInsnNode)ain.getNext()).var;
						 int var1Value = 0;
						 int var2Index = ((VarInsnNode)ain.getNext().getNext().getNext().getNext()).var;
						 int var2Value = 0;
						 
						 AbstractInsnNode next = ain.getNext().getNext().getNext().getNext();
						 int count = 0;
						 while(true)
						 {
							 boolean found = false;
							 LabelNode lbl = null;
							 while(next != null && !(next instanceof LabelNode))
							 {
								 if(Utils.getIntValue(next) == -1 && ain.getNext() != null
									 && next.getNext().getOpcode() == Opcodes.ISTORE
									 && next.getNext().getNext() != null
									 && next.getNext().getNext().getOpcode() == Opcodes.LDC
									 && ((LdcInsnNode)next.getNext().getNext()).cst.equals("0")
									 && next.getNext().getNext().getNext() != null
									 && next.getNext().getNext().getNext().getOpcode() == Opcodes.IINC
									 && ((IincInsnNode)next.getNext().getNext().getNext()).incr == 1
									 && ((IincInsnNode)next.getNext().getNext().getNext()).var == ((VarInsnNode)next.getNext()).var
									 && next.getNext().getNext().getNext().getNext() != null
									 && next.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.ASTORE)
									 break;
								 if(next.getOpcode() == Opcodes.ALOAD && ((VarInsnNode)next).var == var2Index
									 && next.getNext() != null && next.getNext().getOpcode() == Opcodes.INVOKESTATIC
									 && ((MethodInsnNode)next.getNext()).name.equals("parseInt")
									 && ((MethodInsnNode)next.getNext()).owner.equals("java/lang/Integer")
									 && next.getNext().getNext() != null
									 && next.getNext().getNext().getOpcode() == Opcodes.TABLESWITCH
									 && ((TableSwitchInsnNode)next.getNext().getNext()).min == 0
									 && ((TableSwitchInsnNode)next.getNext().getNext()).labels.size() == 1)
								 {
									 remove.add(next);
									 remove.add(next.getNext());
									 found = true;
									 lbl = var2Value == 0 ? ((TableSwitchInsnNode)next.getNext().getNext()).labels.get(0) :
										 ((TableSwitchInsnNode)next.getNext().getNext()).dflt;
									 replace.put(next.getNext().getNext(), new JumpInsnNode(Opcodes.GOTO, lbl));
									 next = lbl.getNext();
									 break;
								 }else if(next.getOpcode() == Opcodes.ILOAD && ((VarInsnNode)next).var == var1Index
									 && next.getNext() != null && next.getNext().getOpcode() == Opcodes.TABLESWITCH
									 && ((TableSwitchInsnNode)next.getNext()).min == 0
									 && ((TableSwitchInsnNode)next.getNext()).labels.size() == 1)
								 {
									 remove.add(next);
									 found = true;
									 lbl = var1Value == 0 ? ((TableSwitchInsnNode)next.getNext()).labels.get(0) :
										 ((TableSwitchInsnNode)next.getNext()).dflt;
									 replace.put(next.getNext(), new JumpInsnNode(Opcodes.GOTO, lbl));
									 next = lbl.getNext();
									 break;
								 }
								 next = next.getNext();
							 }
							 if(!found)
								 break;
							 count++;
							 boolean found2 = false;
							 while(next != null && !(next instanceof LabelNode))
							 {
								 if(next.getOpcode() == Opcodes.IINC && ((IincInsnNode)next).var == var1Index
									 && next.getNext() != null && next.getNext().getOpcode() == Opcodes.LDC
									 && next.getNext().getNext() != null
									 && next.getNext().getNext().getOpcode() == Opcodes.ASTORE
									 && ((VarInsnNode)next.getNext().getNext()).var == var2Index)
								 {
									 remove.add(next);
									 remove.add(next.getNext());
									 remove.add(next.getNext().getNext());
									 found2 = true;
									 var1Value += ((IincInsnNode)next).incr;
									 var2Value = Integer.parseInt((String)((LdcInsnNode)next.getNext()).cst);
									 if(next.getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
										 next = ((JumpInsnNode)next.getNext().getNext().getNext()).label.getNext();
									 else if(next.getNext().getNext().getNext() instanceof LabelNode)
										 next = next.getNext().getNext().getNext().getNext();
									 break;
								 }else if(Utils.isInteger(next) && next.getNext() != null
									 && next.getNext().getOpcode() == Opcodes.ISTORE 
									 && ((VarInsnNode)next.getNext()).var == var1Index
									 && next.getNext().getNext() != null
									 && next.getNext().getNext().getOpcode() == Opcodes.LDC
									 && next.getNext().getNext().getNext() != null
									 && next.getNext().getNext().getNext().getOpcode() == Opcodes.ASTORE
									 && ((VarInsnNode)next.getNext().getNext().getNext()).var == var2Index)
								 {
									 remove.add(next);
									 remove.add(next.getNext());
									 remove.add(next.getNext().getNext());
									 remove.add(next.getNext().getNext().getNext());
									 found2 = true;
									 var1Value = Utils.getIntValue(next);
									 var2Value = Integer.parseInt((String)((LdcInsnNode)next.getNext().getNext()).cst);
									 if(next.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.GOTO)
										 next = ((JumpInsnNode)next.getNext().getNext().getNext().getNext()).label.getNext();
									 else if(next.getNext().getNext().getNext().getNext() instanceof LabelNode)
										 next = next.getNext().getNext().getNext().getNext().getNext();
									 break;
								 }
								 next = next.getNext();
							 }
							 if(!found2)
								 continue loop;
						 }
						 
						 if(count > 0)
						 {
							 for(AbstractInsnNode a : remove)
								 method.instructions.remove(a);
							 for(Entry<AbstractInsnNode, AbstractInsnNode> en : replace.entrySet())
								 method.instructions.set(en.getKey(), en.getValue());
							 counter.incrementAndGet();
						 }
					 }
		 System.out.println("[DashO] [FlowObfuscationTransformer] Removed " + counter.get() + " flow obfuscated chunks");
		 System.out.println("[DashO] [FlowObfuscationTransformer] Done");
		 return counter.get() > 0;
	 }
}
