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

package com.javadeobfuscator.deobfuscator.transformers.general.removers;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

import java.util.Iterator;
import java.util.List;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.TypeAnnotationNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckFieldAdapter;
import org.objectweb.asm.util.CheckMethodAdapter;

import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class IllegalTypeAnnotationRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
    	CheckClassAdapter classAdapter = new CheckClassAdapter(null);
    	CheckMethodAdapter methodAdapter = new CheckMethodAdapter(null);
    	CheckFieldAdapter fieldAdapter = new CheckFieldAdapter(null);
    	classAdapter.visit(Opcodes.V1_5, 0, "java/lang/Object", null, null, null);
        classNodes().forEach(classNode -> {
        	removeInvalidTypeAnnotations(classAdapter, classNode.invisibleTypeAnnotations, false);
        	removeInvalidTypeAnnotations(classAdapter, classNode.visibleTypeAnnotations, true);
            classNode.methods.forEach(methodNode -> {
            	removeInvalidTypeAnnotations(methodAdapter, methodNode.invisibleTypeAnnotations, false);
            	removeInvalidTypeAnnotations(methodAdapter, methodNode.visibleTypeAnnotations, true);
            });
            classNode.fields.forEach(fieldNode -> {
            	removeInvalidTypeAnnotations(fieldAdapter, fieldNode.invisibleTypeAnnotations, false);
            	removeInvalidTypeAnnotations(fieldAdapter, fieldNode.visibleTypeAnnotations, true);
            });
        });
        return true;
    }
    
    private void removeInvalidTypeAnnotations(Object visitor, List<TypeAnnotationNode> typeAnnots, boolean visible)
    {
    	if(typeAnnots == null)
    		return;
    	Iterator<TypeAnnotationNode> itr = typeAnnots.iterator();
    	while(itr.hasNext())
    	{
    		TypeAnnotationNode type = itr.next();
            try {
            	if(visitor instanceof CheckClassAdapter)
            		((CheckClassAdapter)visitor).visitTypeAnnotation(type.typeRef, type.typePath, type.desc, visible);
            	else if(visitor instanceof CheckMethodAdapter)
            		((CheckMethodAdapter)visitor).visitTypeAnnotation(type.typeRef, type.typePath, type.desc, visible);
            	else
            		((CheckFieldAdapter)visitor).visitTypeAnnotation(type.typeRef, type.typePath, type.desc, visible);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                itr.remove();
            }
    	}
    }
}
