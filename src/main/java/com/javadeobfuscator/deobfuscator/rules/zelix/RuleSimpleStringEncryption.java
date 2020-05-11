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

package com.javadeobfuscator.deobfuscator.rules.zelix;

import com.javadeobfuscator.deobfuscator.*;
import com.javadeobfuscator.deobfuscator.rules.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.transformers.zelix.string.*;

import java.util.*;

public class RuleSimpleStringEncryption implements Rule {
    @Override
    public String getDescription() {
        return "Zelix Klassmaster has several modes of string encryption. " +
                "This mode replaces string literals with a static string or string array, which are decrypted in <clinit> " +
                "Note that this mode does not generate a method with signature (II)Ljava/lang/String;";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        if (new RuleEnhancedStringEncryption().test(deobfuscator) != null)
            return null;
        if (new RuleMethodParameterChangeStringEncryption().test(deobfuscator) != null)
            return null;

        return new RuleSuspiciousClinit().test(deobfuscator); // lol
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return Collections.singletonList(SimpleStringEncryptionTransformer.class);
    }
}
