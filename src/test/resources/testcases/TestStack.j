.version 49 0 
.class public super TestStack
.super java/lang/Object 

.method public static main : ([Ljava/lang/String;)V 
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestStack test (Ljava/io/PrintStream;)V
        return
    .end code 
.end method 

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        ldc "Hello"
        astore_1
        iconst_0
        aload_1
        dup_x1
        pop
        ifeq L0
        aload_0
        ldc "Failed"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
L0:
        aload_0
        swap
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
    .end code
.end method
.end class 
