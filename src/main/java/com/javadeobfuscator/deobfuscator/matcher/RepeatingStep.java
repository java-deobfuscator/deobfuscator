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

public class RepeatingStep implements Step {

    private final Step step;
    private final int min;
    private final int max;

    public RepeatingStep(Step step, int min, int max) {
        this.step = step;
        this.min = min;
        this.max = max;
    }

    @Override
    public AbstractInsnNode tryMatch(InstructionMatcher matcher, AbstractInsnNode now) {
        if (max == -1) {
            int amount = 0;
            AbstractInsnNode next;
            while (true) {
                next = step.tryMatch(matcher, now);
                if (next == null) {
                    break;
                } else {
                    now = next;
                }
                amount++;
            }
            return (min == -1 || amount >= min) ? now : null;
        } else {
            AbstractInsnNode next;
            for (int i = 0; i < max; i++) {
                next = step.tryMatch(matcher, now);
                if (next == null) {
                    if (min != -1 && i >= min) return now;
                    return null;
                } else {
                    now = next;
                }
            }
            return now;
        }
    }
}
