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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.HideAccessClassFinder;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.InvokedynamicMethodFinder;
import com.javadeobfuscator.deobfuscator.transformers.stringer.v3.utils.StringEncryptionClassFinder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;
import java.util.Set;

@TransformerConfig.ConfigOptions(configClass = CleanupTransformer.Config.class)
public class CleanupTransformer extends Transformer<CleanupTransformer.Config> {

    @Override
    public boolean transform() throws Throwable {
        cleanupInvokedynamic();
        cleanupStringEncryptionClasses();
        cleanupHideAccessClasses();
        cleanupResourceEncryption();
        return true;
    }

    private void cleanupResourceEncryption() {
        if (!getConfig().cleanupResourceEncryption) return;


    }

    private void cleanupHideAccessClasses() {
        if (!getConfig().cleanupHideAccess) return;

        Set<String> remove = new HideAccessClassFinder().findNames(classes.values());
        remove.forEach(classes::remove);
        logger.info("Removed {} hide access decryption classes", remove.size());
    }

    private void cleanupStringEncryptionClasses() {
        if (!getConfig().cleanupStringEncryption) return;

        Set<String> remove = new StringEncryptionClassFinder().findNames(classes.values());
        remove.forEach(classes::remove);
        logger.info("Removed {} string encryption classes", remove.size());
    }

    private void cleanupInvokedynamic() {
        if (!getConfig().cleanupInvokedynamic) return;

        Map<ClassNode, Set<MethodNode>> remove = new InvokedynamicMethodFinder().find(classes.values());
        remove.forEach((c, m) -> c.methods.removeAll(m));
        logger.info("Removed {} invokedynamic decryption methods", remove.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());
    }

    public static class Config extends TransformerConfig {
        @JsonProperty(value = "clean-hide-access")
        private boolean cleanupHideAccess = true;
        @JsonProperty(value = "clean-string-encryption")
        private boolean cleanupStringEncryption = true;
        @JsonProperty(value = "clean-invokedynamic")
        private boolean cleanupInvokedynamic = true;
        @JsonProperty(value = "clean-resource-encryption")
        private boolean cleanupResourceEncryption = true;

        public Config() {
            super(CleanupTransformer.class);
        }

        public boolean isCleanupHideAccess() {
            return cleanupHideAccess;
        }

        public void setCleanupHideAccess(boolean cleanupHideAccess) {
            this.cleanupHideAccess = cleanupHideAccess;
        }

        public boolean isCleanupStringEncryption() {
            return cleanupStringEncryption;
        }

        public void setCleanupStringEncryption(boolean cleanupStringEncryption) {
            this.cleanupStringEncryption = cleanupStringEncryption;
        }

        public boolean isCleanupInvokedynamic() {
            return cleanupInvokedynamic;
        }

        public void setCleanupInvokedynamic(boolean cleanupInvokedynamic) {
            this.cleanupInvokedynamic = cleanupInvokedynamic;
        }

        public boolean isCleanupResourceEncryption() {
            return cleanupResourceEncryption;
        }

        public void setCleanupResourceEncryption(boolean cleanupResourceEncryption) {
            this.cleanupResourceEncryption = cleanupResourceEncryption;
        }
    }
}
