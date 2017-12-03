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

package com.javadeobfuscator.deobfuscator.matcher;

import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

public class ANewArrayStep implements Step {
    private final String type;
    private final boolean basic;

    public ANewArrayStep(String type, boolean basic) {
        this.type = type;
        this.basic = basic;
    }

    @Override
    public AbstractInsnNode tryMatch(InstructionMatcher matcher, AbstractInsnNode now) {
        if (now.getOpcode() != Opcodes.ANEWARRAY) {
            return null;
        }

        TypeInsnNode typeInsnNode = (TypeInsnNode) now;
        if (!(basic ? TransformerHelper.basicType(typeInsnNode.desc) : typeInsnNode.desc).equals(type)) {
            return null;
        }
        
        return now.getNext();
    }
}
