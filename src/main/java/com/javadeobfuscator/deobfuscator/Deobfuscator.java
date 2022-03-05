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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.javadeobfuscator.deobfuscator.asm.ConstantPool;
import com.javadeobfuscator.deobfuscator.config.Configuration;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.NoClassInPathException;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.rules.Rules;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import me.coley.cafedude.classfile.ClassFile;
import me.coley.cafedude.InvalidClassException;
import me.coley.cafedude.io.ClassFileReader;
import me.coley.cafedude.io.ClassFileWriter;
import me.coley.cafedude.transform.IllegalStrippingTransformer;
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

public class Deobfuscator {

    private static final boolean DEBUG = false;

    private final Logger logger = LoggerFactory.getLogger(Deobfuscator.class);

    private final Configuration configuration;

    private final Set<String> missingRefs = new HashSet<>();
    private final Map<String, ClassNode> classpath = new HashMap<>();
    private final Map<String, ClassNode> libraries = new HashMap<>();
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Map<String, ClassTree> hierachy = new HashMap<>();
    private final Set<ClassNode> libraryClassnodes = new HashSet<>();
    /**
     * Entries from the input jar that will be passed through to the output
     */
    private final Map<String, byte[]> inputPassthrough = new HashMap<>();
    /**
     * Constant pool data since ClassNodes don't support custom data
     */
    private final Map<ClassNode, ConstantPool> constantPools = new HashMap<>();
    private final Map<ClassNode, ClassReader> readers = new HashMap<>();
    public Map<String, byte[]> invalidClasses = new HashMap<>();
    public List<String> junkFiles = new ArrayList<>();

    public Deobfuscator(Configuration configuration) {
        this.configuration = configuration;
    }

    public ConstantPool getConstantPool(ClassNode classNode) {
        return this.constantPools.get(classNode);
    }

    public void setConstantPool(ClassNode owner, ConstantPool pool) {
        this.constantPools.put(owner, pool);
    }

    public Map<ClassNode, ConstantPool> getConstantPools() {
        return this.constantPools;
    }

    public Map<String, byte[]> getInputPassthrough() {
        return inputPassthrough;
    }

    public Configuration getConfig() {
        return this.configuration;
    }

    public Map<String, ClassNode> getClasses() {
        return this.classes;
    }

    public Map<ClassNode, ClassReader> getReaders() {
        return readers;
    }

    public Map<String, ClassNode> getLibraries() {
        return libraries;
    }

    private Map<String, ClassNode> loadClasspathFile(File file, boolean skipCode) throws IOException {
        Map<String, ClassNode> map = new HashMap<>();

        ZipFile zipIn = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipIn.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ent = entries.nextElement();
            if (ent.getName().endsWith(".class")) {
                try {
                    ClassReader reader = new ClassReader(zipIn.getInputStream(ent));
                    ClassNode node = new ClassNode();
                    reader.accept(node, (skipCode ? 0 : 0) | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    map.put(node.name, node);

                    setConstantPool(node, new ConstantPool(reader));
                } catch (Exception ex) {
                    logger.warn("Could not load class " + ent.getName() + " from library " + file, ex);
                }
            }
        }
        zipIn.close();

        return map;
    }

