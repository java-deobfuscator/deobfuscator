package com.javadeobfuscator.deobfuscator.rules.special;

import java.util.Collection;
import java.util.Collections;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.special.SuperblaubeereTransformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class RuleSuperblaubeereObfuscation implements Rule {

	@Override
	public String getDescription() {
		return "Superblaubeere obfuscator uses a variety of methods. It can obfuscate numbers, add redundant ifs, encrypt strings, pool numbers & strings into an" +
			   " " +
			   "array per class and obfuscate method calls with invokedynamic instructions.";
	}

	@Override
	public String test(Deobfuscator deobfuscator) {
		for (ClassNode classNode : deobfuscator.getClasses().values()) {
			MethodNode clinit = TransformerHelper.findClinit(classNode);
			if (clinit == null) {
				continue;
			}
			AbstractInsnNode first = clinit.instructions.getFirst();
			if (first == null) {
				continue;
			}
			// Number pool
			numberPool:
			{
				if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
					break numberPool;
				}
				MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
				if (refMethod == null || refMethod.instructions.size() <= 3) {
					break numberPool;
				}
				AbstractInsnNode thirdInsn = refMethod.instructions.getFirst().getNext().getNext();
				if (!TransformerHelper.isPutStatic(thirdInsn, null, null, "[I")) {
					break numberPool;
				}
				FieldInsnNode insnNode = (FieldInsnNode) thirdInsn;
				FieldNode field = TransformerHelper.findFieldNode(classNode, insnNode.name, insnNode.desc);
				if (field == null) {
					break numberPool;
				}
				for (MethodNode method : classNode.methods) {
					for (AbstractInsnNode ain : method.instructions.toArray()) {
						if (TransformerHelper.isGetStatic(ain, classNode.name, field.name, field.desc) && Utils.isInteger(ain.getNext())) {
							return "Found potential number pool in class " + classNode.name;
						}
					}
				}
			}
			// String encryption
			stringEncrypt:
			{
				if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
					break stringEncrypt;
				}
				MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
				if (refMethod == null || refMethod.instructions.size() <= 3) {
					break stringEncrypt;
				}
				if (!SuperblaubeereTransformer.STRING_ENCRYPT_MATCHER.matchImmediately(refMethod.instructions.iterator())) {
					break stringEncrypt;
				}
				AbstractInsnNode lastPrev = refMethod.instructions.getLast().getPrevious();
				if (lastPrev.getOpcode() != PUTSTATIC) {
					continue;
				}
				FieldInsnNode insnNode = (FieldInsnNode) lastPrev;
				FieldNode field = TransformerHelper.findFieldNode(classNode, insnNode.name, insnNode.desc);
				if (field == null) {
					break stringEncrypt;
				}
				for (MethodNode method : classNode.methods) {
					for (AbstractInsnNode ain : method.instructions.toArray()) {
						if (TransformerHelper.isGetStatic(ain, classNode.name, field.name, field.desc) && Utils.isInteger(ain.getNext())) {
							return "Found potential string encryption in class " + classNode.name;
						}
					}
				}
			}
			// Unpool strings
			stringPool:
			{
				if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
					break stringPool;
				}
				MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
				if (refMethod == null) {
					break stringPool;
				}
				if (refMethod.instructions.size() <= 3) {
					break stringPool;
				}
				AbstractInsnNode thirdInsn = refMethod.instructions.getFirst().getNext().getNext();
				if (!TransformerHelper.isPutStatic(thirdInsn, null, null, "[Ljava/lang/String;")) {
					break stringPool;
				}
				FieldInsnNode insnNode = (FieldInsnNode) thirdInsn;
				FieldNode field = TransformerHelper.findFieldNode(classNode, insnNode.name, insnNode.desc);
				if (field == null) {
					break stringPool;
				}
				boolean contains = false;
				for (AbstractInsnNode ain : refMethod.instructions.toArray()) {
					if (TransformerHelper.isInvokeStatic(ain, classNode.name, null, null)) {
						MethodInsnNode min = (MethodInsnNode) ain;
						contains = TransformerHelper.findMethodNode(classNode, min.name, min.desc) != null;
						if (contains) {
							break;
						}
					}
				}
				if (!contains) {
					break stringPool;
				}
				for (MethodNode method : classNode.methods) {
					for (AbstractInsnNode ain : method.instructions.toArray()) {
						if (TransformerHelper.isGetStatic(ain, classNode.name, field.name, field.desc) && Utils.isInteger(ain.getNext())) {
							return "Found potential string pool in class " + classNode.name;
						}
					}
				}
			}
			// invokedynamic
			invokedyn:
			{
				if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
					break invokedyn;
				}
				MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
				if (refMethod == null) {
					break invokedyn;
				}
				FieldNode[] fields = SuperblaubeereTransformer.isIndyMethod(classNode, refMethod);
				if (fields == null) {
					break invokedyn;
				}
				MethodNode bootstrap = classNode.methods.stream()
						.filter(m -> SuperblaubeereTransformer.isBootstrap(classNode, fields, m))
						.findFirst().orElse(null);
				if (bootstrap == null) {
					break invokedyn;
				}
				boolean yep = false;
				for (AbstractInsnNode ain : refMethod.instructions.toArray()) {
					if (TransformerHelper.getConstantString(ain) != null) {
						yep = true;
					} else if (TransformerHelper.getConstantType(ain) != null) {
						yep = true;
					} else if (TransformerHelper.isGetStatic(ain, null, "TYPE", "Ljava/lang/Class;")) {
						yep = true;
					}
					if (yep) {
						break;
					}
				}
				if (!yep) {
					break invokedyn;
				}
				for (MethodNode method : classNode.methods) {
					for (AbstractInsnNode ain : method.instructions.toArray()) {
						if (TransformerHelper.isInvokeDynamic(ain, null, null, classNode.name, bootstrap.name, bootstrap.desc, 0)) {
							return "Found potential invokedynamic obfuscation in class " + classNode.name;
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
		return Collections.singleton(SuperblaubeereTransformer.class);
	}
}
