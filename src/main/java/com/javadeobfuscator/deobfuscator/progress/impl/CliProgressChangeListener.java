package com.javadeobfuscator.deobfuscator.progress.impl;

import com.javadeobfuscator.deobfuscator.progress.ProgressChangeListener;
import com.javadeobfuscator.deobfuscator.progress.ProgressMonitorState;
import com.javadeobfuscator.deobfuscator.progress.task.ProgressTask;
import com.javadeobfuscator.deobfuscator.progress.task.StateProgressTask;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.util.HashMap;
import java.util.Map;

public class CliProgressChangeListener implements ProgressChangeListener {
    private final ProgressBarStyle style;
    private final Map<Class<? extends ProgressTask>, ProgressBar> progressBarMap = new HashMap<>();

    public CliProgressChangeListener(ProgressBarStyle style) {
        this.style = style;
    }

    @Override
    public void onObjectiveChange(ProgressTask task) {
        if (task instanceof StateProgressTask && ((StateProgressTask) task).getState() == ProgressMonitorState.TRANSFORMING)
            return;

        ProgressBar previousProgressBar = progressBarMap.get(task.getClass());
        if (previousProgressBar != null)
            previousProgressBar.close();

        progressBarMap.put(task.getClass(), new ProgressBarBuilder()
                .setStyle(style)
                .setUpdateIntervalMillis(50)
                .setTaskName(task.getName())
                .setInitialMax(task.getMaxProgress())
                .build());
    }

    @Override
    public void onProgressUpdate(ProgressTask task, int amount) {
        if (!progressBarMap.containsKey(task.getClass()))
            throw new IllegalArgumentException("No objecitve present!");

        progressBarMap.get(task.getClass()).stepTo(amount);
    }
}
