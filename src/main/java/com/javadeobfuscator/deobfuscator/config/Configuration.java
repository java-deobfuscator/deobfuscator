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
import java.util.List;

public class Configuration {

    @JsonProperty
    private File input;

    @JsonProperty
    private File output;

    @JsonProperty
    private List<TransformerConfig> transformers;

    @JsonProperty
    private List<File> path;

    @JsonProperty
    private List<File> libraries;

    @JsonProperty
    private List<String> ignoredClasses;

    @JsonProperty
    private boolean smartRedo;

    @JsonProperty
    private boolean verify;

    @JsonProperty
    private boolean detect;

    public File getInput() {
        return input;
    }

    public void setInput(File input) {
        this.input = input;
    }

    public File getOutput() {
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public List<TransformerConfig> getTransformers() {
        return transformers;
    }

    public void setTransformers(List<TransformerConfig> transformers) {
        this.transformers = transformers;
    }

    public List<File> getPath() {
        return path;
    }

    public void setPath(List<File> path) {
        this.path = path;
    }

    public boolean isVerify() {
        return verify;
    }

    public void setVerify(boolean verify) {
        this.verify = verify;
    }

    public List<String> getIgnoredClasses() {
        return ignoredClasses;
    }

    public void setIgnoredClasses(List<String> ignoredClasses) {
        this.ignoredClasses = ignoredClasses;
    }

    public List<File> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<File> libraries) {
        this.libraries = libraries;
    }

    public boolean isDetect() {
        return detect;
    }

    public void setDetect(boolean detect) {
        this.detect = detect;
    }

    public boolean isSmartRedo() {
        return smartRedo;
    }

    public void setSmartRedo(boolean smartRedo) {
        this.smartRedo = smartRedo;
    }
}
