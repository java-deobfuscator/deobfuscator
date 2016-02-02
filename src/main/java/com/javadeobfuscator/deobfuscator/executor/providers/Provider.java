package com.javadeobfuscator.deobfuscator.executor.providers;

import java.util.List;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public interface Provider {

    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context);

    public boolean instanceOf(StackObject target, Type type, Context context);
    
    public boolean checkEquality(StackObject first, StackObject second, Context context);

    public void setField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context);

    public Object getField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context);

    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context);

    public boolean canCheckInstanceOf(StackObject target, Type type, Context context);
    
    public boolean canCheckEquality(StackObject first, StackObject second, Context context);

    public boolean canGetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Context context);

    public boolean canSetField(String className, String fieldName, String fieldDesc, StackObject targetObject, Object value, Context context);
}