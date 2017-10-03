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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class Configuration {

    @JsonProperty
    private File input;

    @JsonProperty
    private File output;

    @JsonProperty
    private boolean generateIntermediateJars;

    @JsonProperty
    private List<TransformerConfig> transformers;

    @JsonProperty
    private List<File> path;

    private Configuration() {
    }

    private Configuration(File input, File output, boolean generateIntermediateJars, List<TransformerConfig> transformers, List<File> path) {
        this.input = input;
        this.output = output;
        this.generateIntermediateJars = generateIntermediateJars;
        this.transformers = transformers;
        this.path = path;
    }

    public File getInput() {
        return input;
    }

    public File getOutput() {
        return output;
    }

    public boolean isGenerateIntermediateJars() {
        return generateIntermediateJars;
    }

    public List<TransformerConfig> getTransformers() {
        return transformers == null ? Collections.emptyList() : transformers;
    }

    public List<File> getPath() {
        return path == null ? Collections.emptyList() : path;
    }

    public static class ConfigurationBuilder {
        private File input;
        private File output;
        private boolean generateIntermediateJars;
        private List<TransformerConfig> transformers;
        private List<File> path;

        public ConfigurationBuilder setInput(File input) {
            this.input = input;
            return this;
        }

        public ConfigurationBuilder setOutput(File output) {
            this.output = output;
            return this;
        }

        public ConfigurationBuilder setGenerateIntermediateJars(boolean generateIntermediateJars) {
            this.generateIntermediateJars = generateIntermediateJars;
            return this;
        }

        public ConfigurationBuilder setTransformers(List<TransformerConfig> transformers) {
            this.transformers = transformers;
            return this;
        }

        public ConfigurationBuilder setPath(List<File> path) {
            this.path = path;
            return this;
        }

        public Configuration createConfiguration() {
            return new Configuration(input, output, generateIntermediateJars, transformers, path);
        }
    }
}
