package com.javadeobfuscator.deobfuscator.executor.providers;

import java.util.List;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;

public abstract class ComparisonProvider implements Provider {
    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        return false;
    }

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return false;
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        return false;
    }

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        throw new IllegalStateException("Cannot invoke method on ComparisonProvider");
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        throw new IllegalStateException("Cannot set field on ComparisonProvider");
    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        throw new IllegalStateException("Cannot get field on ComparisonProvider");
    }
}
