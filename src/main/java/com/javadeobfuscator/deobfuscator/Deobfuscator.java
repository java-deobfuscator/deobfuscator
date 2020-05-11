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

import com.javadeobfuscator.deobfuscator.asm.*;
import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.apache.commons.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
import org.slf4j.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.AbstractMap.*;
import java.util.*;
import java.util.Map.*;
import java.util.regex.*;
import java.util.zip.*;

public class Deobfuscator {
    private Map<String, ClassNode> classpath = new HashMap<>();
    private Map<String, ClassNode> libraries = new HashMap<>();
    private Map<String, ClassNode> classes = new HashMap<>();
    private Map<String, ClassTree> hierachy = new HashMap<>();
    private Set<ClassNode> libraryClassnodes = new HashSet<>();

    public Map<String, byte[]> getInputPassthrough() {
        return inputPassthrough;
    }

    // Entries from the input jar that will be passed through to the output
    private Map<String, byte[]> inputPassthrough = new HashMap<>();

    // Constant pool data since ClassNodes don't support custom data
    private Map<ClassNode, ConstantPool> constantPools = new HashMap<>();
    private Map<ClassNode, ClassReader> readers = new HashMap<>();

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
    
    public Map<String, byte[]> invaildClasses = new HashMap<>();
    
    /**
     * Must enable for paramorphism obfuscated files.
     */
    private static final boolean PARAMORPHISM = false;
    
    public List<String> junkFiles = new ArrayList<>();
    
    /**
     * Must enable for paramorphism v2 obfuscated files.
     */
    private static final boolean PARAMORPHISM_V2 = false;

    public ConstantPool getConstantPool(ClassNode classNode) {
        return this.constantPools.get(classNode);
    }

    public void setConstantPool(ClassNode owner, ConstantPool pool) {
        this.constantPools.put(owner, pool);
    }

    public Map<ClassNode, ConstantPool> getConstantPools() {
        return this.constantPools;
    }

