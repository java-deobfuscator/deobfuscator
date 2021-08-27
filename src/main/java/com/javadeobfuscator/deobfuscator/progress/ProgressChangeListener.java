package com.javadeobfuscator.deobfuscator.progress;

import com.javadeobfuscator.deobfuscator.progress.task.ProgressTask;

public interface ProgressChangeListener {
    void onObjectiveChange(ProgressTask task);

    void onProgressUpdate(ProgressTask task, int amount);
}
