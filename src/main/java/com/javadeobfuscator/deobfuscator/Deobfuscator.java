/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator;

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.ClassReader;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.ClassWriter;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.commons.JSRInlinerAdapter;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.util.CheckClassAdapter;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class Deobfuscator {

    private List<Class<? extends Transformer>> transformers = new ArrayList<>();
    private List<File> classpathFiles = new ArrayList<>();
    private Map<String, WrappedClassNode> classpath = new HashMap<>();
    private Map<String, WrappedClassNode> classes = new HashMap<>();
    private Map<String, ClassTree> hierachy = new HashMap<>();
    private File input;
    private File output;

    public Deobfuscator withTransformer(Class<? extends Transformer> transformer) {
        this.transformers.add(transformer);
        return this;
    }

    public Deobfuscator withClasspath(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles((dir, name) -> name.endsWith(".jar"));
            if (files != null) {
                classpathFiles.addAll(Arrays.asList(files));
            }
        } else {
            this.classpathFiles.add(file);
        }
        return this;
    }

    public Deobfuscator withInput(File input) {
        this.input = input;
        return this;
    }

    public Deobfuscator withOutput(File output) {
        this.output = output;
        return this;
    }

    public void start() throws Throwable {
        for (File file : classpathFiles) {
            ZipFile zipIn = new ZipFile(file);
            Enumeration<? extends ZipEntry> entries = zipIn.entries();
            while (entries.hasMoreElements()) {
                ZipEntry ent = entries.nextElement();
                if (ent.getName().endsWith(".class")) {
                    ClassReader reader = new ClassReader(zipIn.getInputStream(ent));
                    ClassNode node = new ClassNode();
                    node.isLibrary = true;
                    reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    WrappedClassNode wrappedClassNode = new WrappedClassNode(node, reader.getItemCount());
                    classpath.put(node.name, wrappedClassNode);
                }
            }
            zipIn.close();
        }
        ZipFile zipIn = new ZipFile(input);
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(output));
        Enumeration<? extends ZipEntry> e = zipIn.entries();
        while (e.hasMoreElements()) {
            ZipEntry next = e.nextElement();
            if (next.getName().endsWith(".class")) {
                try {
                    InputStream in = zipIn.getInputStream(next);
                    ClassReader reader = new ClassReader(in);
                    ClassNode node = new ClassNode();
                    reader.accept(node, ClassReader.SKIP_FRAMES);
                    for (int i = 0; i < node.methods.size(); i++) {
                        MethodNode methodNode = node.methods.get(i);
                        JSRInlinerAdapter adapter = new JSRInlinerAdapter(methodNode, methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
                        methodNode.accept(adapter);
                        node.methods.set(i, adapter);
                    }
                    WrappedClassNode wr = new WrappedClassNode(node, reader.getItemCount());
                    classes.put(node.name, wr);
                } catch (IllegalArgumentException x) {
                    System.out.println("Could not parse " + next.getName() + " (is it a class?)");
                    x.printStackTrace(System.out);
                    zipOut.putNextEntry(new ZipEntry(next.getName()));
                    Utils.copy(zipIn.getInputStream(next), zipOut);
                    zipOut.closeEntry();
                }
            } else {
                zipOut.putNextEntry(new ZipEntry(next.getName()));
                Utils.copy(zipIn.getInputStream(next), zipOut);
                zipOut.closeEntry();
            }
        }

        classpath.putAll(classes);

        Map<MethodNode, List<Entry<WrappedClassNode, MethodNode>>> callers = new HashMap<>();
        classes.values().forEach(wrappedClassNode -> {
            wrappedClassNode.classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode node = methodNode.instructions.get(i);
                    if (node instanceof MethodInsnNode) {
                        MethodInsnNode mn = (MethodInsnNode) node;
                        WrappedClassNode targetNode = classes.get(mn.owner);
                        if (targetNode != null) {
                            MethodNode targetMethod = targetNode.classNode.methods.stream().filter(m -> m.name.equals(mn.name) && m.desc.equals(mn.desc)).findFirst().orElse(null);
                            if (targetMethod != null) {
                                List<Entry<WrappedClassNode, MethodNode>> caller = callers.get(targetMethod);
                                if (caller == null) {
                                    caller = new ArrayList<>();
                                    callers.put(targetMethod, caller);
                                }
                                caller.add(new SimpleEntry<>(wrappedClassNode, methodNode));
                            }
                        }
                    }
                }
            });
        });

        System.out.println();
        System.out.println("Reading complete. Loading hierachy");
        System.out.println();

        loadHierachy();

        System.out.println();
        System.out.println("Transforming");
        System.out.println();


        for (Class<? extends Transformer> transformerClass : transformers) {
            Transformer transformer = transformerClass.getConstructor(Map.class, Map.class).newInstance(classes, classpath);
            transformer.setDeobfuscator(this);
            transformer.transform();
        }

        System.out.println();
        System.out.println("Transforming complete. Writing to file");
        System.out.println();

        classes.values().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
            try {
                byte[] b = toByteArray(classNode);
                if (b != null) {
                    zipOut.putNextEntry(new ZipEntry(classNode.name + ".class"));
                    zipOut.write(b);
                    zipOut.closeEntry();
                }
            } catch (Throwable t) {
                System.out.println("Uncaught error");
                t.printStackTrace(System.out);
            }
        });
        zipOut.close();
        zipIn.close();
    }

    public ClassNode assureLoaded(String ref) {
        WrappedClassNode clazz = classpath.get(ref);
        if (clazz == null) {
            throw new NoClassInPathException(ref);
        }
        return clazz.classNode;
    }

    public void loadHierachy() {
        Set<String> processed = new HashSet<>();
        LinkedList<ClassNode> toLoad = new LinkedList<>();
        toLoad.addAll(this.classes.values().stream().map(wrappedClassNode -> wrappedClassNode.classNode).collect(Collectors.toList()));
        while (!toLoad.isEmpty()) {
            for (ClassNode toProcess : loadHierachy(toLoad.poll())) {
                if (processed.add(toProcess.name)) {
                    toLoad.add(toProcess);
                }
            }
        }
    }

    public void resetHierachy() {
        this.hierachy.clear();
    }

    public List<ClassNode> loadHierachy(ClassNode specificNode) {
        if (specificNode.name.equals("java/lang/Object")) {
            return Collections.emptyList();
        }
        if ((specificNode.access & Opcodes.ACC_INTERFACE) != 0) {
            getClassTree(specificNode.name).parentClasses.add("java/lang/Object");
            return Collections.emptyList();
        }
        List<ClassNode> toProcess = new ArrayList<>();

        ClassTree thisTree = getClassTree(specificNode.name);
        ClassNode superClass = assureLoaded(specificNode.superName);
        if (superClass == null) {
            throw new IllegalArgumentException("Could not load " + specificNode.name);
        }
        ClassTree superTree = getClassTree(superClass.name);
        superTree.subClasses.add(specificNode.name);
        thisTree.parentClasses.add(superClass.name);
        toProcess.add(superClass);

        for (String interfaceReference : specificNode.interfaces) {
            ClassNode interfaceNode = assureLoaded(interfaceReference);
            if (interfaceNode == null) {
                throw new IllegalArgumentException("Could not load " + interfaceReference);
            }
            ClassTree interfaceTree = getClassTree(interfaceReference);
            interfaceTree.subClasses.add(specificNode.name);
            thisTree.parentClasses.add(interfaceReference);
            toProcess.add(interfaceNode);
        }
        return toProcess;
    }

    public boolean isSubclass(String possibleParent, String possibleChild) {
        if (possibleParent.equals(possibleChild)) {
            return true;
        }
        ClassTree parentTree = hierachy.get(possibleParent);
        if (parentTree != null && hierachy.get(possibleChild) != null) {
            List<String> layer = new ArrayList<>();
            layer.add(possibleParent);
            layer.addAll(parentTree.subClasses);
            while (!layer.isEmpty()) {
                if (layer.contains(possibleChild)) {
                    return true;
                }
                List<String> clone = new ArrayList<>();
                clone.addAll(layer);
                layer.clear();
                for (String r : clone) {
                    ClassTree tree = hierachy.get(r);
                    if (tree != null)
                        layer.addAll(tree.subClasses);
                }
            }
        }
        return false;
    }

    public ClassTree getClassTree(String classNode) {
        ClassTree tree = hierachy.get(classNode);
        if (tree == null) {
            tree = new ClassTree();
            tree.thisClass = classNode;
            hierachy.put(classNode, tree);
        }
        return tree;
    }

    public byte[] toByteArray(ClassNode node) {
        if (node.innerClasses != null) {
            node.innerClasses.stream().filter(in -> in.innerName != null).forEach(in -> {
                if (in.innerName.indexOf('/') != -1) {
                    in.innerName = in.innerName.substring(in.innerName.lastIndexOf('/') + 1); //Stringer
                }
            });
        }
        ClassWriter writer = new CustomClassWriter(ClassWriter.COMPUTE_FRAMES);
        try {
            try {
                node.accept(writer);
            } catch (RuntimeException e) {
                if (e instanceof NoClassInPathException) {
                    NoClassInPathException ex = (NoClassInPathException) e;
                    System.out.println("Error: " + ex.className + " could not be found while writing " + node.name + ". Using COMPUTE_MAXS");
                    writer = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                } else if (e.getMessage() != null) {
                    if (e.getMessage().contains("JSR/RET")) {
                        System.out.println("ClassNode contained JSR/RET so COMPUTE_MAXS instead");
                        writer = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                        node.accept(writer);
                    } else {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
            byte[] classBytes = writer.toByteArray();

            ClassReader cr = new ClassReader(classBytes);
            try {
                cr.accept(new CheckClassAdapter(new ClassWriter(0)), 0);
            } catch (Throwable t) {
                System.out.println("Error: " + node.name + " failed verification");
                t.printStackTrace(System.out);
            }
            return classBytes;
        } catch (Throwable t) {
            System.out.println("Error while writing " + node.name);
            t.printStackTrace(System.out);
        }
        return null;
    }

    public class CustomClassWriter extends ClassWriter {
        public CustomClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            String a = getCommonSuperClass1(type1, type2);
            return a;
        }

        private String getCommonSuperClass1(String type1, String type2) {
            if (type1.equals("java/lang/Object") || type2.equals("java/lang/Object")) {
                return "java/lang/Object";
            }
            String a = getCommonSuperClass0(type1, type2);
            String b = getCommonSuperClass0(type2, type1);
            if (!a.equals("java/lang/Object")) {
                return a;
            }
            if (!b.equals("java/lang/Object")) {
                return b;
            }
            ClassNode first = assureLoaded(type1);
            ClassNode second = assureLoaded(type2);
            return getCommonSuperClass(first.superName, second.superName);
        }

        private String getCommonSuperClass0(String type1, String type2) {
            ClassNode first = assureLoaded(type1);
            ClassNode second = assureLoaded(type2);
            if (isAssignableFrom(type1, type2)) {
                return type1;
            } else if (isAssignableFrom(type2, type1)) {
                return type2;
            } else if (Modifier.isInterface(first.access) || Modifier.isInterface(second.access)) {
                return "java/lang/Object";
            } else {
                do {
                    type1 = first.superName;
                    first = assureLoaded(type1);
                } while (!isAssignableFrom(type1, type2));
                return type1;
            }
        }

        private boolean isAssignableFrom(String type1, String type2) {
            if (type1.equals("java/lang/Object"))
                return true;
            if (type1.equals(type2)) {
                return true;
            }
            assureLoaded(type1);
            assureLoaded(type2);
            ClassTree firstTree = getClassTree(type1);
            Set<String> allChilds1 = new HashSet<>();
            LinkedList<String> toProcess = new LinkedList<>();
            toProcess.addAll(firstTree.subClasses);
            while (!toProcess.isEmpty()) {
                String s = toProcess.poll();
                if (allChilds1.add(s)) {
                    assureLoaded(s);
                    ClassTree tempTree = getClassTree(s);
                    toProcess.addAll(tempTree.subClasses);
                }
            }
            if (allChilds1.contains(type2)) {
                return true;
            }
            return false;
        }
    }

    class NoClassInPathException extends RuntimeException {
        String className;

        public NoClassInPathException(String className) {
            super(className);
            this.className = className;
        }
    }
}
