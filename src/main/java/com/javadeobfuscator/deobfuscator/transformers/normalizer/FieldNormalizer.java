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
import com.javadeobfuscator.deobfuscator.utils.ClassTree;

import org.assertj.core.internal.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@TransformerConfig.ConfigOptions(configClass = FieldNormalizer.Config.class)
public class FieldNormalizer extends AbstractNormalizer<FieldNormalizer.Config> {

	public static boolean EXCLUDE_ENUMS = true;

    @Override
    public void remap(CustomRemapper remapper) {
        AtomicInteger id = new AtomicInteger(0);
        //We must load the entire class tree so subclasses are correctly counted
        classNodes().forEach(classNode -> {
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTry = new LinkedList<>();
            toTry.add(tree.thisClass);
            while (!toTry.isEmpty()) {
                String t = toTry.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    toTry.addAll(ct.parentClasses);
                    toTry.addAll(ct.subClasses);
                }
            }
        });
        classNodes().forEach(classNode -> {
            ClassTree tree = this.getDeobfuscator().getClassTree(classNode.name);
            Set<String> allClasses = new HashSet<>();
            Set<String> tried = new HashSet<>();
            LinkedList<String> toTryParent = new LinkedList<>();
            LinkedList<String> toTryChild = new LinkedList<>();
            toTryParent.addAll(tree.parentClasses);
            toTryChild.addAll(tree.subClasses);
            while (!toTryParent.isEmpty()) {
                String t = toTryParent.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.parentClasses);
                    toTryParent.addAll(ct.parentClasses);
                }
            }
            while (!toTryChild.isEmpty()) {
                String t = toTryChild.poll();
                if (tried.add(t) && !t.equals("java/lang/Object")) {
                    ClassTree ct = this.getDeobfuscator().getClassTree(t);
                    allClasses.add(t);
                    allClasses.addAll(ct.subClasses);
                    toTryChild.addAll(ct.subClasses);
                }
            }
            for (FieldNode fieldNode : classNode.fields) {
            	if(EXCLUDE_ENUMS && classNode.superName.equals("java/lang/Enum")
            		&& Type.getType(fieldNode.desc).getSort() == Type.OBJECT
            		&& Type.getType(fieldNode.desc).getInternalName().equals(classNode.name))
            		continue;
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
    }

    public static class Config extends AbstractNormalizer.Config {
        public Config() {
            super(FieldNormalizer.class);
        }
    }
}
