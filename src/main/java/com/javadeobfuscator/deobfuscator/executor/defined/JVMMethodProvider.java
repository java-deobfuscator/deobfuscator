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

package com.javadeobfuscator.deobfuscator.executor.defined;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jooq.lambda.function.Function3;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaConstantPool;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaField;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethod;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaMethodHandle;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public class JVMMethodProvider extends MethodProvider {
    @SuppressWarnings("serial")
    //@formatter:off
    private static final Map<String, Map<String, Function3<StackObject, List<StackObject>, Context, Object>>> functions = new HashMap<String, Map<String, Function3<StackObject, List<StackObject>, Context, Object>>>() {{
        put("java/lang/String", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("<init>([CII)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/String");
                targetObject.initialize(new String(args.get(0).as(char[].class), args.get(1).as(int.class), args.get(2).as(int.class)));
                return null;
            });
            put("<init>([C)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/String");
                targetObject.initialize(new String(args.get(0).as(char[].class)));
                return null;
            });
            put("intern()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(String.class).intern());
            put("equals(Ljava/lang/Object;)Z", (targetObject, args, context) -> targetObject.as(String.class).equals(args.get(0).value));
            put("trim()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(String.class).trim());
            put("toCharArray()[C", (targetObject, args, context) -> targetObject.as(String.class).toCharArray());
            put("length()I", (targetObject, args, context) -> targetObject.as(String.class).length());
            put("hashCode()I", (targetObject, args, context) -> targetObject.as(String.class).hashCode());
            put("charAt(I)C", (targetObject, args, context) -> targetObject.as(String.class).charAt(args.get(0).as(int.class)));
            put("indexOf(I)I", (targetObject, args, context) -> targetObject.as(String.class).indexOf(args.get(0).as(int.class)));
            put("substring(I)Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(String.class).substring(args.get(0).as(int.class)));
            put("substring(II)Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(String.class).substring(args.get(0).as(int.class), args.get(1).as(int.class)));
            put("indexOf(II)I", (targetObject, args, context) -> targetObject.as(String.class).indexOf(args.get(0).as(int.class), args.get(1).as(int.class)));
            put("valueOf([CII)Ljava/lang/String;", (targetObject, args, context) -> String.valueOf(args.get(0).as(char[].class), args.get(1).as(int.class),args.get(2).as(int.class)));
        }});
        put("java/lang/StringBuilder", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("<init>()V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/StringBuilder");
                targetObject.initialize(new StringBuilder());
                return null;
            });
            put("<init>(Ljava/lang/String;)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/StringBuilder");
                targetObject.initialize(new StringBuilder(args.get(0).as(String.class)));
                return null;
            });
            put("append(I)Ljava/lang/StringBuilder;", (targetObject, args, context) -> targetObject.as(StringBuilder.class).append(args.get(0).as(int.class)));
            put("append(Ljava/lang/String;)Ljava/lang/StringBuilder;", (targetObject, args, context) -> targetObject.as(StringBuilder.class).append(args.get(0).as(String.class)));
            put("toString()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(StringBuilder.class).toString());
            put("length()I", (targetObject, args, context) -> targetObject.as(StringBuilder.class).length());
            put("charAt(I)C", (targetObject, args, context) -> targetObject.as(StringBuilder.class).charAt(args.get(0).as(int.class)));
            put("setCharAt(IC)V", (targetObject, args, context) -> {
                targetObject.as(StringBuilder.class).setCharAt(args.get(0).as(int.class), (char) args.get(1).cast(char.class).value);
                return null;
            });
        }});
        put("java/lang/StringBuffer", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("<init>(Ljava/lang/String;)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/StringBuffer");
                targetObject.initialize(new StringBuffer(args.get(0).as(String.class)));
                return null;
            });
            put("<init>(I)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/StringBuffer");
                targetObject.initialize(new StringBuffer(args.get(0).as(int.class)));
                return null;
            });
            put("<init>()V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/StringBuffer");
                targetObject.initialize(new StringBuffer());
                return null;
            });
            put("insert(ILjava/lang/String;)Ljava/lang/StringBuffer;", (targetObject, args, context) -> targetObject.as(StringBuffer.class).insert(args.get(0).as(int.class), args.get(1).as(String.class)));
            put("append(Ljava/lang/String;)Ljava/lang/StringBuffer;", (targetObject, args, context) -> targetObject.as(StringBuffer.class).append(args.get(0).as(String.class)));
            put("append(C)Ljava/lang/StringBuffer;", (targetObject, args, context) -> targetObject.as(StringBuffer.class).append(args.get(0).as(char.class)));
            put("toString()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(StringBuffer.class).toString());
        }});
        put("java/lang/Exception", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("<init>()V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/Exception");
                targetObject.initialize(null);
                return null;
            });
            put("getStackTrace()[Ljava/lang/StackTraceElement;", (targetObject, args, context) -> context.getStackTrace());
        }});
        put("java/lang/RuntimeException", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("<init>(Ljava/lang/String;)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/RuntimeException");
                targetObject.initialize(args.get(0).value);
                return null;
            });
        }});
        put("java/lang/Class", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("forName(Ljava/lang/String;)Ljava/lang/Class;", (targetObject, args, context) -> new JavaClass(args.get(0).as(String.class), context));
            put("getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", (targetObject, args, context) -> targetObject.as(JavaClass.class).getDeclaredMethod(args.get(0).as(String.class), toJavaClass(args.get(1).as(Object[].class))));
            put("getDeclaredMethods()[Ljava/lang/reflect/Method;", (targetObject, args, context) -> targetObject.as(JavaClass.class).getDeclaredMethods());
            put("getDeclaredFields()[Ljava/lang/reflect/Field;", (targetObject, args, context) -> targetObject.as(JavaClass.class).getDeclaredFields());
            put("getClassLoader()Ljava/lang/ClassLoader;", (targetObject, args, context) -> null);
            put("getName()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(JavaClass.class).getName());
            put("getSuperclass()Ljava/lang/Class;", (targetObject, args, context) -> targetObject.as(JavaClass.class).getSuperclass());
            put("getInterfaces()[Ljava/lang/Class;", (targetObject, args, context) -> targetObject.as(JavaClass.class).getInterfaces());
        }});
        put("java/lang/reflect/Method", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("getName()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(JavaMethod.class).getName());
            put("getReturnType()Ljava/lang/Class;", (targetObject, args, context) -> targetObject.as(JavaMethod.class).getReturnType());
            put("getParameterTypes()[Ljava/lang/Class;", (targetObject, args, context) -> targetObject.as(JavaMethod.class).getParameterTypes());
            put("setAccessible(Z)V", (targetObject, args, context) -> {
                targetObject.as(JavaMethod.class).setAccessible(args.get(0).as(boolean.class));
                return null;
            });
        }});
        put("java/lang/reflect/Field", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("getName()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(JavaField.class).getName());
            put("getType()Ljava/lang/Class;", (targetObject, args, context) -> targetObject.as(JavaField.class).getType());
        }});
        put("java/lang/invoke/MethodType", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("fromMethodDescriptorString(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;", (targetObject, args, context) -> args.get(0).value);
        }});
        put("java/lang/invoke/MethodHandles$Lookup", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("findStatic(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", (targetObject, args, context) -> new JavaMethodHandle(args.get(0).as(JavaClass.class).getType().getInternalName(), args.get(1).toString(), args.get(2).toString(), "static"));
            put("findVirtual(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", (targetObject, args, context) -> new JavaMethodHandle(args.get(0).as(JavaClass.class).getType().getInternalName(), args.get(1).toString(), args.get(2).toString(), "virtual"));
        }});
        put("java/lang/invoke/MethodHandle", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("asType(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", (targetObject, args, context) -> targetObject.value);
        }});
        put("java/lang/invoke/ConstantCallSite", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("<init>(Ljava/lang/invoke/MethodHandle;)V", (targetObject, args, context) -> {
                expect(targetObject, "java/lang/invoke/ConstantCallSite");
                targetObject.initialize(args.get(0).value);
                return null;
            });
        }});
        put("java/lang/System", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("currentTimeMillis()J", (targetObject, args, context) -> System.currentTimeMillis());
            put("arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V", (targetObject, args, context) -> {
                System.arraycopy(args.get(0).value, args.get(1).as(int.class), args.get(2).value, args.get(3).as(int.class), args.get(4).as(int.class));
                return null;
            });
        }});
        put("java/lang/Thread", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("currentThread()Ljava/lang/Thread;", (targetObject, args, context) -> null);
            put("getStackTrace()[Ljava/lang/StackTraceElement;", (targetObject, args, context) -> {
                context.push("java.lang.Thread", "getStackTrace", 0);
                StackTraceElement[] elems = context.getStackTrace();
                context.pop();
                return elems;
            });
        }});
        put("sun/misc/SharedSecrets", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("getJavaLangAccess()Lsun/misc/JavaLangAccess;", (targetObject, args, context) -> null);
        }});
        put("java/lang/StackTraceElement", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("getClassName()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(StackTraceElement.class).getClassName());
            put("getMethodName()Ljava/lang/String;", (targetObject, args, context) -> targetObject.as(StackTraceElement.class).getMethodName());
        }});
        put("sun/misc/JavaLangAccess", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("getConstantPool(Ljava/lang/Class;)Lsun/reflect/ConstantPool;", (targetObject, args, context) -> new JavaConstantPool(args.get(0).as(JavaClass.class)));
        }});
        put("sun/reflect/ConstantPool", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("getSize()I", (targetObject, args, context) -> targetObject.as(JavaConstantPool.class).getSize());
        }});
        put("java/lang/Long", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("parseLong(Ljava/lang/String;)J", (targetObject, args, context) -> Long.parseLong(args.get(0).as(String.class)));
            put("parseLong(Ljava/lang/String;I)J", (targetObject, args, context) -> Long.parseLong(args.get(0).as(String.class), args.get(1).as(int.class)));
        }});
        put("java/lang/Integer", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("parseInt(Ljava/lang/String;)I", (targetObject, args, context) -> Integer.parseInt(args.get(0).as(String.class)));
        }});
        put("java/util/regex/Pattern", new HashMap<String, Function3<StackObject, List<StackObject>, Context, Object>>() {{
            put("compile(Ljava/lang/String;)Ljava/util/regex/Pattern;", (targetObject, args, context) -> Pattern.compile(args.get(0).as(String.class)));
        }});
    }};
    //@formatter:on

    @Override
    public boolean instanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        Map<String, Function3<StackObject, List<StackObject>, Context, Object>> map = functions.get(className);
        return map.get(methodName + methodDesc).apply(targetObject, args, context);
    }

    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        Map<String, Function3<StackObject, List<StackObject>, Context, Object>> map = functions.get(className);
        return map != null && map.containsKey(methodName + methodDesc);
    }

    @Override
    public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    private static void expect(StackObject object, String type) {
        if (!object.initType.toString().equals(type)) {
            throw new IllegalArgumentException("Expected UninitializedObject[" + type + "] but got " + object.initType);
        }
    }

    @Override
    public boolean checkEquality(StackObject first, StackObject second, Context context) {
        if (first.value instanceof JavaClass && second.value instanceof JavaClass) {
            return first.as(JavaClass.class).equals(second.value);
        }
        return first == second;
    }

    @Override
    public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
        return true;
    }

    private static JavaClass[] toJavaClass(Object[] arr) {
        JavaClass[] clazz = new JavaClass[arr.length];
        for (int i = 0; i < arr.length; i++) {
            clazz[i] = (JavaClass) arr[i];
        }
        return clazz;
    }
}
