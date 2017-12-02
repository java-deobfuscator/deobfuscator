/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils;

import com.javadeobfuscator.deobfuscator.transformers.ClassFinder;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.Constants.HIDE_ACCESS_DECRYPT_FIELD_SIG;
import static com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.Constants.HIDE_ACCESS_DECRYPT_METHOD_SIG;

public class HideAccessClassFinder implements ClassFinder {
    @Override
    public Set<ClassNode> find(Collection<ClassNode> classes) {
        Set<ClassNode> found = new HashSet<>();
        for (ClassNode classNode : classes) {
            if (!Modifier.isFinal(classNode.access)) {
                continue;
            }

            FieldNode array = TransformerHelper.findFieldNode(classNode, null, "[Ljava/lang/Object;");
            if (array == null) {
                continue;
            }
            if (!Modifier.isFinal(array.access) || !Modifier.isStatic(array.access)) {
                continue;
            }

            MethodNode getFieldMethod = TransformerHelper.findMethodNode(classNode, null, HIDE_ACCESS_DECRYPT_FIELD_SIG);
            if (getFieldMethod == null) {
                continue;
            }
            if (!Modifier.isStatic(getFieldMethod.access)) {
                continue;
            }

            MethodNode getMethodMethod = TransformerHelper.findMethodNode(classNode, null, HIDE_ACCESS_DECRYPT_METHOD_SIG);
            if (getMethodMethod == null) {
                continue;
            }
            if (!Modifier.isStatic(getMethodMethod.access)) {
                continue;
            }

            found.add(classNode);
        }

        return found;
    }
}
