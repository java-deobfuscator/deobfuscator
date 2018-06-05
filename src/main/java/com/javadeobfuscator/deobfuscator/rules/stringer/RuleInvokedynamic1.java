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

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.stringer.invokedynamic.Invokedynamic1Transformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;

public class RuleInvokedynamic1 implements Rule, Opcodes {
    @Override
    public String getDescription() {
        return "Stringer has several modes of invokedynamic obfuscation. " +
                "This one creates a bootstrap method which decrypts the target method based on the given method name, and" +
                "an additional string parameter. The MethodHandle is then resolved using findStatic() or findVirtual()";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            MethodNode bsm = TransformerHelper.findMethodNode(classNode, null, Invokedynamic1Transformer.BSM_DESC);
            if (bsm == null) {
                continue;
            }
            if (!Modifier.isStatic(bsm.access)) {
                continue;
            }

            boolean isBSM = true;

            isBSM = isBSM && TransformerHelper.containsInvokeVirtual(bsm, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
            isBSM = isBSM && TransformerHelper.containsInvokeVirtual(bsm, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
            isBSM = isBSM && TransformerHelper.countOccurencesOf(bsm, TABLESWITCH) > 0;
            isBSM = isBSM && TransformerHelper.countOccurencesOf(bsm, IXOR) > 0;
            isBSM = isBSM && TransformerHelper.countOccurencesOf(bsm, IREM) > 0;

            if (isBSM) {
                return "Found potential bootstrap method " + classNode.name + " " + bsm.name + bsm.desc;
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer>> getRecommendTransformers() {
        return Collections.singletonList(Invokedynamic1Transformer.class);
    }
}
