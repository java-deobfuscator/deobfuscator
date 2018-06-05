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

package com.javadeobfuscator.deobfuscator.graph;

import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GraphHelper {
    public static Map<String, ClassNode> validateUniqueClasses(Collection<ClassNode> nodes) {
        Map<String, ClassNode> map = new HashMap<>();
        for (ClassNode classNode : nodes) {
            if (map.put(classNode.name, classNode) != null) {
                throw new IllegalArgumentException("Duplicate class " + classNode.name);
            }
        }
        return map;
    }
}
