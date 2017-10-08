/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
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

package com.javadeobfuscator.deobfuscator.transformers.general.removers;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import org.objectweb.asm.util.CheckClassAdapter;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class IllegalSignatureRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        classNodes().forEach(classNode -> {
            if (classNode.signature != null) {
                try {
                    CheckClassAdapter.checkClassSignature(classNode.signature);
                } catch (IllegalArgumentException ignored) {
                    classNode.signature = null;
                }
            }
            classNode.methods.forEach(methodNode -> {
                if (methodNode.signature != null) {
                    try {
                        CheckClassAdapter.checkMethodSignature(methodNode.signature);
                    } catch (IllegalArgumentException ignored) {
                        methodNode.signature = null;
                    }
                }
            });
            classNode.fields.forEach(fieldNode -> {
                if (fieldNode.signature != null) {
                    try {
                        CheckClassAdapter.checkFieldSignature(fieldNode.signature);
                    } catch (IllegalArgumentException ignored) {
                        fieldNode.signature = null;
                    }
                }
            });
        });
        return true;
    }
}
