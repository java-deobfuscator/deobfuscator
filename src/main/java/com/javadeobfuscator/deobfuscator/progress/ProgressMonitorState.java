package com.javadeobfuscator.deobfuscator.progress;

public enum ProgressMonitorState {
    LOADING_CLASSPATH("Loading classpath"),
    LOADING_INPUT("Loading input"),
    COMPUTING_CALLERS("Computing callers"),
    TRANSFORMING("Transforming"),
    WRITING_PASSTHROUGH("Writing passthrough files"),
    WRITING_CLASSES("Writing classes");

    private final String name;

    ProgressMonitorState(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
