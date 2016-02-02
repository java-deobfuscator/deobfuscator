package com.javadeobfuscator.deobfuscator.transformers.dasho;

import java.util.Arrays;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.IntInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.LdcInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class StringEncryptionTransformer extends Transformer {

    public StringEncryptionTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());

        classNodes().forEach(wrappedClassNode -> {
            wrappedClassNode.classNode.methods.forEach(methodNode -> {
                for (int index = 0; index < methodNode.instructions.size(); index++) {
                    AbstractInsnNode current = methodNode.instructions.get(index);
                    if (current instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) current;
                        if (ldc.cst instanceof String) {
                            if (ldc.getNext() instanceof MethodInsnNode) {
                                MethodInsnNode m = (MethodInsnNode) ldc.getNext();
                                String strCl = m.owner;
                                if (m.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) {
                                    Context context = new Context(provider);
                                    context.push(wrappedClassNode.classNode.name, methodNode.name, wrappedClassNode.constantPoolSize);
                                    ClassNode innerClassNode = classes.get(strCl).classNode;
                                    MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(m.name) && mn.desc.equals(m.desc)).findFirst().orElse(null);

                                    int intConstant = Integer.MIN_VALUE;
                                    if (ldc.getNext() instanceof IntInsnNode) {
                                        intConstant = ((IntInsnNode) ldc.getNext()).operand;
                                    } else {
                                        intConstant = Utils.iconstToInt(ldc.getNext().getOpcode());
                                    }
                                    if (intConstant != Integer.MAX_VALUE) {
                                        try {
                                            Object o = MethodExecutor.execute(wrappedClassNode, decrypterNode, Arrays.asList(new StackObject(Object.class, ldc.cst), new StackObject(int.class, intConstant)), null, context);
                                            ldc.cst = o;
                                            methodNode.instructions.remove(ldc.getNext());
                                            methodNode.instructions.remove(ldc.getNext());
                                        } catch (Throwable t) {
                                            System.out.println("Error while decrypting DashO string.");
                                            System.out.println("Are you sure you're deobfuscating something obfuscated by DashO?");
                                            t.printStackTrace(System.out);
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            });
        });
    }
}
