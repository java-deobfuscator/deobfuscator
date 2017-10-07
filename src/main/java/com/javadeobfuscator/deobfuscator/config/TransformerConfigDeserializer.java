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

package com.javadeobfuscator.deobfuscator.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.javadeobfuscator.deobfuscator.transformers.DelegatingTransformer;
import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class TransformerConfigDeserializer extends JsonDeserializer<TransformerConfig> {

    private Logger logger;

    public TransformerConfigDeserializer(Logger logger) {
        this.logger = logger;
    }

    @Override
    public TransformerConfig deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.isObject()) {
            List<TransformerConfig> configs = new ArrayList<>();

            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();

                TransformerConfig config = getById(p, ctxt, field.getKey());

                // todo is there a better way to do this other than making a new ObjectMapper?
                ObjectMapper mapper = new ObjectMapper(new JsonFactory());
                configs.add(mapper.readerForUpdating(config).readValue(field.getValue()));
            }

            if (configs.size() == 0) {
                throw new IllegalArgumentException("didn't see that coming " + node);
            } else if (configs.size() == 1) {
                return configs.get(0);
            } else {
                DelegatingTransformer.Config config = new DelegatingTransformer.Config();
                config.setConfigs(configs);
                return config;
            }
        } else if (node.isTextual()) {
            return getById(p, ctxt, node.asText());
        } else {
            throw ctxt.wrongTokenException(p, String.class, JsonToken.VALUE_STRING, null);
        }
    }

    private TransformerConfig getById(JsonParser p, DeserializationContext ctxt, String id) throws JsonMappingException {
        Class<?> clazz = null;
        try {
            clazz = Class.forName("com.javadeobfuscator.deobfuscator.transformers." + id);
        } catch (ClassNotFoundException ignored) {
        }
        if (clazz == null) {
            try {
                clazz = Class.forName(id);
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (clazz == DelegatingTransformer.class) {
            throw ctxt.weirdStringException(id, Transformer.class, "Cannot explicitly request DelegatingTransformer");
        }

        if (clazz != null) {
            if (Transformer.class.isAssignableFrom(clazz)) {
                return TransformerConfig.configFor(clazz.asSubclass(Transformer.class));
            } else {
                throw ctxt.weirdStringException(id, Transformer.class, "Class does not extend com.javadeobfuscator.deobfuscator.transformers.Transformer");
            }
        } else {
            throw ctxt.weirdStringException(id, Transformer.class, "Could not locate specified transformer");
        }
    }
}
