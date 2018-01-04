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

            RSAPrivateKeySpec spec = new RSAPrivateKeySpec(
                    new BigInteger(1, Arrays.copyOfRange(lib, data.getModulusOffset(), data.getModulusOffset() + 128)),
                    new BigInteger(1, Arrays.copyOfRange(lib, data.getExponentOffset(), data.getExponentOffset() + 128))
            );

            PrivateKey privateKey;
            try {
                privateKey = rsaFactory.generatePrivate(spec);
            } catch (InvalidKeySpecException e) {
                logger.debug("Failed {}", data.name(), e);
                continue;
            }

            madeChanges = madeChanges | tryDecryptEntriesUsingKey(rsaCipher, privateKey, aesCipher, Arrays.copyOfRange(lib, data.getClassEncKeyOffset(), data.getClassEncKeyOffset() + 128), data, true);
            madeChanges = madeChanges | tryDecryptEntriesUsingKey(rsaCipher, privateKey, aesCipher, Arrays.copyOfRange(lib, data.getRsrcEncKeyOffset(), data.getRsrcEncKeyOffset() + 128), data, false);
        }

        return madeChanges;
    }

    private boolean tryDecryptEntriesUsingKey(Cipher rsaCipher, PrivateKey privateKey, Cipher aesCipher, byte[] encAesKey, ClassGuardData data, boolean decryptingClasses) {
        SecretKey aesKey;
        try {
            rsaCipher.init(Cipher.DECRYPT_MODE, privateKey);
            aesKey = new SecretKeySpec(rsaCipher.doFinal(encAesKey), "AES");
            aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
        } catch (GeneralSecurityException e) {
            logger.debug("Failed {}", data.name(), e);
            return false;
        }

        logger.info("Found working {} decryption key for {}", decryptingClasses ? "class" : "rsrc", data.name());

        boolean madeChanges = false;

        Map<String, byte[]> decrypted = new HashMap<>();

        for (Iterator<Map.Entry<String, byte[]>> it = getDeobfuscator().getInputPassthrough().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, byte[]> entry = it.next();

            try {
                aesCipher.init(Cipher.DECRYPT_MODE, aesKey);
            } catch (InvalidKeyException e) {
                throw new RuntimeException("Did not expect this", e);
            }

            byte[] decEntry;
            try {
                decEntry = aesCipher.doFinal(entry.getValue());
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                continue;
            }

            String modifiedName = entry.getKey();
            if (modifiedName.endsWith("x")) {
                modifiedName = modifiedName.substring(0, modifiedName.length() - 1);
            }
            logger.info("Decrypted {} {} -> {}", decryptingClasses ? "class" : "rsrc", entry.getKey(), modifiedName);

            decrypted.put(modifiedName, decEntry);
            madeChanges = true;

            it.remove();
        }

        for (Map.Entry<String, byte[]> ent : decrypted.entrySet()) {
            getDeobfuscator().loadInput(ent.getKey(), ent.getValue());
        }

        return madeChanges;
    }
}
