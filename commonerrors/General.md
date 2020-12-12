## Ordering
In general, files must be deobfuscated in the reverse order in which obfuscators were applied. For example, if your file is obfuscated with Stringer and then Allatori, you would run the Allatori transformers, and then the Stringer ones. Also, if an obfuscator was applied multiple times (e.g. several layers of Allatori), you must run the transformers multiple times. The only exception with this rule is with flow obfuscation transformers, which typically do not need to be run multiple times.

## Write Errors
If you see an error similar to "ClassA could not be found while writing ClassB" it is NOT a deobfuscation error. It just means that you have not added the right libraries. Unless you are trying to run the outputted file (Note: There are no guarantees that it will run, especially with obfuscators like ZKM) it will not affect you.

## Adding Libraries
Adding libraries is usually good practice, but isn't necessary unless you are dealing with reflection obfuscation. If you are having trouble adding libraries, you can use deobfuscator-gui (https://github.com/java-deobfuscator/deobfuscator-gui) and add the library JARs to the "Path" tab. Libraries are what the program needs to run, so rt.jar (look this up!) should always be added. Then, check what JARs need to be available (for example, spigot plugins would need the spigot JAR) and add them accordingly.

## ZIP Compression Errors
If your file cannot be opened in Bytecode Viewer or another decompiler (and it has valid classes), it may be using a certain ZIP compression that crashes decompilers. In that case, be sure to run deobfuscator on it once with no transformers, and then decompile the output ZIP. This will most likely fix the error.

## ASM Errors
If the class files cannot be loaded (an error like IllegalArgumentException appears) and you are **certain** that they are valid, that means that an ASM exploit is being used. Since ASM is unable to parse these files, the deobfuscator is unable to deobfuscate it.

## Normalizers
Normalizers should always be used last. If it is used first, then any string or reference obfuscation transformers applied afterwards will most likely fail, because they require that the classes and methods to retain their obfuscated names.