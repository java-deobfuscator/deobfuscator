package com.javadeobfuscator.deobfuscator.transformers.special;

import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.javadeobfuscator.deobfuscator.executor.values.JavaArray;
import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import com.javadeobfuscator.deobfuscator.iterablematcher.CastedSimpleStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.ConstantIntInsnStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.FieldInsnStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.IterableInsnMatcher;
import com.javadeobfuscator.deobfuscator.iterablematcher.MethodInsnStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.SimpleStep;
import com.javadeobfuscator.deobfuscator.iterablematcher.TypeInsnStep;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

@TransformerConfig.ConfigOptions(configClass = SuperblaubeereTransformer.Config.class)
public class SuperblaubeereTransformer extends Transformer<SuperblaubeereTransformer.Config> {

    public static class Config extends TransformerConfig {

        private boolean fastIndy = true;
        private boolean classEncryption = false;

        public Config() {
            super(SuperblaubeereTransformer.class);
        }

        public boolean isFastIndy() {
            return fastIndy;
        }

        public void setFastIndy(boolean fastIndy) {
            this.fastIndy = fastIndy;
        }

        public boolean isClassEncryption() {
            return classEncryption;
        }

        public void setClassEncryption(boolean classEncryption) {
            this.classEncryption = classEncryption;
        }
    }

    public static final IterableInsnMatcher STRING_ENCRYPT_MATCHER = new IterableInsnMatcher(a -> {
        a.addStep(new TypeInsnStep(NEW, "java/lang/Exception"));
        a.addStep(new SimpleStep(DUP));
        a.addStep(new MethodInsnStep(INVOKESPECIAL, "java/lang/Exception", "<init>", null, null));
        a.addStep(new MethodInsnStep(INVOKEVIRTUAL, null, "getStackTrace", null,
                i -> i.owner.equals("java/lang/Exception") || i.owner.equals("java/lang/Throwable")));
    });

    public static final IterableInsnMatcher REDUNDANT_IF_1_MATCHER = new IterableInsnMatcher(a -> {
        a.addStep(SimpleStep.ofOpcodeRangeInclusive(LLOAD, DLOAD));
        a.addStep(SimpleStep.ofOpcodeRangeInclusive(LLOAD, DLOAD));
        a.addStep(SimpleStep.ofOpcodeRangeInclusive(LCMP, DCMPG));
        a.addStep(new SimpleStep(IRETURN));
    });

    public final CastedSimpleStep<JumpInsnNode> REDUNDANT_IF_2_GOTO = new CastedSimpleStep<>(GOTO);
    public final IterableInsnMatcher REDUNDANT_IF_2_MATCHER = new IterableInsnMatcher(a -> {
        a.addStep(new SimpleStep(ILOAD, ALOAD));
        a.addStep(SimpleStep.ofOpcodeRangeInclusive(IFEQ, IFLE, new int[]{IFNULL, IFNONNULL}));
        a.addStep(new ConstantIntInsnStep(1));
        a.addStep(REDUNDANT_IF_2_GOTO);
    });

    public final CastedSimpleStep<JumpInsnNode> REDUNDANT_IF_3_GOTO = new CastedSimpleStep<>(GOTO);
    public final IterableInsnMatcher REDUNDANT_IF_3_MATCHER = new IterableInsnMatcher(a -> {
        a.addStep(new SimpleStep(ILOAD, ALOAD));
        a.addStep(new SimpleStep(ILOAD, ALOAD));
        a.addStep(SimpleStep.ofOpcodeRangeInclusive(IF_ICMPEQ, IF_ACMPNE));
        a.addStep(new ConstantIntInsnStep(1));
        a.addStep(REDUNDANT_IF_3_GOTO);
    });

