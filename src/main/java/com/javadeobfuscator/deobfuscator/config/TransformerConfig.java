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

import com.javadeobfuscator.deobfuscator.transformers.Transformer;
import com.javadeobfuscator.javavm.*;

import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.*;

public class TransformerConfig {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ConfigOptions {
        Class<? extends TransformerConfig> configClass();
    }

    private final Class<? extends Transformer> implementation;

    private List<Consumer<VirtualMachine>> vmModifiers = new ArrayList<>();

    public TransformerConfig(Class<? extends Transformer> implementation) {
        this.implementation = implementation;
    }

    public Class<? extends Transformer> getImplementation() {
        return implementation;
    }

    public List<Consumer<VirtualMachine>> getVmModifiers() {
        return vmModifiers;
    }

    public void setVmModifiers(List<Consumer<VirtualMachine>> vmModifiers) {
        this.vmModifiers = vmModifiers;
    }

    public static TransformerConfig configFor(Class<? extends Transformer> implementation) {
        TransformerConfig.ConfigOptions options = implementation.getAnnotation(TransformerConfig.ConfigOptions.class);
        if (options != null) {
            Class<? extends TransformerConfig> configClass = options.configClass();

//            try {
//                return configClass.getConstructor(Class.class).newInstance(implementation);
//            } catch (NoSuchMethodException ignored) {
//            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
//                throw new RuntimeException("Error instantiating TransformerConfig for " + implementation.getCanonicalName(), e);
//            }

            try {
                return configClass.getConstructor().newInstance();
            } catch (NoSuchMethodException ignored) {
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException("Error instantiating TransformerConfig for " + implementation.getCanonicalName(), e);
            }

            throw new RuntimeException("Could not find suitable constructor for TransformerConfig");
        } else {
            return new TransformerConfig(implementation);
        }
    }
}
