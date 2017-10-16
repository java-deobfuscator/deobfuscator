/*
 * Copyright 2016 Sam Sun <me@samczsun.com>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.javadeobfuscator.deobfuscator.analyzer.frame;

import java.util.Collections;
import java.util.List;

public class MethodFrame extends Frame {

    private Frame instance;
    private List<Frame> args;
    private String owner;
    private String name;
    private String desc;

    public MethodFrame(int opcode, String owner, String name, String desc, Frame instance, List<Frame> args) {
        super(opcode);
        this.instance = instance;
        this.args = args;
        this.owner = owner;
        this.name = name;
        this.desc = desc;
        if (this.instance != null) {
            this.instance.children.add(this);
        }
        for (Frame arg : this.args) {
            arg.children.add(this);
        }
    }

    public List<Frame> getArgs() {
        return Collections.unmodifiableList(args);
    }

    public String getDesc() {
        return desc;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public Frame getInstance() {
        return instance;
    }
}
