/*
 * Copyright 2018 Sam Sun <github-contact@samczsun.com>
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

package com.javadeobfuscator.deobfuscator.transformers.classguard;

public enum ClassGuardData {
    V4_LINUX_X64("lib/libcg_x64_linux.sox", 0x60C40, 0x615D0, 0x617F0, 0x61768),
    V4_OSX_X64("lib/libcg_x64_osx.jnilibx", 0x375E0, 0x3D189, 0x3D31C, 0x3D299),
    V4_OSX6_X64("lib/libcg_x64_osx6.jnilibx", 0x375E0, 0x3D189, 0x3D31C, 0x3D299),
    V4_WIN_X64("lib/libcg_x64_win.dllx", 0x43400, 0x441a0, 0x44230, 0x44110),
    V4_LINUX_X86("lib/libcg_x86_linux.sox", 0x54E40, 0x5578C, 0x559A4, 0x55920),
    V4_OSX_X86("lib/libcg_x86_osx.jnilibx", 0x33EE8, 0x38D9F, 0x38F32, 0x38EAF),
    V4_WIN_X86("lib/libcg_x86_win.dllx", 0x38E00, 0x39990, 0x39B30, 0x39A18),

    V5_LINUX_X64("lib/libcg_x64_linux.sox", 0x60A80, 0x61410, 0x61630, 0x615A8),
    V5_OSX_X64("lib/libcg_x64_osx.jnilibx", 0x375E0, 0x3D292, 0x3D321, 0x379E8),
    V5_WIN_X64("lib/libcg_x64_win.dllx", 0x43000, 0x43C80, 0x43E30, 0x43D10),
    V5_LINUX_X86("lib/libcg_x86_linux.sox", 0x54C60, 0x555AC, 0x557C4, 0x55740),
    V5_WIN_X86("lib/libcg_x86_win.dllx", 0x38C00, 0x398A0, 0x39928, 0x39818);

    private final String targetFile;
    private final int modulusOffset;
    private final int exponentOffset;
    private final int classEncKeyOffset;
    private final int rsrcEncKeyOffset;

    ClassGuardData(String targetFile, int modulusOffset, int exponentOffset, int classEncKeyOffset, int rsrcEncKeyOffset) {
        this.targetFile = targetFile;
        this.modulusOffset = modulusOffset;
        this.exponentOffset = exponentOffset;
        this.classEncKeyOffset = classEncKeyOffset;
        this.rsrcEncKeyOffset = rsrcEncKeyOffset;
    }

    public String getTargetFile() {
        return targetFile;
    }

    public int getModulusOffset() {
        return modulusOffset;
    }

    public int getExponentOffset() {
        return exponentOffset;
    }

    public int getClassEncKeyOffset() {
        return classEncKeyOffset;
    }

    public int getRsrcEncKeyOffset() {
        return rsrcEncKeyOffset;
    }
}
