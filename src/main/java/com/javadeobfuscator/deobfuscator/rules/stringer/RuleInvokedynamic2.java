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
import com.javadeobfuscator.deobfuscator.transformers.stringer.invokedynamic.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.*;
import java.util.*;

public class RuleInvokedynamic2 implements Rule, Opcodes {
    @Override
    public String getDescription() {
        return "Stringer has several modes of invokedynamic obfuscation. " +
                "This one creates a bootstrap method for each invokedynamic instruction";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            List<MethodNode> bsms = TransformerHelper.findMethodNodes(classNode, null, Invokedynamic2Transformer.BSM_DESC, false);

            for (MethodNode bsm : bsms) {
                if (!Modifier.isStatic(bsm.access)) {
                    continue;
                }

                boolean isBSM = true;

                isBSM = isBSM && (
                        TransformerHelper.containsInvokeVirtual(bsm, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")
                                || TransformerHelper.containsInvokeVirtual(bsm, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
                isBSM = isBSM && TransformerHelper.containsInvokeStatic(bsm, "java/lang/invoke/MethodHandles", "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;");
                isBSM = isBSM && TransformerHelper.countOccurencesOf(bsm, ANEWARRAY) > 0;
                isBSM = isBSM && TransformerHelper.countOccurencesOf(bsm, AASTORE) > 0;
                isBSM = isBSM && TransformerHelper.countOccurencesOf(bsm, AALOAD) > 0;

                if (isBSM) {
                    return "Found potential bootstrap method " + classNode.name + " " + bsm.name + bsm.desc;
                }
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(Invokedynamic2Transformer.class);
    }
}
