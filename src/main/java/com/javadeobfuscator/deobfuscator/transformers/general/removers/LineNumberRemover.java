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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

import java.util.Iterator;

public class LineNumberRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                Iterator<AbstractInsnNode> it = methodNode.instructions.iterator();
                while (it.hasNext()) {
                    if (it.next() instanceof LineNumberNode) {
                        it.remove();
                    }
                }
            });
        });

        return true;
    }
}
