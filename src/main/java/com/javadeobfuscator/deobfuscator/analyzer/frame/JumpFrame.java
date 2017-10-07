/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.analyzer.frame;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JumpFrame extends Frame {
    private List<Frame> comparators = new ArrayList<>();
    private transient List<AbstractInsnNode> targets; 

    public JumpFrame(int opcode, List<Frame> comparators, AbstractInsnNode... targets) {
        super(opcode);
        this.comparators.addAll(comparators);
        this.targets = Arrays.asList(targets);

        for (Frame comparator : this.comparators) {
            comparator.children.add(this);
        }
    }

    public List<Frame> getComparators() {
        return comparators;
    }

    public List<AbstractInsnNode> getTargets() {
        return targets;
    }
}
