package com.javadeobfuscator.deobfuscator.executor.exceptions;

public class ExecutionException extends RuntimeException {
    public ExecutionException(String msg) {
        super(msg);
    }
    
    public ExecutionException(Throwable t) {
        super(t);
    }
}
