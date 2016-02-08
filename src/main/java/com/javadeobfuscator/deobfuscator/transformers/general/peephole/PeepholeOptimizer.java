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

import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PeepholeOptimizer extends Transformer {
    private static final Set<Class<? extends Transformer>> PEEPHOLE_TRANSFORMERS = new HashSet<>();

    public PeepholeOptimizer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        for (Class<? extends Transformer> peepholeTransformerClass :
                PEEPHOLE_TRANSFORMERS) {
            peepholeTransformerClass.getConstructor(Map.class, Map.class).newInstance(classes, classpath).transform();
        }
    }

    static {
        PEEPHOLE_TRANSFORMERS.add(GotoRearranger.class);
        PEEPHOLE_TRANSFORMERS.add(ContinousGotoRemover.class);
        PEEPHOLE_TRANSFORMERS.add(UnconditionalSwitchRemover.class);
        PEEPHOLE_TRANSFORMERS.add(DeadCodeRemover.class);
        PEEPHOLE_TRANSFORMERS.add(NopRemover.class);
        PEEPHOLE_TRANSFORMERS.add(GotoUnconditionalJumpRemover.class);
        PEEPHOLE_TRANSFORMERS.add(RedundantTrapRemover.class);
        PEEPHOLE_TRANSFORMERS.add(LdcPopRemover.class);
        PEEPHOLE_TRANSFORMERS.add(LdcSwapInvokeSwapPopRemover.class);
    }
}