    private void loadClasspath() throws IOException {
        if (configuration.getPath() != null) {
            for (File file : configuration.getPath()) {
                if (file.isFile()) {
                    classpath.putAll(loadClasspathFile(file, true));
                } else {
                    File[] files = file.listFiles(child -> child.getName().endsWith(".jar"));
                    if (files != null) {
                        for (File child : files) {
                            classpath.putAll(loadClasspathFile(child, true));
                        }
                    }
                }
            }
        }
        if (configuration.getLibraries() != null) {
            for (File file : configuration.getLibraries()) {
                if (file.isFile()) {
                    libraries.putAll(loadClasspathFile(file, false));
                } else {
                    File[] files = file.listFiles(child -> child.getName().endsWith(".jar"));
                    if (files != null) {
                        for (File child : files) {
                            libraries.putAll(loadClasspathFile(child, false));
                        }
                    }
                }
            }
        }
        classpath.putAll(libraries);
        libraryClassnodes.addAll(classpath.values());
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
        if (this.configuration.isParamorphismV2()) {
            //Load folder "classes"
            try (ZipFile zipIn = new ZipFile(configuration.getInput())) {
                Enumeration<? extends ZipEntry> e = zipIn.entries();
                while (e.hasMoreElements()) {
                    ZipEntry next = e.nextElement();
                    if (next.isDirectory() && next.getName().endsWith(".class/")) {
                        byte[] data = IOUtils.toByteArray(zipIn.getInputStream(next));
                        loadInput(next.getName().substring(0, next.getName().length() - 1), data);
                    } else if (!next.isDirectory() && next.getName().contains(".class/")) {
                        junkFiles.add(next.getName());
                    }
                }
            }
        } else if (this.configuration.isParamorphism()) {
            Map<String, String> classNameToName = new HashMap<>();
            Map<String, byte[]> entries = new HashMap<>();
            //Check all duplicate files
            try (ZipFile zipIn = new ZipFile(configuration.getInput())) {
                Enumeration<? extends ZipEntry> e = zipIn.entries();
                while (e.hasMoreElements()) {
                    ZipEntry next = e.nextElement();
                    if (next.isDirectory() || !next.getName().endsWith(".class")) {
                        continue;
                    }

                    try {
                        byte[] data = IOUtils.toByteArray(zipIn.getInputStream(next));
                        ClassReader reader = new ClassReader(data);
                        ClassNode node = new ClassNode();
                        reader.accept(node, ClassReader.SKIP_FRAMES);
                        if (entries.containsKey(node.name)) {
                            invalidClasses.put(next.getName(), data);
                            invalidClasses.put(classNameToName.get(node.name), entries.get(node.name));
                        } else {
                            classNameToName.put(node.name, next.getName());
                            entries.put(node.name, data);
                        }
                    } catch (Exception ex) {
                        continue;
                    }
                }
            }
            //Filter out real classes
            List<String> real = new ArrayList<>();
            for (Entry<String, byte[]> entry : invalidClasses.entrySet()) {
                ClassReader reader = new ClassReader(entry.getValue());
                ClassNode node = new ClassNode();
                reader.accept(node, ClassReader.SKIP_FRAMES);
                if ((node.name + ".class").equals(entry.getKey())) {
                    real.add(entry.getKey());
                }
            }
            real.forEach(s -> invalidClasses.remove(s));
        }
        try (ZipFile zipIn = new ZipFile(configuration.getInput())) {
            Enumeration<? extends ZipEntry> e = zipIn.entries();
            while (e.hasMoreElements()) {
                ZipEntry next = e.nextElement();

                // Some obfuscators can screw with the ZIP format and store data
                // in directory entries, and yes, the JVM is cool with that.
                if (next.isDirectory() || next.getName().endsWith(".class/")) {
                    continue;
                }

                byte[] data = IOUtils.toByteArray(zipIn.getInputStream(next));
                loadInput(next.getName(), data);
            }
        }
    }

