package com.javadeobfuscator.deobfuscator.executor.exceptions;

public class ExecutionException extends RuntimeException {
    public String clazz = "";
    public String method = "";

    public ExecutionException(String msg) {
        super(msg);
    }
    
    public ExecutionException(Throwable t) {
        super(t);
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " @ " + clazz + " " + method;
    }
}
