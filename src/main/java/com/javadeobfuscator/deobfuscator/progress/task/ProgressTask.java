package com.javadeobfuscator.deobfuscator.progress.task;

public interface ProgressTask {
    String getName();

    int getCurrentProgress();

    int getMaxProgress();
}
