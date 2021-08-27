package com.javadeobfuscator.deobfuscator.progress.task;

public class TransformerProgressTask extends BasicProgressTask {
    private final Class<?> transformer;

    public TransformerProgressTask(int classCount, Class<?> transformer) {
        super(transformer.getSimpleName(), classCount);
        this.transformer = transformer;
    }

    public Class<?> getTransformer() {
        return transformer;
    }
}
