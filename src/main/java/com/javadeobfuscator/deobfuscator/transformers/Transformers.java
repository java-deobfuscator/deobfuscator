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
    }
}
