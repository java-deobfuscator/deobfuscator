package com.javadeobfuscator.deobfuscator;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.JVMComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedFieldProvider;
import com.javadeobfuscator.deobfuscator.executor.defined.MappedMethodProvider;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.DelegatingProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.ClassReader;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import sun.invoke.util.BytecodeDescriptor;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertTrue;

public class TestRunner {
    private Map<String, WrappedClassNode> jre = new HashMap<>();

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
                        WrappedClassNode wr = new WrappedClassNode(node, reader.getItemCount());
                        jre.put(node.name, wr);
                    } catch (IllegalArgumentException x) {
                        System.out.println("Could not parse " + next.getName() + " (is it a class?)");
                    }
                }
            }
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
            throw new AssertionError("Krakatau assembler does not exist");
        }
        for (File file : testcases.listFiles()) {
            if (file.getName().endsWith(".j")) {
                System.out.println("Compiling " + file);
                File compiled = new File(testcases, file.getName().replace(".j", ".class"));
                if (compiled.exists() && !compiled.delete()) {
                    throw new AssertionError("Could not delete existing compiled testcase");
                }
                try {
                    Process process = Runtime.getRuntime().exec(new String[]{
                            "py",
                            new File(krakatau, "assemble.py").getAbsolutePath(),
                            file.getAbsolutePath()
                    }, new String[0], testcases);
                    process.waitFor();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (!compiled.exists()) {
                    throw new AssertionError("Failed to compile " + file.getName());
                }

                try {
                    ClassNode classNode = new ClassNode();
                    ClassReader reader = new ClassReader(new FileInputStream(compiled));
                    reader.accept(classNode, 0);

                    DelegatingProvider provider = new DelegatingProvider();
                    provider.register(new JVMComparisonProvider());
                    provider.register(new MethodProvider() {
                        @Override
                        public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
                            if (jre.containsKey(className)) {
                                try {
                                    Class<?> clazz = Class.forName(className.replace("/", "."));
                                    List<Class<?>> l = BytecodeDescriptor.parseMethod(methodDesc, ClassLoader.getSystemClassLoader());
                                    l.remove(l.size() - 1);
                                    Class<?>[] clazzes = l.toArray(new Class<?>[0]);

                                    if (methodName.equals("<init>")) {
                                        Constructor<?> method = clazz.getDeclaredConstructor(clazzes);
                                        method.setAccessible(true);
                                        List<Object> ar = new ArrayList<>();
                                        for (StackObject o : args) {
                                            ar.add(o.value);
                                        }
                                        targetObject.value = method.newInstance(ar.toArray());
                                        return null;
                                    } else {
                                        Method method = clazz.getDeclaredMethod(methodName, clazzes);
                                        method.setAccessible(true);
                                        Object instance = targetObject.value;
                                        List<Object> ar = new ArrayList<>();
                                        for (StackObject o : args) {
                                            ar.add(o.value);
                                        }
                                        return method.invoke(instance, ar.toArray());
                                    }
                                } catch (Throwable e) {
                                    throw new ExecutionException(e);
                                }
                            }
                            return null;
                        }

                        @Override
                        public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
                            return jre.containsKey(className);
                        }
                    });
                    provider.register(new FieldProvider() {
                        @Override
                        public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
                        }

                        @Override
                        public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
                            if (jre.containsKey(className)) {
                                try {
                                    Class<?> clazz = Class.forName(className.replace("/", "."));
                                    Field f = clazz.getDeclaredField(fieldName);
                                    return f.get(targetObject == null ? null : targetObject.value);
                                } catch (Throwable e) {
                                    throw new ExecutionException(e);
                                }
                            }
                            return null;
                        }

                        @Override
                        public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
                            return jre.containsKey(className);
                        }

                        @Override
                        public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
                            return false;
                        }
                    });
                    provider.register(new MappedFieldProvider());
                    Context context = new Context(provider);
                    context.dictionary = new HashMap<>();
                    context.dictionary.putAll(jre);
                    context.dictionary.put(classNode.name, new WrappedClassNode(classNode, reader.getItemCount()));
                    provider.register(new MappedMethodProvider(context.dictionary));

                    MethodNode main = classNode.methods.stream().filter(mn -> mn.name.equals("test") && mn.desc.equals("(Ljava/io/PrintStream;)V")).findFirst().get();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    MethodExecutor.execute(context.dictionary.get(classNode.name), main, Arrays.asList(new StackObject(Object.class, ps)), null, context);
                    String actual = baos.toString("UTF-8");
                    System.out.println("Program execution completed");
                    
                    Process p1 = Runtime.getRuntime().exec(
                            new String[] {
                                    "java",
                                    compiled.getName().replace(".class", "")
                            }
                            , new String[0], testcases
                    );
                    String s = IOUtils.toString(p1.getInputStream());
                    if (s.equals(actual)) {
                        System.out.println("Tests matched");
                    } else {
                        System.out.println("Error: Test did not match");
                        System.out.println("------------");
                        System.out.println(s);
                        System.out.println("------------");
                        System.out.println(actual);
                        System.out.println("------------");
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
