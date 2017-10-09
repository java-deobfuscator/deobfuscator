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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class IllegalVarargsRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        classNodes().forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                Type[] args = Type.getArgumentTypes(methodNode.desc);
                if (args.length > 0 && args[args.length - 1].getSort() != Type.ARRAY) {
                    methodNode.access &= ~Opcodes.ACC_VARARGS;
                }
            });
        });
        return true;
    }
}
