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

package com.javadeobfuscator.deobfuscator.rules;

import com.google.common.collect.*;
import com.javadeobfuscator.deobfuscator.rules.classguard.*;
import com.javadeobfuscator.deobfuscator.rules.generic.*;
import com.javadeobfuscator.deobfuscator.rules.normalizer.*;
import com.javadeobfuscator.deobfuscator.rules.stringer.*;
import com.javadeobfuscator.deobfuscator.rules.zelix.*;

import java.util.*;

public class Rules {
    public static final List<Rule> RULES = ImmutableList.of(
            // ClassGuard
            new RuleClassGuardPackage(),
            new RuleEncryptedClass(),

            // Generic
            new RuleIllegalSignature(),

            // Normalizer
            new RuleSourceFileAttribute(),

            // Stringer
            new RuleStringDecryptor(),
            new RuleStringDecryptorWithThread(),
            new RuleInvokedynamic1(),
            new RuleInvokedynamic2(),
            new RuleStringDecryptorV3(),

            // Zelix
            new RuleSuspiciousClinit(),
            new RuleReflectionDecryptor(),
            new RuleSimpleStringEncryption(),
            new RuleEnhancedStringEncryption(),
            new RuleMethodParameterChangeStringEncryption(),

            // Dash-O
            new com.javadeobfuscator.deobfuscator.rules.dasho.RuleStringDecryptor()
    );
}
