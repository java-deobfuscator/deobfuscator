package com.javadeobfuscator.deobfuscator.executor;

import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

import java.lang.reflect.Method;

public class StackObject {
    public Class<?> type;
    public Object value;

    public Type initType = null;
    public boolean isUninitialized = false;

    public boolean isldc = false;

    public StackObject(Class<?> type, Object value) {
        if (Primitives.unwrap(type) != type) {
            throw new IllegalArgumentException();
        }
        this.type = type;
        this.value = value;
    }

    public StackObject(Class<?> type, Object value, boolean ldc) {
        if (Primitives.unwrap(type) != type) {
            throw new IllegalArgumentException();
        }
        this.type = type;
        this.value = value;
        this.isldc = ldc;
    }

    public StackObject(Type type) {
        this.isUninitialized = true;
        this.initType = type;
        this.value = this;
        this.type = Object.class;
    }

    public StackObject copy() {
        if (this.isUninitialized) {
            return this;
        }
        StackObject clone = new StackObject(type, value);
        return clone;
    }

    public <T> T as(Class<T> prim) {
        if (Primitives.wrap(prim) != prim) {
            return MethodExecutor.castToPrimitive(value, prim);
        }
        if (value == null)
            return null;
        if (prim.isInstance(value)) {
            return prim.cast(value);
        } else {
            throw new IllegalArgumentException("Expected type " + prim.getCanonicalName() + " but got " + value.getClass().getCanonicalName());
        }
    }

    public StackObject cast(Class<?> prim) {
        if (value == null) {
            throw new NullPointerException();
        }
        try {
            Object start = value;
            if (start instanceof Boolean) {
                start = ((Boolean) start) ? 1 : 0;
            }
            if (start instanceof Character) {
                start = (int) ((Character) start).charValue();
            }
            if (prim == char.class) {
                start = Character.valueOf((char) ((Number) start).intValue());
            }
            String type = prim.getName();
            Method unbox = start.getClass().getMethod(type + "Value");
            StackObject result = new StackObject(prim, unbox.invoke(start));
            return result;
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        throw new IllegalStateException();
    }

    public static StackObject forPrimitive(Class<?> prim) {
        if (prim == null) {
            throw new NullPointerException();
        }
        return new StackObject(prim, PrimitiveUtils.getDefaultValue(prim));
    }

    public String toString() {
        return initType != null ? "UninitType[" + initType + "]" : value == null ? "Null object" : value.toString();
    }

    public void initialize(Object value) {
        this.initType = null;
        this.isUninitialized = false;
        this.value = value;
    }
}