    public void loadInput(String name, byte[] data) {
        boolean passthrough = true;

        if (name.endsWith(".class") || name.endsWith(".class/")) {
            // These 'classes' are likely red-herrings using the '.class/' trick.
            // So we will toss them since they're not real classes.
            if (data.length <= 30) {
                return;
            }

            try {
                // Patching optional, disabling useful for ensuring that output problems related to patch process.
                if (configuration.isPatchAsm()) {
                    ClassFileReader cfr = new ClassFileReader();
                    cfr.setDropForwardVersioned(true);
                    cfr.setDropEofAttributes(true);
                    ClassFile cf = cfr.read(data);
                    new IllegalStrippingTransformer(cf).transform();
                    ClassFileWriter cfw = new ClassFileWriter();
                    data = cfw.write(cf);
                }
                // Should be compliant now unless a new crash is discovered.
                // Check for updates or open an issue on the CAFED00D project if this occurs
                ClassReader reader = new ClassReader(data);
                ClassNode node = new ClassNode();
                reader.accept(node, ClassReader.SKIP_FRAMES);
                readers.put(node, reader);
                setConstantPool(node, new ConstantPool(reader));

                if (!isClassIgnored(node)) {
                    for (int i = 0; i < node.methods.size(); i++) {
                        MethodNode methodNode = node.methods.get(i);
                        JSRInlinerAdapter adapter = new JSRInlinerAdapter(
                                methodNode,
                                methodNode.access,
                                methodNode.name,
                                methodNode.desc,
                                methodNode.signature,
                                methodNode.exceptions.toArray(new String[0]));
                        methodNode.accept(adapter);
                        node.methods.set(i, adapter);
                    }

                    if (!invalidClasses.containsKey(name)) {
                        classes.put(node.name, node);
                    }
                    classpath.put(node.name, node);
                    passthrough = false;
                } else {
                    classpath.put(node.name, node);
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException | InvalidClassException x) {
                if (configuration.isParamorphismV2()) {
                    invalidClasses.put(name, data);
                } else  if (!configuration.isPatchAsm()) {
                    logger.error("Could not parse {} (Try adding \"patchAsm: true\" to the config?)", name, x);
                } else {
                    logger.error("Could not parse {} (Is it a class file?)", name, x);
                }
            }
        }

        if (passthrough && !junkFiles.contains(name)) {
            inputPassthrough.put(name, data);
        }
    }

    /**
     * @deprecated do we need this?
     */
    @Deprecated
    private void computeCallers() {
        Map<MethodNode, List<Entry<ClassNode, MethodNode>>> callers = new HashMap<>();
        classes.values().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                for (int i = 0; i < methodNode.instructions.size(); i++) {
                    AbstractInsnNode node = methodNode.instructions.get(i);
                    if (node instanceof MethodInsnNode) {
                        MethodInsnNode mn = (MethodInsnNode) node;
                        ClassNode targetNode = classes.get(mn.owner);
                        if (targetNode != null) {
                            targetNode.methods.stream()
                                    .filter(m -> m.name.equals(mn.name) && m.desc.equals(mn.desc))
                                    .findFirst()
                                    .ifPresent(targetMethod -> {
                                        callers.computeIfAbsent(targetMethod, k -> new ArrayList<>())
                                                .add(new SimpleEntry<>(classNode, methodNode));
                                    });
                        }
                    }
                }
            });
        });
    }

    public boolean isLibrary(ClassNode classNode) {
        return libraryClassnodes.contains(classNode);
    }

    public void start() throws Throwable {
        logger.info("Loading classpath");
        loadClasspath();

        logger.info("Loading input");
        loadInput();

        if (getConfig().isDetect()) {
            logger.info("Detecting known obfuscators");

            for (Rule rule : Rules.RULES) {
            	try
            	{
	                String message = rule.test(this);
	                if (message == null) {
	                    continue;
	                }

	                logger.info("");
	                logger.info("{}: {}", rule.getClass().getSimpleName(), rule.getDescription());
	                logger.info("\t{}", message);
	                logger.info("Recommend transformers:");
	                logger.info("(Choose one transformer. If there are multiple, it's recommended to try the transformer listed first)");

	                Collection<Class<? extends Transformer<?>>> recommended = rule.getRecommendTransformers();
	                if (recommended == null) {
	                    logger.info("\tNone");
	                } else {
	                    for (Class<? extends Transformer<?>> transformer : recommended) {
	                        logger.info("\t{}", transformer.getName());
	                    }
	                }
            	}catch(Exception e)
            	{
            		e.printStackTrace();
            	}
            }

            logger.info("All detectors have been run. If you do not see anything listed, check if your file only contains name obfuscation.");
            logger.info("Do note that some obfuscators do not have detectors.");
            return;
        }

        logger.info("Computing callers");
        computeCallers();

        if (configuration.isDeleteUselessClasses()) {
        	logger.warn("Warning: You have enabled the option \"delete useless classes\".");
        	logger.warn("This option will delete any classes whose superclasses or interfaces cannot be resolved for certain transformers.");
        	logger.warn("This feature is only to be used when your file contains trash classes that prevent transformers from working.");
        	logger.warn("All libraries must be added for this to work properly.");
        }

        if (configuration.isSmartRedo()) {
        	logger.warn("You have enabled \"smart redo\". For some transformers, this may result in an infinite loop.");
        }

        logger.info("Transforming");
        if (configuration.getTransformers() != null) {
            for (TransformerConfig config : configuration.getTransformers()) {
                logger.info("Running {}", config.getImplementation().getCanonicalName());
                runFromConfig(config);
            }
        }

        logger.info("Writing");
        if (DEBUG) {
            classes.values().forEach(Utils::printClass);
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

        classes.values().forEach(classNode -> {
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
    }

    public boolean runFromConfig(TransformerConfig config) throws Throwable {
        Transformer<?> transformer = config.getImplementation().newInstance();
        transformer.init(this, config, classes, classpath, readers);
        boolean madeChangesAtLeastOnce = false;
        boolean madeChanges;
        do {
            madeChanges = transformer.transform();
            madeChangesAtLeastOnce = madeChangesAtLeastOnce || madeChanges;
        } while (madeChanges && getConfig().isSmartRedo());
        return madeChangesAtLeastOnce;
    }

    public ClassNode assureLoaded(String ref) {
        ClassNode clazz = classpath.get(ref);
        // Attempt to fetch from runtime, cases like 'java/awt/bla' can be loaded this way
        if (clazz == null) {
            clazz = pullFromRuntime(ref);
        }
        // Still missing, cannot recover
        if (clazz == null) {
            throw new NoClassInPathException(ref);
        }
        return clazz;
    }

    public ClassNode assureLoadedElseRemove(String referencer, String ref) {
        ClassNode clazz = classpath.get(ref);
        if (clazz == null) {
            classes.remove(referencer);
            classpath.remove(referencer);
            return null;
        }
        return clazz;
    }

    private ClassNode pullFromRuntime(String ref) {
        try {
            if (!missingRefs.contains(ref)) {
                // Realistically we do not need the method bodies at all, can skip.
                ClassNode node = new ClassNode(Opcodes.ASM9);
                new ClassReader(ref).accept(node, ClassReader.SKIP_CODE);
                classpath.put(ref, node);
                return node;
            }
        } catch (IOException ex) {
            // Ignored, plenty of cases where ref will not exist at runtime.
            // Cache missing value so that we don't try again later.
            missingRefs.add(ref);
        }
        return null;
    }

    public void loadHierachy() {
        Set<String> processed = new HashSet<>();
        LinkedList<ClassNode> toLoad = new LinkedList<>(this.classes.values());
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
        List<ClassNode> toProcess = new ArrayList<>();

        ClassTree thisTree = getOrCreateClassTree(specificNode.name);
        ClassNode superClass;
        if (this.configuration.isDeleteUselessClasses()) {
            superClass = assureLoadedElseRemove(specificNode.name, specificNode.superName);
            if (superClass == null) {
                //It got removed
                return toProcess;
            }
        } else {
            superClass = assureLoaded(specificNode.superName);
        }
        if (superClass == null) {
            throw new IllegalArgumentException("Could not load " + specificNode.name);
        }
        ClassTree superTree = getOrCreateClassTree(superClass.name);
        superTree.subClasses.add(specificNode.name);
        thisTree.parentClasses.add(superClass.name);
        toProcess.add(superClass);

        for (String interfaceReference : specificNode.interfaces) {
            ClassNode interfaceNode;
            if (this.configuration.isDeleteUselessClasses()) {
                interfaceNode = assureLoadedElseRemove(specificNode.name, interfaceReference);
                if (interfaceNode == null) {
                    //It got removed
                    return toProcess;
                }
            } else {
                interfaceNode = assureLoaded(interfaceReference);
            }
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
                    if (tree != null) {
                        layer.addAll(tree.subClasses);
                    }
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
            node.accept(writer);
        } catch (Throwable e) {
            if (e instanceof NoClassInPathException) {
                NoClassInPathException ex = (NoClassInPathException) e;
                System.out.println("Error: " + ex.getClassName() + " could not be found while writing " + node.name + ". Using COMPUTE_MAXS");
                writer = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
            } else if (e instanceof NegativeArraySizeException || e instanceof ArrayIndexOutOfBoundsException) {
                System.out.println("Error: failed to compute frames");
                writer = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
            } else if (e.getMessage() != null) {
                if (e.getMessage().contains("JSR/RET")) {
                    System.out.println("ClassNode contained JSR/RET so COMPUTE_MAXS instead");
                    writer = new CustomClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                } else {
                    System.out.println("Error while writing " + node.name);
                    e.printStackTrace(System.out);
                }
            } else {
                System.out.println("Error while writing " + node.name);
                e.printStackTrace(System.out);
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
            if (type1.equals("java/lang/Object")) {
                return true;
            }
            if (type1.equals(type2)) {
                return true;
            }
            assureLoaded(type1);
            assureLoaded(type2);
            ClassTree firstTree = getClassTree(type1);
            Set<String> allChilds1 = new HashSet<>();
            LinkedList<String> toProcess = new LinkedList<>(firstTree.subClasses);
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
