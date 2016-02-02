package com.javadeobfuscator.deobfuscator.executor.defined;

import java.util.HashMap;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.providers.FieldProvider;

public class MappedFieldProvider extends FieldProvider {
    private Map<String, Object> fields = new HashMap<>();

    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return fields.get(className + fieldName + fieldDesc);
    }

    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        fields.put(className + fieldName + fieldDesc, value);
    }

    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return true;
    }

    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        return true;
    }
}
