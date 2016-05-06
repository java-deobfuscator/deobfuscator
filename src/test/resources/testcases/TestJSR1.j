.version 49 0 
.class public super TestJSR1
.super java/lang/Object 

.method public static main : ([Ljava/lang/String;)V 
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestJSR1 test (Ljava/io/PrintStream;)V
        return
    .end code 
.end method 

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        jsr L0
        aload_0
        ldc "L0"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
L0:
        jsr L1
        aload_0
        ldc "L1"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
L1:
        jsr L2
        aload_0
        ldc "L2"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return
L2:
        swap
        pop
        swap
        pop
        astore_1
        ret 1
    .end code
.end method
.end class 
