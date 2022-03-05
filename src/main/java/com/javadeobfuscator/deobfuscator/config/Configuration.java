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

import com.fasterxml.jackson.annotation.*;

import java.io.*;
import java.util.*;

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

    /**
     * Allows patching of ASM-crashing exploits.
     */
    @JsonProperty
    private boolean patchAsm;

    /**
     * Must enable for paramorphism obfuscated files.
     */
    @JsonProperty
    private boolean paramorphism;

    /**
     * Must enable for paramorphism v2 obfuscated files.
     */
    @JsonProperty
    private boolean paramorphismV2;

    @JsonProperty
    private boolean debugRulesAnalyzer;

    /**
     * Some obfuscators like to have junk classes. If ALL your libraries are added, enable this to dump troublesome classes. Note that this will not get rid of all
     * junk classes.
     */
    @JsonProperty
    private boolean deleteUselessClasses;

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

    public boolean isPatchAsm() {
        return patchAsm;
    }

    public void setPatchAsm(boolean patchAsm) {
        this.patchAsm = patchAsm;
    }

    public boolean isSmartRedo() {
        return smartRedo;
    }

    public void setSmartRedo(boolean smartRedo) {
        this.smartRedo = smartRedo;
    }

    public boolean isParamorphism() {
        return paramorphism;
    }

    public void setParamorphism(boolean paramorphism) {
        this.paramorphism = paramorphism;
    }

    public boolean isParamorphismV2() {
        return paramorphismV2;
    }

    public void setParamorphismV2(boolean paramorphismV2) {
        this.paramorphismV2 = paramorphismV2;
    }

    public boolean isDebugRulesAnalyzer() {
        return debugRulesAnalyzer;
    }

    public void setDebugRulesAnalyzer(boolean debugRulesAnalyzer) {
        this.debugRulesAnalyzer = debugRulesAnalyzer;
    }

    public boolean isDeleteUselessClasses() {
        return deleteUselessClasses;
    }

    public void setDeleteUselessClasses(boolean deleteUselessClasses) {
        this.deleteUselessClasses = deleteUselessClasses;
    }
}
