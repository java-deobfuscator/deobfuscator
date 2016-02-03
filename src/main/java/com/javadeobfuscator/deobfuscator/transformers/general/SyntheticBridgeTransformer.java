/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.general;

import java.util.Map;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class SyntheticBridgeTransformer extends Transformer {
    public SyntheticBridgeTransformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            classNode.methods.forEach(methodNode -> {
                methodNode.access &= ~Opcodes.ACC_SYNTHETIC;
                methodNode.access &= ~Opcodes.ACC_BRIDGE;
            });
            classNode.fields.forEach(fieldNode -> {
                fieldNode.access &= ~Opcodes.ACC_SYNTHETIC;
                fieldNode.access &= ~Opcodes.ACC_BRIDGE;
            });
        });
    }
}
