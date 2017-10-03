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

package com.javadeobfuscator.deobfuscator.transformers;

import com.javadeobfuscator.deobfuscator.config.TransformerConfig;

import java.util.List;

@TransformerConfig.ConfigOptions(configClass = DelegatingTransformer.DelegatingTransformerConfig.class)
public class DelegatingTransformer extends Transformer {
    @Override
    public boolean transform() throws Throwable {
        for (TransformerConfig childConfig : getConfig().getConfigs()) {
            getDeobfuscator().runFromConfig(childConfig);
        }

        return true;
    }

    @Override
    public DelegatingTransformer.DelegatingTransformerConfig getConfig() {
        return (DelegatingTransformer.DelegatingTransformerConfig) super.getConfig();
    }

    public static class DelegatingTransformerConfig extends TransformerConfig {
        private List<TransformerConfig> configs;

        public DelegatingTransformerConfig(List<TransformerConfig> configs) {
            super(DelegatingTransformer.class);
            this.configs = configs;
        }

        public List<TransformerConfig> getConfigs() {
            return configs;
        }
    }

    public class DelegatingTransformerConfigBuilder {
        private List<TransformerConfig> configs;

        public DelegatingTransformerConfigBuilder setConfigs(List<TransformerConfig> configs) {
            this.configs = configs;
            return this;
        }

        public DelegatingTransformer.DelegatingTransformerConfig createDelegatingTransformerConfig() {
            return new DelegatingTransformer.DelegatingTransformerConfig(configs);
        }
    }
}
