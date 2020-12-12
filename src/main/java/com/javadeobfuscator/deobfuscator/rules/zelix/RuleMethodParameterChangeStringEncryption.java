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
import com.javadeobfuscator.deobfuscator.transformers.zelix.v9.utils.*;
import com.javadeobfuscator.deobfuscator.utils.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.lang.reflect.*;
import java.util.*;

public class RuleMethodParameterChangeStringEncryption implements Rule, Opcodes {
    @Override
    public String getDescription() {
        return "Zelix Klassmaster has several modes of string encryption. " +
                "This mode is currently not supported. In this mode, a magic number is passed through method calls " +
                "in order to make deobfuscation more difficult. It can be identified by an additional int parameter in method calls" +
                "and a call to (III)Ljava/lang/String;, where the first two numbers are constant, and the third is the magic number. " +
                "Newer versions of ZKM may use the DES cipher and call (IJ)Ljava/lang/String; instead";
    }

    @Override
    public String test(Deobfuscator deobfuscator) {
        if (new RuleSuspiciousClinit().test(deobfuscator) == null) {
            return null;
        }

        boolean containsLookup = new MethodParameterChangeClassFinder().find(deobfuscator.getClasses().values()).isEmpty();

        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            MethodNode enhanced = TransformerHelper.findMethodNode(classNode, null, "(III)Ljava/lang/String;");
            if (enhanced == null || enhanced.instructions == null) {
                continue;
            }
            if (!Modifier.isStatic(enhanced.access)) {
                continue;
            }

            boolean isMPC = true;

            isMPC = isMPC && TransformerHelper.containsInvokeVirtual(enhanced, "java/lang/String", "intern", "()Ljava/lang/String;");
            isMPC = isMPC && TransformerHelper.containsInvokeVirtual(enhanced, "java/lang/String", "toCharArray", "()[C");
            isMPC = isMPC && TransformerHelper.countOccurencesOf(enhanced, AALOAD) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(enhanced, AASTORE) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(enhanced, TABLESWITCH) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(enhanced, IXOR) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(enhanced, IREM) > 0;

            if (isMPC) {
                return "Found potential method parameter changed string encrypted class " + classNode.name + " without DES cipher"
                	+ (containsLookup ? " (lookup found)" : " (lookup not found)");
            }
        }
        
        for (ClassNode classNode : deobfuscator.getClasses().values()) {
            MethodNode des = TransformerHelper.findMethodNode(classNode, null, "(IJ)Ljava/lang/String;");
            if (des == null) {
                continue;
            }
            if (!Modifier.isStatic(des.access)) {
                continue;
            }

            boolean isMPC = true;

            isMPC = isMPC && TransformerHelper.containsInvokeVirtual(des, "javax/crypto/SecretKeyFactory", "generateSecret", "(Ljava/security/spec/KeySpec;)Ljavax/crypto/SecretKey;");
            isMPC = isMPC && TransformerHelper.containsInvokeVirtual(des, "javax/crypto/Cipher", "doFinal", "([B)[B");
            isMPC = isMPC && TransformerHelper.countOccurencesOf(des, AALOAD) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(des, AASTORE) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(des, IXOR) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(des, LSHL) > 0;
            isMPC = isMPC && TransformerHelper.countOccurencesOf(des, LUSHR) > 0;

            if (isMPC) {
                return "Found potential method parameter changed string encrypted class " + classNode.name + " using DES cipher"
                	+ (containsLookup ? " (lookup found)" : " (lookup not found)");
            }
        }

        return null;
    }

    @Override
    public Collection<Class<? extends Transformer<?>>> getRecommendTransformers() {
        return null;
    }
}
