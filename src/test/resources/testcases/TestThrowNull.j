.version 49 0
.class public super TestThrowNull
.super java/lang/Object

.method public static main : ([Ljava/lang/String;)V
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestThrowNull test (Ljava/io/PrintStream;)V
        return
    .end code
.end method

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        .catch java/lang/Throwable from L0 to L1 using L1
        aconst_null
L0:
        athrow
L1:
        aload_0
        swap
        invokevirtual java/io/PrintStream println (Ljava/lang/Object;)V
        return
    .end code
.end method
.end class
