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

package com.javadeobfuscator.deobfuscator.rules.normalizer;

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.normalizer.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public class RuleSourceFileAttribute implements Rule {
    @Override
    public String getDescription() {
        return "Some obfuscators don't remove the SourceFile attribute by default. This information can be recovered, and is very useful";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            if (classNode.sourceFile == null) {
                continue;
            }

            if (classNode.sourceFile.equals("SourceFile")) {
                continue;
            }

            String sourceFile = classNode.sourceFile;
            if (sourceFile.endsWith(".java")) {
                sourceFile = sourceFile.substring(0, sourceFile.length() - 5);
            }

            if (sourceFile.equals(TransformerHelper.getFullClassName(classNode.name))) {
                continue;
            }

            if (sourceFile.equals(TransformerHelper.getOuterClassName(classNode.name))) {
                continue;
            }

            if (sourceFile.equals(TransformerHelper.getInnerClassName(classNode.name))) {
                continue;
            }

            return "Found possible SourceFile attribute on " + classNode.name + ": " + classNode.sourceFile;
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(SourceFileClassNormalizer.class);
    }
}
