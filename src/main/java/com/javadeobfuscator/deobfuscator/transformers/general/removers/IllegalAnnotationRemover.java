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
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.CheckFieldAdapter;
import org.objectweb.asm.util.CheckMethodAdapter;

import com.javadeobfuscator.deobfuscator.transformers.Transformer;

public class IllegalAnnotationRemover extends Transformer<TransformerConfig> {
    @Override
    public boolean transform() throws Throwable {
    	CheckClassAdapter classAdapter = new CheckClassAdapter(null);
    	CheckMethodAdapter methodAdapter = new CheckMethodAdapter(null);
    	CheckFieldAdapter fieldAdapter = new CheckFieldAdapter(null);
    	classAdapter.visit(Opcodes.V1_5, 0, "java/lang/Object", null, null, null);
        classNodes().forEach(classNode -> {
        	removeInvalidAnnotations(classAdapter, classNode.invisibleAnnotations, false);
        	removeInvalidAnnotations(classAdapter, classNode.visibleAnnotations, true);
            classNode.methods.forEach(methodNode -> {
            	removeInvalidAnnotations(methodAdapter, methodNode.invisibleAnnotations, false);
            	removeInvalidAnnotations(methodAdapter, methodNode.visibleAnnotations, true);
            });
            classNode.fields.forEach(fieldNode -> {
            	removeInvalidAnnotations(fieldAdapter, fieldNode.invisibleAnnotations, false);
            	removeInvalidAnnotations(fieldAdapter, fieldNode.visibleAnnotations, true);
            });
        });
        return true;
    }
    
    private void removeInvalidAnnotations(Object visitor, List<AnnotationNode> annots, boolean visible)
    {
    	if(annots == null)
    		return;
    	Iterator<AnnotationNode> itr = annots.iterator();
    	while(itr.hasNext())
    	{
    		AnnotationNode type = itr.next();
            try {
            	if(visitor instanceof CheckClassAdapter)
            		((CheckClassAdapter)visitor).visitAnnotation(type.desc, visible);
            	else if(visitor instanceof CheckMethodAdapter)
            		((CheckMethodAdapter)visitor).visitAnnotation(type.desc, visible);
            	else
            		((CheckFieldAdapter)visitor).visitAnnotation(type.desc, visible);
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                itr.remove();
            }
    	}
    }
}
