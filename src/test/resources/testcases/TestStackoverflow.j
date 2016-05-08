.version 49 0
.class public super TestStackoverflow
.super java/lang/Object

.method public static main : ([Ljava/lang/String;)V
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestStackoverflow test (Ljava/io/PrintStream;)V
        return
    .end code
.end method

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        .catch java/lang/Throwable from L0 to L1 using L0
        iconst_0
        istore_1
        aconst_null
L0:
        pop
        new java/lang/RuntimeException
        dup
        invokespecial java/lang/RuntimeException <init> ()V
        iinc 1 1
        iload_1
        ldc 100000
        if_icmpeq L1
        athrow
L1:
        aload_0
        iload_1
        invokevirtual java/io/PrintStream println (I)V
        return
    .end code
.end method
.end class
