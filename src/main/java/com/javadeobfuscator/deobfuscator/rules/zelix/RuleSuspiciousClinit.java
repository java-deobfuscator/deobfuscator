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

public class RuleSuspiciousClinit implements Rule, Opcodes {
    @Override
    public String getDescription() {
        return "Zelix Klassmaster typically embeds decryption code in <clinit>. This sample may have been obfuscated with Zelix Klassmaster";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            MethodNode clinit = TransformerHelper.findClinit(classNode);
            if (clinit == null) {
                continue;
            }

            boolean isZKM = true;

            isZKM = isZKM && TransformerHelper.containsInvokeVirtual(clinit, "java/lang/String", "intern", "()Ljava/lang/String;");
            isZKM = isZKM && TransformerHelper.containsInvokeVirtual(clinit, "java/lang/String", "toCharArray", "()[C");
            isZKM = isZKM && TransformerHelper.countOccurencesOf(clinit, TABLESWITCH) > 0;
            isZKM = isZKM && TransformerHelper.countOccurencesOf(clinit, IXOR) > 0;
            isZKM = isZKM && TransformerHelper.countOccurencesOf(clinit, IREM) > 0;

            if (isZKM) {
                return "Found suspicious <clinit> in " + classNode.name;
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return null;
    }
}
