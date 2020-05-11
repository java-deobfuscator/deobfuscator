/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
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

package com.javadeobfuscator.deobfuscator.transformers.special;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.transformers.*;

public class InvaildClassRemover extends Transformer<TransformerConfig> 
{
    @Override
    public boolean transform()
    {
    	System.out.println("[Special] [InvaildClassRemover] Starting");
    	List<Pattern> patterns = new ArrayList<>();
    	if(getDeobfuscator().getConfig().getIgnoredClasses() != null)
    	for (String ignored : getDeobfuscator().getConfig().getIgnoredClasses()) {
            Pattern pattern;
            try {
                pattern = Pattern.compile(ignored);
            } catch (PatternSyntaxException e) {
                logger.error("Error while compiling pattern for ignore statement {}", ignored, e);
                continue;
            }
            patterns.add(pattern);
        }
    	int before = getDeobfuscator().getInputPassthrough().size();
    	getDeobfuscator().getInputPassthrough().entrySet().removeIf(e -> e.getKey().endsWith(".class")
    		&& !hasClass(e.getKey(), patterns));
    	int after = getDeobfuscator().getInputPassthrough().size();
    	System.out.println("[Special] [InvaildClassRemover] Removed " + (before - after) + " classes");
    	return before - after > 0;
    }
    
    private boolean hasClass(String name, List<Pattern> patterns)
    {
    	int start = name.lastIndexOf(".class");
    	String newName = name.substring(0, start);
    	for(Pattern pattern : patterns)
    		if(pattern.matcher(newName).find())
    			return true;
    	return false;
    }
}
