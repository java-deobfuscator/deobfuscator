.version 52 0
.class public super TestMathOperations
.super java/lang/Object

.method public static main : ([Ljava/lang/String;)V
    .code stack 10 locals 10
        getstatic java/lang/System out Ljava/io/PrintStream;
        invokestatic TestMathOperations test (Ljava/io/PrintStream;)V
        return
    .end code
.end method

.method public static test : (Ljava/io/PrintStream;)V
    .code stack 10 locals 10
        aload_0
        iconst_1
        iconst_2
        iadd
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        isub
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        imul
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        idiv
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        iand
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        ior
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        irem
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        ishl
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        ishr
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        iushr
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        iconst_2
        ixor
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        i2d
        iconst_2
        i2d
        dadd
        invokevirtual java/io/PrintStream println (D)V
        aload_0
        iconst_1
        i2d
        iconst_2
        i2d
        dsub
        invokevirtual java/io/PrintStream println (D)V
        aload_0
        iconst_1
        i2d
        iconst_2
        i2d
        dcmpl
        invokevirtual java/io/PrintStream println (I)V
        aload_0
        iconst_1
        i2d
        iconst_2
        i2d
        dcmpg
        invokevirtual java/io/PrintStream println (I)V
        return
    .end code
.end method
.end class
