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
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ResourceEncryptionClassFinder implements ClassFinder {
    @Override
    public Set<ClassNode> find(Collection<ClassNode> classes) {
        Set<ClassNode> results = new HashSet<>();
        for (ClassNode classNode : classes) {
            if (!classNode.superName.equals("java/io/FilterInputStream")) {
                continue;
            }
            FieldNode objArr = TransformerHelper.findFieldNode(classNode, null, "[Ljava/lang/Object;");
            if (objArr == null) {
                continue;
            }
            if (!Modifier.isStatic(objArr.access)) {
                continue;
            }
            FieldNode intArr = TransformerHelper.findFieldNode(classNode, null, "[I");
            if (intArr == null) {
                continue;
            }
            if (Modifier.isStatic(intArr.access)) {
                continue;
            }
            MethodNode ctor = TransformerHelper.findMethodNode(classNode, "<init>", "(Ljava/io/InputStream;)V");
            if (ctor == null) {
                continue;
            }
            results.add(classNode);
        }
        return results;
    }
}
