.version 49 0 
.class public super TestJSR
.super java/lang/Object 

.method public static main : ([Ljava/lang/String;)V 
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestJSR test (Ljava/io/PrintStream;)V
        return
    .end code 
.end method 

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        jsr L0
        aload_0
        ldc "Out"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        return        
L0:
        aload_0
        ldc "In"
        invokevirtual Method java/io/PrintStream println (Ljava/lang/Object;)V
        astore_1
        ret 1
    .end code
.end method
.end class 
