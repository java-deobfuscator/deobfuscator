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

package com.javadeobfuscator.deobfuscator.executor.defined.types;

public class JavaMethodHandle extends JavaHandle {
    public final String clazz;
    public final String name;
    public final String desc;
    public final String type;

    public JavaMethodHandle(String clazz, String name, String desc, String type) {
        this.clazz = clazz;
        this.name = name;
        this.desc = desc;
        this.type = type;
    }
}
