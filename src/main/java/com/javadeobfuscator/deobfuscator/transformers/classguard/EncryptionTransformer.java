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

package com.javadeobfuscator.deobfuscator.transformers.classguard;

import com.javadeobfuscator.deobfuscator.config.*;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.deobfuscator.transformers.*;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.math.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/**
 * Class encryption doesn't work, folks
 */
public class EncryptionTransformer extends Transformer<TransformerConfig> {

    @Override
    public boolean transform() throws WrongTransformerException {
        boolean madeChanges = false;

        KeyFactory rsaFactory;
        try {
            rsaFactory = KeyFactory.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Expected RSA KeyFactory", e);
        }
        Cipher rsaCipher;
        try {
            rsaCipher = Cipher.getInstance("RSA");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException("Expected RSA Cipher", e);
        }
        Cipher aesCipher;
        try {
            aesCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException("Expected AES Cipher", e);
        }

        for (ClassGuardData data : ClassGuardData.values()) {
            byte[] lib = getDeobfuscator().getInputPassthrough().get(data.getTargetFile());
            if (lib == null) {
                continue;
            }

            try {
                rsaCipher.init(Cipher.DECRYPT_MODE, rsaFactory.generatePrivate(new RSAPrivateKeySpec(
                        new BigInteger(1, Arrays.copyOfRange(lib, data.getModulusOffset(), data.getModulusOffset() + 128)),
                        new BigInteger(1, Arrays.copyOfRange(lib, data.getExponentOffset(), data.getExponentOffset() + 128))
                )));
            } catch (GeneralSecurityException e) {
                logger.debug("Failed {}", data.name(), e);
                continue;
            }

            madeChanges = madeChanges | tryDecryptEntriesUsingKey(rsaCipher, aesCipher, Arrays.copyOfRange(lib, data.getClassEncKeyOffset(), data.getClassEncKeyOffset() + 128), data, true);
            madeChanges = madeChanges | tryDecryptEntriesUsingKey(rsaCipher, aesCipher, Arrays.copyOfRange(lib, data.getRsrcEncKeyOffset(), data.getRsrcEncKeyOffset() + 128), data, false);
        }

        return madeChanges;
    }

    private boolean tryDecryptEntriesUsingKey(Cipher rsaCipher, Cipher aesCipher, byte[] encAesKey, ClassGuardData data, boolean decryptingClasses) {
        try {
            aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(rsaCipher.doFinal(encAesKey), "AES"));
        } catch (GeneralSecurityException e) {
            logger.debug("Failed {}", data.name(), e);
            return false;
        }

        logger.info("Found valid {} decryption key for {}", decryptingClasses ? "class" : "rsrc", data.name());

        Map<String, byte[]> decrypted = new HashMap<>();

        for (Map.Entry<String, byte[]> entry : getDeobfuscator().getInputPassthrough().entrySet()) {
            byte[] decEntry;
            try {
                decEntry = aesCipher.doFinal(entry.getValue());
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                continue;
            }

            decrypted.put(entry.getKey(), decEntry);
        }

        for (Map.Entry<String, byte[]> entry : decrypted.entrySet()) {
            String modifiedName = entry.getKey();
            if (modifiedName.endsWith("x")) {
                modifiedName = modifiedName.substring(0, modifiedName.length() - 1);
            }
            logger.info("Decrypted {} {} -> {}", decryptingClasses ? "class" : "rsrc", entry.getKey(), modifiedName);
            getDeobfuscator().getInputPassthrough().remove(entry.getKey());
            getDeobfuscator().loadInput(modifiedName, entry.getValue());
        }

        return !decrypted.isEmpty();
    }
}
