.version 49 0 
.class public super TestException1
.super java/lang/Object 

.method public static main : ([Ljava/lang/String;)V 
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestException1 test (Ljava/io/PrintStream;)V
        return
    .end code 
.end method 

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        .catch java/lang/Exception from L0 to L1 using L2
        .catch java/lang/RuntimeException from L0 to L1 using L3
        new java/lang/RuntimeException
        dup
        invokespecial java/lang/RuntimeException <init> ()V
        
    L0:
        athrow
    L1:
    L2:
        aload_0
        swap
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
    L3:
        aload_0
        ldc "Worked"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
    .end code
.end method
.end class 
