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
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class PackageNormalizer extends Transformer<PackageNormalizer.Config> {

    @Override
    public boolean transform() throws Throwable {
        CustomRemapper remapper = new CustomRemapper();
        AtomicInteger id = new AtomicInteger(0);
        classNodes().stream().map(WrappedClassNode::getClassNode).forEach(classNode -> {
            String packageName = classNode.name.lastIndexOf('/') == -1 ? "" : classNode.name.substring(0, classNode.name.lastIndexOf('/'));
            if (packageName.length() > 0) {
                int lin = -1;
                while ((lin = packageName.lastIndexOf('/')) != -1) {
                    String parentPackage = packageName.substring(0, lin);
                    if (!remapper.mapPackage(packageName, parentPackage + "/package" + id.getAndIncrement())) {
                        break;
                    }
                    packageName = parentPackage;
                }
                remapper.mapPackage(packageName, "package" + id.getAndIncrement());
            }
        });

        Map<String, WrappedClassNode> updated = new HashMap<>();
        Set<String> removed = new HashSet<>();

        classNodes().forEach(wr -> {
            String oldName = wr.classNode.name;
            String newName = remapper.map(oldName);
            if (!oldName.equals(newName)) {
                ClassNode newNode = new ClassNode();
                RemappingClassAdapter remap = new RemappingClassAdapter(newNode, remapper);
                removed.add(wr.classNode.name);
                wr.classNode.accept(remap);
                wr.classNode = newNode;
                updated.put(newNode.name, wr);
            }
        });

        classes.putAll(updated);
        classpath.putAll(updated);
        removed.forEach(classes::remove);
        removed.forEach(classpath::remove);
        getDeobfuscator().resetHierachy();
        getDeobfuscator().loadHierachy();
        return true;
    }

    public static class Config extends TransformerConfig {
        @JsonProperty(value = "mapping-file")
        private File mappingFile;

        public Config() {
            super(PackageNormalizer.class);
        }

        public File getMappingFile() {
            return mappingFile;
        }

        public void setMappingFile(File mappingFile) {
            this.mappingFile = mappingFile;
        }
    }
}
