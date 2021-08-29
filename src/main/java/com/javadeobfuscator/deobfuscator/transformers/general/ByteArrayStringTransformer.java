package com.javadeobfuscator.deobfuscator.transformers.general;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.ArrayStoreFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LdcFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.LocalFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MethodFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.NewArrayFrame;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class ByteArrayStringTransformer extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws Throwable {
        LongAdder counter = new LongAdder();
        LongAdder errorCounter = new LongAdder();

        classNodes().forEach(classNode -> classNode.methods.stream().filter(Utils::notAbstractOrNative).forEach(methodNode -> {
            try {
                boolean modify;
                do {
                    modify = false;
                    AnalyzerResult result = null;

                    List<AbstractInsnNode> replaceWithPop3Total = new ArrayList<>();

                    ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator();
                    while (it.hasNext()) {
                        AbstractInsnNode ain = it.next();
                        if (!(ain instanceof MethodInsnNode)) {
                            continue;
                        }

                        MethodInsnNode min = (MethodInsnNode) ain;

                        if (!TransformerHelper.isInvokeSpecial(min,"java/lang/String", "<init>", "([B)V")) {
                            continue;
                        }

                        if (result == null) {
                            result = MethodAnalyzer.analyze(classNode, methodNode);
                        }
                        Set<String> strResults = new HashSet<>();
                        Set<AbstractInsnNode> replaceWithPop3 = new HashSet<>();
                        List<Frame> frames = result.getFrames().get(min);
                        for (Frame frame0 : frames) {
                            MethodFrame methodFrame = (MethodFrame) frame0;

                            if (methodFrame.getArgs().size() != 1) {
                                continue;
                            }

                            Frame f = methodFrame.getArgs().get(0);
                            if (f instanceof LocalFrame && f.isConstant()) {
                                f = ((LocalFrame) f).getValue();
                            }
                            if (!(f instanceof NewArrayFrame)) {
                                continue;
                            }
                            NewArrayFrame naf = (NewArrayFrame) f;
                            if (!(naf.getLength() instanceof LdcFrame)) {
                                continue;
                            }
                            LdcFrame length = (LdcFrame) naf.getLength();
                            if (!naf.isConstant()) {
                                continue;
                            }

                            Map<Frame, AbstractInsnNode> mapping = result.getMapping();

                            byte[] arr = new byte[((Number) length.getConstant()).intValue()];

                            ArrayDeque<Frame> children = new ArrayDeque<>(naf.getChildren());
                            while (!children.isEmpty()) {
                                Frame child0 = children.pop();
                                if (child0 == naf) {
                                    continue;
                                }
                                if (child0 instanceof LocalFrame && child0.isConstant()) {
                                    children.addAll(child0.getChildren());
                                    continue;
                                }
                                if (child0 instanceof MethodFrame && mapping.get(child0) == min) {
                                    continue;
                                }
                                if (!(child0 instanceof ArrayStoreFrame)) {
                                    throw new IllegalStateException("Unexpected child frame: " + child0);
                                }
                                ArrayStoreFrame arrayStoreFrame = (ArrayStoreFrame) child0;
                                if (arrayStoreFrame.getOpcode() != BASTORE) {
                                    continue;
                                }
                                replaceWithPop3.add(mapping.get(arrayStoreFrame));
                                Frame arrayPos = arrayStoreFrame.getIndex();
                                if (!(arrayPos instanceof LdcFrame)) {
                                    throw new IllegalStateException("Unexpected store index frame: " + child0);
                                }
                                Frame value = arrayStoreFrame.getObject();
                                if (!(value instanceof LdcFrame)) {
                                    throw new IllegalStateException("Unexpected store object frame: " + child0);
                                }
                                int pos = ((Number) ((LdcFrame) arrayPos).getConstant()).intValue();
                                arr[pos] = (byte) ((Number) ((LdcFrame) value).getConstant()).intValue();
                            }
                            String str = new String(arr);
                            strResults.add(str);
                        }
                        if (strResults.size() != 1) {
                            continue;
                        }
                        String str = strResults.iterator().next();
                        it.set(new InsnNode(POP));
                        it.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/String", "<init>", "()V", false));
                        it.add(new InsnNode(POP));
                        it.add(new LdcInsnNode(str)); // load const string
                        counter.increment();
                        modify = true;
                        replaceWithPop3Total.addAll(replaceWithPop3);
                    }
                    for (AbstractInsnNode insn : replaceWithPop3Total) {
                        methodNode.instructions.insertBefore(insn, new InsnNode(POP2));
                        methodNode.instructions.set(insn, new InsnNode(POP));
                    }
                } while (modify);
            } catch (Exception ex) {
                System.err.println("[ByteArrayStringTransformer] An error occurred while deobfuscating " + classNode.name + " " + methodNode.name + methodNode.desc + ":");
                ex.printStackTrace();
                errorCounter.increment();
            }
        }));

        System.out.println("[ByteArrayStringTransformer] Successfully transformed " + counter + " byte arrays into strings; " + errorCounter + " errors occurred.");

        return counter.sum() > 0;
    }
}
