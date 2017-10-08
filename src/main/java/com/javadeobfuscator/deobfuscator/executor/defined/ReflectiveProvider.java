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

package com.javadeobfuscator.deobfuscator.executor.defined;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.providers.Provider;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;
import org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.ClassNode;
import sun.invoke.util.BytecodeDescriptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Not recommended for production usage
 */
public class ReflectiveProvider implements Provider {
    private Map<String, ClassNode> dictionary;
    private Map<String, Method> methodCache = new HashMap<>();
    private Map<String, Constructor<?>> ctorCache = new HashMap<>();
    private Map<String, Field> fieldCache = new HashMap<>();

    public ReflectiveProvider(Map<String, ClassNode> dictionary) {
        this.dictionary = dictionary;
    }

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        if (!dictionary.containsKey(className)) {
            throw new ExecutionException(className + " could not be located in dictionary");
        }
        Class<?> targetClass;
        Class<?>[] clazzes;
        try {
            targetClass = Class.forName(className.replace("/", "."));
            List<Class<?>> l = BytecodeDescriptor.parseMethod(methodDesc, ClassLoader.getSystemClassLoader());
            l.remove(l.size() - 1);
            clazzes = l.toArray(new Class<?>[0]);
        } catch (Throwable e) {
            throw new ExecutionException(e);
        }
        if (methodName.equals("<init>")) {
            Constructor<?> ctor = ctorCache.computeIfAbsent(className + methodName + methodDesc, key -> {
                try {
                    Constructor<?> constructor = targetClass.getDeclaredConstructor(clazzes);
                    constructor.setAccessible(true);
                    return constructor;
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });

            List<Object> ar = new ArrayList<>();
            for (JavaValue o : args) {
                ar.add(o.value());
            }
            Object o;
            try {
                o = ctor.newInstance(ar.toArray());
            } catch (ReflectiveOperationException ex) {
                Utils.sneakyThrow(ex.getCause());
                return null;
            } catch (Throwable t) {
                throw new ExecutionException(t);
            }
            targetObject.initialize(o);
            if (targetObject.value() instanceof Throwable) {
                ((Throwable) targetObject.value()).setStackTrace(context.getStackTrace());
            }
            return null;
        } else {
            Method method = methodCache.computeIfAbsent(className + methodName + methodDesc, key -> {
                try {
                    Method innerMethod = targetClass.getDeclaredMethod(methodName, clazzes);
                    innerMethod.setAccessible(true);
                    return innerMethod;
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });
            Object instance = targetObject == null ? null : targetObject.value();
            List<Object> ar = new ArrayList<>();
            for (JavaValue o : args) {
                ar.add(o.value());
            }
            try {
                return method.invoke(instance, ar.toArray());
            } catch (ReflectiveOperationException ex) {
                Utils.sneakyThrow(ex.getCause());
                return null;
            } catch (Throwable t) {
                throw new ExecutionException(t);
            }
        }
    }

    @Override
    public boolean instanceOf(JavaValue target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean checkcast(JavaValue target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean checkEquality(JavaValue first, JavaValue second, Context context) {
        return false;
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {

    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        if (!dictionary.containsKey(className)) {
            throw new ExecutionException(className + " could not be located in dictionary");
        }
        Class<?> targetClass;
        try {
            targetClass = Class.forName(className.replace("/", "."));
        } catch (Throwable e) {
            throw new ExecutionException(e);
        }
        Field field = fieldCache.computeIfAbsent(className + fieldName + fieldDesc, key -> {
            Field[] fields = targetClass.getDeclaredFields();
            for (Field f : fields) {
                if (f.getName().equals(fieldName) && Type.getType(f.getType()).getDescriptor().equals(fieldDesc)) {
                    return f;
                }
            }
            throw new ExecutionException("Could not find field");
        });
        try {
            return field.get(targetObject == null ? null : targetObject.value());
        } catch (ReflectiveOperationException ex) {
            Utils.sneakyThrow(ex.getCause());
            return null;
        } catch (Throwable e) {
            throw new ExecutionException(e);
        }
    }

    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, JavaValue targetObject, List<JavaValue> args, Context context) {
        return dictionary.containsKey(className);
    }

    @Override
    public boolean canCheckInstanceOf(JavaValue target, Type type, Context context) {
        Type finalType = type;
        while (finalType.getSort() == Type.ARRAY) {
            finalType = finalType.getElementType();
        }
        if (finalType.getSort() != Type.OBJECT) {
            throw new ExecutionException("Expected instanceof Object, but got " + finalType.getSort());
        }
        return dictionary.containsKey(finalType.getInternalName());
    }

    @Override
    public boolean canCheckcast(JavaValue target, Type type, Context context) {
        Type finalType = type;
        while (finalType.getSort() == Type.ARRAY) {
            finalType = finalType.getElementType();
        }
        if (finalType.getSort() != Type.OBJECT) {
            throw new ExecutionException("Expected instanceof Object, but got " + finalType.getSort());
        }
        return dictionary.containsKey(finalType.getInternalName());
    }

    @Override
    public boolean canCheckEquality(JavaValue first, JavaValue second, Context context) {
        return false;
    }

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Context context) {
        return dictionary.containsKey(className);
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, JavaValue targetObject, Object value, Context context) {
        return dictionary.containsKey(className);
    }
}
