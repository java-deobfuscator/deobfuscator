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

package com.javadeobfuscator.deobfuscator.rules.classguard;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.rules.Rule;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.classguard.EncryptionTransformer;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.Collections;

public class RuleClassGuardPackage implements Rule {
    @Override
    public String getDescription() {
        return "ClassGuard bundles classes within the com/zenofx/classguard package";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            if (classNode.name.startsWith("com/zenofx/")) {
                return "Found classnode " + classNode.name;
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer>> getRecommendTransformers() {
        return Collections.singletonList(EncryptionTransformer.class);
    }
}
