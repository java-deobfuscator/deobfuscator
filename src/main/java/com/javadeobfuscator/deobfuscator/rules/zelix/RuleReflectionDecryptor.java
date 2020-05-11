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

package com.javadeobfuscator.deobfuscator.rules.zelix;

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.zelix.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public class RuleReflectionDecryptor implements Rule, Opcodes {
    @Override
    public String getDescription() {
        return "Zelix Klassmaster generates a separate class to deobfuscate reflection calls. This class can be easily identified";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            boolean isZKM = true;

            isZKM = isZKM && TransformerHelper.findFieldNodes(classNode, null, "[Ljava/lang/Object;").size() == 1;
            isZKM = isZKM && TransformerHelper.findFieldNodes(classNode, null, "[Ljava/lang/String;").size() == 3;

            MethodNode deobfuscateMethod = TransformerHelper.findMethodNode(classNode, null, "(J)Ljava/lang/reflect/Method;");
            if (deobfuscateMethod == null) {
                continue;
            }

            isZKM = isZKM && TransformerHelper.containsInvokeStatic(deobfuscateMethod, "java/lang/Long", "parseLong", "(Ljava/lang/String;I)J");
            isZKM = isZKM && TransformerHelper.containsInvokeVirtual(deobfuscateMethod, "java/lang/Class", "getSuperclass", "()Ljava/lang/Class;");
            isZKM = isZKM && TransformerHelper.containsInvokeVirtual(deobfuscateMethod, "java/lang/Class", "getInterfaces", "()[Ljava/lang/Class;");

            if (isZKM) {
                return "Found possible reflection deobfuscation class: " + classNode.name;
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(ReflectionObfuscationTransformer.class);
    }
}
