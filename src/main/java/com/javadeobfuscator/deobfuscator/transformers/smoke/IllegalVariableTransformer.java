package com.javadeobfuscator.deobfuscator.transformers.smoke; 
 
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode; 
 
import java.util.Map; 
import java.util.stream.Collectors; 
 
public class IllegalVariableTransformer extends Transformer<TransformerConfig> {
    @Override 
    public boolean transform() throws Throwable {
        classNodes().stream().map(wrappedClassNode -> wrappedClassNode.classNode).forEach( 
                classNode -> classNode.methods.stream().filter(methodNode -> methodNode.localVariables != null).forEach(methodNode -> 
                        methodNode.localVariables = methodNode.localVariables.stream().filter(localVariableNode -> (int) localVariableNode.name.charAt(0) <= 128).collect(Collectors.toList())));
        return true;
    }
} 
