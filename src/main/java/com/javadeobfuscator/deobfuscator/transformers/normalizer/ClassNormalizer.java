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

@TransformerConfig.ConfigOptions(configClass = ClassNormalizer.Config.class)
public class ClassNormalizer extends AbstractNormalizer<ClassNormalizer.Config> {
    @Override
    public void remap(CustomRemapper remapper) {
        AtomicInteger id = new AtomicInteger(0);
        classNodes().forEach(classNode -> {
        	
        	String newName = "Class";
        	
        	if(classNode.name.contains("/")){
            String packageName = classNode.name.substring(0, classNode.name.lastIndexOf('/'));
            newName = packageName + "/" + "Class";
        	}

            String mappedName;
            
            do {
                mappedName = newName + id.getAndIncrement();
            } while (!remapper.map(classNode.name, mappedName));
        });
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(ClassNormalizer.class);
        }
    }
}
