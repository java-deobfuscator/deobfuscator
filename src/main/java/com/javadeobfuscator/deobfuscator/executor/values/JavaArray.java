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

package com.javadeobfuscator.deobfuscator.executor.values;

import java.lang.reflect.Array;
import java.util.AbstractMap;
import java.util.Map.Entry;

import org.objectweb.asm.Type;

public class JavaArray extends JavaObject {
    private Object array;
    private String[] typeArray;

    public JavaArray(Object array) 
    {
    	super(array, "java/lang/Object");
    	if(!array.getClass().isArray())
    		throw new IllegalArgumentException("Object must be array");
        this.array = array;
    	typeArray = new String[Array.getLength(array)];
    	for(int i = 0; i < Array.getLength(array); i++) 
    	{
    		Object o = Array.get(array, i);
    		if(o == null)
        		typeArray[i] = "java/lang/Object";
        	else if(Type.getType(o.getClass()).getSort() == Type.OBJECT)
        		typeArray[i] = JavaValue.valueOf(o).type();
    		else if(Type.getType(o.getClass()).getSort() == Type.ARRAY)
    			typeArray[i] = "java/lang/Object";
    		else
    			typeArray[i] = Type.getType(o.getClass()).getClassName();
    	}
    }
    
    public JavaArray(Object array, String[] typeArray) 
    {
    	super(array, "java/lang/Object");
    	if(!array.getClass().isArray())
    		throw new IllegalArgumentException("Object must be array");        
    	this.array = array;
    	this.typeArray = typeArray;
    }

    
    public void onValueStored(int index, String type)
    {
    	typeArray[index] = type;
    }
    
    public String getValueType(int index) 
    {
		return typeArray[index];
    }
    
    public String[] getTypeArray() 
    {
    	return typeArray;
    }
     
    public Entry<Object, String[]> getObjectArrayWithValues() 
    {
    	return new AbstractMap.SimpleEntry<>(array, typeArray);
    }
    
    @Override
    public Object value() {
        return this.array;
    }

    @Override
    public JavaObject copy() {
        return this;
    }

    @Override
    public void initialize(Object value) {
    	throw new IllegalStateException("Cannot initialize an array twice");
    }
    
    @Override
    public String toString() {
        return "JavaArray@" + Integer.toHexString(System.identityHashCode(this)) + "(value=" + array + ")";
    }
}
