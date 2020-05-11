package com.javadeobfuscator.deobfuscator.transformers.dasho;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;

public class FakeExceptionTransformer extends Transformer<TransformerConfig>
{
	 @Override
	 public boolean transform() throws Throwable {
		 System.out.println("[DashO] [FakeExceptionTransformer] Starting");
		 AtomicInteger counter = new AtomicInteger();
		 Set<String> fakeExceptionClass = new HashSet<>();
		 
		 classNodes().forEach(classNode -> {
			 classNode.methods.stream().filter(Utils::notAbstractOrNative).forEach(methodNode -> {
				 List<TryCatchBlockNode> remove = new ArrayList<>();
				 for(TryCatchBlockNode tcbn : methodNode.tryCatchBlocks)
				 {
					 String handler = tcbn.type;
					 if(handler != null && classes.containsKey(handler))
					 {
						 ClassNode handlerClass = classes.get(handler);
						 if(handlerClass.methods.size() == 0 && handlerClass.superName.equals("java/lang/RuntimeException"))
						 {
							 remove.add(tcbn);
							 fakeExceptionClass.add(handler);
							 counter.incrementAndGet();
						 }
					 }
				 }
				 methodNode.tryCatchBlocks.removeIf(remove::contains);
			 });
		 });
		 
		 fakeExceptionClass.forEach(str -> {
			 classes.remove(str);
			 classpath.remove(str);
		 });
		 
		 System.out.println("[DashO] [FakeExceptionTransformer] Removed " + counter.get() + " fake try-catch blocks");
		 System.out.println("[DashO] [FakeExceptionTransformer] Removed " + fakeExceptionClass.size() + " fake exception classes");
		 System.out.println("[DashO] [FakeExceptionTransformer] Done");
		 return counter.get() > 0;
	 }
}
