package com.javadeobfuscator.deobfuscator.executor.defined.types;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.values.*;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;

import java.util.ArrayList;
import java.util.List;

public class JavaConstructor {
    private final JavaClass clazz;
    private final String desc;

    public JavaConstructor(JavaClass clazz, String desc) {
        this.clazz = clazz;
        this.desc = desc;
    }

    public JavaClass getClazz() {
        return clazz;
    }

    public JavaClass[] getParameterTypes() {
        List<JavaClass> params = new ArrayList<>();
        for (Type type : Type.getArgumentTypes(desc)) {
            Class<?> primitive = PrimitiveUtils.getPrimitiveByName(type.getClassName());
            if (primitive != null) {
                params.add(new JavaClass(type.getClassName(), clazz.getContext()));
            } else {
                params.add(new JavaClass(type.getInternalName(), clazz.getContext()));
            }
        }
        return params.toArray(new JavaClass[params.size()]);
    }

    public Object newInstance(Context context, JavaValue argsObject)
    {
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
    	MethodNode method = clazz.getClassNode().methods.stream().filter(m -> m.name.equals("<init>") 
    		&& m.desc.equals(desc)).findFirst().orElse(null);
    	List<JavaValue> javaArgs = new ArrayList<>();
    	for(int i = 0; i < args.length; i++)
    	{
    		Object arg = args[i];
    		if(arg instanceof Type)
    		{
                Type type = (Type)arg;
                arg = new JavaClass(type.getInternalName().replace('/', '.'), context);
            }
    		if(arg instanceof Boolean)
    			javaArgs.add(0, new JavaBoolean((Boolean)arg));
    		else if(arg instanceof Character)
                javaArgs.add(0, new JavaCharacter((Character)arg));
    		else if(arg instanceof Byte)
                javaArgs.add(0, new JavaByte((Byte)arg));
    		else if(arg instanceof Short)
                javaArgs.add(0, new JavaShort((Short)arg));
    		else if(arg instanceof Integer)
                javaArgs.add(0, new JavaInteger((Integer)arg));
            else if(arg instanceof Float)
                javaArgs.add(0, new JavaFloat((Float)arg));
            else if(arg instanceof Double)
                javaArgs.add(0, new JavaDouble((Double)arg));
            else if(arg instanceof Long)
                javaArgs.add(0, new JavaLong((Long)arg));
            else if(arg != null && arg.getClass().isArray())
            	javaArgs.add(new JavaArray(arg));
            else
            	javaArgs.add(new JavaObject(arg, argTypes[i]));
    	}
    	JavaObject instance = new JavaObject(clazz.getName().replace(".", "/"));
    	context.provider.invokeMethod(clazz.getName().replace(".", "/"), method.name, method.desc,
    		instance, javaArgs, context);
    	return instance.value();
    }
    
    public void setAccessible(boolean accessible) {
    }

    public String getClassName() {
        return clazz.getClassNode().name;
    }

    public String getDesc() {
        return desc;
    }
}
