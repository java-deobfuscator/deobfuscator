package com.javadeobfuscator.deobfuscator.utils;

import java.util.HashMap;
import java.util.Map;

public class PrimitiveUtils {
    private static final Map<String, Class<?>> nameToPrimitive = new HashMap<>();
    private static final Map<Class<?>, Object> defaultPrimitiveValues = new HashMap<>();

    static {
        defaultPrimitiveValues.put(Integer.TYPE, 0);
        defaultPrimitiveValues.put(Long.TYPE, 0L);
        defaultPrimitiveValues.put(Double.TYPE, 0D);
        defaultPrimitiveValues.put(Float.TYPE, 0F);
        defaultPrimitiveValues.put(Boolean.TYPE, false);
        defaultPrimitiveValues.put(Character.TYPE, '\0');
        defaultPrimitiveValues.put(Byte.TYPE, (byte) 0);
        defaultPrimitiveValues.put(Short.TYPE, (short) 0);
        defaultPrimitiveValues.put(Object.class, null);
        nameToPrimitive.put("int", Integer.TYPE);
        nameToPrimitive.put("long", Long.TYPE);
        nameToPrimitive.put("double", Double.TYPE);
        nameToPrimitive.put("float", Float.TYPE);
        nameToPrimitive.put("boolean", Boolean.TYPE);
        nameToPrimitive.put("char", Character.TYPE);
        nameToPrimitive.put("byte", Byte.TYPE);
        nameToPrimitive.put("short", Short.TYPE);
        nameToPrimitive.put("void", Void.TYPE);
    }
    
    public static Class<?> getPrimitiveByName(String name) {
        return nameToPrimitive.get(name);
    }
    
    public static Object getDefaultValue(Class<?> primitive) {
        return defaultPrimitiveValues.get(primitive);
    }
}
