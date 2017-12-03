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

package com.javadeobfuscator.deobfuscator.transformers;

import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public interface ClassFinder {
    Set<ClassNode> find(Collection<ClassNode> classes);

    default Set<String> findNames(Collection<ClassNode> classes) {
        return find(classes).stream().map(classNode -> classNode.name).collect(Collectors.toSet());
    }
}
