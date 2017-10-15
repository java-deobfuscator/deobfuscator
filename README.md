# Deobfuscator [![Build Status](https://ci.samczsun.com/buildStatus/icon?job=java-deobfuscator/Deobfuscator)](https://ci.samczsun.com/job/java-deobfuscator/job/Deobfuscator)

This is an all-in-one Java deobfuscator which will deobfuscate code obfuscated by most obfuscators available on the market.

## What can the deobfuscator do?

The deobfuscator supports deobfuscation of transformations such as string literal encryption, or reflection obfuscation. These transformations have been hard coded for a specific obfuscator, but generic deobfuscators are also available.

## What can't the deobfuscator do?

Things like method names, class names, etc cannot be deobfuscated because the renaming is irreversible: the information needed to deobfuscate is removed.

## Examples

### As a library

```java
public class SomeRandomDeobfuscator {
    public static void main(String[] args) throws Throwable {
        Configuration config = new Configuration();
        config.setInput(new File("input.jar"));
        config.setOutput(new File("output.jar"));
        config.setPath(Arrays.asList(
                new File("C:\\Program Files\\Java\\jdk_8\\jre\\lib\\rt.jar"),
                new File("C:\\Program Files\\Java\\jdk_8\\jre\\lib\\jce.jar"),
                new File("C:\\Program Files\\Java\\jdk_8\\jre\\lib\\ext\\jfxrt.jar"),
                new File("C:\\Program Files\\Java\\jdk_8\\lib\\tools.jar")
        ));
        config.setTransformers(Arrays.asList(
                TransformerConfig.configFor(PeepholeOptimizer.class)
        ));
        new Deobfuscator(config).start();
    }
}
```

### CLI

If you don't want to import the project, you can always use the command line interface.

| Argument | Description |
| --- | --- |
| --config | The configuration file |

You may specify multiple transformers, and they will be applied in the order given. Order does matter as sometimes one transformation depends on another not being present.

If you wish to use one of the default transformers, then you may remove the `com.javadeobfuscator.deobfuscator.transformers` prefix.

Here is a sample `config.yaml`:

```yaml
input: input.jar
output: output.jar
transformers:
  - normalizer.MethodNormalizer:
      mapping-file: normalizer.txt
  - stringer.StringEncryptionTransformer
  - normalizer.ClassNormalizer: {}
    normalizer.FieldNormalizer: {}
```

For more details, please take a look at the wiki.

## Transformers

Official transformers are linked via the `Transformers` class.

| Transformer | Canonical Name |  Description |
| --- | --- | --- |
| Allatori.STRING_ENCRYPTION | allatori.StringEncryptionTransformer | Decrypts strings encrypted by Allatori |
| DashO.STRING_ENCRYPTION | dasho.StringEncryptionTransformer | Decrypts strings encrypted by DashO |
| SkidSuite.STRING_ENCRYPTION | skidsuite2.StringEncryptionTransformer | Decrypts strings encrypted by SkidSuite2 |
| Stringer.STRING_ENCRYPTION | stringer.StringEncryptionTransformer | Decrypts strings encrypted by Stringer |
| Stringer.INVOKEDYNAMIC | stringer.InvokedynamicTransformer | Decrypts invokedynamic obfuscated calls by Stringer (Below version 3.0.0) |
| Stringer.REFLECTION_OBFUSCATION | stringer.ReflectionObfuscationTransformer | Decrypts reflection obfuscated calls by Stringer (Below version 3.0.0) |
| Stringer.HIDEACCESS_OBFUSCATION | stringer.HideAccessObfuscationTransformer | Decrypts hide access by Stringer (Included invokedynamic and reflection) |
| Zelix.STRING_ENCRYPTION | zelix.StringEncryptionTransformer | Decrypts strings encrypted by Zelix |
| Zelix.REFLECTION_OBFUSCATION | zelix.ReflectionObfuscationTransformer | Decrypts reflection obfuscated calls by Zelix |
| General.PEEPHOLE_OPTIMIZER | general.peephole.PeepholeOptimizer| Optimizes the code |
| General.Removers.SYNTHETIC_BRIDGE | general.remover.SyntheticBridgeRemover | Removes synthetic and bridge modifiers from all methods and fields |
| General.Removers.LINE_NUMBER | general.remover.LineNumberRemover | Removes line number metadata |
| General.Removers.ILLEGAL_VARARGS | general.remover.IllegalVarargsRemover | Unmangles methods marked as variadic but aren't really |
| General.Removers.ILLEGAL_SIGNATURE | general.remover.IllegalSignatureRemover | Removes illegal signatures from members |
| Normalizer.CLASS_NORMALIZER | normalizer.ClassNormalizer | Renames all classes to Class<number> |
| Normalizer.METHOD_NORMALIZER | normalizer.MethodNormalizer | Renames all methods to Method<number> |
| Normalizer.FIELD_NORMALIZER | normalizer.FieldNormalizer | Renames all fields to Field<number> |  
| Normalizer.PACKAGE_NORMALIZER | normalizer.PackageNormalizer | Renames all packages to Package<number> |
| Normalizer.SOURCEFILE_CLASS_NORMALIZER | normalizer.SourceFileClassNormalizer | Recovers `SourceFile` attributes when possible |
| Normalizer.VARIABLE_NORMALIZER | normalizer.VariableNormalizer | Renames all local variables to var<number> |

## Downloads

The latest build can be downloaded from my [CI Server](https://ci.samczsun.com/job/java-deobfuscator/job/Deobfuscator/)

## Supported Obfuscators

[Zelix Klassmaster](http://www.zelix.com/)  
[Stringer](https://jfxstore.com/stringer/)  
[Allatori](http://www.allatori.com/)  
[DashO](https://www.preemptive.com/products/dasho/overview)  
[DexGuard](https://www.guardsquare.com/dexguard)  
[Smoke](https://newtownia.net/smoke)  
SkidSuite2 (dead, some forks are listed [here](https://github.com/tetratec/SkidSuite2/network/members))  
Generic obfuscation

## FAQs

#### I got an error that says "Could not locate a class file"
You need to specify all the JARs that the input file references. You'll almost always need to add `rt.jar`
(which contains all the classes used by the Java Runtime)

#### I got an error that says "A StackOverflowError occurred during deobfuscation"
Increase your stack size. For example, `java -Xss128m -jar deobfuscator.jar`

#### Does this work on Android apps?
Technically, yes, you could use something like [dex2jar](https://github.com/pxb1988/dex2jar) or [enjarify](https://github.com/storyyeller/enjarify), but try [simplify](https://github.com/CalebFenton/simplify) first.
It's a deobfuscator of sorts built specifically for Android.

## Licensing

Java Deobfuscator is licensed under the Apache 2.0 license.