    private Map<String, ClassNode> loadClasspathFile(File file, boolean skipCode) throws IOException {
        Map<String, ClassNode> map = new HashMap<>();

        ZipFile zipIn = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipIn.entries();
        while (entries.hasMoreElements()) {
            ZipEntry ent = entries.nextElement();
            if (ent.getName().endsWith(".class")) {
                ClassReader reader = new ClassReader(zipIn.getInputStream(ent));
                ClassNode node = new ClassNode();
                reader.accept(node, (skipCode ? 0 : 0) | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                map.put(node.name, node);

                setConstantPool(node, new ConstantPool(reader));
            }
        }
        zipIn.close();

        return map;
    }

    public Map<String, ClassNode> getLibraries() {
        return libraries;
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
    	if(PARAMORPHISM_V2)
    	{
    		//Load folder "classes"
    		try(ZipFile zipIn = new ZipFile(configuration.getInput())) {
	            Enumeration<? extends ZipEntry> e = zipIn.entries();
	            while(e.hasMoreElements()) {
	                ZipEntry next = e.nextElement();
	                if(next.isDirectory() && next.getName().endsWith(".class/")) {
	                	byte[] data = IOUtils.toByteArray(zipIn.getInputStream(next));
	                	loadInput(next.getName().substring(0, next.getName().length() - 1), data);
	                }else if(!next.isDirectory() && next.getName().contains(".class/"))
	                	junkFiles.add(next.getName());
	            }
    		}
    	}else if(PARAMORPHISM)
    	{
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
	                    if(entries.containsKey(node.name))
	                    {
	                    	invaildClasses.put(next.getName(), data);
	                    	invaildClasses.put(classNameToName.get(node.name), entries.get(node.name));
	                    }else
	                    {
	                    	classNameToName.put(node.name, next.getName());
	                    	entries.put(node.name, data);
	                    }
	                }catch(Exception ex)
	                {
	                	continue;
	                }
	            }
	        }
	    	//Filter out real classes
	    	List<String> real = new ArrayList<>();
	    	for(Entry<String, byte[]> entry : invaildClasses.entrySet())
	    	{
                ClassReader reader = new ClassReader(entry.getValue());
                ClassNode node = new ClassNode();
                reader.accept(node, ClassReader.SKIP_FRAMES);
                if((node.name + ".class").equals(entry.getKey()))
                	real.add(entry.getKey());
	    	}
	    	real.forEach(s -> invaildClasses.remove(s));
    	}
        try (ZipFile zipIn = new ZipFile(configuration.getInput())) {
            Enumeration<? extends ZipEntry> e = zipIn.entries();
            while (e.hasMoreElements()) {
                ZipEntry next = e.nextElement();
                if (next.isDirectory()) {
                    continue;
                }

                byte[] data = IOUtils.toByteArray(zipIn.getInputStream(next));
                loadInput(next.getName(), data);
            }
        }
    }

    public void loadInput(String name, byte[] data) {
        boolean passthrough = true;

        if (name.endsWith(".class")) {
            try {
                ClassReader reader = new ClassReader(data);
                ClassNode node = new ClassNode();
                reader.accept(node, ClassReader.SKIP_FRAMES);

                readers.put(node, reader);
                setConstantPool(node, new ConstantPool(reader));

                if (!isClassIgnored(node)) {
                    for (int i = 0; i < node.methods.size(); i++) {
                        MethodNode methodNode = node.methods.get(i);
                        JSRInlinerAdapter adapter = new JSRInlinerAdapter(methodNode, methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(new String[0]));
                        methodNode.accept(adapter);
                        node.methods.set(i, adapter);
                    }

                    if(!invaildClasses.containsKey(name))
                    	classes.put(node.name, node);
                    classpath.put(node.name, node);
                    passthrough = false;
                } else {
                    classpath.put(node.name, node);
                }
            } catch (IllegalArgumentException x) {
            	if(PARAMORPHISM_V2)
            		invaildClasses.put(name, data);
            	else
            		logger.error("Could not parse {} (is it a class file?)", name, x);
            } catch (ArrayIndexOutOfBoundsException x) {
            	if(PARAMORPHISM_V2)
            		invaildClasses.put(name, data);
            	else
            		logger.error("Could not parse {} (is it a class file?)", name, x);
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
                            MethodNode targetMethod = targetNode.methods.stream().filter(m -> m.name.equals(mn.name) && m.desc.equals(mn.desc)).findFirst().orElse(null);
                            if (targetMethod != null) {
                                callers.computeIfAbsent(targetMethod, k -> new ArrayList<>()).add(new SimpleEntry<>(classNode, methodNode));
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

    public void start() throws Throwable {
        logger.info("Loading classpath");
        loadClasspath();

        logger.info("Loading input");
        loadInput();

        if (getConfig().isDetect()) {
            logger.info("Detecting known obfuscators");

            for (Rule rule : Rules.RULES) {
                String message = rule.test(this);
                if (message == null) {
                    continue;
                }

                logger.info("");
                logger.info("{}: {}", rule.getClass().getSimpleName(), rule.getDescription());
                logger.info("\t{}", message);
                logger.info("Recommend transformers:");

                Collection<Class<? extends Transformer<?>>> recommended = rule.getRecommendTransformers();
                if (recommended == null) {
                    logger.info("\tNone");
                } else {
                    for (Class<? extends Transformer<?>> transformer : recommended) {
                        logger.info("\t{}", transformer.getName());
                    }
                }
            }

            return;
        }

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
        Transformer transformer = config.getImplementation().newInstance();
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

    public void loadHierachy() {
        Set<String> processed = new HashSet<>();
        LinkedList<ClassNode> toLoad = new LinkedList<>();
        toLoad.addAll(this.classes.values());
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

    public Configuration getConfig() {
        return this.configuration;
    }

    public Map<String, ClassNode> getClasses() {
        return this.classes;
    }

    public Map<ClassNode, ClassReader> getReaders() {
        return readers;
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
