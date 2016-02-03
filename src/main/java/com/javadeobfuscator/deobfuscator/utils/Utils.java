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

package com.javadeobfuscator.deobfuscator.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Map;

import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Opcodes;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.AbstractInsnNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.ClassNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.util.Printer;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.util.Textifier;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.util.TraceMethodVisitor;

public class Utils {
    public static int iconstToInt(int opcode) {
        int operand = Integer.MIN_VALUE;
        switch (opcode) {
        case Opcodes.ICONST_0:
            operand = 0;
            break;
        case Opcodes.ICONST_1:
            operand = 1;
            break;
        case Opcodes.ICONST_2:
            operand = 2;
            break;
        case Opcodes.ICONST_3:
            operand = 3;
            break;
        case Opcodes.ICONST_4:
            operand = 4;
            break;
        case Opcodes.ICONST_5:
            operand = 5;
            break;
        case Opcodes.ICONST_M1:
            operand = -1;
            break;
        }
        return operand;
    }

    public static MethodNode getMethodNode(ClassNode start, String methodName, String methodDesc, Map<String, ClassNode> dictionary) {
        MethodNode targetMethod = null;

        LinkedList<ClassNode> haystack = new LinkedList<>();
        haystack.add(start);
        while (targetMethod == null && !haystack.isEmpty()) {
            ClassNode needle = haystack.poll();
            targetMethod = needle.methods.stream().filter(imn -> imn.name.equals(methodName) && imn.desc.equals(methodDesc)).findFirst().orElse(null);
            if (targetMethod == null) {
                if (!needle.name.equals("java/lang/Object")) {
                    for (String intf : needle.interfaces) {
                        ClassNode intfNode = dictionary.get(intf);
                        if (intfNode == null) {
                            throw new IllegalArgumentException("Class not found: " + intf);
                        }
                        haystack.add(intfNode);
                    }
                    String superName = needle.superName;
                    needle = dictionary.get(needle.superName);
                    if (needle == null) {
                        throw new IllegalArgumentException("Class not found: " + superName);
                    }
                    haystack.add(needle);
                }
            }
        }

        return targetMethod;
    }

    public static long copy(InputStream from, OutputStream to) throws IOException {
        byte[] buf = new byte[4096];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }
    
    public static String descFromTypes(Type[] types) {
        StringBuilder descBuilder = new StringBuilder("(");
        for (Type type : types) {
            descBuilder.append(type.getDescriptor());
        }
        descBuilder.append(")");
        return descBuilder.toString();
    }

    public static void sneakyThrow(Throwable t) {
        Utils.<Error> sneakyThrow0(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow0(Throwable t) throws T {
        throw (T) t;
    }

    private static final Printer printer = new Textifier();
    private static final TraceMethodVisitor methodPrinter = new TraceMethodVisitor(printer);

    public static String prettyprint(AbstractInsnNode insnNode) {
        insnNode.accept(methodPrinter);
        StringWriter sw = new StringWriter();
        printer.print(new PrintWriter(sw));
        printer.getText().clear();
        return sw.toString().trim();
    }
}
