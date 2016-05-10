# Deobfuscator
[![Build Status](https://ci.samczsun.com/buildStatus/icon?job=Deobfuscator)](https://ci.samczsun.com/job/Deobfuscator/)

This is an all-in-one Java deobfuscator which will deobfuscate code obfuscated by most obfuscators available on the market.

## What can the deobfuscator do?

The deobfuscator supports deobfuscation of transformations such as string literal encryption, or reflection obfuscation. These transformations have been hard coded for a specific obfuscator, but generic deobfuscators are also available.

## What can't the deobfuscator do?

Things like method names, class names, etc cannot be deobfuscated because there renaming is irreversible. The information needed to deobfuscate is removed.

## Examples

### As a library

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

### CLI

If you don't want to import the project, you can always use the command line interface. There are four arguments that are taken.

| Argument | Description |
| --- | --- |
| -input | The JAR to deobfuscate |
| -output | The file to write to |
| -transformer | A canonical name of the transformer class|
| -path | A dependency of the JAR being deobfuscated |

You may specify multiple transformers, and they will be applied in the order given. Order does matter as sometimes one transformation depends on another not being present.

If you wish to use one of the default transformers, then you may remove the `com.javadeobfuscator.deobfuscator.transformers` prefix. For example, the command below will do the same as the example above.

`java -jar deobfuscator.jar -input input.jar -output output.jar -transformer general.SyntheticBridgeTransformer -path path/to/rt.jar`

## Transformers

Official transformers are linked via the `Transformers` class.

| Transformer | Canonical Name |  Description |
| --- | --- | --- |
| Allatori.STRING_ENCRYPTION | allatori.StringEncryptionTransformer | Decrypts strings encrypted by Allatori |
| DashO.STRING_ENCRYPTION | dasho.StringEncryptionTransformer | Decrypts strings encrypted by DashO |
| Stringer.STRING_ENCRYPTION | stringer.StringEncryptionTransformer | Decrypts strings encrypted by Stringer |
| Stringer.INVOKEDYNAMIC | stringer.InvokedynamicTransformer | Decrypts invokedynamic obfuscated calls by Stringer |
| Stringer.REFLECTION_OBFUSCATION | stringer.ReflectionObfuscationTransformer | Decrypts reflection obfuscated calls by Stringer |
| Zelix.STRING_ENCRYPTION | zelix.StringEncryptionTransformer | Decrypts strings encrypted by Zelix |
| Zelix.REFLECTION_OBFUSCATION | zelix.ReflectionObfuscationTransformer | Decrypts reflection obfuscated calls by Zelix |
| General.SYNTHETIC_BRIDGE | general.SyntheticBridgeTransformer | Removes synthetic and bridge modifiers from all methods and fields |
| General.PEEPHOLE_OPTIMIZER | general.peephole.PeepholeOptimizer| Optimizes the code |
| Normalizer.CLASS_NORMALIZER | normalizer.ClassNormalizer | Renames all classes to Class<number> |
| Normalizer.METHOD_NORMALIZER | normalizer.MethodNormalizer | Renames all methods to Method<number> |
| Normalizer.FIELD_NORMALIZER | normalizer.FieldNormalizer | Renames all fields to Field<number> |  

## Downloads

The latest build can be downloaded from my [CI Server](https://ci.samczsun.com/job/Deobfuscator/)

## Supported Obfuscators

[Zelix Klassmaster](http://www.zelix.com/)  
[Stringer](https://jfxstore.com/stringer/)  
[Allatori](http://www.allatori.com/)  
[DashO](https://www.preemptive.com/products/dasho/overview)  
[DexGuard](https://www.guardsquare.com/dexguard)  
Generic obfuscation

## Licensing

Java Deobfuscator is licensed under the Apache 2.0 license.