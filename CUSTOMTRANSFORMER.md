# Custom Transformers
So you need to deobfuscate some custom obfuscation? Maybe this repo isn't updated for the latest obfuscators?
Here's some tips for writing a custom transformer.

### Disassemble your target program  
Try using [Krakatau](https://github.com/Storyyeller/Krakatau), it's pretty good.

### Identify patterns  
Most of the time obfuscations are applied with a very simple pattern. For example, string encryption may look like this    
```jasmin
ldc "someencryptedstring"
invokestatic foo/DecryptorClass decrypt (Ljava/lang/String;)Ljava/lang/String;
```
```jasmin
ldc "someencryptedstring"
dup
invokevirtual java/lang/String length ()I
ldc 52
sipush 29
ixor
imul
invokestatic foo/DecryptorClass decrypt(Ljava/lang/String;I)Ljava/lang/String; 
```
If you can identify the common pattern being used, you can use [InstructionPattern](https://github.com/java-deobfuscator/deobfuscator/blob/master/src/main/java/com/javadeobfuscator/deobfuscator/matcher/InstructionPattern.java)
to quickly isolate the relevant bytecodes.  
For example, at the time of writing Stringer v3 follows some very simple [patterns](https://github.com/java-deobfuscator/deobfuscator/blob/master/src/main/java/com/javadeobfuscator/deobfuscator/transformers/stringer/v3/utils/Constants.java#L60).    
### Deobfuscate it  
[JavaVM](https://github.com/java-deobfuscator/javavm) provides a very easy way to execute unsafe bytecodes
and intercept/modify results. Again, check out the [Stringer transformers](https://github.com/java-deobfuscator/deobfuscator/blob/master/src/main/java/com/javadeobfuscator/deobfuscator/transformers/stringer/v3/StringEncryptionTransformer.java) for sample usages

### Open a ticket
If that didn't work, or if you think the repo needs an update, open a ticket and provide the file (or a reproducible sample).
This way, the next person who comes along can use an existing transformer!