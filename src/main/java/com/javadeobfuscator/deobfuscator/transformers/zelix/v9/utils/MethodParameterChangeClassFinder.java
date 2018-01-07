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

package com.javadeobfuscator.deobfuscator.transformers.zelix.v9.utils;

import com.javadeobfuscator.deobfuscator.graph.inheritancegraph.InheritanceGraph;
import com.javadeobfuscator.deobfuscator.transformers.ClassFinder;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MethodParameterChangeClassFinder implements ClassFinder {
    @Override
    public Set<ClassNode> find(Collection<ClassNode> classes) {
        Set<ClassNode> result = new HashSet<>();
        Set<ClassNode> interfaces = findInterfaces(classes);
        Set<ClassNode> implementations = findImplementations(classes, interfaces);
        result.addAll(interfaces);
        result.addAll(implementations);
        return result;
    }

    private Set<ClassNode> findImplementations(Collection<ClassNode> classes, Set<ClassNode> interfaces) {
        Set<ClassNode> result = new HashSet<>();
        for (ClassNode classNode : classes) {
            if (classNode.interfaces == null) {
                continue;
            }
            for (ClassNode intf : interfaces) {
                if (classNode.interfaces.contains(intf.name)) {
                    result.add(classNode);
                    break;
                }
            }
        }
        return result;
    }

    private Set<ClassNode> findInterfaces(Collection<ClassNode> classes) {
        Set<ClassNode> result = new HashSet<>();
        for (ClassNode classNode : classes) {
            if (!Modifier.isInterface(classNode.access)) {
                continue;
            }
            MethodNode intArr = TransformerHelper.findMethodNode(classNode, null, "()[I");
            if (intArr == null) {
                continue;
            }
            if (Modifier.isStatic(intArr.access) || !Modifier.isAbstract(intArr.access)) {
                continue;
            }
            MethodNode boolRet = TransformerHelper.findMethodNode(classNode, null, "(Ljava/lang/Object;)I", true);
            if (boolRet == null) {
                continue;
            }
            if (Modifier.isStatic(boolRet.access) || !Modifier.isAbstract(boolRet.access)) {
                continue;
            }
            result.add(classNode);
        }
        return result;
    }
}
