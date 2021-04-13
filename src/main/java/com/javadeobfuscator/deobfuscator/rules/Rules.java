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

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.javadeobfuscator.deobfuscator.rules.antireleak.RuleInvokeDynamic;
import com.javadeobfuscator.deobfuscator.rules.classguard.RuleClassGuardPackage;
import com.javadeobfuscator.deobfuscator.rules.classguard.RuleEncryptedClass;
import com.javadeobfuscator.deobfuscator.rules.generic.RuleIllegalSignature;
import com.javadeobfuscator.deobfuscator.rules.normalizer.RuleSourceFileAttribute;
import com.javadeobfuscator.deobfuscator.rules.skidsuite2.RuleFakeException;
import com.javadeobfuscator.deobfuscator.rules.smoke.RuleNumberObfuscation;
import com.javadeobfuscator.deobfuscator.rules.special.RuleBisguardClassEncryption;
import com.javadeobfuscator.deobfuscator.rules.special.RuleSuperblaubeereObfuscation;
import com.javadeobfuscator.deobfuscator.rules.stringer.RuleHideAccess;
import com.javadeobfuscator.deobfuscator.rules.stringer.RuleInvokedynamic1;
import com.javadeobfuscator.deobfuscator.rules.stringer.RuleInvokedynamic2;
import com.javadeobfuscator.deobfuscator.rules.stringer.RuleStringDecryptor;
import com.javadeobfuscator.deobfuscator.rules.stringer.RuleStringDecryptorV3;
import com.javadeobfuscator.deobfuscator.rules.stringer.RuleStringDecryptorWithThread;
import com.javadeobfuscator.deobfuscator.rules.zelix.RuleEnhancedStringEncryption;
import com.javadeobfuscator.deobfuscator.rules.zelix.RuleMethodParameterChangeStringEncryption;
import com.javadeobfuscator.deobfuscator.rules.zelix.RuleReflectionDecryptor;
import com.javadeobfuscator.deobfuscator.rules.zelix.RuleSimpleStringEncryption;
import com.javadeobfuscator.deobfuscator.rules.zelix.RuleSuspiciousClinit;

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
            new RuleStringDecryptorV3(),
            new RuleHideAccess(),
            new RuleInvokedynamic1(),
            new RuleInvokedynamic2(),

            // Zelix
            new RuleSuspiciousClinit(),
            new RuleReflectionDecryptor(),
            new RuleSimpleStringEncryption(),
            new RuleEnhancedStringEncryption(),
            new RuleMethodParameterChangeStringEncryption(),

            // Dash-O
            new com.javadeobfuscator.deobfuscator.rules.dasho.RuleStringDecryptor(),
            new com.javadeobfuscator.deobfuscator.rules.dasho.RuleFlowObfuscation(),

            // Allatori
            new com.javadeobfuscator.deobfuscator.rules.allatori.RuleStringDecryptor(),

            // AntiReleak
            new com.javadeobfuscator.deobfuscator.rules.antireleak.RuleStringDecryptor(),
            new RuleInvokeDynamic(),

            // SkidSuite2
            new RuleFakeException(),
            new com.javadeobfuscator.deobfuscator.rules.skidsuite2.RuleStringDecryptor(),

            // Smoke
            new com.javadeobfuscator.deobfuscator.rules.smoke.RuleStringDecryptor(),
            new RuleNumberObfuscation(),

            // Special
            new RuleBisguardClassEncryption(),
            new RuleSuperblaubeereObfuscation()
    );
}
