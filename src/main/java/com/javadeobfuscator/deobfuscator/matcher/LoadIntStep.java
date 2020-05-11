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

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.LdcInsnNode;

public class LoadIntStep extends OpcodeStep {
    public LoadIntStep() {
        super((a) -> 
        {
        	if(!(a instanceof LdcInsnNode))
        		return true;
        	return ((LdcInsnNode)a).cst instanceof Integer;
        },Opcodes.ICONST_M1,
                Opcodes.ICONST_0,
                Opcodes.ICONST_1,
                Opcodes.ICONST_2,
                Opcodes.ICONST_3,
                Opcodes.ICONST_4,
                Opcodes.ICONST_5,
                Opcodes.LDC,
                Opcodes.BIPUSH,
                Opcodes.SIPUSH
        );
    }
}
