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

import com.javadeobfuscator.deobfuscator.transformers.MethodFinder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.*;

public class InvokedynamicMethodFinder implements MethodFinder {
    @Override
    public Map<ClassNode, Set<MethodNode>> find(Collection<ClassNode> classes) {
        Map<ClassNode, Set<MethodNode>> result = new HashMap<>();

        for (ClassNode classNode : classes) {
            Set<MethodNode> remove = new HashSet<>();
            for (MethodNode methodNode : classNode.methods) {
                if (Modifier.isPrivate(methodNode.access)
                        && Modifier.isStatic(methodNode.access)
                        && methodNode.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                        && methodNode.name.length() == 2) {
                    remove.add(methodNode);
                }
            }
            if (!remove.isEmpty()) {
                result.put(classNode, remove);
            }
        }

        return result;
    }
}
