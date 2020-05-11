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

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.classguard.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public class RuleEncryptedClass implements Rule {
    @Override
    public String getDescription() {
        return "ClassGuard encrypted classes have their extensions changed to .classx";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        for (Map.Entry<String, byte[]> name : deobfuscator.getInputPassthrough().entrySet()) {
            if (name.getKey().endsWith(".classx")) {
                return "Found file " + name.getKey();
            }
        }
        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(EncryptionTransformer.class);
    }
}
