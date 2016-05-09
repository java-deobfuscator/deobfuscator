/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers;

public class Transformers {
    public static class Allatori {
        public static final Class<? extends Transformer> STRING_ENCRYPTION = com.javadeobfuscator.deobfuscator.transformers.allatori.StringEncryptionTransformer.class;
    }

    public static class DashO {
        public static final Class<? extends Transformer> STRING_ENCRYPTION = com.javadeobfuscator.deobfuscator.transformers.dasho.StringEncryptionTransformer.class;
    }

    public static class Stringer {
        public static final Class<? extends Transformer> STRING_ENCRYPTION = com.javadeobfuscator.deobfuscator.transformers.stringer.StringEncryptionTransformer.class;
        public static final Class<? extends Transformer> INVOKEDYNAMIC = com.javadeobfuscator.deobfuscator.transformers.stringer.InvokedynamicTransformer.class;
        public static final Class<? extends Transformer> REFLECTION_OBFUSCATION = com.javadeobfuscator.deobfuscator.transformers.stringer.ReflectionObfuscationTransformer.class;
    }
    
    public static class General {
        public static final Class<? extends Transformer> SYNTHETIC_BRIDGE = com.javadeobfuscator.deobfuscator.transformers.general.SyntheticBridgeTransformer.class;
        public static final Class<? extends Transformer> PEEPHOLE_OPTIMIZER = com.javadeobfuscator.deobfuscator.transformers.general.peephole.PeepholeOptimizer.class;
        public static final Class<? extends Transformer> LINE_NUMBER = com.javadeobfuscator.deobfuscator.transformers.general.LineNumberRemover.class;
    }

    public static class Zelix {
        public static final Class<? extends Transformer> STRING_ENCRYPTION = com.javadeobfuscator.deobfuscator.transformers.zelix.StringEncryptionTransformer.class;
        public static final Class<? extends Transformer> REFLECTION_OBFUSCATION = com.javadeobfuscator.deobfuscator.transformers.zelix.ReflectionObfuscationTransformer.class;
    }

    public static class Normalizer {
        public static final Class<? extends Transformer> CLASS_NORMALIZER = com.javadeobfuscator.deobfuscator.transformers.normalizer.ClassNormalizer.class;
        public static final Class<? extends Transformer> FIELD_NORMALIZER = com.javadeobfuscator.deobfuscator.transformers.normalizer.FieldNormalizer.class;
        public static final Class<? extends Transformer> METHOD_NORMALIZER = com.javadeobfuscator.deobfuscator.transformers.normalizer.MethodNormalizer.class;
    }
}
