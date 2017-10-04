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

package com.javadeobfuscator.deobfuscator.transformers.normalizer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.commons.RemappingClassAdapter;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@TransformerConfig.ConfigOptions(configClass = AbstractClassNormalizer.Config.class)
public abstract class AbstractClassNormalizer<T extends AbstractClassNormalizer.Config> extends Transformer<T> {
    @Override
    public final boolean transform() throws Throwable {
        CustomRemapper remapper = new CustomRemapper();

        remap(remapper);

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

        removed.forEach(classes::remove);
        removed.forEach(classpath::remove);
        classes.putAll(updated);
        classpath.putAll(updated);
        getDeobfuscator().resetHierachy();
        getDeobfuscator().loadHierachy();
        return true;
    }

    public abstract void remap(CustomRemapper remapper);

    public static abstract class Config extends TransformerConfig {
        @JsonProperty(value = "mapping-file")
        private File mappingFile;

        public Config(Class<? extends Transformer> implementation) {
            super(implementation);
        }

        public File getMappingFile() {
            return mappingFile;
        }

        public void setMappingFile(File mappingFile) {
            this.mappingFile = mappingFile;
        }
    }
}
