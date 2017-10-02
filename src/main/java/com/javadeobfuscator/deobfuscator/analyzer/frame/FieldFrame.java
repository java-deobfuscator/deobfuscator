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

public class FieldFrame extends Frame {

    private Frame instance;
    private Frame obj;
    private String owner;
    private String name;
    private String desc;

    public FieldFrame(int opcode, String owner, String name, String desc, Frame instance, Frame obj) {
        super(opcode);
        this.instance = instance;
        this.obj = obj;
        this.owner = owner;
        this.name = name;
        this.desc = desc;

        if (this.instance != null) {
            this.instance.children.add(this);
        }
        if (this.obj != null) {
            this.obj.children.add(this);
        }
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

    public Frame getObj() {
        return obj;
    }
}
