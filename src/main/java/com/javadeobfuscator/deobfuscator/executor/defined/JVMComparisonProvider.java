package com.javadeobfuscator.deobfuscator.executor.defined;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public class JVMComparisonProvider extends ComparisonProvider {
    @Override
    public boolean checkEquality(StackObject first, StackObject second, Context context) {
        if (first.value instanceof JavaClass && second.value instanceof JavaClass) {
            return first.as(JavaClass.class).equals(second.value);
        }
        return first == second;
    }

    @Override
    public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
        return true;
    }

    @Override
    public boolean instanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
        return false;
    }
}
