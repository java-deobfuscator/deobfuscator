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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.FieldNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.ClassTree;
import com.javadeobfuscator.deobfuscator.utils.PrimitiveUtils;
import com.javadeobfuscator.deobfuscator.utils.Utils;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public class JavaClass {

    private final String name;
    private final Type type;
    private final WrappedClassNode wrappedClassNode;
    private final ClassNode classNode;
    private final Context context;
    private final boolean isPrimitive;

    public JavaClass(String name, Context context) {
        if (name.contains("/")) {
            name = name.replace('/', '.');
        }
        this.name = name;
        this.context = context;
        String internalName = this.name.replace('.', '/');
        Class<?> primitive = PrimitiveUtils.getPrimitiveByName(internalName);
        if (primitive == null) {
            this.type = Type.getObjectType(internalName);
            Type elementType = this.type;
            if (elementType.getSort() == Type.ARRAY) {
                elementType = elementType.getElementType();
            }
            primitive = PrimitiveUtils.getPrimitiveByName(elementType.getClassName());
            if (primitive == null) {
                this.wrappedClassNode = context.dictionary.get(elementType.getInternalName());
                if (this.wrappedClassNode == null) {
                    System.out.println("Could not find classnode " + this.name);
                    throw new NoClassDefFoundError(this.name);
                }
                this.classNode = this.wrappedClassNode.classNode;
                this.isPrimitive = false;
            } else {
                this.wrappedClassNode = context.dictionary.get("java/lang/Object");
                this.classNode = this.wrappedClassNode.classNode;
                this.isPrimitive = false;
            }
        } else {
            this.type = Type.getType(primitive);
            this.wrappedClassNode = null;
            this.classNode = null;
            this.isPrimitive = true;
        }
    }

    public String getName() {
        return this.name;
    }

    private Map<String, ClassTree> hierachy = new HashMap<>();

    public ClassNode assureLoaded(String ref) {
        WrappedClassNode clazz = context.dictionary.get(ref);
        if (clazz == null) {
            throw new IllegalArgumentException("No class in path " + ref);
        }
        return clazz.classNode;
    }

    public ClassTree getClassTree(String classNode) {
        ClassTree tree = hierachy.get(classNode);
        if (tree == null) {
            tree = new ClassTree();
            tree.thisClass = classNode;
            hierachy.put(classNode, tree);
            loadHierachy(context.dictionary.get(classNode).classNode);
        }
        return tree;
    }

    public List<ClassNode> loadHierachy(ClassNode specificNode) {
        if (specificNode.name.equals("java/lang/Object")) {
            return Collections.emptyList();
        }
        if ((specificNode.access & Opcodes.ACC_INTERFACE) != 0) {
            getClassTree(specificNode.name).parentClasses.add("java/lang/Object");
            return Collections.emptyList();
        }
        List<ClassNode> toProcess = new ArrayList<>();

        ClassTree thisTree = getClassTree(specificNode.name);
        ClassNode superClass = assureLoaded(specificNode.superName);
        if (superClass == null) {
            throw new IllegalArgumentException("Could not load " + specificNode.name);
        }
        ClassTree superTree = getClassTree(superClass.name);
        superTree.subClasses.add(specificNode.name);
        thisTree.parentClasses.add(superClass.name);
        toProcess.add(superClass);

        for (String interfaceReference : specificNode.interfaces) {
            ClassNode interfaceNode = assureLoaded(interfaceReference);
            if (interfaceNode == null) {
                throw new IllegalArgumentException("Could not load " + interfaceReference);
            }
            ClassTree interfaceTree = getClassTree(interfaceReference);
            interfaceTree.subClasses.add(specificNode.name);
            thisTree.parentClasses.add(interfaceReference);
            toProcess.add(interfaceNode);
        }
        return toProcess;
    }

    private boolean isAssignableFrom(String type1, String type2) {
        if (type1.equals("java/lang/Object"))
            return true;
        if (type1.equals(type2)) {
            return true;
        }
        assureLoaded(type1);
        assureLoaded(type2);
        ClassTree firstTree = getClassTree(type1);
        Set<String> allChilds1 = new HashSet<>();
        LinkedList<String> toProcess = new LinkedList<>();
        toProcess.addAll(firstTree.subClasses);
        while (!toProcess.isEmpty()) {
            String s = toProcess.poll();
            if (allChilds1.add(s)) {
                assureLoaded(s);
                ClassTree tempTree = getClassTree(s);
                toProcess.addAll(tempTree.subClasses);
            }
        }
        if (allChilds1.contains(type2)) {
            return true;
        }
        return false;
    }

    public JavaMethod getDeclaredMethod(String name, JavaClass[] clazz) {
        StringBuilder descBuilder = new StringBuilder("(");
        for (JavaClass javaClass : clazz) {
            descBuilder.append(javaClass.type.getDescriptor());
        }
        descBuilder.append(")");
        String desc = descBuilder.toString();

        List<MethodNode> possibleMethods = classNode.methods.stream().filter(methodNode -> methodNode.name.equals(name) && methodNode.desc.startsWith(desc)).collect(Collectors.toList());
        if (possibleMethods.size() == 0) {
            Utils.sneakyThrow(new NoSuchMethodException(this.name + " " + name + desc));
            return null;
        } else if (possibleMethods.size() > 1) {
            List<Type> returnTypes = new ArrayList<>();
            for (MethodNode m : possibleMethods) {
                returnTypes.add(Type.getReturnType(m.desc));
            }
            Collections.sort(returnTypes, (o1, o2) -> {
                String s1 = o1.getInternalName();
                String s2 = o2.getInternalName();
                if (isAssignableFrom(s1, s2)) {
                    return 1;
                } else if (isAssignableFrom(s2, s1)) {
                    return -1;
                }
                return 0;
            });
            MethodNode target = possibleMethods.stream().filter(mn -> mn.desc.endsWith(returnTypes.get(0).getDescriptor())).findFirst().orElse(null);
            return new JavaMethod(this, target);
        } else {
            return new JavaMethod(this, possibleMethods.get(0));
        }
    }

    public Type getType() {
        return this.type;
    }

    public String toString() {
        return this.name;
    }

    public JavaMethod[] getDeclaredMethods() {
        List<JavaMethod> methods = new ArrayList<>();
        for (MethodNode methodNode : this.classNode.methods) {
            if (!methodNode.name.startsWith("<")) {
                methods.add(new JavaMethod(this, methodNode));
            }
        }
        return methods.toArray(new JavaMethod[methods.size()]);
    }

    public JavaField[] getDeclaredFields() {
        List<JavaField> fields = new ArrayList<>();
        for (FieldNode fieldNode : this.classNode.fields) {
            fields.add(new JavaField(this, fieldNode));
        }
        return fields.toArray(new JavaField[fields.size()]);
    }

    public JavaClass getSuperclass() {
        if (this.type.getSort() == Type.ARRAY) {
            return new JavaClass("java/lang/Object", this.context);
        }
        if (this.classNode.name.equals("java/lang/Object")) {
            return null;
        }
        return new JavaClass(this.classNode.superName, this.context);
    }

    public Context getContext() {
        return this.context;
    }

    public boolean isPrimitive() {
        return this.isPrimitive;
    }

    public JavaClass[] getInterfaces() {
        List<JavaClass> interfaces = new ArrayList<>();
        for (String intf : this.classNode.interfaces) {
            interfaces.add(new JavaClass(intf, this.context));
        }
        return interfaces.toArray(new JavaClass[interfaces.size()]);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((classNode == null) ? 0 : classNode.hashCode());
        //        result = prime * result + ((context == null) ? 0 : context.hashCode());
        //        result = prime * result + ((name == null) ? 0 : name.hashCode());
        //        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JavaClass other = (JavaClass) obj;
        //        if (classNode == null) {
        //            if (other.classNode != null)
        //                return false;
        //        } else if (!classNode.equals(other.classNode))
        //            return false;
        //        if (context == null) {
        //            if (other.context != null)
        //                return false;
        //        } else if (!context.equals(other.context))
        //            return false;
        //        if (name == null) {
        //            if (other.name != null)
        //                return false;
        //        } else if (!name.equals(other.name))
        //            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type)) {
            return false;
        }
        return true;
    }

    public ClassNode getClassNode() {
        return this.classNode;
    }

    public WrappedClassNode getWrappedClassNode() {
        return this.wrappedClassNode;
    }
}
