package com.javadeobfuscator.deobfuscator.progress.task;

public class BasicProgressTask implements ProgressTask {
    private final String name;
    private final int maxProgress;
    private int currentProgress;

    public BasicProgressTask(String name, int maxProgress) {
        this.name = name;
        this.maxProgress = maxProgress;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getCurrentProgress() {
        return currentProgress;
    }

    @Override
    public int getMaxProgress() {
        return maxProgress;
    }

    public void setProgress(int progress) {
        this.currentProgress = progress;
    }
}
