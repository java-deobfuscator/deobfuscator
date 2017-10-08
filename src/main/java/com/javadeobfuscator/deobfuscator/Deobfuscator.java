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

import com.javadeobfuscator.deobfuscator.config.Configuration;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.exceptions.PreventableStackOverflowError;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Deobfuscator {
    private Map<String, WrappedClassNode> classpath = new HashMap<>();
    private Map<String, WrappedClassNode> classes = new HashMap<>();
    private Map<String, ClassTree> hierachy = new HashMap<>();
    private Set<ClassNode> libraryClassnodes = new HashSet<>();

    // Entries from the input jar that will be passed through to the output
    private Map<String, byte[]> inputPassthrough = new HashMap<>();

    private final Configuration configuration;
    private final Logger logger = LoggerFactory.getLogger(Deobfuscator.class);

    public Deobfuscator(Configuration configuration) {
        this.configuration = configuration;
    }

    private static final boolean DEBUG = false;
    /**
     * Some obfuscators like to have junk classes. If ALL your libraries are added,
     * enable this to dump troublesome classes. Note that this will not get rid of all junk classes.
     */
    private static final boolean DELETE_USELESS_CLASSES = false;

    private Map<String, WrappedClassNode> loadClasspathFile(File file) throws IOException {
        Map<String, WrappedClassNode> map = new HashMap<>();

        ZipFile zipIn = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipIn.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ent = entries.nextElement();
            if (ent.getName().endsWith(".class")) {
                ClassReader reader = new ClassReader(zipIn.getInputStream(ent));
                ClassNode node = new ClassNode();
                reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                WrappedClassNode wrappedClassNode = new WrappedClassNode(node, reader.getItemCount());
                map.put(node.name, wrappedClassNode);
            }
        }
        zipIn.close();

        return map;
    }

    private void loadClasspath() throws IOException {
        if (configuration.getPath() != null) {
            for (File file : configuration.getPath()) {
                if (file.isFile()) {
                    classpath.putAll(loadClasspathFile(file));
                } else {
                    File[] files = file.listFiles(child -> child.getName().endsWith(".jar"));
                    if (files != null) {
                        for (File child : files) {
                            classpath.putAll(loadClasspathFile(child));
                        }
                    }
                }
            }
            libraryClassnodes.addAll(classpath.values().stream().map(WrappedClassNode::getClassNode).collect(Collectors.toList()));
        }
    }

    private boolean isClassIgnored(ClassNode classNode) {
        if (configuration.getIgnoredClasses() == null) {
            return false;
        }

        for (String ignored : configuration.getIgnoredClasses()) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(ignored);
            } catch (PatternSyntaxException e) {
                logger.error("Error while compiling pattern for ignore statement {}", ignored, e);
                continue;
            }
            Matcher matcher = pattern.matcher(classNode.name);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    private void loadInput() throws IOException {
        try (ZipFile zipIn = new ZipFile(configuration.getInput())) {
            Enumeration<? extends ZipEntry> e = zipIn.entries();
            while (e.hasMoreElements()) {
                ZipEntry next = e.nextElement();
                if (next.isDirectory()) {
                    continue;
                }

                boolean passthrough = true;

                byte[] data = IOUtils.toByteArray(zipIn.getInputStream(next));

                if (next.getName().endsWith(".class")) {
                    try {
                        ClassReader reader = new ClassReader(data);
                        ClassNode node = new ClassNode();
                        reader.accept(node, ClassReader.SKIP_FRAMES);

                        if (!isClassIgnored(node)) {
                            for (int i = 0; i < node.methods.size(); i++) {
                                MethodNode methodNode = node.methods.get(i);
                                JSRInlinerAdapter adapter = new JSRInlinerAdapter(methodNode, methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
                                methodNode.accept(adapter);
                                node.methods.set(i, adapter);
                            }

                            WrappedClassNode wr = new WrappedClassNode(node, reader.getItemCount());
                            classes.put(node.name, wr);
                            passthrough = false;
                        } else {
                            classpath.put(node.name, new WrappedClassNode(node, reader.getItemCount()));
                        }
                    } catch (IllegalArgumentException x) {
                        logger.error("Could not parse {} (is it a class file?)", next.getName(), x);
                    }
                }

                if (passthrough) {
                    inputPassthrough.put(next.getName(), data);
                }
            }

            classpath.putAll(classes);
        }
    }

    /**
     * @deprecated do we need this?
     */
    @Deprecated
    private void computeCallers() {
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
                                callers.computeIfAbsent(targetMethod, k -> new ArrayList<>()).add(new SimpleEntry<>(wrappedClassNode, methodNode));
                            }
                        }
                    }
                }
            });
        });
    }

    public boolean isLibrary(ClassNode classNode) {
        return libraryClassnodes.contains(classNode);
    }

    public int start() {
    	try {
	        logger.info("Loading classpath");
	        loadClasspath();
	
	        logger.info("Loading input");
	        loadInput();
	
	        logger.info("Computing callers");
	        computeCallers();
	
	        logger.info("Transforming");
	        if (configuration.getTransformers() != null) {
	            for (TransformerConfig config : configuration.getTransformers()) {
	                logger.info("Running {}", config.getImplementation().getCanonicalName());
	                runFromConfig(config);
	            }
	        }
	
	        logger.info("Writing");
	        if (DEBUG) {
	            classes.values().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(Utils::printClass);
	        }
	
	        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(configuration.getOutput()));
	        inputPassthrough.forEach((name, val) -> {
	            ZipEntry entry = new ZipEntry(name);
	            try {
	                zipOut.putNextEntry(entry);
	                zipOut.write(val);
	                zipOut.closeEntry();
	            } catch (IOException e) {
	                logger.error("Error writing entry {}", name, e);
	            }
	        });
	
	        classes.values().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach(classNode -> {
	            try {
	                byte[] b = toByteArray(classNode);
	                if (b != null) {
	                    zipOut.putNextEntry(new ZipEntry(classNode.name + ".class"));
	                    zipOut.write(b);
	                    zipOut.closeEntry();
	                }
	            } catch (IOException e) {
	                logger.error("Error writing entry {}", classNode.name, e);
	            }
	        });
	
	        zipOut.close();
	        return 0;
    	} catch (NoClassInPathException ex) {
            for (int i = 0; i < 5; i++)
                System.out.println();
            System.out.println("** DO NOT OPEN AN ISSUE ON GITHUB **");
            System.out.println("Could not locate a class file.");
            System.out.println("Have you added the necessary files to the -path argument?");
            System.out.println("The error was:");
            ex.printStackTrace(System.out);
            return -2;
        } catch (PreventableStackOverflowError ex) {
            for (int i = 0; i < 5; i++)
                System.out.println();
            System.out.println("** DO NOT OPEN AN ISSUE ON GITHUB **");
            System.out.println("A StackOverflowError occurred during deobfuscation, but it is preventable");
            System.out.println("Try increasing your stack size using the -Xss flag");
            System.out.println("The error was:");
            ex.printStackTrace(System.out);
            return -3;
        } catch (Throwable t) {
            for (int i = 0; i < 5; i++)
                System.out.println();
            System.out.println("Deobfuscation failed. Please open a ticket on GitHub and provide the following error:");
            t.printStackTrace(System.out);
            return -1;
        }
    }

    public boolean runFromConfig(TransformerConfig config) throws Throwable {
        Transformer transformer = config.getImplementation().newInstance();
        transformer.init(this, config, classes, classpath);
        return transformer.transform();
    }

    public ClassNode assureLoaded(String ref) {
        WrappedClassNode clazz = classpath.get(ref);
        if (clazz == null) {
            throw new NoClassInPathException(ref);
        }
        return clazz.classNode;
    }

    public ClassNode assureLoadedElseRemove(String referencer, String ref) {
        WrappedClassNode clazz = classpath.get(ref);
        if (clazz == null) {
            classes.remove(referencer);
            classpath.remove(referencer);
            return null;
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

    public void loadHierachyAll(ClassNode classNode) {
        Set<String> processed = new HashSet<>();
        LinkedList<ClassNode> toLoad = new LinkedList<>();
        toLoad.add(classNode);
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

    private ClassTree getOrCreateClassTree(String name) {
        return this.hierachy.computeIfAbsent(name, ClassTree::new);
    }

    public List<ClassNode> loadHierachy(ClassNode specificNode) {
        if (specificNode.name.equals("java/lang/Object")) {
            return Collections.emptyList();
        }
        if ((specificNode.access & Opcodes.ACC_INTERFACE) != 0) {
            getOrCreateClassTree(specificNode.name).parentClasses.add("java/lang/Object");
            return Collections.emptyList();
        }
        List<ClassNode> toProcess = new ArrayList<>();

        ClassTree thisTree = getOrCreateClassTree(specificNode.name);
        ClassNode superClass;
        if (DELETE_USELESS_CLASSES) {
            superClass = assureLoadedElseRemove(specificNode.name, specificNode.superName);
            if (superClass == null)
                //It got removed
                return toProcess;
        } else
            superClass = assureLoaded(specificNode.superName);
        if (superClass == null) {
            throw new IllegalArgumentException("Could not load " + specificNode.name);
        }
        ClassTree superTree = getOrCreateClassTree(superClass.name);
        superTree.subClasses.add(specificNode.name);
        thisTree.parentClasses.add(superClass.name);
        toProcess.add(superClass);

        for (String interfaceReference : specificNode.interfaces) {
            ClassNode interfaceNode;
            if (DELETE_USELESS_CLASSES) {
                interfaceNode = assureLoadedElseRemove(specificNode.name, interfaceReference);
                if (interfaceNode == null)
                    //It got removed
                    return toProcess;
            } else
                interfaceNode = assureLoaded(interfaceReference);
            if (interfaceNode == null) {
                throw new IllegalArgumentException("Could not load " + interfaceReference);
            }
            ClassTree interfaceTree = getOrCreateClassTree(interfaceReference);
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
        loadHierachyAll(assureLoaded(possibleParent));
        loadHierachyAll(assureLoaded(possibleChild));
        ClassTree parentTree = hierachy.get(possibleParent);
        if (parentTree != null && hierachy.get(possibleChild) != null) {
            List<String> layer = new ArrayList<>();
            layer.add(possibleParent);
            layer.addAll(parentTree.subClasses);
            while (!layer.isEmpty()) {
                if (layer.contains(possibleChild)) {
                    return true;
                }
                List<String> clone = new ArrayList<>(layer);
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
            loadHierachyAll(assureLoaded(classNode));
            return getClassTree(classNode);
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
                    System.out.println("Error: " + ex.getClassName() + " could not be found while writing " + node.name + ". Using COMPUTE_MAXS");
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

            if (configuration.isVerify()) {
                ClassReader cr = new ClassReader(classBytes);
                try {
                    cr.accept(new CheckClassAdapter(new ClassWriter(0)), 0);
                } catch (Throwable t) {
                    System.out.println("Error: " + node.name + " failed verification");
                    t.printStackTrace(System.out);
                }
            }
            return classBytes;
        } catch (Throwable t) {
            System.out.println("Error while writing " + node.name);
            t.printStackTrace(System.out);
        }
        return null;
    }

    public Configuration getConfig() {
        return this.configuration;
    }

    public class CustomClassWriter extends ClassWriter {
        public CustomClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return getCommonSuperClass1(type1, type2);
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
            return allChilds1.contains(type2);
        }
    }

}
