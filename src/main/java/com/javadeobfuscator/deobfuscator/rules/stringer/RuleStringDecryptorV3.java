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
import com.javadeobfuscator.deobfuscator.transformers.stringer.StringEncryptionTransformer;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Modifier;
import java.util.*;

public class RuleStringDecryptorV3 implements Rule {
    @Override
    public String getDescription() {
        return "This variant of Stringer's string decryptor makes use of invokedynamic obfuscation within the string decryption classes";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                String basicType;
                try {
                    basicType = TransformerHelper.basicType(methodNode.desc);
                } catch (IllegalArgumentException ex) {
                    if (deobfuscator.getConfig().isDebugRulesAnalyzer()) {
                        String message = "Encountered illegal method desc at " + classNode.name + " " + methodNode.name + methodNode.desc;
                        new IllegalArgumentException(message, ex).printStackTrace();
                    }
                    continue;
                }
                if (!basicType.equals("(Ljava/lang/Object;III)Ljava/lang/Object;")
                    || !Modifier.isStatic(methodNode.access) || methodNode.instructions == null) {
                    continue;
                }

                boolean isStringer = true;

                isStringer = isStringer && TransformerHelper.containsInvokeStatic(methodNode, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;");
                isStringer = isStringer && TransformerHelper.containsInvokeVirtual(methodNode, "java/lang/Thread", "getStackTrace", "()[Ljava/lang/StackTraceElement;");
                isStringer = isStringer && TransformerHelper.countOccurencesOf(methodNode, IAND) > 20;
                isStringer = isStringer && TransformerHelper.countOccurencesOf(methodNode, IXOR) > 20;
                isStringer = isStringer && TransformerHelper.countOccurencesOf(methodNode, IUSHR) > 10;
                isStringer = isStringer && TransformerHelper.countOccurencesOf(methodNode, ISHL) > 10;

                if (!isStringer) {
                    continue;
                }

                return "Found possible string decryption class " + classNode.name;
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Arrays.asList(StringEncryptionTransformer.class,
        	com.javadeobfuscator.deobfuscator.transformers.stringer.v9.StringEncryptionTransformer.class);
    }
}
