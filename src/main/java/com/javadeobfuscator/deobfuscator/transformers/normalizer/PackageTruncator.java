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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

@TransformerConfig.ConfigOptions(configClass = PackageTruncator.Config.class)
public class PackageTruncator extends AbstractNormalizer<PackageTruncator.Config> {
    @Override
    public void remap(CustomRemapper remapper) {
    	Set<String> mappedNames = new HashSet<>();
    	remapper.setIgnorePackages(true);
        classNodes().forEach(classNode -> {
        	int matches = StringUtils.countMatches(classNode.name, "/");
        	if(matches > getConfig().getPackageLayers())
        	{
        		int lastIndex = classNode.name.lastIndexOf("/");
        		String packages = classNode.name.substring(0, lastIndex);
        		String name = classNode.name.substring(lastIndex, classNode.name.length());
        		int indexFromStart = StringUtils.ordinalIndexOf(packages, "/", getConfig().getPackageLayers()) + 1;
        		int indexFromEnd = StringUtils.lastOrdinalIndexOf(packages, "/", getConfig().getPackageLayers());
        		String newName = indexFromStart > packages.length() - indexFromEnd ? 
        			packages.substring(0, indexFromStart) : packages.substring(indexFromEnd + 1, packages.length());
        		newName += name;
        		int counter = 1;
        		boolean useCounter = false;
        		if(classes.containsKey(newName) || mappedNames.contains(newName))
        		{
        			useCounter = true;
        			while(classes.containsKey(newName + "_" + counter)
        				 || mappedNames.contains(newName + "_" + counter))
        				counter++;
        		}
        		remapper.map(classNode.name, useCounter ? newName + "_" + counter : newName);
        		mappedNames.add(newName);
        	}
        });
    }

    public static class Config extends AbstractNormalizer.Config {
    	/**
    	 * The number of package layers to allow before the class's packages should be truncated.
    	 */
    	private int packageLayers = 15;
    	
        public Config() {
            super(PackageTruncator.class);
        }
        
        public int getPackageLayers() {
        	return packageLayers;
        }
        
        public void setPackageLayers(int packageLayers) {
        	this.packageLayers = packageLayers;
        }
    }
}
