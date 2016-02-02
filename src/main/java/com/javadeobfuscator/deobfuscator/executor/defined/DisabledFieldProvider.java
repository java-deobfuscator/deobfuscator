package com.javadeobfuscator.deobfuscator.executor.defined;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;

public class DisabledFieldProvider extends FieldProvider {
    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        throw new IllegalArgumentException("Field get: " + className + " " + fieldName + fieldDesc);
    }

    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        throw new IllegalArgumentException("Field set: " + className + " " + fieldName + fieldDesc);
    }

    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return true;
    }

    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        return true;
    }
}
