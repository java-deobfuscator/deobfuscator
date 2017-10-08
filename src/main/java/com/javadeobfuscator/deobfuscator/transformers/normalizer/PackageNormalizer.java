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

@TransformerConfig.ConfigOptions(configClass = PackageNormalizer.Config.class)
public class PackageNormalizer extends AbstractNormalizer<PackageNormalizer.Config> {

    @Override
    public void remap(CustomRemapper remapper) {
        AtomicInteger id = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
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
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(PackageNormalizer.class);
        }
    }
}
