/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.transformers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.tree.MethodNode;
import com.javadeobfuscator.deobfuscator.utils.WrappedClassNode;

public abstract class Transformer {

    protected final Map<String, WrappedClassNode> classes;
    protected final Map<String, WrappedClassNode> classpath;
    protected final Map<MethodNode, List<Entry<WrappedClassNode, MethodNode>>> callers;

    protected Deobfuscator deobfuscator;

    public Transformer(Map<String, WrappedClassNode> classes, Map<String, WrappedClassNode> classpath) {
        this.classes = classes;
        this.classpath = classpath;
        this.callers = null;
    }

    public Collection<WrappedClassNode> classNodes() {
        return this.classes.values();
    }

    public abstract void transform() throws Throwable;

    // heh
    public void setDeobfuscator(Deobfuscator deobfuscator) {
        this.deobfuscator = deobfuscator;
    }
}
