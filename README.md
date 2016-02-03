# Deobfuscator

This is an all-in-one Java deobfuscator which will deobfuscate nearly all transformations applied by nearly all obfuscators.

Of course, things like renaming won't be deobfuscated because that is literally impossible. The information is simply not there.

Instead, this deobfuscator can undo transformations such as string encryption, reflection obfuscation, and invokedynamic obfuscation.

There is no command line interface yet. You will have to clone this repo and manually invoke the proper methods.

## Example

```java
public class SomeRandomDeobfuscator {
    public static void main(String[] args) throws Throwable {
        new Deobfuscator()
            .withInput(new File("input.jar"))
            .withOutput(new File("output.jar"))
            .withClasspath(new File("path/to/rt.jar"))
            .withTransformer(Transformers.Generic.SYNTHETIC_BRIDGE)
            .start();
    }
}
```

## Transformers

Official transformers are linked via the `Transformers` class.

| Transformer | Description |
| --- | --- |
| Allatori.STRING_ENCRYPTION | Decrypts strings encrypted by Allatori |
| DashO.STRING_ENCRYPTION | Decrypts strings encrypted by DashO |
| Stringer.STRING_ENCRYPTION | Decrypts strings encrypted by Stringer |
| Stringer.INVOKEDYNAMIC | Decrypts invokedynamic obfuscated calls by Stringer |
| Stringer.REFLECTION_OBFUSCATION | Decrypts reflection obfuscated calls by Stringer |
| General.SYNTHETIC_BRIDGE | Removes synthetic and bridge modifiers from all methods and fields |


## Supported Obfuscators

[Zelix Klassmaster](http://www.zelix.com/)  
[Stringer](https://jfxstore.com/stringer/)  
[Allatori](http://www.allatori.com/)  
[DashO](https://www.preemptive.com/products/dasho/overview)  
[DexGuard](https://www.guardsquare.com/dexguard)  
Generic obfuscation

## Licensing

Java Deobfuscator is licensed under the Apache 2.0 license.