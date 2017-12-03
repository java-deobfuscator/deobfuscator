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

import com.javadeobfuscator.deobfuscator.utils.Utils;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.ArrayList;
import java.util.List;

public class CapturingStep implements Step {
    private final Step capture;
    private final String id;

    public CapturingStep(Step step, String id) {
        this.capture = step;
        this.id = id;
    }

    @Override
    public AbstractInsnNode tryMatch(InstructionMatcher matcher, AbstractInsnNode now) {
        AbstractInsnNode start = now;
        final AbstractInsnNode end = capture.tryMatch(matcher, now);
        if (end == null) {
            return null;
        }
        List<AbstractInsnNode> captured = new ArrayList<>();
        for (; start != end; start = start.getNext()) {
            if (Utils.isInstruction(start))
                captured.add(start);
        }
        matcher.capture(id, captured);
        return end;
    }
}
