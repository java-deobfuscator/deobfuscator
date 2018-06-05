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
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import org.objectweb.asm.Opcodes;

public class SyntheticBridgeRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
        classNodes().forEach(classNode -> {
            classNode.access &= ~(Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE);
            classNode.methods.forEach(methodNode -> {
                methodNode.access &= ~(Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE);
            });
            classNode.fields.forEach(fieldNode -> {
                fieldNode.access &= ~(Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE);
            });
        });
        return true;
    }
}
