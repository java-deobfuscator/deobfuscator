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

package com.javadeobfuscator.deobfuscator.transformers.stringer.v3;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.ResourceEncryptionClassFinder;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.javavm.MethodExecution;
import com.javadeobfuscator.javavm.VirtualMachine;
import com.javadeobfuscator.javavm.mirrors.JavaClass;
import com.javadeobfuscator.javavm.utils.ArrayConversionHelper;
import com.javadeobfuscator.javavm.values.JavaWrapper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class ResourceEncryptionTransformer extends Transformer<TransformerConfig> implements Opcodes {

    @Override
    public boolean transform() throws Throwable {
        Set<String> decryptionClassnodes = new ResourceEncryptionClassFinder().findNames(classes.values());
        if (decryptionClassnodes.size() == 0) {
            return false;
        }
        if (decryptionClassnodes.size() > 1) {
            throw new RuntimeException("Did not expect more than one decryptor. Please provide this sample on GitHub");
        }

        VirtualMachine vm = TransformerHelper.newVirtualMachine(this);

        JavaClass pushBackInputStream = JavaClass.forName(vm, "java/io/PushbackInputStream");

        int decrypted = 0;

        ClassNode stringerResourceDecryptor = classes.get(decryptionClassnodes.iterator().next());
        MethodNode generatedDecryptorMethod = generateDecryptionMethod(stringerResourceDecryptor);
        for (Map.Entry<String, byte[]> passthrough : getDeobfuscator().getInputPassthrough().entrySet()) {
            JavaWrapper byteArray = ArrayConversionHelper.convertByteArray(vm, passthrough.getValue());
            JavaWrapper inputStream = vm.newInstance(JavaClass.forName(vm, "java/io/ByteArrayInputStream"), "([B)V", byteArray);

            JavaWrapper decryptorInputStream = vm.newInstance(JavaClass.forName(vm, stringerResourceDecryptor.name), "(Ljava/io/InputStream;)V", inputStream);

            JavaWrapper wrappedInputStream = decryptorInputStream.asObject().getField("in", "Ljava/io/InputStream;"); // decryptorInputStream instanceof FilterInputStream

            if (wrappedInputStream.getJavaClass() == pushBackInputStream) {
                // not an encrypted resource
                continue;
            }

            logger.debug("Decrypting {}", passthrough.getKey());

            MethodExecution execution = vm.execute(stringerResourceDecryptor, generatedDecryptorMethod, null, Collections.<JavaWrapper>singletonList(wrappedInputStream));
            byte[] b = ArrayConversionHelper.convertByteArray(execution.getReturnValue().asArray());
            passthrough.setValue(b);

            logger.debug("Decrypted {}", passthrough.getKey());
            decrypted++;
        }

        logger.info("Decrypted {} encrypted resources", decrypted);
        return true;
    }

    // generates the equivalent of IOUtils.toByteArray
    private static MethodNode generateDecryptionMethod(ClassNode decryptorClass) {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        MethodNode decryptorNode = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "Decrypt", "(Ljava/io/InputStream;)[B", null, null);
        decryptorNode.instructions.add(new TypeInsnNode(NEW, "java/io/ByteArrayOutputStream"));
        decryptorNode.instructions.add(new InsnNode(DUP));
        decryptorNode.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false));
        decryptorNode.instructions.add(new VarInsnNode(ASTORE, 1));
        decryptorNode.instructions.add(new LdcInsnNode(4096));
        decryptorNode.instructions.add(new IntInsnNode(NEWARRAY, Opcodes.T_BYTE));
        decryptorNode.instructions.add(new VarInsnNode(ASTORE, 2));
        decryptorNode.instructions.add(start);
        decryptorNode.instructions.add(new VarInsnNode(ALOAD, 0));
        decryptorNode.instructions.add(new VarInsnNode(ALOAD, 2));
        decryptorNode.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/InputStream", "read", "([B)I", false));
        decryptorNode.instructions.add(new VarInsnNode(ISTORE, 3));
        decryptorNode.instructions.add(new VarInsnNode(ILOAD, 3));
        decryptorNode.instructions.add(new InsnNode(ICONST_M1));
        decryptorNode.instructions.add(new JumpInsnNode(IF_ICMPEQ, end));
        decryptorNode.instructions.add(new VarInsnNode(ALOAD, 1));
        decryptorNode.instructions.add(new VarInsnNode(ALOAD, 2));
        decryptorNode.instructions.add(new InsnNode(ICONST_0));
        decryptorNode.instructions.add(new VarInsnNode(ILOAD, 3));
        decryptorNode.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/OutputStream", "write", "([BII)V", false));
        decryptorNode.instructions.add(new JumpInsnNode(GOTO, start));
        decryptorNode.instructions.add(end);
        decryptorNode.instructions.add(new VarInsnNode(ALOAD, 1));
        decryptorNode.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/ByteArrayInputStream", "toByteArray", "()[B", false));
        decryptorNode.instructions.add(new InsnNode(ARETURN));
        decryptorClass.methods.add(decryptorNode);
        return decryptorNode;
    }
}
