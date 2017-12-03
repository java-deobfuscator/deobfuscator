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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class InvocationStep implements Step {
    private final int opcode;
    private final String owner;
    private final String name;
    private final String desc;
    private final boolean basic;

    public InvocationStep(int opcode, String owner, String name, String desc, boolean basic) {
        this.opcode = opcode;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        this.basic = basic;
    }

    @Override
    public AbstractInsnNode tryMatch(InstructionMatcher matcher, AbstractInsnNode now) {
        if (opcode != -1 && now.getOpcode() != opcode) {
            return null;
        }
        if (!(now instanceof MethodInsnNode)) {
            return null;
        }
        MethodInsnNode methodInsnNode = (MethodInsnNode) now;
        boolean ownerMatches = owner == null || methodInsnNode.owner.equals(owner);
        boolean nameMatches = name == null || methodInsnNode.name.equals(name);
        boolean descMatches = desc == null || (basic ? TransformerHelper.basicType(methodInsnNode.desc) : methodInsnNode.desc).equals(desc);
        if (!ownerMatches || !nameMatches || !descMatches) {
            return null;
        }
        return now.getNext();
    }
}
