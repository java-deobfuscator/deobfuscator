package com.javadeobfuscator.deobfuscator.progress.task;

import com.javadeobfuscator.deobfuscator.progress.ProgressMonitorState;

public class StateProgressTask extends BasicProgressTask{
    private final ProgressMonitorState state;

    public StateProgressTask(ProgressMonitorState state, int maxProgress) {
        super(state.getName(), maxProgress);
        this.state = state;
    }

    public ProgressMonitorState getState() {
        return state;
    }
}
