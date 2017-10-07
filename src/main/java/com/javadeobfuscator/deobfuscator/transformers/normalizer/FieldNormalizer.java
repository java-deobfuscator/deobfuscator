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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.commons.RemappingClassAdapter;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.FieldNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FieldNormalizer extends Transformer<FieldNormalizer.Config> {

    @Override
    public boolean transform() throws Throwable {
        CustomRemapper remapper = new CustomRemapper();
        AtomicInteger id = new AtomicInteger(0);
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> allClasses = new HashSet<>();
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTry = new LinkedList<>();
            toTry.add(tree.thisClass);
            while (!toTry.isEmpty()) {
                String t = toTry.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.parentClasses);
                    allClasses.addAll(ct.subClasses);
                    toTry.addAll(ct.parentClasses);
                    toTry.addAll(ct.subClasses);
                }
            }
            for (FieldNode fieldNode : classNode.fields) {
                List<String> references = new ArrayList<>();
                for (String possibleClass : allClasses) {
                    ClassNode otherNode = this.getDeobfuscator().assureLoaded(possibleClass);
                    boolean found = false;
                    for (FieldNode otherField : otherNode.fields) {
                        if (otherField.name.equals(fieldNode.name) && otherField.desc.equals(fieldNode.desc)) {
                            found = true;
                        }
                    }
                    if (!found) {
                        references.add(possibleClass);
                    }
                }
                if (!remapper.fieldMappingExists(classNode.name, fieldNode.name, fieldNode.desc)) {
                    while (true) {
                        String newName = "Field" + id.getAndIncrement();
                        if (remapper.mapFieldName(classNode.name, fieldNode.name, fieldNode.desc, newName, false)) {
                            for (String s : references) {
                                remapper.mapFieldName(s, fieldNode.name, fieldNode.desc, newName, true);
                            }
                            break;
                        }
                    }
                }
            }
        });

        classNodes().forEach(wr -> {
            ClassNode newNode = new ClassNode();
            RemappingClassAdapter remap = new RemappingClassAdapter(newNode, remapper);
            wr.classNode.accept(remap);
            wr.classNode = newNode;
        });
        return true;
    }

    public static class Config extends TransformerConfig {
        @JsonProperty(value = "mapping-file")
        private File mappingFile;

        public Config() {
            super(FieldNormalizer.class);
        }

        public File getMappingFile() {
            return mappingFile;
        }

        public void setMappingFile(File mappingFile) {
            this.mappingFile = mappingFile;
        }
    }
}
