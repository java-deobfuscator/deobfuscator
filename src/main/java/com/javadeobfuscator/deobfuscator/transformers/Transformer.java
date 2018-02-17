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
import java.util.HashMap;
import java.util.Map;
import com.javadeobfuscator.deobfuscator.Deobfuscator;
import com.javadeobfuscator.deobfuscator.config.TransformerConfig;
import com.javadeobfuscator.deobfuscator.exceptions.*;
import com.javadeobfuscator.javavm.*;
import com.javadeobfuscator.javavm.exceptions.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Transformer<T extends TransformerConfig> implements Opcodes {

    protected Map<String, ClassNode> classes;
    protected Map<String, ClassNode> classpath;
    protected Map<ClassNode, ClassReader> readers;

    private Deobfuscator deobfuscator;
    private T config;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public void init(Deobfuscator deobfuscator, TransformerConfig config, Map<String, ClassNode> classes, Map<String, ClassNode> classpath, Map<ClassNode, ClassReader> readers) {
        this.deobfuscator = deobfuscator;
        this.classes = classes;
        this.classpath = classpath;
        this.config = (T) config;
        this.readers = readers;
    }

    public T getConfig() {
        return this.config;
    }

    public Collection<ClassNode> classNodes() {
        return this.classes.values();
    }

    /**
     * @return whether some modifications were made
     * @throws WrongTransformerException If this transformer doesn't apply
     */
    public abstract boolean transform() throws Throwable, WrongTransformerException; // Throwable will be removed soon

    public Deobfuscator getDeobfuscator() {
        return this.deobfuscator;
    }

    protected void oops(String why, Object... args) {
        logger.debug("oops: " + why, args);
    }
    protected void fail(String why, Object... args) {
        logger.error("fail: " + why, args);
    }
}
