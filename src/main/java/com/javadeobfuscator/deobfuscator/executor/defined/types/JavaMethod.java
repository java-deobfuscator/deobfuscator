/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.executor.defined.types;

import java.util.ArrayList;
import java.util.List;
import com.google.common.primitives.Primitives;
import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.exceptions.ExecutionException;
import com.javadeobfuscator.deobfuscator.executor.values.*;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

public class JavaMethod {
    private final JavaClass clazz;
    private final MethodNode method;

    public JavaMethod(JavaClass javaClass, MethodNode methodNode) {
        this.clazz = javaClass;
        this.method = methodNode;
    }

    public String getOwner() {
        return getDeclaringClass().getName().replace(".", "/");
    }

    public String getName() {
        return method.name;
    }

    public String getDesc() {
        return method.desc;
    }

    public JavaClass getReturnType() {
        Class<?> primitive = PrimitiveUtils.getPrimitiveByName(Type.getReturnType(method.desc).getClassName());
        if (primitive != null) {
            return new JavaClass(Type.getReturnType(method.desc).getClassName(), clazz.getContext());
        }
        return new JavaClass(Type.getReturnType(method.desc).getInternalName(), clazz.getContext());
    }

    public JavaClass getDeclaringClass() {
        return this.clazz;
    }

    public JavaClass[] getParameterTypes() {
        List<JavaClass> params = new ArrayList<>();
        for (Type type : Type.getArgumentTypes(method.desc)) {
            Class<?> primitive = PrimitiveUtils.getPrimitiveByName(type.getClassName());
            if (primitive != null) {
                params.add(new JavaClass(type.getClassName(), clazz.getContext()));
            } else {
                params.add(new JavaClass(type.getInternalName(), clazz.getContext()));
            }
        }
        return params.toArray(new JavaClass[params.size()]);
    }

    public MethodNode getMethodNode() {
        return this.method;
    }

    @Override
    public int hashCode() {
        return getDeclaringClass().getName().hashCode() ^ getName().hashCode();
    }

    public Object invoke(JavaValue instance, JavaValue argsObject, Context context) {
    	Object[] args; 
    	String[] argTypes;
    	if(argsObject.value() == null) 
    	{
    		args = null;
    		argTypes = null;
    	}else
    	{
    		args = (Object[])((JavaArray)argsObject).value();
    		argTypes = ((JavaArray)argsObject).getTypeArray();
    	}
        try {
        	//Fix for unboxing/boxing
        	List<Integer> fixed = new ArrayList<>();
            if(args != null && args.length == getParameterTypes().length)
            	for(int i = 0; i < args.length; i++)
            	{
            		Object o = args[i];
            		JavaClass[] params = getParameterTypes();
            		JavaClass argClass = params[i];
            		if(argClass.isPrimitive())
            		{
            			Object value = o;
            			if(Primitives.unwrap(value.getClass()).getName().equals(argClass.getName()))
	            			switch(argClass.getName())
	            			{
	            				case "boolean":
	            					args[i] = new JavaBoolean((Boolean)o);
	            					fixed.add(i);
	            					break;
	            				case "byte":
	            					args[i] = new JavaByte((Byte)o);
	            					fixed.add(i);
	            					break;
	            				case "char":
	            					args[i] = new JavaCharacter((Character)o);
	            					fixed.add(i);
	            					break;
	            				case "double":
	            					args[i] = new JavaDouble((Double)o);
	            					fixed.add(i);
	            					break;
	            				case "float":
	            					args[i] = new JavaFloat((Float)o);
	            					fixed.add(i);
	            					break;
	            				case "int":
	            					args[i] = new JavaInteger((Integer)o);
	            					fixed.add(i);
	            					break;
	            				case "long":
	            					args[i] = new JavaLong((Long)o);
	            					fixed.add(i);
	            					break;
	            				case "short":
	            					args[i] = new JavaShort((Short)o);
	            					fixed.add(i);
	            					break;
	            			}
            		}
            	}
            List<JavaValue> argsobjects = new ArrayList<>();
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                	Object o = args[i];
                	if(!fixed.contains(i))
                	{
                		if(o != null && o.getClass().isArray())
                			argsobjects.add(new JavaArray(o));
                        else
                        	argsobjects.add(new JavaObject(o, argTypes[i]));
                	}else
                		argsobjects.add((JavaValue)o);
                }
            }

            if (context.provider.canInvokeMethod(this.clazz.getClassNode().name, this.method.name, this.method.desc, instance, argsobjects, context)) {
            	Object o = this.clazz.getContext().provider.invokeMethod(this.clazz.getClassNode().name, this.method.name, this.method.desc, instance, argsobjects, context);
            	context.pop();
            	context.pop();
            	context.pop();
            	context.pop();
            	return o;
            }
        } catch (ExecutionException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new ExecutionException(t);
        }
        throw new ExecutionException("Could not invoke " + this.clazz.getName() + " " + method.name + method.desc);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JavaMethod other = (JavaMethod) obj;
        if (clazz == null) {
            if (other.clazz != null)
                return false;
        } else if (!clazz.equals(other.clazz))
            return false;
        if (method == null) {
            if (other.method != null)
                return false;
        } else if (!method.equals(other.method))
            return false;
        return true;
    }

    public void setAccessible(boolean accessible) {
    }

    public boolean isStatic() {
        return (method.access & Opcodes.ACC_STATIC) != 0;
    }
}
