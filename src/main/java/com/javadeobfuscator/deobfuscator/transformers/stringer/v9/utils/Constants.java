/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v9.utils;

import com.javadeobfuscator.deobfuscator.matcher.InstructionPattern;
import com.javadeobfuscator.deobfuscator.matcher.InvocationStep;
import com.javadeobfuscator.deobfuscator.matcher.LoadIntStep;
import com.javadeobfuscator.deobfuscator.matcher.OpcodeStep;
import org.objectweb.asm.Opcodes;

public class Constants implements Opcodes {
    public static final InstructionPattern DECRYPT_PATTERN = new InstructionPattern(
            new OpcodeStep(LDC),
            new InvocationStep(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false),
            new OpcodeStep(DUP),
            new OpcodeStep(DUP),
            new LoadIntStep(),
            new OpcodeStep(DUP_X1),
            new OpcodeStep(CALOAD),
            new LoadIntStep(),
            new OpcodeStep(IXOR),
            new OpcodeStep(I2C),
            new OpcodeStep(CASTORE),
            new LoadIntStep(),
            new LoadIntStep(),
            new OpcodeStep(ISHL),
            new LoadIntStep(),
            new OpcodeStep(IOR),
            new LoadIntStep(),
            new LoadIntStep(),
            new InvocationStep(INVOKESTATIC, null, null, "(Ljava/lang/Object;III)Ljava/lang/Object;", true)
    );
}
