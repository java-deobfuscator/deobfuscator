package com.javadeobfuscator.deobfuscator.executor.defined;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.executor.MethodExecutor;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.Context;
import com.javadeobfuscator.deobfuscator.executor.MethodExecutor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.providers.MethodProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class DictionaryMethodProvider extends MethodProvider {
    private final Map<String, WrappedClassNode> classes;
    
    public DictionaryMethodProvider(Map<String, WrappedClassNode> classes) {
        this.classes = classes;
    }
    
    public Object invokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        WrappedClassNode wrappedClassNode = classes.get(className);
        if (wrappedClassNode != null) {
            ClassNode classNode = wrappedClassNode.classNode;
            MethodNode methodNode = classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElseGet(null);
            if (methodNode != null) {
                List<StackObject> argsClone = new ArrayList<>();
                for (StackObject arg : args) {
                    argsClone.add(arg.copy());
                }
                return MethodExecutor.execute(wrappedClassNode, methodNode, argsClone, targetObject == null ? null : targetObject.value, context);
            }
        }
        throw new IllegalStateException();
    }

    public boolean canInvokeMethod(String className, String methodName, String methodDesc, StackObject targetObject, List<StackObject> args, Context context) {
        WrappedClassNode wrappedClassNode = classes.get(className);
        if (wrappedClassNode != null) {
            MethodNode methodNode = wrappedClassNode.classNode.methods.stream().filter(mn -> mn.name.equals(methodName) && mn.desc.equals(methodDesc)).findFirst().orElseGet(null);
            return methodNode != null;
        }
        return false;
    }
}
