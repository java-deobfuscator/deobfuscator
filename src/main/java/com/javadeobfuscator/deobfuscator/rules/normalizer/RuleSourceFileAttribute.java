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
import java.util.regex.Pattern;

public class RuleSourceFileAttribute implements Rule {
	private static final Pattern fileEndingPattern = Pattern.compile("\\.\\w{1,10}$");

    @Override
    public String getDescription() {
        return "Some obfuscators don't remove the SourceFile attribute by default. This information can be recovered, and is very useful";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            if (classNode.sourceFile == null || classNode.sourceFile.trim().isEmpty()) {
                continue;
            }

            if (classNode.sourceFile.equalsIgnoreCase("SourceFile")) {
                continue;
            }

            String sourceFile = classNode.sourceFile;
            if (sourceFile.endsWith(".java")) {
                sourceFile = sourceFile.substring(0, sourceFile.length() - ".java".length());
            } else if (sourceFile.endsWith(".kt")) {
                sourceFile = sourceFile.substring(0, sourceFile.length() - ".kt".length());
            } else if (!fileEndingPattern.matcher(sourceFile).find()) {
                // Without or with a too long or non-ascii file ending it is safe to assume that the sourceFile attribute
                // was modified by some obfuscator, don't suggest the transformer in such cases
                continue;
            }

            if (similar(sourceFile, TransformerHelper.getFullClassName(classNode.name))) {
                continue;
            }

			if (similar(sourceFile, TransformerHelper.getOuterClassName(classNode.name))) {
				continue;
			}

			if (similar(sourceFile, TransformerHelper.getMostOuterClassName(classNode.name))) {
				continue;
			}

            if (similar(sourceFile, TransformerHelper.getInnerClassName(classNode.name))) {
                continue;
            }

			if (similar(sourceFile, TransformerHelper.getInnerClassName(TransformerHelper.getOuterClassName(classNode.name)))) {
				continue;
			}

            return "Found possible SourceFile attribute on " + classNode.name + ": " + classNode.sourceFile;
        }

        return null;
    }

    private static boolean similar(String sourceFile, String simpleClassName) {
        if (sourceFile.equalsIgnoreCase(simpleClassName)) {
            return true;
        }
        if (simpleClassName.endsWith("Kt")) {
            simpleClassName = simpleClassName.substring(0, simpleClassName.length() - "Kt".length());
        }
        return sourceFile.equalsIgnoreCase(simpleClassName);
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(SourceFileClassNormalizer.class);
    }
}
