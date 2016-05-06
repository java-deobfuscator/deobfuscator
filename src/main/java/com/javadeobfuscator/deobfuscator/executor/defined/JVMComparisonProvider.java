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

package com.javadeobfuscator.deobfuscator.executor.defined;

import com.javadeobfuscator.deobfuscator.executor.Context;
import com.javadeobfuscator.deobfuscator.executor.StackObject;
import com.javadeobfuscator.deobfuscator.executor.defined.types.JavaClass;
import com.javadeobfuscator.deobfuscator.executor.providers.ComparisonProvider;
import com.javadeobfuscator.deobfuscator.org.objectweb.asm.Type;

public class JVMComparisonProvider extends ComparisonProvider {
    @Override
    public boolean checkEquality(StackObject first, StackObject second, Context context) {
        if (first.value instanceof JavaClass && second.value instanceof JavaClass) {
            return first.as(JavaClass.class).equals(second.value);
        }
        return first == second;
    }

    @Override
    public boolean canCheckEquality(StackObject first, StackObject second, Context context) {
        return first.value instanceof JavaClass && second.value instanceof JavaClass;
    }

    @Override
    public boolean instanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean checkcast(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canCheckInstanceOf(StackObject target, Type type, Context context) {
        return false;
    }

    @Override
    public boolean canCheckcast(StackObject target, Type type, Context context) {
        return false;
    }
}
