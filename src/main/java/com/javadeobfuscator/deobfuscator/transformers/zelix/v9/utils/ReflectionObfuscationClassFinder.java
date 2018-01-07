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

package com.javadeobfuscator.deobfuscator.transformers.zelix.v9.utils;

import com.javadeobfuscator.deobfuscator.transformers.ClassFinder;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ReflectionObfuscationClassFinder implements ClassFinder {
    @Override
    public Set<ClassNode> find(Collection<ClassNode> classes) {
        Set<ClassNode> result = new HashSet<>();

        for (ClassNode classNode : classes) {
            if (classNode.fields.size() != 2) {
                continue;
            }
            FieldNode objArrField = TransformerHelper.findFieldNode(classNode, null, "[Ljava/lang/Object;");
            if (objArrField == null) {
                continue;
            }
            if (!Modifier.isStatic(objArrField.access) || !Modifier.isFinal(objArrField.access)) {
                continue;
            }
            FieldNode strArrField = TransformerHelper.findFieldNode(classNode, null, "[Ljava/lang/String;");
            if (strArrField == null) {
                continue;
            }
            if (!Modifier.isStatic(strArrField.access) || !Modifier.isFinal(strArrField.access)) {
                continue;
            }

            result.add(classNode);
        }

        return result;
    }
}
