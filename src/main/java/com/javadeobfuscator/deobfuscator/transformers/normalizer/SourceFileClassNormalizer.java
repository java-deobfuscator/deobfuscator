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

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

import java.util.concurrent.atomic.AtomicInteger;

// todo maybe make a "recover identifiers from source" transformer? the InnerClasses attribute has more useful info
@TransformerConfig.ConfigOptions(configClass = SourceFileClassNormalizer.Config.class)
public class SourceFileClassNormalizer extends AbstractNormalizer<SourceFileClassNormalizer.Config> {

    @Override
    public void remap(CustomRemapper remapper) {
        AtomicInteger counter = new AtomicInteger();

        classNodes().forEach(classNode -> {
            if (classNode.sourceFile == null) {
                return;
            }

            // todo handle inner classes gracefully (can we give them numerical ids? don't forget about nested inner classes)
            String packageName = classNode.name.contains("/") ? classNode.name.substring(0, classNode.name.lastIndexOf('/')) : "";

            String sourceFileName = classNode.sourceFile;
            if (classNode.sourceFile.endsWith(".java")) {
                sourceFileName = sourceFileName.substring(0, sourceFileName.lastIndexOf("."));
            }

            String innerClasses = "";
            if (classNode.name.contains("$")) {
                // note that there may be nested inner classes (com/package/Outer$Inner$NestedInner)
                innerClasses = classNode.name.substring(classNode.name.indexOf("$") + 1);
            }

            String reconstructedName = (packageName.isEmpty() ? "" : packageName + "/") + sourceFileName + (innerClasses.isEmpty() ? "" : "$" + innerClasses);

            int id = 0;
            String mappedName = reconstructedName;
            while (!remapper.map(classNode.name, mappedName)) {
                mappedName = reconstructedName + ++id;
            }

            counter.incrementAndGet();
        });

        System.out.println("[SourceFileClassNormalizer] Recovered " + counter + " source filenames");
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(SourceFileClassNormalizer.class);
        }
    }
}
