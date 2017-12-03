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

package com.javadeobfuscator.deobfuscator.utils;

import com.google.common.collect.ImmutableSet;
import com.javadeobfuscator.javavm.StackTraceHolder;
import com.javadeobfuscator.javavm.VirtualMachine;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class DisableInitializationHook {
    private final Set<String> whitelist;

    public DisableInitializationHook(Collection<String> whitelist) {
        this.whitelist = ImmutableSet.copyOf(whitelist);
    }

    public void register(VirtualMachine vm) {
        vm.beforeCallHooks.add(info -> {
            if (!info.is("sun/misc/Unsafe", "ensureClassInitialized", "(Ljava/lang/Class;)V")) {
                return;
            }
            info.setReturnValue(vm.getNull());
        });
        vm.beforeCallHooks.add(info -> {
            if (!info.is("java/lang/Class", "forName0", "(Ljava/lang/String;ZLjava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/Class;")) {
                return;
            }
            List<StackTraceHolder> stacktrace = vm.getStacktrace();
            if (stacktrace.size() < 3) {
                return;
            }
            if (!whitelist.contains(stacktrace.get(2).getClassNode().name)) {
                return;
            }
            info.getParams().set(1, vm.newBoolean(false));
        });
    }
}
