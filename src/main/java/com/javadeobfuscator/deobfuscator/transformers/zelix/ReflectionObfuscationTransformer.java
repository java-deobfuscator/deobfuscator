/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.zelix;

import com.javadeobfuscator.deobfuscator.analyzer.AnalyzerResult;
import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.*;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.*;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaField;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaLong;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ReflectionObfuscationTransformer extends Transformer<TransformerConfig> {
    static Map<String, String> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put("boolean", "java/lang/Boolean");
        PRIMITIVES.put("byte", "java/lang/Byte");
        PRIMITIVES.put("char", "java/lang/Character");
        PRIMITIVES.put("short", "java/lang/Short");
        PRIMITIVES.put("int", "java/lang/Integer");
        PRIMITIVES.put("float", "java/lang/Float");
        PRIMITIVES.put("double", "java/lang/Double");
        PRIMITIVES.put("long", "java/lang/Long");
    }

    @Override
    public boolean transform() throws Throwable {
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Starting");
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Finding reflection obfuscation");
        int count = findReflectionObfuscation();
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Found " + count + " reflection obfuscation instructions");
        if (count > 0) {
            int amount = inlineReflection(count);
            System.out.println("[Zelix] [ReflectionObfuscationTransformer] Inlined " + amount + " reflection obfuscation instructions");
        }
        System.out.println("[Zelix] [ReflectionObfuscationTransformer] Done");
        return true;
    }

    public int inlineReflection(int expected) throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        final boolean[] alerted = new boolean[100];

        DelegatingProvider provider = new DelegatingProvider();

        provider.register(new PrimitiveFieldProvider());
        provider.register(new MappedFieldProvider());
        provider.register(new JVMMethodProvider());
        provider.register(new JVMComparisonProvider());
        provider.register(new MappedMethodProvider(classes));
        provider.register(new ComparisonProvider() {
            @Override
            public boolean instanceOf(JavaValue target, Type type, Context context) {
                return type.getDescriptor().equals("Ljava/lang/String;") && target.value() instanceof String;
            }

            @Override
            public boolean checkcast(JavaValue target, Type type, Context context) {
                if (type.getInternalName().equals("java/lang/String")) {
                    return target.value() instanceof String;
                } else if (type.getInternalName().equals("java/lang/Class")) {
                    return target.value() instanceof JavaClass || target.value() instanceof Type; //TODO consolidate types
                } else if (type.getInternalName().equals("java/lang/reflect/Method")) {
                    return target.value() instanceof JavaMethod;
                } else if (type.getInternalName().equals("java/lang/reflect/Field")) {
                    return target.value() instanceof JavaField;
                } else if (type.getInternalName().equals("[Ljava/lang/reflect/Method;")) {
                    return target.value() instanceof Object[];
                } else if (type.getInternalName().equals("[Ljava/lang/Class;")) {
                    return target.value() instanceof Object[];
                }
                return false;
            }

            @Override
            public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }

            @Override
            public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
                return true;
            }

            @Override
            public boolean canCheckcast(JavaValue target, Type type, Context context) {
                return type.getInternalName().equals("java/lang/String")
                        || type.getInternalName().equals("java/lang/Class")
                        || type.getInternalName().equals("java/lang/reflect/Method")
                        || type.getInternalName().equals("java/lang/reflect/Field")
                        || type.getInternalName().equals("[Ljava/lang/reflect/Method;")
                        || type.getInternalName().equals("[Ljava/lang/Class;");
            }

            @Override
            public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
                return false;
            }
        });

        Set<ClassNode> initted = new HashSet<>();
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                /*
                NOTE: We can't remove reflection try/catch blocks until we remove the reflection, otherwise we may throw the wrong exceptions
                For example:

                try {
                    new File("something").delete();
                } catch (IOException e) {
                }

                is turned into

                try {
                    try {
                        ReflectionObfuscation(5464891915L).invoke(new File("something"));
                    } catch (InvocationTargetException e) {
                        throw e.getTargetException();
                    }
                } catch (IOException e) {
                }
                 */
                boolean found = false;
                { //fixme for loop
                    AbstractInsnNode current = methodNode.instructions.getFirst();
                    int i = 0;
                    while (i < methodNode.instructions.size()) {
                        current = methodNode.instructions.get(i++);
                        if (current == null) {
                            continue;
                        }
                        if (current instanceof MethodInsnNode) {

                            MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                            if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Method;")) {
                                long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                                String strCl = methodInsnNode.owner;
                                ClassNode innerClassNode = classpath.get(strCl);
                                if (initted.add(innerClassNode)) {
                                    try {
                                        MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                        Context context = new Context(provider);
                                        context.dictionary = this.classpath;
                                        MethodExecutor.execute(classpath.get(innerClassNode.name), decrypterNode, Collections.emptyList(), null, context);
                                    } catch (Throwable t) {
                                        System.out.println("Error while fully initializing  " + strCl);
                                        t.printStackTrace(System.out);
                                    }
                                }

                                MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                                Context ctx = new Context(provider);
                                ctx.dictionary = classpath;
                                JavaMethod javaMethod = MethodExecutor.execute(classpath.get(innerClassNode.name), decrypterNode, Arrays.asList(new JavaLong(ldc)), null, ctx);

                                InsnList replacement = new InsnList();
                                String str = javaMethod.getDeclaringClass().getName().replace('.', '/');

                                Type t = Type.getObjectType(str);
                                replacement.add(new LdcInsnNode(t));
                                replacement.add(new LdcInsnNode(javaMethod.getName()));
                                replacement.add(new LdcInsnNode(javaMethod.getParameterTypes().length));
                                replacement.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"));
                                for (int x = 0; x < javaMethod.getParameterTypes().length; x++) {
                                    JavaClass param = javaMethod.getParameterTypes()[x];
                                    replacement.add(new InsnNode(Opcodes.DUP));
                                    replacement.add(new LdcInsnNode(x));
                                    if (param.isPrimitive()) {
                                        replacement.add(new FieldInsnNode(Opcodes.GETSTATIC, PRIMITIVES.get(param.getName()), "TYPE", "Ljava/lang/Class;"));
                                    } else {
                                        String pp = param.getName().replace('.', '/');
                                        Type t1 = Type.getObjectType(pp);
                                        replacement.add(new LdcInsnNode(t1));
                                    }
                                    replacement.add(new InsnNode(Opcodes.AASTORE));
                                }
                                replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false));
                                methodNode.instructions.insert(current.getPrevious().getPrevious(), replacement);
                                methodNode.instructions.remove(current.getPrevious());
                                methodNode.instructions.remove(current);
                                count.incrementAndGet();
                                found = true;
                                int x = (int) ((count.get() * 1.0d / expected) * 100);
                                if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                    System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                    alerted[x - 1] = true;
                                }
                            } else if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Field;")) {
                                long ldc = (long) ((LdcInsnNode) current.getPrevious()).cst;
                                String strCl = methodInsnNode.owner;
                                ClassNode innerClassNode = classpath.get(strCl);
                                if (initted.add(innerClassNode)) {
                                    MethodNode decrypterNode1 = innerClassNode.methods.stream().filter(mn -> mn.name.equals("<clinit>")).findFirst().orElse(null);
                                    MethodExecutor.execute(classpath.get(innerClassNode.name), decrypterNode1, Collections.singletonList(new JavaLong(ldc)), null, new Context(provider));
                                }
                                MethodNode decrypterNode = innerClassNode.methods.stream().filter(mn -> mn.name.equals(methodInsnNode.name) && mn.desc.equals(methodInsnNode.desc)).findFirst().orElse(null);
                                Context ctx = new Context(provider);
                                ctx.dictionary = classpath;
                                JavaField javaField = MethodExecutor.execute(classpath.get(classNode.name), decrypterNode, Collections.singletonList(new JavaLong(ldc)), null, ctx);
                                InsnList replacement = new InsnList();
                                Type t = Type.getObjectType(javaField.getDeclaringClass().getName().replace('.', '/'));
                                replacement.add(new LdcInsnNode(t));
                                replacement.add(new LdcInsnNode(javaField.getName()));
                                replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false));
                                methodNode.instructions.insertBefore(current.getPrevious(), replacement);
                                methodNode.instructions.remove(current.getPrevious());
                                methodNode.instructions.remove(current);
                                count.incrementAndGet();
                                found = true;
                                int x = (int) ((count.get() * 1.0d / expected) * 100);
                                if (x != 0 && x % 10 == 0 && !alerted[x - 1]) {
                                    System.out.println("[Zelix] [ReflectionObfucationTransformer] Done " + x + "%");
                                    alerted[x - 1] = true;
                                }
                            }
                        }
                    }
                }

                if (found && false) {
                    int maxLocals = -1;

                    boolean modified = false;
                    outer:
                    do {
                        AnalyzerResult result = MethodAnalyzer.analyze(classes.get(classNode.name), methodNode);
                        Map<AbstractInsnNode, List<Frame>> analysis = result.getFrames();
                        Map<Frame, AbstractInsnNode> reverseMapping = new HashMap<>();
                        analysis.entrySet().forEach(ent -> ent.getValue().forEach(frame -> reverseMapping.put(frame, ent.getKey())));
                        modified = false;
                        for (int i = 0; i < methodNode.instructions.size(); i++) {
                            AbstractInsnNode node = methodNode.instructions.get(i);
                            if (node instanceof MethodInsnNode) {
                                MethodInsnNode cast = (MethodInsnNode) node;
                                if (cast.owner.equals("java/lang/reflect/Method") && cast.name.equals("invoke")) {
                                    List<Frame> frames = analysis.get(cast);
                                    if (frames != null) {
                                        Map<AbstractInsnNode, Frame> tmp = new HashMap<>();
                                        for (Frame frame : frames) {
                                            tmp.put(reverseMapping.get(frame), frame);
                                        }
                                        frames = new ArrayList<>(tmp.values());
                                        if (frames.size() != 1) {
                                            throw new IllegalArgumentException("Expected only one frame, but got " + frames.size());
                                        }
                                        MethodFrame methodFrame = (MethodFrame) frames.get(0);
                                        if (methodFrame.getInstance() instanceof MethodFrame) {
                                            MethodFrame instance = (MethodFrame) methodFrame.getInstance();
                                            LdcFrame targetClazz = (LdcFrame) instance.getInstance();
                                            LdcFrame targetMethod = (LdcFrame) instance.getArgs().get(0);
                                            NewArrayFrame targetArgs = (NewArrayFrame) instance.getArgs().get(1);

                                            String findClass = ((Type) targetClazz.getConstant()).getInternalName();
                                            String findMethod = (String) targetMethod.getConstant();
                                            String findDesc = frameToDesc(targetArgs);

                                            Frame invokeInstance = methodFrame.getArgs().get(0);
                                            MethodFrame invokeArgs = (MethodFrame) methodFrame.getArgs().get(1);
                                            MethodInsnNode toArray = (MethodInsnNode) reverseMapping.get(invokeArgs);

                                            InsnList store = new InsnList();
                                            int index = (maxLocals == -1 ? result.getMaxLocals() : maxLocals);
                                            maxLocals = index;
                                            List<Type> types = Arrays.asList(Type.getArgumentTypes(toArray.desc));
                                            Collections.reverse(types);
                                            List<Integer> indices = new ArrayList<>();

                                            AbstractInsnNode first = null;

                                            for (Type type : types) {
                                                int opcode, size;
                                                switch (type.getSort()) {
                                                    case Type.BOOLEAN:
                                                        opcode = Opcodes.ISTORE;
                                                        size = 1;
                                                        break;
                                                    case Type.CHAR:
                                                        opcode = Opcodes.ISTORE;
                                                        size = 1;
                                                        break;
                                                    case Type.BYTE:
                                                        opcode = Opcodes.ISTORE;
                                                        size = 1;
                                                        break;
                                                    case Type.SHORT:
                                                        opcode = Opcodes.ISTORE;
                                                        size = 1;
                                                        break;
                                                    case Type.INT:
                                                        opcode = Opcodes.ISTORE;
                                                        size = 1;
                                                        break;
                                                    case Type.FLOAT:
                                                        opcode = Opcodes.FSTORE;
                                                        size = 1;
                                                        break;
                                                    case Type.LONG:
                                                        opcode = Opcodes.LSTORE;
                                                        size = 2;
                                                        break;
                                                    case Type.DOUBLE:
                                                        opcode = Opcodes.DSTORE;
                                                        size = 2;
                                                        break;
                                                    case Type.OBJECT:
                                                        opcode = Opcodes.ASTORE;
                                                        size = 1;
                                                        break;
                                                    default:
                                                        throw new IllegalArgumentException("Unknown type");
                                                }
                                                VarInsnNode vin = new VarInsnNode(opcode, index);
                                                store.add(vin);
                                                if (first == null) {
                                                    first = vin;
                                                }
                                                indices.add(0, index);
                                                index += size;
                                            }
                                            store.add(new InsnNode(Opcodes.ACONST_NULL));

                                            if (toArray.getNext().getOpcode() == Opcodes.CHECKCAST) {
                                                methodNode.instructions.remove(toArray.getNext());
                                            }
                                            methodNode.instructions.insert(toArray, store);
                                            methodNode.instructions.remove(toArray);

                                            MethodNode target = null;
                                            ClassNode startingNode = classpath.get(findClass);
                                            if (startingNode == null) {
                                                throw new IllegalArgumentException(findClass);
                                            }
                                            ClassNode currentNode = startingNode;
                                            LinkedList<ClassNode> candidates = new LinkedList<>();
                                            candidates.add(currentNode);
                                            loop:
                                            while (!candidates.isEmpty()) {
                                                currentNode = candidates.pop();
                                                for (MethodNode possible : currentNode.methods) {
                                                    if (possible.name.equals(findMethod) && possible.desc.startsWith(findDesc)) {
                                                        target = possible;
                                                        break loop;
                                                    }
                                                }
                                                if (!currentNode.name.equals("java/lang/Object")) {
                                                    ClassNode newCurrent = classpath.get(currentNode.superName);
                                                    if (newCurrent == null) {
                                                        throw new IllegalArgumentException(currentNode.name + " " + findMethod + findDesc);
                                                    }
                                                    candidates.add(newCurrent);
                                                }
                                                for (String intf : currentNode.interfaces) {
                                                    ClassNode newCurrent = classpath.get(intf);
                                                    if (newCurrent == null) {
                                                        throw new IllegalArgumentException(intf + " " + findMethod + findDesc);
                                                    }
                                                    candidates.add(newCurrent);
                                                }
                                            }

                                            if (target != null) {
                                                InsnList replacement = new InsnList();
                                                replacement.add(new InsnNode(Opcodes.POP));
                                                if (!Modifier.isStatic(target.access)) {
                                                    replacement.add(new InsnNode(Opcodes.SWAP));
                                                } else {
                                                    replacement.add(new InsnNode(Opcodes.POP));
                                                }
                                                replacement.add(new InsnNode(Opcodes.POP));
                                                Collections.reverse(types);
                                                int ind = 0;
                                                for (Type type : types) {
                                                    int opcode;
                                                    switch (type.getSort()) {
                                                        case Type.BOOLEAN:
                                                            opcode = Opcodes.ILOAD;
                                                            break;
                                                        case Type.CHAR:
                                                            opcode = Opcodes.ILOAD;
                                                            break;
                                                        case Type.BYTE:
                                                            opcode = Opcodes.ILOAD;
                                                            break;
                                                        case Type.SHORT:
                                                            opcode = Opcodes.ILOAD;
                                                            break;
                                                        case Type.INT:
                                                            opcode = Opcodes.ILOAD;
                                                            break;
                                                        case Type.FLOAT:
                                                            opcode = Opcodes.FLOAD;
                                                            break;
                                                        case Type.LONG:
                                                            opcode = Opcodes.LLOAD;
                                                            break;
                                                        case Type.DOUBLE:
                                                            opcode = Opcodes.DLOAD;
                                                            break;
                                                        case Type.OBJECT:
                                                            opcode = Opcodes.ALOAD;
                                                            break;
                                                        default:
                                                            throw new IllegalArgumentException("Unknown type");
                                                    }
                                                    replacement.add(new VarInsnNode(opcode, indices.get(ind++)));
                                                }

                                                MethodInsnNode methodinsnnode = new MethodInsnNode(Modifier.isStatic(target.access) ? Opcodes.INVOKESTATIC : Modifier.isInterface(target.access) ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL, findClass, target.name, target.desc, Modifier.isInterface(target.access));
                                                replacement.add(methodinsnnode);
                                                int returnType = Type.getReturnType(target.desc).getSort();
                                                if (returnType == Type.VOID) {
                                                    replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                                                } else if (returnType != Type.OBJECT && returnType != Type.ARRAY) {
                                                    if (cast.getNext().getOpcode() == Opcodes.CHECKCAST) {
                                                        methodNode.instructions.remove(cast.getNext());
                                                    }
                                                    if (cast.getNext().getOpcode() == Opcodes.INVOKEVIRTUAL) {
                                                        methodNode.instructions.remove(cast.getNext());
                                                    }
                                                }

                                                methodNode.instructions.insert(cast, replacement);
                                                methodNode.instructions.remove(cast);

                                                replacement = new InsnList();
                                                replacement.add(new InsnNode(Opcodes.ACONST_NULL));

                                                AbstractInsnNode ldcClass = reverseMapping.get(instance.getInstance());
                                                AbstractInsnNode ldcName = reverseMapping.get(instance.getArgs().get(0));

                                                methodNode.instructions.remove(ldcClass);
                                                methodNode.instructions.remove(ldcName);

                                                methodNode.instructions.insert(reverseMapping.get(instance), replacement);
                                                methodNode.instructions.remove(reverseMapping.get(instance));

                                                methodNode.instructions.remove(reverseMapping.get(targetArgs));
                                                methodNode.instructions.remove(reverseMapping.get(targetArgs.getLength()));
                                                targetArgs.getChildren().forEach(fr -> {
                                                    if (fr instanceof ArrayStoreFrame) {
                                                        ArrayStoreFrame asf = (ArrayStoreFrame) fr;
                                                        methodNode.instructions.remove(reverseMapping.get(asf.getIndex()));
                                                        methodNode.instructions.remove(reverseMapping.get(asf.getObject()));
                                                        methodNode.instructions.remove(reverseMapping.get(fr));
                                                    } else if (fr instanceof DupFrame) {
                                                        methodNode.instructions.remove(reverseMapping.get(fr));
                                                    } else if (fr != instance) {
                                                        throw new IllegalArgumentException(fr.toString());
                                                    }
                                                });
//
//                                            List<AbstractInsnNode> toRemove = new ArrayList<>();
//                                            int aconstnull = 0;
//                                            AbstractInsnNode cur = methodinsnnode.getPrevious();
//                                            if (methodinsnnode.getPrevious().getOpcode() == Opcodes.POP) {
//                                                while (true) {
//                                                    if (!(cur instanceof LabelNode)) {
//                                                        toRemove.add(cur);
//                                                    }
//                                                    if (cur.getOpcode() == Opcodes.ACONST_NULL) {
//                                                        aconstnull++;
//                                                    }
//                                                    if (aconstnull == 2) {
//                                                        break;
//                                                    }
//                                                    cur = cur.getPrevious();
//                                                }
//                                            } else {
//                                                VarInsnNode vin = (VarInsnNode) methodinsnnode.getPrevious();
//                                                toRemove.add(vin);
//                                                cur = vin.getPrevious();
//                                                while (true) {
//                                                    if (!(cur instanceof LabelNode)) {
//                                                        toRemove.add(cur);
//                                                    }
//                                                    if (cur instanceof VarInsnNode) {
//                                                        VarInsnNode vin1 = (VarInsnNode) cur;
//                                                        if (vin1.var == vin.var) {
//                                                            break;
//                                                        }
//                                                    }
//                                                    cur = cur.getPrevious();
//                                                }
//                                            }
//                                            toRemove.forEach(methodNode.instructions::remove);
                                            } else {
                                                System.out.println("Could not find " + findMethod + findDesc + " in " + findClass);
                                            }
                                            modified = true;
                                            continue outer;
                                        }
                                    } else {
                                        System.out.println("Null frame?");
                                    }
                                } else if (cast.owner.equals("java/lang/reflect/Field")) {

                                }
                            }
                        }
                    } while (modified);
                    if (methodNode.tryCatchBlocks != null) {
                        Iterator<TryCatchBlockNode> it = methodNode.tryCatchBlocks.iterator();
                        while (it.hasNext()) {
                            TryCatchBlockNode next = it.next();
                            if (next.type != null && next.type.startsWith("java/lang/reflect")) {
                                it.remove();
                            }
                        }
                    }
                }
            });
        });
        classNodes().stream().filter(initted::contains).forEach(node -> {
            node.fields.add(0, new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "REFLECTION_OBFUSCATION_CLASS", "Z", null, true));
        });

        return count.get();
    }

    private String frameToDesc(NewArrayFrame frame) {
        StringBuilder builder = new StringBuilder("(");
        int index = 0;
        for (Frame child : frame.getChildren()) {
            if (child instanceof ArrayStoreFrame) {
                ArrayStoreFrame arrayStore = (ArrayStoreFrame) child;
                int nowIndex = (int) ((LdcFrame) arrayStore.getIndex()).getConstant();
                if (nowIndex != index) {
                    throw new IllegalArgumentException("Index mismatch");
                }

                Frame value = arrayStore.getObject();
                if (value instanceof LdcFrame) {
                    builder.append(((Type) ((LdcFrame) value).getConstant()).getDescriptor());
                } else if (value instanceof FieldFrame) {
                    FieldFrame fieldFrame = (FieldFrame) value;
                    String desc = null;
                    switch (fieldFrame.getOwner()) {
                        case "java/lang/Integer":
                            desc = "I";
                            break;
                        case "java/lang/Byte":
                            desc = "B";
                            break;
                        case "java/lang/Short":
                            desc = "S";
                            break;
                        case "java/lang/Float":
                            desc = "F";
                            break;
                        case "java/lang/Boolean":
                            desc = "Z";
                            break;
                        case "java/lang/Character":
                            desc = "C";
                            break;
                        case "java/lang/Double":
                            desc = "D";
                            break;
                        case "java/lang/Long":
                            desc = "J";
                            break;
                        default:
                            throw new IllegalStateException(fieldFrame.getOwner());
                    }
                    builder.append(desc);
                } else {
                    throw new IllegalArgumentException("Unexpected frame " + value);
                }

                index++;
            }
        }

        builder.append(")");
        return builder.toString();
    }

    public int findReflectionObfuscation() throws Throwable {
        AtomicInteger count = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode current = methodNode.instructions.get(i);
                    if (current instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) current;
                        if (methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Method;") || methodInsnNode.desc.equals("(J)Ljava/lang/reflect/Field;")) {
                            count.incrementAndGet();
                        }
                    }
                }
            });
        });
        return count.get();
    }
}
