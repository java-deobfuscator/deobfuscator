package com.javadeobfuscator.deobfuscator.rules.special;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.special.BisGuardTransformer;
import org.objectweb.asm.tree.ClassNode;

public class RuleBisguardClassEncryption implements Rule {
    
    private static final Set<String> classNames = new HashSet<>();
    
    static {
        classNames.add("JavaPreloader".toLowerCase(Locale.ROOT));
        classNames.add("JavaPreloader$Cipher".toLowerCase(Locale.ROOT));
        classNames.add("com/bisguard/utils/Authenticator".toLowerCase(Locale.ROOT));
    }

    @Override
    public String getDescription() {
        return "Bisguard does encrypt classes and decrypts them on load";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        int count = 0;
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            if (classNames.contains(classNode.name.toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        if (count >= 2) {
            byte[] manifest = deobfuscator.getInputPassthrough().get("META-INF/MANIFEST.MF");
            if (manifest == null) {
                return null;
            }
            String[] lines = new String(manifest).split("\n");
            for (String line : lines) {
                if (line.startsWith("Subordinate-Class: ")) {
                    return "Found possible Bisguard class encryption files";
                }
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singleton(BisGuardTransformer.class);
    }
}
