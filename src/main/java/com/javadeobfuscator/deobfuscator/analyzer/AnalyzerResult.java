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

package com.javadeobfuscator.deobfuscator.analyzer;

import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyzerResult {
    public static final AnalyzerResult EMPTY_RESULT;

    static {
        EMPTY_RESULT = new AnalyzerResult();
        EMPTY_RESULT.frames = Collections.emptyMap();
    }

    protected Map<AbstractInsnNode, List<Frame>> frames;
    protected Map<Frame, AbstractInsnNode> mapping;
    private Map<Frame, AbstractInsnNode> reverse;
    protected int maxLocals;
    protected int maxStack;

    public Map<AbstractInsnNode, List<Frame>> getFrames() {
        return frames;
    }

    public AbstractInsnNode getInsnNode(Frame frame) {
        if (reverse == null) {
            reverse = new HashMap<>();
            frames.forEach((key, value) -> value.forEach(f -> reverse.put(f, key)));
        }

        return reverse.get(frame);
    }

    public Map<Frame, AbstractInsnNode> getMapping() {
        if (mapping == null) {
            Map<Frame, AbstractInsnNode> reverseMapping = new HashMap<>();
            frames.entrySet().forEach(ent -> ent.getValue().forEach(frame -> reverseMapping.put(frame, ent.getKey())));
            mapping = reverseMapping;
        }
        return mapping;
    }

    public int getMaxLocals() {
        return maxLocals;
    }
}
