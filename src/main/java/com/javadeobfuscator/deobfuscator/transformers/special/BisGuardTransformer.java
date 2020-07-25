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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.transformers.*;
import com.javadeobfuscator.deobfuscator.utils.TransformerHelper;
import com.javadeobfuscator.javavm.MethodExecution;
import com.javadeobfuscator.javavm.VirtualMachine;
import com.javadeobfuscator.javavm.mirrors.JavaClass;
import com.javadeobfuscator.javavm.utils.ArrayConversionHelper;
import com.javadeobfuscator.javavm.values.JavaWrapper;

public class BisGuardTransformer extends Transformer<TransformerConfig> 
{
    @Override
    public boolean transform() throws WrongTransformerException
    {
    	VirtualMachine vm = TransformerHelper.newVirtualMachine(this);
    	AtomicInteger count = new AtomicInteger();
    	System.out.println("[Special] [BisGuardTransformer] Starting");
    	ClassNode loader = classNodes().stream().filter(c -> c.name.equals("JavaPreloader")).findFirst().orElse(null);
    	MethodNode getCipher = loader == null ? null : loader.methods.stream().filter(m -> m.name.equals("getCipher")
    		&& m.desc.equals("([B)LJavaPreloader$Cipher;")).findFirst().orElse(null);
    	ClassNode cipher = classNodes().stream().filter(c -> c.name.equals("JavaPreloader$Cipher")).findFirst().orElse(null);
    	MethodNode decrypt = cipher == null ? null : cipher.methods.stream().filter(m -> m.name.equals("decrypt")
    		&& m.desc.equals("([B)V")).findFirst().orElse(null);
    	if(getCipher != null && decrypt != null)
    	{
    		MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null); 
    		init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    		init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
    		init.instructions.add(new InsnNode(Opcodes.RETURN));
    		loader.methods.add(init);
    		JavaWrapper instance = vm.newInstance(JavaClass.forName(vm, "JavaPreloader"), "(I)V", vm.newInt(0));
    		loader.methods.remove(init);
			MethodExecution cipherInstance = vm.execute(loader, getCipher, instance, Collections.<JavaWrapper>singletonList(
				vm.getNull()));
			boolean contains = getDeobfuscator().getInputPassthrough().containsKey("JavaSerialNo.class");
			byte[] decryptionKey = null;
			if(contains)
			{
				byte[] data = getDeobfuscator().getInputPassthrough().get("JavaSerialNo.class");
				JavaWrapper byteArr = ArrayConversionHelper.convertByteArray(vm, data);
				vm.execute(cipher, decrypt, cipherInstance.getReturnValue(), Collections.<JavaWrapper>singletonList(byteArr));
				byte[] b = ArrayConversionHelper.convertByteArray(byteArr.asArray());
				getDeobfuscator().getInputPassthrough().remove("JavaSerialNo.class");
				getDeobfuscator().loadInput("JavaSerialNo.class", b);
				ClassNode cn = classNodes().stream().filter(c -> c.name.equals("JavaSerialNo")).findFirst().orElse(null);
				MethodNode serialBytes = cn.methods.stream().filter(m -> m.name.equals("toSerialNoBytes")).findFirst().orElse(null);
				MethodExecution execution = vm.execute(cn, serialBytes);
				String res = vm.convertJavaObjectToString(execution.getReturnValue());
				//Convert to decryption key
				MethodNode hex2Bytes = loader.methods.stream().filter(m -> m.name.equals("hexToBytes")).findFirst().orElse(null);
				MethodExecution execution2 = vm.execute(loader, hex2Bytes, instance, Collections.<JavaWrapper>singletonList(
					vm.getString(res)));
				cipherInstance = vm.execute(loader, getCipher, instance, Collections.<JavaWrapper>singletonList(
					vm.getNull()));
				decryptionKey = ArrayConversionHelper.convertByteArray(execution2.getReturnValue().asArray());
				JavaWrapper byteArr1 = ArrayConversionHelper.convertByteArray(vm, decryptionKey);
				vm.execute(cipher, decrypt, cipherInstance.getReturnValue(), Collections.<JavaWrapper>singletonList(byteArr1));
				decryptionKey = ArrayConversionHelper.convertByteArray(byteArr1.asArray());
				cipherInstance = vm.execute(loader, getCipher, instance, Collections.<JavaWrapper>singletonList(
					ArrayConversionHelper.convertByteArray(vm, decryptionKey)));
			}
			Map<String, byte[]> decrypted = new HashMap<>();
    		for(Entry<String, byte[]> passthrough : getDeobfuscator().getInputPassthrough().entrySet())
    			if(passthrough.getKey().endsWith(".class"))
    			{
    				byte[] data = passthrough.getValue();
    				if(data[0] != -54 || data[1] != -2 || data[2] != -70 || data[3] != -66)
    				{
    					cipherInstance = vm.execute(loader, getCipher, instance, Collections.<JavaWrapper>singletonList(
    						decryptionKey == null ? vm.getNull() : ArrayConversionHelper.convertByteArray(vm, decryptionKey)));
        				JavaWrapper byteArr = ArrayConversionHelper.convertByteArray(vm, data);
        				vm.execute(cipher, decrypt, cipherInstance.getReturnValue(), Collections.<JavaWrapper>singletonList(byteArr));
        				byte[] b = ArrayConversionHelper.convertByteArray(byteArr.asArray());
        				decrypted.put(passthrough.getKey(), b);
        				count.getAndIncrement();
    				}
    			}
    		for(Entry<String, byte[]> entry : decrypted.entrySet())
    		{
    			getDeobfuscator().getInputPassthrough().remove(entry.getKey());
    			getDeobfuscator().loadInput(entry.getKey(), entry.getValue());
    		}
    		//Delete all class files related to encryption
    		classNodes().removeIf(c -> c.name.equals("JavaPreloader$1") || c.name.equals("JavaPreloader$2")
    			|| c.name.equals("JavaPreloader$3") || c.name.equals("JavaPreloader$Cipher")
    			|| c.name.equals("JavaPreloader$KlassLoader") || c.name.equals("JavaPreloader$Loader")
    			|| c.name.equals("JavaPreloader$Protected") || c.name.equals("JavaPreloader")
    			|| c.name.equals("JavaSerialNo")  || c.name.equals("SerialNoClass")
    			|| c.name.equals("com/bisguard/utils/Authenticator"));
    		//Set Main-Class to Subordinate-Class
    		String realMain = null;
    		int index = -1;
    		String[] lines = new String(getDeobfuscator().getInputPassthrough().get("META-INF/MANIFEST.MF")).split("\n");
    		for(int i = 0; i < lines.length; i++)
    		{
    			String line = lines[i];
    			if(line.startsWith("Subordinate-Class: "))
    				realMain = line.replace("Subordinate-Class: ", "");
    			else if(line.startsWith("Main-Class: "))
    				index = i;
    		}
    		lines[index] = "Main-Class: " + realMain;
    		String res = "";
    		for(String line : lines)
    			res += line + "\n";
    		res = res.substring(0, res.length() - 2);
    		getDeobfuscator().getInputPassthrough().put("META-INF/MANIFEST.MF", res.getBytes());
    	}
    	System.out.println("[Special] [BisGuardTransformer] Decrypted " + count.get() + " classes");
    	System.out.println("[Special] [BisGuardTransformer] Done");
		return count.get() > 0;
    }
}