    @Override
    public boolean transform() throws Throwable {
        DelegatingProvider provider = new DelegatingProvider();
        provider.register(new MappedFieldProvider());
        provider.register(new PrimitiveFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type,
                    Context context) {
                if (!(target.value() instanceof JavaObject)) {
                    return false;
                }
                return type.getInternalName().equals(((JavaObject) target.value()).type());
            }

            @Override
            public boolean checkcast(JavaValue target, Type type,
                    Context context) {
                return true;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second,
                    Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type,
                    Context context) {
                return true;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type,
                    Context context) {
                return true;
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second,
                    Context context) {
                return false;
            }
        });

        System.out.println("[Special] [SuperblaubeereTransformer] Starting");
        AtomicInteger num = new AtomicInteger();
        AtomicInteger unpoolNum = new AtomicInteger();
        AtomicInteger unpoolString = new AtomicInteger();
        AtomicInteger inlinedIfs = new AtomicInteger();
        AtomicInteger indy = new AtomicInteger();

        Set<String> erroredClasses = new HashSet<>();
        //Fold numbers
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                for (MethodNode method : classNode.methods) {
                    boolean modified;
                    do {
                        modified = false;
                        for (AbstractInsnNode ain : method.instructions.toArray()) {
                            if (Utils.isInteger(ain) && ain.getNext() != null && Utils.isInteger(ain.getNext()) && ain.getNext().getNext() != null
                                && isArth(ain.getNext().getNext())) {
                                int res = doArth(Utils.getIntValue(ain), Utils.getIntValue(ain.getNext()), ain.getNext().getNext());
                                method.instructions.remove(ain.getNext().getNext());
                                method.instructions.remove(ain.getNext());
                                method.instructions.set(ain, Utils.getIntInsn(res));
                                num.incrementAndGet();
                                modified = true;
                            } else if (Utils.isInteger(ain) && ain.getNext() != null && ain.getNext().getOpcode() == INEG) {
                                method.instructions.remove(ain.getNext());
                                method.instructions.set(ain, Utils.getIntInsn(-Utils.getIntValue(ain)));
                                num.incrementAndGet();
                                modified = true;
                            } else {
                                String str = TransformerHelper.getConstantString(ain);
                                if (StringUtils.containsOnly(str, ' ')
                                    && TransformerHelper.isInvokeVirtual(ain.getNext(), "java/lang/String", "length", null)) {
                                    if (TransformerHelper.nullsafeOpcodeEqual(ain.getNext().getNext(), POP)) {
                                        method.instructions.remove(ain.getNext().getNext());
                                        method.instructions.remove(ain.getNext());
                                        method.instructions.remove(ain);
                                    } else if (TransformerHelper.nullsafeOpcodeEqual(ain.getNext().getNext(), POP2)) {
                                        method.instructions.set(ain.getNext().getNext(), new InsnNode(POP));
                                        method.instructions.remove(ain.getNext());
                                        method.instructions.remove(ain);
                                    } else {
                                        method.instructions.remove(ain.getNext());
                                        method.instructions.set(ain, Utils.getIntInsn(str.length()));
                                        num.incrementAndGet();
                                    }
                                    modified = true;
                                }
                            }
                        }
                    } while (modified);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        //Reduntant ifs
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if ((Utils.isInteger(ain) || ain.getOpcode() == ACONST_NULL)
                            && ain.getNext() != null && isSingleIf(ain.getNext())) {
                            AbstractInsnNode next = ain.getNext();
                            if (runSingleIf(ain, next)) {
                                while (ain.getNext() != null && !(ain.getNext() instanceof LabelNode)) {
                                    method.instructions.remove(ain.getNext());
                                }
                                method.instructions.set(ain, new JumpInsnNode(GOTO, ((JumpInsnNode) next).label));
                            } else {
                                method.instructions.remove(next);
                                method.instructions.remove(ain);
                            }
                        } else if (Utils.isInteger(ain) && ain.getNext() != null
                                   && Utils.isInteger(ain.getNext()) && ain.getNext().getNext() != null
                                   && isDoubleIf(ain.getNext().getNext())) {
                            AbstractInsnNode next = ain.getNext().getNext();
                            if (runDoubleIf(Utils.getIntValue(ain), Utils.getIntValue(ain.getNext()), next)) {
                                while (ain.getNext() != null && !(ain.getNext() instanceof LabelNode)) {
                                    method.instructions.remove(ain.getNext());
                                }
                                method.instructions.set(ain, new JumpInsnNode(GOTO, ((JumpInsnNode) next).label));
                            } else {
                                method.instructions.remove(next);
                                method.instructions.remove(ain.getNext());
                                method.instructions.remove(ain);
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        //Reduntant ifs
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                Set<MethodNode> toRemove = new HashSet<>();
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (ain.getOpcode() == INVOKESTATIC && ((MethodInsnNode) ain).owner.equals(classNode.name)) {
                            MethodNode refer = classNode.methods.stream().filter(m -> m.name.equals(((MethodInsnNode) ain).name)
                                                                                      && m.desc.equals(((MethodInsnNode) ain).desc)).findFirst().orElse(null);
                            if (refer != null && !Modifier.isNative(refer.access) && !Modifier.isAbstract(refer.access)
                                && Modifier.isPrivate(refer.access) && Modifier.isStatic(refer.access)) {
                                int mode = -1;
                                AbstractInsnNode first = refer.instructions.getFirst();
                                if (REDUNDANT_IF_1_MATCHER.matchImmediately(refer.instructions.iterator())) {
                                    mode = 0;
                                } else if (REDUNDANT_IF_2_MATCHER.matchImmediately(refer.instructions.iterator())
                                           && Utils.isInteger(REDUNDANT_IF_2_GOTO.getCaptured().label.getPrevious())
                                           && Utils.getIntValue(REDUNDANT_IF_2_GOTO.getCaptured().label.getPrevious()) == 0
                                           && refer.instructions.getLast().getOpcode() == IRETURN
                                           && refer.instructions.getLast().getPrevious() == REDUNDANT_IF_2_GOTO.getCaptured().label) {
                                    mode = 1;
                                } else if (REDUNDANT_IF_3_MATCHER.matchImmediately(refer.instructions.iterator())
                                           && Utils.isInteger(REDUNDANT_IF_3_GOTO.getCaptured().label.getPrevious())
                                           && Utils.getIntValue(REDUNDANT_IF_3_GOTO.getCaptured().label.getPrevious()) == 0
                                           && refer.instructions.getLast().getOpcode() == IRETURN
                                           && refer.instructions.getLast().getPrevious() == REDUNDANT_IF_3_GOTO.getCaptured().label) {
                                    mode = 2;
                                }
                                if (mode == 0) {
                                    toRemove.add(refer);
                                    method.instructions.set(ain, first.getNext().getNext().clone(null));
                                    inlinedIfs.incrementAndGet();
                                } else if (mode == 1) {
                                    toRemove.add(refer);
                                    LabelNode jump = ((JumpInsnNode) ain.getNext()).label;
                                    method.instructions.remove(ain.getNext());
                                    method.instructions.set(ain,
                                            new JumpInsnNode(first.getNext().getOpcode(), jump));
                                    inlinedIfs.incrementAndGet();
                                } else if (mode == 2) {
                                    toRemove.add(refer);
                                    LabelNode jump = ((JumpInsnNode) ain.getNext()).label;
                                    method.instructions.remove(ain.getNext());
                                    method.instructions.set(ain,
                                            new JumpInsnNode(first.getNext().getNext().getOpcode(), jump));
                                    inlinedIfs.incrementAndGet();
                                }
                            }
                        }
                    }
                }
                toRemove.forEach(m -> classNode.methods.remove(m));
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        //Unpool numbers
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                MethodNode clinit = TransformerHelper.findClinit(classNode);
                if (clinit == null) {
                    continue;
                }
                AbstractInsnNode first = clinit.instructions.getFirst();
                if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
                    continue;
                }
                MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
                AbstractInsnNode thirdInsn = refMethod.instructions.getFirst().getNext().getNext();
                if (!TransformerHelper.isPutStatic(thirdInsn, null, null, "[I")) {
                    continue;
                }
                FieldInsnNode insnNode = (FieldInsnNode) thirdInsn;
                FieldNode field = TransformerHelper.findFieldNode(classNode, insnNode.name, insnNode.desc);
                Context context = new Context(provider);
                MethodExecutor.execute(classNode, refMethod, null, null, context);
                int[] result = (int[]) context.provider.getField(classNode.name, insnNode.name, insnNode.desc, null, context);
                classNode.methods.remove(refMethod);
                clinit.instructions.remove(clinit.instructions.getFirst());
                classNode.fields.remove(field);
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (TransformerHelper.isGetStatic(ain, classNode.name, field.name, field.desc)) {
                            if (!Utils.isInteger(ain.getNext())) {
                                throw new IllegalStateException();
                            }
                            method.instructions.remove(ain.getNext().getNext());
                            int value = Utils.getIntValue(ain.getNext());
                            method.instructions.remove(ain.getNext());
                            method.instructions.set(ain, Utils.getIntInsn(result[value]));
                            unpoolNum.incrementAndGet();
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        //Decrypt encrypted strings
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                MethodNode clinit = TransformerHelper.findClinit(classNode);
                if (clinit == null) {
                    continue;
                }
                AbstractInsnNode first = clinit.instructions.getFirst();
                if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
                    continue;
                }
                MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
                if (refMethod == null || refMethod.instructions.size() <= 3) {
                    continue;
                }
                if (!STRING_ENCRYPT_MATCHER.matchImmediately(refMethod.instructions.iterator())) {
                    continue;
                }
                AbstractInsnNode lastPrev = refMethod.instructions.getLast().getPrevious();
                if (lastPrev.getOpcode() != PUTSTATIC) {
                    continue;
                }
                FieldInsnNode insnNode = (FieldInsnNode) lastPrev;
                FieldNode field = TransformerHelper.findFieldNode(classNode, insnNode.name, insnNode.desc);
                Context context = new Context(provider);
                context.dictionary = classpath;
                MethodExecutor.execute(classNode, refMethod, null, null, context);
                Object[] resultArray = (Object[]) context.provider.getField(classNode.name, insnNode.name, insnNode.desc, null, context);
                classNode.methods.remove(refMethod);
                clinit.instructions.remove(clinit.instructions.getFirst());
                classNode.fields.remove(field);
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (!TransformerHelper.isGetStatic(ain, classNode.name, field.name, field.desc)) {
                            continue;
                        }
                        if (!Utils.isInteger(ain.getNext())) {
                            throw new IllegalStateException();
                        }
                        method.instructions.remove(ain.getNext().getNext());
                        int value = Utils.getIntValue(ain.getNext());
                        method.instructions.remove(ain.getNext());
                        Object resVal = resultArray[value];
                        if (resVal == null) {
                            System.out.println("Array contains null string?");
                        }
                        method.instructions.set(ain, new LdcInsnNode(resVal));
                    }
                }
                classNode.sourceFile = null;
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        //Unpool strings
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                MethodNode clinit = TransformerHelper.findClinit(classNode);
                if (clinit == null) {
                    continue;
                }
                AbstractInsnNode first = clinit.instructions.getFirst();
                if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
                    continue;
                }
                MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
                AbstractInsnNode possPutStaticInsn = refMethod.instructions.getFirst().getNext().getNext();
                if (!TransformerHelper.isPutStatic(possPutStaticInsn, null, null, "[Ljava/lang/String;")) {
                    possPutStaticInsn = Utils.getPrevious(refMethod.instructions.getLast());
                    if (!TransformerHelper.isPutStatic(possPutStaticInsn, null, null, "[Ljava/lang/String;")) {
                        continue;
                    }
                    AbstractInsnNode prev = Utils.getPrevious(possPutStaticInsn);
                    if (!TransformerHelper.isInvokeVirtual(prev, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;")) {
                        continue;
                    }
                    AbstractInsnNode firstReal = refMethod.instructions.getFirst();
                    if (!Utils.isInstruction(firstReal)) {
                        firstReal = Utils.getNext(firstReal);
                    }
                    AbstractInsnNode insn7 = Utils.getNext(firstReal, 6);
                    if (!TransformerHelper.isInvokeVirtual(insn7, "java/lang/StackTraceElement", "getFileName","()Ljava/lang/String;")) {
                        continue;
                    }
                }
                FieldInsnNode insnNode = (FieldInsnNode) possPutStaticInsn;
                FieldNode field = TransformerHelper.findFieldNode(classNode, insnNode.name, insnNode.desc);
                Context context = new Context(provider);
                context.dictionary = classpath;
                Set<MethodNode> toRemove = new HashSet<>();
                for (AbstractInsnNode ain : refMethod.instructions.toArray()) {
                    if (TransformerHelper.isInvokeStatic(ain, classNode.name, null, null)) {
                        MethodInsnNode min = (MethodInsnNode) ain;
                        toRemove.add(TransformerHelper.findMethodNode(classNode, min.name, min.desc));
                    }
                }
                for (MethodNode m : toRemove) {
                    for (AbstractInsnNode ain : m.instructions.toArray()) {
                        if (TransformerHelper.isInvokeVirtual(ain, "java/lang/String", "getBytes", "(Ljava/nio/charset/Charset;)[B")) {
                            context.customMethodFunc.put(ain, (list, ctx) -> new JavaArray((list.get(1).as(String.class)).getBytes(StandardCharsets.UTF_8)));
                        } else if (TransformerHelper.isInvokeSpecial(ain, "java/lang/String", "<init>", "([BLjava/nio/charset/Charset;)V")) {
                            context.customMethodFunc.put(ain, (list, ctx) -> {
                                list.get(2).initialize(new String(list.get(0).as(byte[].class), StandardCharsets.UTF_8));
                                return null;
                            });
                        }
                    }
                }
                MethodExecutor.execute(classNode, refMethod, null, null, context);
                Object[] result = (Object[]) context.provider.getField(classNode.name, insnNode.name, insnNode.desc,
                        null, context);
                for (MethodNode m : toRemove) {
                    classNode.methods.remove(m);
                }
                classNode.methods.remove(refMethod);
                clinit.instructions.remove(clinit.instructions.getFirst());
                classNode.fields.remove(field);
                for (MethodNode method : classNode.methods) {
                    for (AbstractInsnNode ain : method.instructions.toArray()) {
                        if (TransformerHelper.isGetStatic(ain, classNode.name, field.name, field.desc)) {
                            if (!Utils.isInteger(ain.getNext())) {
                                throw new IllegalStateException();
                            }
                            method.instructions.remove(ain.getNext().getNext());
                            int value = Utils.getIntValue(ain.getNext());
                            method.instructions.remove(ain.getNext());
                            if (result[value] == null) {
                                System.out.println("Array contains null string?");
                            }
                            method.instructions.set(ain, new LdcInsnNode(result[value]));
                            unpoolString.incrementAndGet();
                        }
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        //Remove InvokeDynamics
        for (ClassNode classNode : classNodes()) {
            if (erroredClasses.contains(classNode.name)) {
                continue;
            }
            try {
                MethodNode clinit = TransformerHelper.findClinit(classNode);
                if (clinit == null) {
                    continue;
                }
                AbstractInsnNode first = clinit.instructions.getFirst();
                if (!TransformerHelper.isInvokeStatic(first, classNode.name, null, "()V")) {
                    continue;
                }
                MethodNode refMethod = TransformerHelper.findMethodNode(classNode, ((MethodInsnNode) first).name, "()V");
                FieldNode[] fields = isIndyMethod(classNode, refMethod);
                if (fields == null) {
                    continue;
                }
                MethodNode bootstrap = classNode.methods.stream().filter(m -> isBootstrap(classNode, fields, m)).findFirst().orElse(null);
                if (getConfig().isFastIndy()) {
                    Map<Integer, String> indys = new HashMap<>();
                    Map<Integer, Type> indyClasses = new HashMap<>();
                    for (AbstractInsnNode ain : refMethod.instructions.toArray()) {
                        String str = TransformerHelper.getConstantString(ain);
                        if (str != null) {
                            indys.put(Utils.getIntValue(ain.getPrevious()), str);
                            continue;
                        }
                        Type type = TransformerHelper.getConstantType(ain);
                        if (type != null) {
                            indyClasses.put(Utils.getIntValue(ain.getPrevious()), type);
                            continue;
                        }
                        if (TransformerHelper.isGetStatic(ain, null, "TYPE", "Ljava/lang/Class;")) {
                            indyClasses.put(Utils.getIntValue(ain.getPrevious()), getTypeForClass(((FieldInsnNode) ain).owner));
                        }
                    }
                    for (MethodNode method : classNode.methods) {
                        for (AbstractInsnNode ain : method.instructions.toArray()) {
                            if (!TransformerHelper.isInvokeDynamic(ain, null, null, classNode.name, bootstrap.name, bootstrap.desc, 0)) {
                                continue;
                            }
                            int value = Integer.parseInt(((InvokeDynamicInsnNode) ain).name);
                            String[] decrypted = indys.get(value).split(":");
                            int typeStrLength = decrypted[3].length();
                            if (typeStrLength <= 2) {
                                method.instructions.set(ain, new MethodInsnNode(getOpcodeByTypeStrLength(typeStrLength), decrypted[0].replace('.', '/'),
                                        decrypted[1], decrypted[2], false));
                            } else {
                                method.instructions.set(ain, new FieldInsnNode(getOpcodeByTypeStrLength(typeStrLength), decrypted[0].replace('.', '/'),
                                        decrypted[1], indyClasses.get(Integer.parseInt(decrypted[2])).getDescriptor()));
                            }
                            indy.incrementAndGet();
                        }
                    }
                } else {
                    Context refCtx = new Context(provider);
                    refCtx.dictionary = classpath;
                    MethodExecutor.execute(classNode, refMethod, null, null, refCtx);
                    for (MethodNode method : classNode.methods) {
                        for (AbstractInsnNode ain : method.instructions.toArray()) {
                            if (!TransformerHelper.isInvokeDynamic(ain, null, null, classNode.name, bootstrap.name, bootstrap.desc, 0)) {
                                continue;
                            }
                            List<JavaValue> args = new ArrayList<>();
                            args.add(new JavaObject(null, "java/lang/invoke/MethodHandles$Lookup")); //Lookup
                            args.add(JavaValue.valueOf(((InvokeDynamicInsnNode) ain).name)); //dyn method name
                            args.add(new JavaObject(null, "java/lang/invoke/MethodType")); //dyn method type
                            try {
                                Context context = new Context(provider);
                                context.dictionary = classpath;

                                JavaHandle result = MethodExecutor.execute(classNode, bootstrap, args, null, context);
                                AbstractInsnNode replacement = null;
                                if (result instanceof JavaMethodHandle) {
                                    JavaMethodHandle jmh = (JavaMethodHandle) result;
                                    String clazz = jmh.clazz.replace('.', '/');
                                    switch (jmh.type) {
                                        case "virtual":
                                            boolean isInterface = (classpath.get(clazz).access & ACC_INTERFACE) != 0;
                                            replacement = new MethodInsnNode(isInterface ?
                                                    INVOKEINTERFACE : INVOKEVIRTUAL, clazz, jmh.name, jmh.desc,
                                                    isInterface);
                                            break;
                                        case "static":
                                            replacement = new MethodInsnNode(INVOKESTATIC, clazz, jmh.name, jmh.desc, false);
                                            break;
                                    }
                                } else {
                                    JavaFieldHandle jfh = (JavaFieldHandle) result;
                                    String clazz = jfh.clazz.replace('.', '/');
                                    switch (jfh.type) {
                                        case "virtual":
                                            replacement = new FieldInsnNode(jfh.setter ?
                                                    PUTFIELD : GETFIELD, clazz, jfh.name, jfh.desc);
                                            break;
                                        case "static":
                                            replacement = new FieldInsnNode(jfh.setter ?
                                                    PUTSTATIC : GETSTATIC, clazz, jfh.name, jfh.desc);
                                            break;
                                    }
                                }
                                method.instructions.insert(ain, replacement);
                                method.instructions.remove(ain);
                                indy.incrementAndGet();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                classNode.fields.remove(fields[0]);
                classNode.fields.remove(fields[1]);
                classNode.methods.remove(bootstrap);
                classNode.methods.remove(refMethod);
                clinit.instructions.remove(first);
            } catch (Throwable t) {
                t.printStackTrace();
                erroredClasses.add(classNode.name);
            }
        }
        AtomicInteger decrypted = new AtomicInteger();
        //Warning: No checks will be done to verify if classloader is from Superblaubeere obf
        classEncryption:
        if (getConfig().isClassEncryption()) {
            String[] lines = null;
            int index = -1;
            if (getDeobfuscator().getInputPassthrough().containsKey("META-INF/MANIFEST.MF")) {
                lines = new String(getDeobfuscator().getInputPassthrough().get("META-INF/MANIFEST.MF")).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].startsWith("Main-Class: ")) {
                        index = i;
                        break;
                    }
                }
            }
            String className = index == -1 ? null : lines[index]
                    .substring("Main-Class: ".length(), lines[index].length() - 1)
                    .replace('.', '/');
            ClassNode loader = classNodes().stream()
                    .filter(c -> className == null ? c.superName.equals("java/lang/ClassLoader") : c.name.equals(className))
                    .findFirst().orElse(null);
            if (loader == null) {
                break classEncryption;
            }
            Context context = new Context(provider);
            context.dictionary = classpath;
            MethodNode clinit = TransformerHelper.findClinit(loader);
            MethodExecutor.execute(loader, clinit, null, null, context);
            String realMainClass = null;
            MethodNode main = TransformerHelper.findMethodNode(loader, "main", "([Ljava/lang/String;)V");
            for (AbstractInsnNode ain : main.instructions.toArray()) {
                if (TransformerHelper.isInvokeVirtual(ain, "java/lang/ClassLoader", "loadClass", null)) {
                    realMainClass = (String) ((LdcInsnNode) ain.getPrevious()).cst;
                    break;
                }
            }
            MethodNode decMethod = TransformerHelper.findFirstMethodNode(loader, null, "([B[B)[B");
            //Decryption array
            byte[] b = (byte[]) context.provider.getField(className, loader.fields.get(0).name, loader.fields.get(0).desc,
                    null, context);
            //Decrypt all classes
            List<String> remove = new ArrayList<>();
            for (Entry<String, byte[]> entry : getDeobfuscator().getInputPassthrough().entrySet()) {
                List<JavaValue> args = Arrays.asList(new JavaArray(entry.getValue()), new JavaArray(b));
                byte[] decBytes = MethodExecutor.execute(loader, decMethod, args, null, context);
                try {
                    ClassReader reader = new ClassReader(decBytes);
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_FRAMES);
                    getDeobfuscator().loadInput(node.name + ".class", decBytes);
                    remove.add(entry.getKey());
                    decrypted.incrementAndGet();
                } catch (Exception e) {
                    //Not an encrypted resource
                }
            }
            remove.forEach(n -> getDeobfuscator().getInputPassthrough().remove(n));
            classes.remove(className);
            classpath.remove(className);
            if (index != -1) {
                lines[index] = "Main-Class: " + realMainClass;
                StringBuilder sb = new StringBuilder();
                for (String line : lines) {
                    sb.append(line).append("\n");
                }
                getDeobfuscator().getInputPassthrough().put("META-INF/MANIFEST.MF", sb.substring(0, sb.length() - 1).getBytes());
            }
        }
        System.out.println("[Special] [SuperblaubeereTransformer] Removed " + num + " number obfuscation instructions");
        System.out.println("[Special] [SuperblaubeereTransformer] Inlined " + unpoolNum + " numbers");
        System.out.println("[Special] [SuperblaubeereTransformer] Unpooled " + unpoolString + " strings");
        System.out.println("[Special] [SuperblaubeereTransformer] Inlined " + inlinedIfs + " if statements");
        System.out.println("[Special] [SuperblaubeereTransformer] Removed " + indy + " invokedynamics");
        if (getConfig().isClassEncryption()) {
            System.out.println("[Special] [SuperblaubeereTransformer] Decrypted " + decrypted + " classes");
        }
        if (!erroredClasses.isEmpty()) {
            System.out.println("[Special] [SuperblaubeereTransformer] Errors occurred during decryption of " + erroredClasses.size() + " classes:");
            for (String erroredClass : erroredClasses) {
                System.out.println("[Special] [SuperblaubeereTransformer]   - " + erroredClass);
            }
        }
        System.out.println("[Special] [SuperblaubeereTransformer] Done");
        return num.get() > 0 || unpoolNum.get() > 0 || unpoolString.get() > 0 || inlinedIfs.get() > 0 || indy.get() > 0;
    }

    private Type getTypeForClass(String clazz) {
        switch (clazz) {
            case "java/lang/Integer":
                return Type.INT_TYPE;
            case "java/lang/Boolean":
                return Type.BOOLEAN_TYPE;
            case "java/lang/Character":
                return Type.CHAR_TYPE;
            case "java/lang/Byte":
                return Type.BYTE_TYPE;
            case "java/lang/Short":
                return Type.SHORT_TYPE;
            case "java/lang/Float":
                return Type.FLOAT_TYPE;
            case "java/lang/Long":
                return Type.LONG_TYPE;
            case "java/lang/Double":
                return Type.DOUBLE_TYPE;
            default:
                return null;
        }
    }

    public static boolean isBootstrap(ClassNode classNode, FieldNode[] fields, MethodNode method) {
        if (!method.desc.equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")) {
            return false;
        }
        boolean[] verify = new boolean[2];
        for (AbstractInsnNode ain : method.instructions.toArray()) {
            if (TransformerHelper.isGetStatic(ain, classNode.name, fields[0].name, fields[0].desc)) {
                verify[0] = true;
            } else if (TransformerHelper.isGetStatic(ain, classNode.name, fields[1].name, fields[1].desc)) {
                verify[1] = true;
            }
        }
        return verify[0] && verify[1];
    }

    public static FieldNode[] isIndyMethod(ClassNode classNode, MethodNode method) {
        FieldNode[] fieldNodes = new FieldNode[2];

        FieldInsnStep fieldStep = new FieldInsnStep(PUTSTATIC, classNode.name, null, null);
        IterableInsnMatcher indyMatcher = new IterableInsnMatcher(a -> {
            a.addStep(new ConstantIntInsnStep());
            a.addStep(new TypeInsnStep(ANEWARRAY, "java/lang/String"));
            a.addStep(fieldStep);
        });
        if (indyMatcher.matchImmediately(method.instructions.iterator())) {
            fieldNodes[0] = TransformerHelper.findFieldNode(classNode, fieldStep.getCaptured().name, fieldStep.getCaptured().desc);
        }
        if (fieldNodes[0] == null) {
            return null;
        }

        FieldInsnStep fieldStep2 = new FieldInsnStep(PUTSTATIC, classNode.name, null, null);
        IterableInsnMatcher indyMatcher2 = new IterableInsnMatcher(a -> {
            a.addStep(new ConstantIntInsnStep());
            a.addStep(new TypeInsnStep(ANEWARRAY, "java/lang/Class"));
            a.addStep(fieldStep2);
        });
        if (indyMatcher2.match(method.instructions.iterator())) {
            fieldNodes[1] = TransformerHelper.findFieldNode(classNode, fieldStep2.getCaptured().name, fieldStep2.getCaptured().desc);
            return fieldNodes;
        }
        return null;
    }

    private static int getOpcodeByTypeStrLength(int len) {
        switch (len) {
            case 1:
                return INVOKESTATIC;
            case 2:
                return INVOKEVIRTUAL;
            case 3:
                return GETFIELD;
            case 4:
                return GETSTATIC;
            case 5:
                return PUTFIELD;
            case 6:
                return PUTSTATIC;
            default:
                throw new RuntimeException("Unexpected length: " + len);
        }
    }

    private static boolean isArth(AbstractInsnNode ain) {
        switch (ain.getOpcode()) {
            case IADD:
            case ISUB:
            case IMUL:
            case IDIV:
            case IXOR:
            case IAND:
            case ISHL:
                return true;
            default:
                return false;
        }
    }

    private static int doArth(int num1, int num2, AbstractInsnNode ain) {
        switch (ain.getOpcode()) {
            case IADD:
                return num1 + num2;
            case ISUB:
                return num1 - num2;
            case IMUL:
                return num1 * num2;
            case IDIV:
                return num1 / num2;
            case IXOR:
                return num1 ^ num2;
            case IAND:
                return num1 & num2;
            case ISHL:
                return num1 << num2;
        }
        throw new RuntimeException("Unexpected opcode");
    }

    private static boolean isSingleIf(AbstractInsnNode ain) {
        return ain.getOpcode() == IFNULL || ain.getOpcode() == IFNONNULL || (ain.getOpcode() >= IFEQ && ain.getOpcode() <= IFLE);
    }

    private static boolean runSingleIf(AbstractInsnNode v, AbstractInsnNode ain) {
        int value = Utils.getIntValue(v);
        switch (ain.getOpcode()) {
            case IFNULL:
                return true;
            case IFNONNULL:
                return false;
            case IFEQ:
                return value == 0;
            case IFNE:
                return value != 0;
            case IFLT:
                return value < 0;
            case IFGE:
                return value >= 0;
            case IFGT:
                return value > 0;
            case IFLE:
                return value <= 0;
        }
        throw new RuntimeException("Unexpected opcode");
    }

    private static boolean isDoubleIf(AbstractInsnNode ain) {
        return ain.getOpcode() >= IF_ICMPEQ && ain.getOpcode() <= IF_ICMPLE;
    }

    private static boolean runDoubleIf(int num1, int num2, AbstractInsnNode ain) {
        switch (ain.getOpcode()) {
            case IF_ICMPEQ:
                return num1 == num2;
            case IF_ICMPNE:
                return num1 != num2;
            case IF_ICMPLT:
                return num1 < num2;
            case IF_ICMPGE:
                return num1 >= num2;
            case IF_ICMPGT:
                return num1 > num2;
            case IF_ICMPLE:
                return num1 <= num2;
        }
        throw new RuntimeException("Unexpected opcode");
    }
}
