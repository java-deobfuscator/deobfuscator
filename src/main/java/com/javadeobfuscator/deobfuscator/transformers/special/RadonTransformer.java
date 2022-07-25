package com.javadeobfuscator.deobfuscator.transformers.special;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.javadeobfuscator.deobfuscator.asm.source.ConstantPropagatingSourceFinder;
import com.javadeobfuscator.deobfuscator.asm.source.SourceFinder;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.PrimitiveFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaFieldHandle;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaHandle;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaInteger;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.InstructionModifier;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

@TransformerConfig.ConfigOptions(configClass = RadonConfig.class)
public class RadonTransformer extends Transformer<RadonConfig> {

    @Override
    public boolean transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new PrimitiveFieldProvider());
        provider.register(new MappedFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                if (type.getDescriptor().equals("[C")) {
                    if (!(target.value() instanceof char[])) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return false;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        System.out.println("[Special] [RadonTransformer] Starting");
        if (getConfig().isTrashClasses()) {
            AtomicInteger trash = new AtomicInteger();
            getDeobfuscator().getInputPassthrough().keySet().removeIf(s -> {
                if (s.endsWith(".class")) {
                    trash.incrementAndGet();
                    return true;
                }
                return false;
            });
            System.out.println("[Special] [RadonTransformer] Removed " + trash + " classes");
        }
        AtomicInteger flow = new AtomicInteger();
        AtomicInteger number = new AtomicInteger();
        AtomicInteger indy = new AtomicInteger();
        AtomicInteger str = new AtomicInteger();
        AtomicInteger strPool = new AtomicInteger();
        Map<ClassNode, Set<FieldNode>> fakeFields = new HashMap<>();
        //Bad Annotations
        for (ClassNode classNode : classNodes()) {
            for (MethodNode method : classNode.methods) {
                if (method.visibleAnnotations != null) {
                    Iterator<AnnotationNode> itr = method.visibleAnnotations.iterator();
                    while (itr.hasNext()) {
                        AnnotationNode node = itr.next();
                        if (node.desc.equals("@") || node.desc.equals("")) {
                            itr.remove();
                        }
                    }
                }
                if (method.invisibleAnnotations != null) {
                    Iterator<AnnotationNode> itr = method.invisibleAnnotations.iterator();
                    while (itr.hasNext()) {
                        AnnotationNode node = itr.next();
                        if (node.desc.equals("@") || node.desc.equals("")) {
                            itr.remove();
                        }
                    }
                }
            }
        }
        if (getConfig().getFlowMode() == RadonConfig.FlowMode.LIGHT || getConfig().getFlowMode() == RadonConfig.FlowMode.COMBINED) {
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (ain.getOpcode() == Opcodes.GETSTATIC
                            && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.IFEQ
                            && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.ACONST_NULL
                            && ain.getNext().getNext().getNext() != null
                            && ain.getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW) {
                            LabelNode jump = ((JumpInsnNode) ain.getNext()).label;
                            ClassNode owner = classNodes().stream().filter(c -> c.name.equals(((FieldInsnNode) ain).owner)).findFirst().orElse(null);
                            if (owner == null) {
                                continue;
                            }
                            FieldNode field = owner.fields.stream().filter(f ->
                                    f.name.equals(((FieldInsnNode) ain).name) && f.desc.equals(((FieldInsnNode) ain).desc)).findFirst().orElse(null);
                            if (field != null && Modifier.isFinal(field.access)) {
                                method.instructions.remove(ain.getNext().getNext().getNext());
                                method.instructions.remove(ain.getNext().getNext());
                                method.instructions.remove(ain.getNext());
                                method.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, jump));
                                fakeFields.putIfAbsent(owner, new HashSet<>());
                                fakeFields.get(owner).add(field);
                                flow.incrementAndGet();
                            }
                        }
                    }
                }
            }
        }
        if (getConfig().getFlowMode() == RadonConfig.FlowMode.NORMAL_OR_HEAVY || getConfig().getFlowMode() == RadonConfig.FlowMode.COMBINED) {
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    ClassNode owner = null;
                    AbstractInsnNode getstatic = null;
                    FieldNode field = null;
                    int var = -1;
                    boolean unresolved = false;
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (var == -1 && ain.getOpcode() == Opcodes.GETSTATIC) {
                            owner = classNodes().stream().filter(c -> c.name.equals(((FieldInsnNode) ain).owner)).findFirst().orElse(null);
                            if (owner == null) {
                                continue;
                            }
                            field = owner.fields.stream().filter(f -> f.name.equals(((FieldInsnNode) ain).name)
                                                                      && f.desc.equals(((FieldInsnNode) ain).desc)).findFirst().orElse(null);
                            if (field == null) {
                                continue;
                            }
                            if (Modifier.isStatic(field.access) && Modifier.isFinal(field.access)
                                && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.ISTORE) {
                                field = classNode.fields.stream().filter(f -> f.name.equals(((FieldInsnNode) ain).name)
                                                                              && f.desc.equals(((FieldInsnNode) ain).desc)).findFirst().orElse(null);
                                var = ((VarInsnNode) ain.getNext()).var;
                                getstatic = ain;
                            }
                        }
                        if (var != -1 && ain.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) ain).var == var
                            && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.IFNE) {
                            method.instructions.remove(ain.getNext());
                            method.instructions.remove(ain);
                        } else if (var != -1 && ain.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) ain).var == var
                                   && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.IFEQ
                                   && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.ACONST_NULL
                                   && ain.getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW) {
                            JumpInsnNode jump = (JumpInsnNode) ain.getNext();
                            method.instructions.remove(ain.getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, jump.label));
                        } else if (ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) ain).owner.equals(classNode.name)
                                   && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.IFEQ
                                   && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.ACONST_NULL
                                   && ain.getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getOpcode() == Opcodes.ATHROW) {
                            LabelNode jump = ((JumpInsnNode) ain.getNext()).label;
                            FieldNode field1 = classNode.fields.stream().filter(f ->
                                    f.name.equals(((FieldInsnNode) ain).name) && f.desc.equals(((FieldInsnNode) ain).desc)).findFirst().orElse(null);
                            if (field1 != null && Modifier.isFinal(field1.access)) {
                                method.instructions.remove(ain.getNext().getNext().getNext());
                                method.instructions.remove(ain.getNext().getNext());
                                method.instructions.remove(ain.getNext());
                                method.instructions.set(ain, new JumpInsnNode(Opcodes.GOTO, jump));
                                flow.incrementAndGet();
                            }
                        } else if (ain.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) ain).var == var) {
                            unresolved = true;
                        }
                    }
                    if (!unresolved && getstatic != null) {
                        method.instructions.remove(getstatic.getNext());
                        method.instructions.remove(getstatic);
                        fakeFields.putIfAbsent(owner, new HashSet<>());
                        fakeFields.get(owner).add(field);
                        flow.incrementAndGet();
                    }
                }
            }
        }
        classNodes().forEach(node -> node.methods.forEach(methodNode -> {
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn.getOpcode() != Opcodes.GETSTATIC) {
                    continue;
                }
                FieldInsnNode fn = (FieldInsnNode) insn;
                ClassNode owner = classes.get(fn.owner);
                if (owner == null) {
                    continue;
                }
                FieldNode fNode = owner.fields.stream().filter(f -> f.name.equals(fn.name) && f.desc.equals(fn.desc)).findFirst().orElse(null);
                if(fakeFields.containsKey(owner))
                	fakeFields.get(owner).remove(fNode);
            }
        }));
        fakeFields.entrySet().forEach(e -> e.getValue().forEach(f -> e.getKey().fields.remove(f)));
        if (getConfig().getNumberMode() == RadonConfig.NumberMode.LEGACY) {
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (Utils.isInteger(ain) && ain.getNext() != null && Utils.isInteger(ain.getNext())
                            && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.IXOR) {
                            int result = Utils.getIntValue(ain) ^ Utils.getIntValue(ain.getNext());
                            method.instructions.remove(ain.getNext().getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, Utils.getIntInsn(result));
                            number.incrementAndGet();
                        } else if (Utils.isLong(ain) && ain.getNext() != null && Utils.isLong(ain.getNext())
                                   && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.LXOR) {
                            long result = Utils.getLongValue(ain) ^ Utils.getLongValue(ain.getNext());
                            method.instructions.remove(ain.getNext().getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, new LdcInsnNode(result));
                            number.incrementAndGet();
                        }
                    }
                }
            }
        } else if (getConfig().getNumberMode() == RadonConfig.NumberMode.NEW) {
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (Utils.isInteger(ain) && ain.getNext() != null && Utils.isInteger(ain.getNext())
                            && ain.getNext().getNext() != null
                            && ain.getNext().getNext().getOpcode() == Opcodes.SWAP
                            && ain.getNext().getNext().getNext() != null
                            && ain.getNext().getNext().getNext().getOpcode() == Opcodes.DUP_X1
                            && ain.getNext().getNext().getNext().getNext() != null
                            && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP2
                            && ain.getNext().getNext().getNext().getNext().getNext() != null
                            && Utils.isInteger(ain.getNext().getNext().getNext().getNext().getNext())
                            && ain.getNext().getNext().getNext().getNext().getNext().getNext() != null
                            && ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.IXOR) {
                            int result = Utils.getIntValue(ain) ^ Utils.getIntValue(
                                    ain.getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, Utils.getIntInsn(result));
                            number.incrementAndGet();
                        } else if (Utils.isLong(ain) && ain.getNext() != null && Utils.isLong(ain.getNext())
                                   && ain.getNext().getNext() != null
                                   && ain.getNext().getNext().getOpcode() == Opcodes.DUP2_X2
                                   && ain.getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getOpcode() == Opcodes.POP2
                                   && ain.getNext().getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.POP2
                                   && ain.getNext().getNext().getNext().getNext().getNext() != null
                                   && Utils.isLong(ain.getNext().getNext().getNext().getNext().getNext())
                                   && ain.getNext().getNext().getNext().getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.LXOR) {
                            long result = Utils.getLongValue(ain.getNext()) ^ Utils.getLongValue(
                                    ain.getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, new LdcInsnNode(result));
                            number.incrementAndGet();
                        } else if (Utils.isInteger(ain) && ain.getNext() != null && ain.getNext().getOpcode() == Opcodes.I2L
                                   && ain.getNext().getNext() != null
                                   && Utils.isInteger(ain.getNext().getNext())
                                   && ain.getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getOpcode() == Opcodes.I2L
                                   && ain.getNext().getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getNext().getOpcode() == Opcodes.LXOR
                                   && ain.getNext().getNext().getNext().getNext().getNext() != null
                                   && ain.getNext().getNext().getNext().getNext().getNext().getOpcode() == Opcodes.L2I) {
                            int result = (int) ((long) Utils.getIntValue(ain) ^ (long) Utils.getIntValue(
                                    ain.getNext().getNext()));
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext().getNext());
                            method.instructions.remove(ain.getNext().getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, Utils.getIntInsn(result));
                            number.incrementAndGet();
                        }
                    }
                }
            }
        }
        Map<ClassNode, Set<MethodNode>> stringDecrypt = new HashMap<>();
        Set<ClassNode> stringDecryptClass = new HashSet<>();
        Map<ClassNode, Set<FieldNode>> stringDecryptField = new HashMap<>();
        if (getConfig().getStringPoolMode() == RadonConfig.StringPoolMode.LEGACY) {
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    InstructionModifier modifier = new InstructionModifier();
                    for (AbstractInsnNode ain : TransformerHelper.instructionIterator(method)) {
                        if (ain.getOpcode() == Opcodes.INVOKESTATIC && ain.getPrevious() != null
                            && Utils.isInteger(ain.getPrevious())) {
                            ClassNode decryptorNode = classNodes().stream().filter(c -> c.name.equals(((MethodInsnNode) ain).owner)).findFirst().orElse(null);
                            MethodNode decryptorMethod = decryptorNode == null ? null : decryptorNode.methods.stream().filter(m ->
                                    m.name.equals(((MethodInsnNode) ain).name) && m.desc.equals(((MethodInsnNode) ain).desc)).findFirst().orElse(null);
                            if (isCorrectStringPool(decryptorMethod, 0)) {
                                int prev = Utils.getIntValue(ain.getPrevious());
                                try {
                                    Context context = new Context(provider);
                                    context.push(classNode.name, method.name, getDeobfuscator().getConstantPools().get(classNode).getSize());
                                    context.dictionary = classpath;
                                    String res = MethodExecutor.execute(decryptorNode, decryptorMethod, Arrays.asList(new JavaInteger(prev)), null, context);
                                    modifier.remove(ain.getPrevious());
                                    modifier.replace(ain, new LdcInsnNode(res));
                                    stringDecrypt.putIfAbsent(decryptorNode, new HashSet<>());
                                    stringDecrypt.get(decryptorNode).add(decryptorMethod);
                                    strPool.incrementAndGet();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    modifier.apply(method);
                }
            }
        } else if (getConfig().getStringPoolMode() == RadonConfig.StringPoolMode.NEW) {
            for (ClassNode classNode : classNodes()) {
                MethodNode clinit = classNode.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElse(null);
                if (clinit != null) {
                    AbstractInsnNode firstInstr = clinit.instructions.getFirst();
                    if (firstInstr != null && firstInstr.getOpcode() == Opcodes.INVOKESTATIC
                        && ((MethodInsnNode) firstInstr).owner.equals(classNode.name)) {
                        MethodNode pool = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode) firstInstr).name)
                                                                                 && m.desc.equals(((MethodInsnNode) firstInstr).desc)).findFirst().orElse(null);
                        if (pool != null && isCorrectStringPool(pool, 1)) {
                            FieldInsnNode putstatic = null;
                            for (AbstractInsnNode ain : pool.instructions.toArray()) {
                                if (ain.getOpcode() == Opcodes.PUTSTATIC) {
                                    putstatic = (FieldInsnNode) ain;
                                    break;
                                }
                            }
                            if (putstatic != null && putstatic.owner.equals(classNode.name)) {
                                FieldInsnNode putstaticF = putstatic;
                                FieldNode poolField = classNode.fields.stream().filter(f -> f.name.equals(putstaticF.name)
                                                                                            && f.desc.equals(putstaticF.desc)).findFirst().orElse(null);
                                if (poolField != null) {
                                    Context context = new Context(provider);
                                    context.dictionary = classpath;
                                    MethodExecutor.execute(classNode, pool, Arrays.asList(), null, context);
                                    Object[] value = (Object[]) context.provider.getField(classNode.name, poolField.name, poolField.desc, null, context);
                                    for (MethodNode method : classNode.methods) {
                                        InstructionModifier modifier = new InstructionModifier();
                                        for (AbstractInsnNode ain : TransformerHelper.instructionIterator(method)) {
                                            if (ain.getOpcode() == Opcodes.GETSTATIC && ((FieldInsnNode) ain).owner.equals(classNode.name)
                                                && ((FieldInsnNode) ain).name.equals(poolField.name)
                                                && ((FieldInsnNode) ain).desc.equals(poolField.desc)
                                                && ain.getNext() != null && Utils.isInteger(ain.getNext())
                                                && ain.getNext().getNext() != null && ain.getNext().getNext().getOpcode() == Opcodes.AALOAD) {
                                                modifier.remove(ain.getNext().getNext());
                                                modifier.remove(ain.getNext());
                                                modifier.replace(ain, new LdcInsnNode(value[Utils.getIntValue(ain.getNext())]));
                                                strPool.incrementAndGet();
                                            }
                                        }
                                        modifier.apply(method);
                                    }
                                    stringDecrypt.putIfAbsent(classNode, new HashSet<>());
                                    stringDecrypt.get(classNode).add(pool);
                                    stringDecryptField.putIfAbsent(classNode, new HashSet<>());
                                    stringDecryptField.get(classNode).add(poolField);
                                    clinit.instructions.remove(clinit.instructions.getFirst());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (getConfig().isString()) {
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    InstructionModifier modifier = new InstructionModifier();
                    Frame<SourceValue>[] frames;
                    try {
                        frames = new Analyzer<>(new SourceInterpreter()).analyze(classNode.name, method);
                    } catch (AnalyzerException e) {
                        oops("unexpected analyzer exception", e);
                        continue;
                    }
                    insns:
                    for (AbstractInsnNode ain : TransformerHelper.instructionIterator(method)) {
                        if (ain.getOpcode() == Opcodes.INVOKESTATIC) {
                            ClassNode decryptorNode = classNodes().stream().filter(c -> c.name.equals(((MethodInsnNode) ain).owner)).findFirst().orElse(null);
                            MethodNode decryptorMethod = decryptorNode == null ? null : decryptorNode.methods.stream().filter(m ->
                                    m.name.equals(((MethodInsnNode) ain).name) && m.desc.equals(((MethodInsnNode) ain).desc)).findFirst().orElse(null);
                            int res = isCorrectStringDecrypt(decryptorNode, decryptorMethod);
                            if (res != -1) {
                                Frame<SourceValue> f1 = frames[method.instructions.indexOf(ain)];
                                if (f1 == null) {
                                    continue;
                                }
                                Type[] argTypes = Type.getArgumentTypes(((MethodInsnNode) ain).desc);
                                Frame<SourceValue> currentFrame = frames[method.instructions.indexOf(ain)];
                                List<JavaValue> args = new ArrayList<>();
                                List<AbstractInsnNode> instructions = new ArrayList<>();

                                for (int i = 0, stackOffset = currentFrame.getStackSize() - argTypes.length; i < argTypes.length; i++) {
                                    Optional<Object> consensus = SourceFinder.findSource(method, frames, instructions, new ConstantPropagatingSourceFinder(),
                                            ain, currentFrame.getStack(stackOffset)).consensus();
                                    if (!consensus.isPresent()) {
                                        continue insns;
                                    }

                                    Object o = consensus.get();
                                    if (o instanceof Integer) {
                                        args.add(new JavaInteger((int) o));
                                    } else {
                                        args.add(new JavaObject(o, "java/lang/String"));
                                    }
                                    stackOffset++;
                                }
                                instructions = new ArrayList<>(new HashSet<>(instructions));
                                Context context = new Context(provider);
                                context.dictionary = classpath;
                                context.push(classNode.name, method.name, getDeobfuscator().getConstantPool(classNode).getSize());
                                try {
                                    if (!stringDecryptClass.contains(decryptorNode) && res == 1) {
                                        MethodExecutor.execute(decryptorNode, decryptorNode.methods.stream().filter(m -> m.name.equals("<clinit>")).
                                                findFirst().orElse(null), Arrays.asList(), null, context);
                                        patchMethodString(decryptorNode);
                                    }
                                    List<AbstractInsnNode> pops = new ArrayList<>();
                                    for (AbstractInsnNode a : method.instructions.toArray()) {
                                        if (a.getOpcode() == Opcodes.POP && frames[method.instructions.indexOf(a)] != null) {
                                            SourceValue value = frames[method.instructions.indexOf(a)].getStack(
                                                    frames[method.instructions.indexOf(a)].getStackSize() - 1);
                                            if (!value.insns.isEmpty()
                                                && instructions.contains(value.insns.iterator().next())) {
                                                pops.add(a);
                                            }
                                        }
                                    }
                                    modifier.replace(ain, new LdcInsnNode(MethodExecutor.execute(decryptorNode, decryptorMethod,
                                            args, null, context)));
                                    modifier.removeAll(instructions);
                                    modifier.removeAll(pops);
                                    if (res == 0) {
                                        stringDecrypt.putIfAbsent(decryptorNode, new HashSet<>());
                                        stringDecrypt.get(decryptorNode).add(decryptorMethod);
                                    } else {
                                        stringDecryptClass.add(decryptorNode);
                                    }
                                    str.getAndIncrement();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                    modifier.apply(method);
                }
            }
        }
        Map<ClassNode, Set<MethodNode>> indyBootstrap = new HashMap<>();
        Map<ClassNode, Set<MethodNode>> indyBootstrap1 = new HashMap<>();
        if (getConfig().isIndy()) {
            Context context = new Context(provider);
            context.dictionary = this.classpath;
            for (ClassNode classNode : classNodes()) {
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (ain.getOpcode() == Opcodes.INVOKEDYNAMIC && ((InvokeDynamicInsnNode) ain).bsmArgs.length == 4) {
                            InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) ain;
                            boolean verify = true;
                            if (!(dyn.bsmArgs[0] instanceof Integer)) {
                                verify = false;
                            }
                            for (int i = 1; i < 4; i++) {
                                Object o = dyn.bsmArgs[i];
                                if (!(o instanceof String)) {
                                    verify = false;
                                    break;
                                }
                            }
                            if (verify) {
                                Handle bootstrap = dyn.bsm;
                                ClassNode bootstrapClassNode = classes.get(bootstrap.getOwner());
                                MethodNode bootstrapMethodNode = bootstrapClassNode.methods.stream()
                                        .filter(mn -> mn.name.equals(bootstrap.getName())
                                                      && mn.desc.equals(bootstrap.getDesc()))
                                        .findFirst()
                                        .orElse(null);
                                if (!indyBootstrap.containsKey(bootstrapClassNode) || !indyBootstrap.get(bootstrapClassNode).contains(bootstrapMethodNode)) {
                                    patchMethodIndy(context, bootstrapMethodNode);
                                }
                                List<JavaValue> args = new ArrayList<>();
                                args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
                                args.add(JavaValue.valueOf(dyn.name)); //dyn method name
                                args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
                                for (Object o : dyn.bsmArgs) {
                                    if (o instanceof Integer) {
                                        args.add(new JavaInteger((Integer) o));
                                    } else {
                                        args.add(JavaValue.valueOf(o));
                                    }
                                }
                                try {
                                    MethodInsnNode replacement = null;

                                    if (getConfig().isFastIndy()) {
                                        context.clearStackTrace();
                                        String[] result = MethodExecutor.execute(bootstrapClassNode, bootstrapMethodNode, args, null, context);
                                        switch (result[3]) {
                                            case "findVirtual":
                                                replacement = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, result[0].replace('.', '/'), result[1], result[2],
                                                        false);
                                                break;
                                            case "findStatic":
                                                replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, result[0].replace('.', '/'), result[1], result[2], false);
                                                break;
                                        }
                                    } else {
                                        context.clearStackTrace();
                                        JavaMethodHandle result = MethodExecutor.execute(bootstrapClassNode, bootstrapMethodNode, args, null, context);
                                        String clazz = result.clazz.replace('.', '/');
                                        switch (result.type) {
                                            case "virtual":
                                                replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ?
                                                        Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, result.name, result.desc,
                                                        (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
                                                break;
                                            case "static":
                                                replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, result.name, result.desc, false);
                                                break;
                                        }
                                    }
                                    method.instructions.set(ain, replacement);
                                    indyBootstrap.putIfAbsent(bootstrapClassNode, new HashSet<>());
                                    indyBootstrap.get(bootstrapClassNode).add(bootstrapMethodNode);
                                    indy.incrementAndGet();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else if (ain.getOpcode() == Opcodes.INVOKEDYNAMIC && ((InvokeDynamicInsnNode) ain).bsmArgs.length == 5) {
                            InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) ain;
                            boolean string = true;
                            if (!(dyn.bsmArgs[0] instanceof Integer) || !(dyn.bsmArgs[1] instanceof Integer)) {
                                string = false;
                            }
                            for (int i = 2; i < 5; i++) {
                                Object o = dyn.bsmArgs[i];
                                if (!(o instanceof String)) {
                                    string = false;
                                    break;
                                }
                            }
                            if (string) {
                                Handle bootstrap = dyn.bsm;
                                ClassNode bootstrapClassNode = classes.get(bootstrap.getOwner());
                                MethodNode bootstrapMethodNode = bootstrapClassNode.methods.stream()
                                        .filter(mn -> mn.name.equals(bootstrap.getName())
                                                      && mn.desc.equals(bootstrap.getDesc()))
                                        .findFirst()
                                        .orElse(null);
                                List<JavaValue> args = new ArrayList<>();
                                args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
                                args.add(JavaValue.valueOf(dyn.name)); //dyn method name
                                args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
                                for (Object o : dyn.bsmArgs) {
                                    if (o instanceof Integer) {
                                        args.add(new JavaInteger((Integer) o));
                                    } else {
                                        args.add(JavaValue.valueOf(o));
                                    }
                                }
                                try {
                                    context.clearStackTrace();

                                    JavaHandle result = MethodExecutor.execute(bootstrapClassNode, bootstrapMethodNode, args, null, context);
                                    AbstractInsnNode replacement = null;
                                    if (result instanceof JavaMethodHandle) {
                                        JavaMethodHandle jmh = (JavaMethodHandle) result;
                                        String clazz = jmh.clazz.replace('.', '/');
                                        switch (jmh.type) {
                                            case "virtual":
                                                replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ?
                                                        Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, jmh.name, jmh.desc,
                                                        (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
                                                break;
                                            case "static":
                                                replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, jmh.name, jmh.desc, false);
                                                break;
                                        }
                                    } else {
                                        JavaFieldHandle jfh = (JavaFieldHandle) result;
                                        String clazz = jfh.clazz.replace('.', '/');
                                        switch (jfh.type) {
                                            case "virtual":
                                                replacement = new FieldInsnNode(jfh.setter ?
                                                        Opcodes.PUTFIELD : Opcodes.GETFIELD, clazz, jfh.name, jfh.desc);
                                                break;
                                            case "static":
                                                replacement = new FieldInsnNode(jfh.setter ?
                                                        Opcodes.PUTSTATIC : Opcodes.GETSTATIC, clazz, jfh.name, jfh.desc);
                                                break;
                                        }
                                    }
                                    method.instructions.set(ain, replacement);
                                    indyBootstrap.putIfAbsent(bootstrapClassNode, new HashSet<>());
                                    indyBootstrap.get(bootstrapClassNode).add(bootstrapMethodNode);
                                    indy.incrementAndGet();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else if (ain.getOpcode() == Opcodes.INVOKEDYNAMIC && ((InvokeDynamicInsnNode) ain).bsmArgs.length == 0) {
                            InvokeDynamicInsnNode dyn = (InvokeDynamicInsnNode) ain;
                            Handle bootstrap = dyn.bsm;
                            ClassNode bootstrapClassNode = classes.get(bootstrap.getOwner());
                            MethodNode bootstrapMethodNode = bootstrapClassNode.methods.stream()
                                    .filter(mn -> mn.name.equals(bootstrap.getName())
                                                  && mn.desc.equals(bootstrap.getDesc()))
                                    .findFirst()
                                    .orElse(null);
                            if ((!indyBootstrap1.containsKey(bootstrapClassNode) || !indyBootstrap1.get(bootstrapClassNode).contains(bootstrapMethodNode))
                                && !isCorrectIndy(bootstrapMethodNode)) {
                                continue;
                            }
                            List<JavaValue> args = new ArrayList<>();
                            args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
                            args.add(JavaValue.valueOf(dyn.name)); //dyn method name
                            args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
                            try {
                                context.clearStackTrace();

                                JavaHandle result = MethodExecutor.execute(bootstrapClassNode, bootstrapMethodNode, args, null, context);
                                AbstractInsnNode replacement = null;
                                if (result instanceof JavaMethodHandle) {
                                    JavaMethodHandle jmh = (JavaMethodHandle) result;
                                    String clazz = jmh.clazz.replace('.', '/');
                                    switch (jmh.type) {
                                        case "virtual":
                                            replacement = new MethodInsnNode((classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0 ?
                                                    Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, clazz, jmh.name, jmh.desc,
                                                    (classpath.get(clazz).access & Opcodes.ACC_INTERFACE) != 0);
                                            break;
                                        case "static":
                                            replacement = new MethodInsnNode(Opcodes.INVOKESTATIC, clazz, jmh.name, jmh.desc, false);
                                            break;
                                        case "special":
                                            replacement = new MethodInsnNode(Opcodes.INVOKESPECIAL, clazz, jmh.name, jmh.desc, false);
                                            break;
                                    }
                                } else {
                                    JavaFieldHandle jfh = (JavaFieldHandle) result;
                                    String clazz = jfh.clazz.replace('.', '/');
                                    switch (jfh.type) {
                                        case "virtual":
                                            replacement = new FieldInsnNode(jfh.setter ?
                                                    Opcodes.PUTFIELD : Opcodes.GETFIELD, clazz, jfh.name, jfh.desc);
                                            break;
                                        case "static":
                                            replacement = new FieldInsnNode(jfh.setter ?
                                                    Opcodes.PUTSTATIC : Opcodes.GETSTATIC, clazz, jfh.name, jfh.desc);
                                            break;
                                    }
                                }
                                method.instructions.set(ain, replacement);
                                indyBootstrap1.putIfAbsent(bootstrapClassNode, new HashSet<>());
                                indyBootstrap1.get(bootstrapClassNode).add(bootstrapMethodNode);
                                indy.incrementAndGet();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
        indyBootstrap.forEach((key, value) -> value.forEach(m -> key.methods.remove(m)));
        indyBootstrap1.forEach((key, value) -> {
            classes.remove(key.name);
            classpath.remove(key.name);
        });
        stringDecrypt.forEach((key, value) -> value.forEach(m -> key.methods.remove(m)));
        stringDecryptField.forEach((key, value) -> value.forEach(m -> key.fields.remove(m)));
        stringDecryptClass.forEach(e -> {
            classes.remove(e.name);
            classpath.remove(e.name);
        });
        System.out.println("[Special] [RadonTransformer] Removed " + flow + " fake jump instructions");
        System.out.println("[Special] [RadonTransformer] Fixed " + number + " number instructions");
        System.out.println("[Special] [RadonTransformer] Unpooled " + strPool + " strings");
        System.out.println("[Special] [RadonTransformer] Decrypted " + str + " strings");
        System.out.println("[Special] [RadonTransformer] Removed " + indy + " invokedynamic instructions");
        System.out.println("[Special] [RadonTransformer] Done");
        return flow.get() > 0 || number.get() > 0 || strPool.get() > 0 || str.get() > 0 || indy.get() > 0;
    }

    private boolean isCorrectStringPool(MethodNode method, int mode) {
        if (method == null) {
            return false;
        }
        if (mode == 0 ? !method.desc.equals("(I)Ljava/lang/String;") : !method.desc.equals("()V")) {
            return false;
        }
        int numberCount = 0;
        int storeCount = 0;
        int dupCount = 0;
        int ldcCount = 0;
        for (AbstractInsnNode ain : method.instructions.toArray()) {
            if (Utils.isInteger(ain)) {
                numberCount++;
            } else if (ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode) ain).cst instanceof String) {
                ldcCount++;
            } else if (ain.getOpcode() == Opcodes.DUP) {
                dupCount++;
            } else if (ain.getOpcode() == Opcodes.AASTORE) {
                storeCount++;
            }
        }
        if (numberCount == storeCount + 1 && dupCount == storeCount && ldcCount == storeCount) {
            return true;
        }
        return false;
    }

    private int isCorrectStringDecrypt(ClassNode classNode, MethodNode method) {
        if (method == null) {
            return -1;
        }
        if (method.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")) {
            int charArray = 0;
            int ixor = 0;
            int special = 0;
            for (AbstractInsnNode ain : method.instructions.toArray()) {
                if (ain.getOpcode() == Opcodes.IXOR) {
                    ixor++;
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                           && ((MethodInsnNode) ain).name.equals("toCharArray")
                           && ((MethodInsnNode) ain).owner.equals("java/lang/String")) {
                    charArray++;
                } else if (ain.getOpcode() == Opcodes.INVOKESTATIC
                           && ((MethodInsnNode) ain).desc.equals("(Ljava/lang/String;Ljava/lang/String;)V")
                           && ((MethodInsnNode) ain).owner.equals(classNode.name)) {
                    special++;
                }
            }
            if (charArray == 1 && ixor == 1) {
                if (special == 1) {
                    return 1;
                }
                return 0;
            }
        } else if (method.desc.equals("(Ljava/lang/Object;I)Ljava/lang/String;")) {
            int stackTrace = 0;
            int stackTrace2 = 0;
            int charArray = 0;
            int ixor = 0;
            for (AbstractInsnNode ain : method.instructions.toArray()) {
                if (ain.getOpcode() == Opcodes.IXOR) {
                    ixor++;
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                           && ((MethodInsnNode) ain).name.equals("toCharArray")
                           && ((MethodInsnNode) ain).owner.equals("java/lang/String")) {
                    charArray++;
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                           && ((MethodInsnNode) ain).name.equals("getStackTrace")
                           && ((MethodInsnNode) ain).owner.equals("java/lang/Throwable")) {
                    stackTrace++;
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                           && ((MethodInsnNode) ain).name.equals("getStackTrace")
                           && ((MethodInsnNode) ain).owner.equals("java/lang/Thread")) {
                    stackTrace2++;
                }
            }
            if (charArray == 1 && stackTrace == 1 && ixor == 3) {
                return 0;
            } else if (charArray == 1 && stackTrace == 0 && stackTrace2 == 1
                       && ixor == 8 && classNode.superName.equals("java/lang/Thread")) {
                return 1;
            }
        } else if (method.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/String;")
                   || method.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;")) {
            int stackTrace = 0;
            int charArray = 0;
            for (AbstractInsnNode ain : method.instructions.toArray()) {
                if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && ((MethodInsnNode) ain).name.equals("toCharArray")
                    && ((MethodInsnNode) ain).owner.equals("java/lang/String")) {
                    charArray++;
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                           && ((MethodInsnNode) ain).name.equals("getStackTrace")
                           && (((MethodInsnNode) ain).owner.equals("java/lang/Thread")
                               || ((MethodInsnNode) ain).owner.equals("java/lang/Throwable"))) {
                    stackTrace++;
                }
            }
            if (charArray == 6 && stackTrace == 2) {
                return 0;
            }
        }
        return -1;
    }

    private boolean isCorrectIndy(MethodNode method) {
        if (method == null) {
            return false;
        }
        if (method.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
            int string = 0;
            int lookup = 0;
            for (AbstractInsnNode ain : method.instructions.toArray()) {
                if (ain.getOpcode() == Opcodes.LDC && ((LdcInsnNode) ain).cst instanceof String) {
                    string++;
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL
                           && ((MethodInsnNode) ain).name.startsWith("find")
                           && ((MethodInsnNode) ain).owner.equals("java/lang/invoke/MethodHandles$Lookup")) {
                    lookup++;
                }
            }
            if (string == 1 && lookup == 7) {
                return true;
            }
        }
        return true;
    }

    private void patchMethodIndy(Context context, MethodNode method) {
        for (AbstractInsnNode ain : method.instructions.toArray()) {
            if (ain instanceof MethodInsnNode && (((MethodInsnNode) ain).owner.equals("java/lang/Runtime")
                                                  || (((MethodInsnNode) ain).owner.equals("java/util/concurrent/ThreadLocalRandom")))) {
                if (((MethodInsnNode) ain).name.equals("nextInt")) {
                    method.instructions.remove(Utils.getNext(ain));
                }
                method.instructions.remove(ain);
            }
        }

        if (getConfig().isFastIndy()) {
            for (AbstractInsnNode ain : method.instructions.toArray()) {
                if (ain.getOpcode() == Opcodes.INVOKESTATIC && ((MethodInsnNode) ain).owner.equals("java/lang/Class")
                    && ((MethodInsnNode) ain).name.equals("forName")) {
                    method.instructions.remove(ain);
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) ain).owner.equals("java/lang/invoke/MethodHandle")
                           && ((MethodInsnNode) ain).name.equals("asType")) {
                    method.instructions.set(ain, new InsnNode(Opcodes.POP));
                } else if (ain.getOpcode() == Opcodes.INVOKEVIRTUAL && ((MethodInsnNode) ain).owner.equals("java/lang/invoke/MethodHandles$Lookup")) {
                    context.customMethodFunc.put(ain, (args, ctx) -> {
                        String owner = args.remove(0).as(String.class);
                        String name = args.remove(0).as(String.class);
                        String desc = args.remove(0).as(String.class);
                        args.remove(0);
                        return JavaValue.valueOf(new String[]{owner, name, desc, ((MethodInsnNode) ain).name});
                    });
                }
            }
        }
    }

    private void patchMethodString(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode ain : method.instructions.toArray()) {
                if (ain instanceof MethodInsnNode && ((MethodInsnNode) ain).name.equals("availableProcessors")
                    && ((MethodInsnNode) ain).owner.equals("java/lang/Runtime")) {
                    method.instructions.set(ain, new LdcInsnNode(4));
                } else if (ain instanceof MethodInsnNode && ((MethodInsnNode) ain).name.equals("getRuntime")
                           && ((MethodInsnNode) ain).owner.equals("java/lang/Runtime")) {
                    method.instructions.set(ain, new InsnNode(Opcodes.ACONST_NULL));
                }
            }
        }
    }
}
