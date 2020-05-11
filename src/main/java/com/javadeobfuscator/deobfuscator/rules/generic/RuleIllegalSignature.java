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

package com.javadeobfuscator.deobfuscator.rules.generic;

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.general.removers.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

import java.util.*;

public class RuleIllegalSignature implements Rule {
    @Override
    public String getDescription() {
        return "Some obfuscators will set the signature of a class/method/field to an illegal value. This may crash naive decompilers/disassemblers";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            if (classNode.signature != null) {
                try {
                    CheckClassAdapter.checkClassSignature(classNode.signature);
                } catch (IllegalArgumentException ignored) {
                    return "Found illegal class signature for " + classNode.name;
                }
            }
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.signature != null) {
                    try {
                        CheckClassAdapter.checkMethodSignature(methodNode.signature);
                    } catch (IllegalArgumentException ignored) {
                        return "Found illegal method signature for " + classNode.name + " " + methodNode.name + methodNode.desc;
                    }
                }
            }
            for (FieldNode fieldNode : classNode.fields) {
                if (fieldNode.signature != null) {
                    try {
                        CheckClassAdapter.checkFieldSignature(fieldNode.signature);
                    } catch (IllegalArgumentException ignored) {
                        return "Found illegal field signature for " + classNode.name + " " + fieldNode.name + fieldNode.desc;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(IllegalSignatureRemover.class);
    }
}
