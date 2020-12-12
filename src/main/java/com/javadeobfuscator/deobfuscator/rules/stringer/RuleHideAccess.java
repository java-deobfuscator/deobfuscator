/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.rules.stringer;

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.stringer.HideAccessObfuscationTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class RuleHideAccess implements Rule, Opcodes {
    @Override
    public String getDescription() {
        return "Stringer most likely uses a seperate class to decrypt the encrypted calls. It then calls "
        	+ "the encrypted class directly or through invokedynamic";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
        	if(classNode.version == 49 && Modifier.isFinal(classNode.access) && classNode.superName.equals("java/lang/Object")) {
	        	List<String> methodsDesc = classNode.methods.stream().map(m -> m.desc).collect(Collectors.toList());
	            List<String> fieldsDesc = classNode.fields.stream().map(f -> f.desc).collect(Collectors.toList());
	
	            boolean isHideAccess = fieldsDesc.contains("[Ljava/lang/Object;") &&
	            	fieldsDesc.contains("[Ljava/lang/Class;") &&
	            	methodsDesc.contains("(II)Ljava/lang/Class;") &&
	            	methodsDesc.contains("(I)Ljava/lang/reflect/Method;") &&
	            	methodsDesc.contains("(I)Ljava/lang/reflect/Field;");
	
	            if (isHideAccess) {
	            	return "Found potential hideaccess decryptor class " + classNode.name;
	            }
        	}
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(HideAccessObfuscationTransformer.class);
    }
}
