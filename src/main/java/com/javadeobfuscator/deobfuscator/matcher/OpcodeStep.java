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

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OpcodeStep implements Step {
    private final List<Integer> wantOpcodes;
    private Function<AbstractInsnNode, Boolean> function;

    public OpcodeStep(Function<AbstractInsnNode, Boolean> function, int... opcodes) {
    	this.function = function;
        this.wantOpcodes = new ArrayList<>();
        for (int opcode : opcodes) {
            this.wantOpcodes.add(opcode);
        }
    }
    
    public OpcodeStep(int... opcodes) {
        this.wantOpcodes = new ArrayList<>();
        for (int opcode : opcodes) {
            this.wantOpcodes.add(opcode);
        }
    }

    @Override
    public AbstractInsnNode tryMatch(InstructionMatcher matcher, AbstractInsnNode now) {
        if (this.wantOpcodes.contains(now.getOpcode()) && (function == null || function.apply(now))) {
            return now.getNext();
        }
        return null;
    }

    @Override
    public String toString() {
        return "OpcodeStep{" +
                "wantOpcodes=" + wantOpcodes +
                '}';
    }
}
