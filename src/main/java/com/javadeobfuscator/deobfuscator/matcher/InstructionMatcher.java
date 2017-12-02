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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InstructionMatcher {
    public InstructionPattern getPattern() {
        return pattern;
    }

    private final InstructionPattern pattern;
    private final AbstractInsnNode start;
    private AbstractInsnNode end;

    public InstructionMatcher(InstructionPattern pattern, AbstractInsnNode start) {
        this.pattern = pattern;
        this.start = start;
    }

    public boolean find() {
        Step mainStep = new CapturingStep(new MultiStep(pattern.getSteps()), "all");
        end = mainStep.tryMatch(this, start);
        if (end != null) {
            end = Utils.getPrevious(end); // We want this inclusive
            if (end == null) {
                throw new RuntimeException("what?");
            }
            return true;
        }
        return false;
    }

    public AbstractInsnNode getStart() {
        return start;
    }

    public AbstractInsnNode getEnd() {
        return end;
    }

    private Map<String, List<List<AbstractInsnNode>>> capturedInsns = new HashMap<>();

    public void capture(String id, List<AbstractInsnNode> captured) {
        capturedInsns.computeIfAbsent(id, k -> new ArrayList<>()).add(captured);
    }

    public List<List<AbstractInsnNode>> getAllCapturedInstructions(String id) {
        return capturedInsns.get(id);
    }

    public List<AbstractInsnNode> getCapturedInstructions(String id) {
        List<List<AbstractInsnNode>> captured = capturedInsns.get(id);
        if (captured == null || captured.size() > 1) {
            return null;
        }
        return captured.get(0);
    }

    public AbstractInsnNode getCapturedInstruction(String id) {
        List<AbstractInsnNode> captured = getCapturedInstructions(id);
        if (captured == null || captured.size() > 1) {
            return null;
        }
        return captured.get(0);
    }
}
