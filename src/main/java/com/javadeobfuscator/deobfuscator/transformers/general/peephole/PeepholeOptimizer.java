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

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;

import java.util.*;

public class PeepholeOptimizer extends Transformer<TransformerConfig> {
    private static final Set<Class<? extends Transformer<?>>> PEEPHOLE_TRANSFORMERS = new LinkedHashSet<>();

    @Override
    public boolean transform() throws Throwable {
        boolean actuallyMadeModifications = false;
        boolean madeModifications;
        do {
            madeModifications = false;
            for (Class<? extends Transformer<?>> peepholeTransformerClass : PEEPHOLE_TRANSFORMERS) {
                // todo the set should have the config
                TransformerConfig config = TransformerConfig.configFor(peepholeTransformerClass);
                if (getDeobfuscator().runFromConfig(config)) {
                    madeModifications = true;
                }
            }
            actuallyMadeModifications = actuallyMadeModifications || madeModifications;
        } while (madeModifications);

        return actuallyMadeModifications;
    }

    static {
        PEEPHOLE_TRANSFORMERS.add(NopRemover.class);
        PEEPHOLE_TRANSFORMERS.add(RedundantTrapRemover.class);
//        PEEPHOLE_TRANSFORMERS.add(TrapHandlerMerger.class); // still experimental
        PEEPHOLE_TRANSFORMERS.add(GotoRearranger.class);
        PEEPHOLE_TRANSFORMERS.add(DeadCodeRemover.class);
        PEEPHOLE_TRANSFORMERS.add(LdcSwapInvokeSwapPopRemover.class);
        PEEPHOLE_TRANSFORMERS.add(ConstantFolder.class);
    }

}
