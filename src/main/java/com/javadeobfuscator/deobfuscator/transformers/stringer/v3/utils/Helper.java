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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils;

import com.javadeobfuscator.deobfuscator.exceptions.WrongTransformerException;
import com.javadeobfuscator.deobfuscator.matcher.InstructionMatcher;
import com.javadeobfuscator.deobfuscator.matcher.InstructionPattern;
import org.objectweb.asm.tree.AbstractInsnNode;

public class Helper {
    public static InstructionMatcher findMatch(AbstractInsnNode start, InstructionPattern... patterns) {
        InstructionMatcher foundMatch = null;
        for (InstructionPattern pattern : patterns) {InstructionMatcher matcher = pattern.matcher(start);
            if (matcher.find()) {
                if (foundMatch != null) {
                    throw new WrongTransformerException("Only expected one decryptor, found at least two");
                }
                foundMatch = matcher;
            }
        }

        return foundMatch;
    }
}
