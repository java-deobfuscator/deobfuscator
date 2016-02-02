package com.javadeobfuscator.deobfuscator.executor.providers;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public abstract class MethodProvider implements Provider {

    @Override
    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        return false;
    }

    @Override
    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        return false;
    }

    @Override
    public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
        return false;
    }

    @Override
    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context) {
        throw new IllegalStateException("Cannot set field on MethodProvider");
    }

    @Override
    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context) {
        throw new IllegalStateException("Cannot get field on MethodProvider");
    }

    @Override
    public boolean instanceOf(StackObject target, Type type, Context context) {
        throw new IllegalStateException("Cannot check instanceof on MethodProvider");
    }

    @Override
    public boolean checkEquality(StackObject first, StackObject second, Context context) {
        throw new IllegalStateException("Cannot check equality on MethodProvider");
    }
}
