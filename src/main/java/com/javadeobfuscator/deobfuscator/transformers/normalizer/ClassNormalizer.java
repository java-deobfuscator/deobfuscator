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

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.commons.RemappingClassAdapter;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassNormalizer extends Transformer {
    public ClassNormalizer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        super(classes, classpath);
    }

    @Override
    public void transform() throws Throwable {
        CustomRemapper remapper = new CustomRemapper();
        AtomicInteger id = new AtomicInteger(0);
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            String packageName = classNode.name.lastIndexOf('/') == -1 ? "" : classNode.name.substring(0, classNode.name.lastIndexOf('/') + 1);
            while (true) {
                String newName = packageName + "Class" + id.getAndIncrement();
                if (remapper.map(classNode.name, newName)) {
                    break;
                }
            }
        });

        Map<String, WrappedClassNode> updated = new HashMap<>();
        Set<String> removed = new HashSet<>();

        classNodes().forEach(wr -> {
            ClassNode newNode = new ClassNode();
            RemappingClassAdapter remap = new RemappingClassAdapter(newNode, remapper);
            removed.add(wr.classNode.name);
            wr.classNode.accept(remap);
            wr.classNode = newNode;
            updated.put(newNode.name, wr);
        });

        classes.putAll(updated);
        classpath.putAll(updated);
        removed.forEach(classes::remove);
        removed.forEach(classpath::remove);
        deobfuscator.resetHierachy();
        deobfuscator.loadHierachy();
    }
}
