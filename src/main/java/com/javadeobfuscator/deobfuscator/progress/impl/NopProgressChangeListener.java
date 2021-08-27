package com.javadeobfuscator.deobfuscator.progress.impl;

import com.javadeobfuscator.deobfuscator.progress.ProgressChangeListener;
import com.javadeobfuscator.deobfuscator.progress.task.ProgressTask;

public class NopProgressChangeListener implements ProgressChangeListener {

    @Override
    public void onObjectiveChange(ProgressTask task) {

    }

    @Override
    public void onProgressUpdate(ProgressTask task, int amount) {

    }
}
