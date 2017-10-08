package com.javadeobfuscator.deobfuscator;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.ReflectiveProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertTrue;

public class TestRunner {
    private Map<String, ClassNode> jre = new HashMap<>();

    @Before
    public void setup() {
        try {
            File input = new File(System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar");

            ZipFile zipIn = new ZipFile(input);
            Enumeration<? extends ZipEntry> e = zipIn.entries();
            while (e.hasMoreElements()) {
                ZipEntry next = e.nextElement();
                if (next.getName().endsWith(".class")) {
                    try {
                        InputStream in = zipIn.getInputStream(next);
                        ClassReader reader = new ClassReader(in);
                        ClassNode node = new ClassNode();
                        reader.accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                        jre.put(node.name, node);
                    } catch (IllegalArgumentException x) {
                        System.out.println("Could not parse " + next.getName() + " (is it a class?)");
                    }
                }
            }
            zipIn.close();
        } catch (IOException e) {
            e.printStackTrace(System.out);
        }
    }

    @Test
    public void test() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, ClassNotFoundException {
        File testcases = new File("./src/test/resources/testcases");
        if (!testcases.exists()) {
            throw new AssertionError("Testcases folder does not exist");
        }
        File krakatau = new File("./src/test/resources/Krakatau");
        if (!krakatau.exists()) {
            System.out.println("Krakatau assembler does not exist");
            return;
        }
        for (File file : testcases.listFiles()) {
            if (file.getName().endsWith(".j")) {
                System.out.println("Compiling " + file);
                File compiled = new File(testcases, file.getName().replace(".j", ".class"));
                if (compiled.exists() && !compiled.delete()) {
                    throw new AssertionError("Could not delete existing compiled testcase");
                }
                String out = "";
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{
                            "py",
                            new File(krakatau, "assemble.py").getAbsolutePath(),
                            file.getAbsolutePath()
                    }, new String[0], testcases);
                    process.waitFor();
                    out = readProcess(process);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (!compiled.exists()) {
                    throw new AssertionError("Failed to compile " + file.getName() + " " + out);
                }

                try {
                    ClassNode classNode = new ClassNode();
                    ClassReader reader = new ClassReader(new FileInputStream(compiled));
                    reader.accept(classNode, 0);

                    DelegatingProvider provider = new DelegatingProvider();
                    provider.register(new JVMComparisonProvider());
                    provider.register(new ComparisonProvider() {
                        @Override
                        public boolean instanceOf(JavaValue target, Type type, Context context) {
                            return false;
                        }

                        @Override
                        public boolean checkcast(JavaValue target, Type type, Context context) {
                            if (type.getDescriptor().equals("[B")) {
                                if (!(target.value() instanceof byte[])) {
                                    return false;
                                }
                            } else if (type.getDescriptor().equals("[I")) {
                                if (!(target.value() instanceof int[])) {
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
                    provider.register(new ReflectiveProvider(jre));

                    provider.register(new MappedFieldProvider());
                    Context context = new Context(provider);
                    context.dictionary = new HashMap<>();
                    context.dictionary.putAll(jre);
                    context.dictionary.put(classNode.name, classNode);
                    context.push(classNode.name, "main", 0);
                    provider.register(new MappedMethodProvider(context.dictionary));

                    MethodNode main = classNode.methods.stream().filter(mn -> mn.name.equals("test") && mn.desc.equals("(Ljava/io/PrintStream;)V")).findFirst().get();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
                    PrintStream ps1 = new PrintStream(baos1);
                    try {
                        System.out.println("Running");
                        MethodExecutor.execute(context.dictionary.get(classNode.name), main, Arrays.asList(JavaValue.valueOf(ps)), null, context);
                    } catch (RuntimeException ex) {
                        if (ex.getCause() != null) {
                            ps1.print("Exception in thread \"main\" ");
                            ex.getCause().printStackTrace(ps1);
                        } else {
                            throw ex;
                        }
                    }
                    String actual = baos.toString("UTF-8");
                    String actualErr = baos1.toString("UTF-8");
                    System.out.println("Program execution completed");

                    Process p1 = Runtime.getRuntime().exec(
                            new String[]{
                                    "java",
                                    compiled.getName().replace(".class", "")
                            }
                            , new String[0], testcases
                    );
                    String s = IOUtils.toString(p1.getInputStream());
                    String s1 = IOUtils.toString(p1.getErrorStream());
                    if (s.equals(actual) && s1.equals(actualErr)) {
                        System.out.println("Tests matched");
                    } else {
                        System.out.println("Error: Test did not match");
                        System.out.println("++++++++++++++++");
                        System.out.println("Expected output");
                        System.out.println("++++++++++++++++");
                        System.out.println(s);
                        System.out.println("------------");
                        System.out.println(s1);
                        System.out.println("++++++++++++++++");
                        System.out.println("Actual Output");
                        System.out.println("++++++++++++++++");
                        System.out.println(actual);
                        System.out.println("-----------");
                        System.out.println(actualErr);
                        System.out.println("++++++++++++++++");
                        assertTrue(false);
                    }
                } finally {
                    compiled.delete();
                }
            }
        }
    }


    public static String readProcess(Process process) {
        StringBuilder result = new StringBuilder();
        result.append("--- BEGIN PROCESS DUMP ---").append("\n");
        result.append("---- STDOUT ----").append("\n");
        InputStream inputStream = process.getInputStream();
        byte[] inputStreamBytes = new byte[0];
        try {
            inputStreamBytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            result.append("An error occured while reading from stdout").append("\n");
            result.append("Caused by: ").append(e.getClass()).append(" ").append(e.getMessage()).append("\n");
        } finally {
            if (inputStreamBytes.length > 0) {
                result.append(new String(inputStreamBytes, StandardCharsets.UTF_8));
            }
        }
        result.append("---- STDERR ----").append("\n");
        inputStream = process.getErrorStream();
        inputStreamBytes = new byte[0];
        try {
            inputStreamBytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            result.append("An error occured while reading from stderr").append("\n");
            result.append("Caused by: ").append(e.getClass()).append(" ").append(e.getMessage()).append("\n");
        } finally {
            if (inputStreamBytes.length > 0) {
                result.append(new String(inputStreamBytes, StandardCharsets.UTF_8));
            }
        }

        result.append("---- EXIT VALUE ----").append("\n");

        int exitValue = -0xCAFEBABE;
        try {
            exitValue = process.waitFor();
        } catch (InterruptedException e) {
            result.append("An error occured while obtaining the exit value").append("\n");
            result.append("Caused by: ").append(e.getClass()).append(" ").append(e.getMessage()).append("\n");
        } finally {
            if (exitValue != -0xCAFEBABE) {
                result.append("Process finished with exit code ").append(exitValue).append("\n");
            }
        }

        return result.toString();
    }
}
