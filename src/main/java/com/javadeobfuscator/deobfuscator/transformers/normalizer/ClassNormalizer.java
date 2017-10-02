/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassNormalizer extends AbstractClassNormalizer {
    public ClassNormalizer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void remap(CustomRemapper remapper) {
        AtomicInteger id = new AtomicInteger(0);
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            String packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/'));

            String newName = packageName + "/" + "Class";
            String mappedName;
            do {
                mappedName = newName + id.getAndIncrement();
            } while (!remapper.map(classNode.name, mappedName));
        });
    }
}
