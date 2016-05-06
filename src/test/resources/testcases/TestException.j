.version 49 0 
.class public super TestException
.super java/lang/Object 

.method public static main : ([Ljava/lang/String;)V 
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestException test (Ljava/io/PrintStream;)V
        return
    .end code 
.end method 

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        .catch java/lang/Exception from L0 to L1 using L2
        new java/lang/Exception
        dup
        invokespecial java/lang/Exception <init> ()V
        
    L0:
        athrow
    L1:
    L2:
        aload_0
        swap
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
    .end code
.end method
.end class 
