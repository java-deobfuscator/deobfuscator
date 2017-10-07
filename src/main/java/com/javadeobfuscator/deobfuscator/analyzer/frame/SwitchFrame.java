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

import org.objectweb.asm.tree.LabelNode;

import java.util.ArrayList;
import java.util.List;

public class SwitchFrame extends Frame {
    private Frame switchTarget;
    private List<LabelNode> nodes = new ArrayList<>();

    public SwitchFrame(int opcode, Frame switchTarget, List<LabelNode> nodes, LabelNode dflt) {
        super(opcode);
        this.switchTarget = switchTarget;
        this.nodes.addAll(nodes);
        this.nodes.add(dflt);

        this.switchTarget.children.add(this);
    }

    public Frame getSwitchTarget() {
        return this.switchTarget;
    }
}
