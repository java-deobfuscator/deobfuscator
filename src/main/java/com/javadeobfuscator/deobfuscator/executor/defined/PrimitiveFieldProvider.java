package com.javadeobfuscator.deobfuscator.executor.defined;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;

public class PrimitiveFieldProvider extends FieldProvider {
    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        switch (className) {
        case "java/lang/Integer":
            return new JavaClass("int", context);
        case "java/lang/Byte":
            return new JavaClass("byte", context);
        case "java/lang/Short":
            return new JavaClass("short", context);
        case "java/lang/Float":
            return new JavaClass("float", context);
        case "java/lang/Boolean":
            return new JavaClass("boolean", context);
        case "java/lang/Character":
            return new JavaClass("char", context);
        case "java/lang/Double":
            return new JavaClass("double", context);
        case "java/lang/Long":
            return new JavaClass("long", context);
        default:
            throw new IllegalStateException();
        }
    }

    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        throw new IllegalStateException();
    }

    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        switch (className) {
        case "java/lang/Integer":
        case "java/lang/Byte":
        case "java/lang/Short":
        case "java/lang/Float":
        case "java/lang/Boolean":
        case "java/lang/Character":
        case "java/lang/Double":
        case "java/lang/Long":
            return true;
        default:
            return false;
        }
    }

    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        return false;
    }
}
