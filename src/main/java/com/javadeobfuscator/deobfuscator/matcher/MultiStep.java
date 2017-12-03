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

public class MultiStep implements Step {
    private final Step[] steps;

    public MultiStep(Step... steps) {
        this.steps = steps;
    }

    @Override
    public AbstractInsnNode tryMatch(InstructionMatcher matcher, AbstractInsnNode now) {
        for (Step step : steps) {
            while (!Utils.isInstruction(now)) now = now.getNext();
            if (now == null) {
                return null;
            }

            AbstractInsnNode next = step.tryMatch(matcher, now);
            if (next == null) {
                return null;
            }
            now = next;
        }
        return now;
    }
}
