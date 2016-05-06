.version 49 0 
.class public super TestSimple
.super java/lang/Object 

.method public static main : ([Ljava/lang/String;)V 
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestSimple test (Ljava/io/PrintStream;)V
        return
    .end code 
.end method 

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        aload_0
        ldc "Hello"
        invokevirtual java/io/PrintStream println (Ljava/lang/Object;)V
        return
    .end code
.end method
.end class 
