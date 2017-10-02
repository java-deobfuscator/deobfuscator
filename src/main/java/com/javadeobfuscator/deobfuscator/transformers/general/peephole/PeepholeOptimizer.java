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

package com.javadeobfuscator.deobfuscator.transformers.general.peephole;

import com.javadeobfuscator.deobfuscator.analyzer.MethodAnalyzer;
import com.javadeobfuscator.deobfuscator.analyzer.frame.Frame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.MathFrame;
import com.javadeobfuscator.deobfuscator.analyzer.frame.SwitchFrame;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.JumpInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.TableSwitchInsnNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PeepholeOptimizer extends Transformer {
    private static final Set<Class<? extends Transformer>> PEEPHOLE_TRANSFORMERS = new LinkedHashSet<>();

    public PeepholeOptimizer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public boolean transform() throws Throwable {
        boolean actuallyMadeModifications = false;
        boolean madeModifications;
        do {
            madeModifications = false;
            for (Class<? extends Transformer> peepholeTransformerClass :
                    PEEPHOLE_TRANSFORMERS) {
                Transformer transformer = peepholeTransformerClass.getConstructor(Map.class, Map.class).newInstance(classes, classpath);
                transformer.setDeobfuscator(deobfuscator);
                if (transformer.transform()) {
                    madeModifications = true;
                }
            }
            actuallyMadeModifications = actuallyMadeModifications || madeModifications;
        } while (madeModifications);

        return actuallyMadeModifications;
    }

    static {
        PEEPHOLE_TRANSFORMERS.add(RedundantTrapRemover.class);
//        PEEPHOLE_TRANSFORMERS.add(TrapHandlerMerger.class); // still experimental
        PEEPHOLE_TRANSFORMERS.add(GotoRearranger.class);
        PEEPHOLE_TRANSFORMERS.add(DeadCodeRemover.class);
        PEEPHOLE_TRANSFORMERS.add(LdcSwapInvokeSwapPopRemover.class);
        PEEPHOLE_TRANSFORMERS.add(ConstantFolder.class);
    }

}
