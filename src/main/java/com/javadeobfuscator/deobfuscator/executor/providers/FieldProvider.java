package com.javadeobfuscator.deobfuscator.executor.providers;

import java.util.List;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public abstract class FieldProvider implements Provider {
    @Override
    public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        return false;
    }

    @Override
    public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
        return false;
    }

    @Override
    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        throw new IllegalStateException("Cannot invoke method on FieldProvider");
    }

    @Override
    public boolean instanceOf(StackObject target, Type type, Context context) {
        throw new IllegalStateException("Cannot check instanceof on FieldProvider");
    }

    @Override
    public boolean checkEquality(StackObject first, StackObject second, Context context) {
        throw new IllegalStateException("Cannot check equality on FieldProvider");
    }
}
