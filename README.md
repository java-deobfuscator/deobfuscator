# Deobfuscator
 
This project aims to deobfuscate most commercially-available obfuscators for Java.

## Updates
To download an updated version of Java Deobfuscator, go to the releases tab.

If you would like to run this program with a GUI, go to https://github.com/java-deobfuscator/deobfuscator-gui and grab a download. Put the deobfuscator-gui.jar in the same folder as deobfuscator.jar.

## Quick Start

* [Download](https://github.com/java-deobfuscator/deobfuscator/releases) the deobfuscator. The latest build is recommended.
* If you know what obfuscators were used, skip the next two steps
* Create `detect.yml` with the following contents. Replace `input.jar` with the name of the input
```yaml
input: input.jar
detect: true
```
* Run `java -jar deobfuscator.jar --config detect.yml` to determine the obfuscators used
* Create `config.yml` with the following contents. Replace `input.jar` with the name of the input
```yaml
input: input.jar
output: output.jar
transformers:
  - [fully-qualified-name-of-transformer]
  - [fully-qualified-name-of-transformer]
  - [fully-qualified-name-of-transformer]
  - ... etc
``` 
* Run `java -jar deobfuscator.jar`
* Re-run the detection if the JAR was not fully deobfuscated - it's possible to layer obfuscations

Take a look at [USAGE.md](USAGE.md) or [wiki](https://github.com/java-deobfuscator/deobfuscator/wiki) for more information.

## It didn't work

If you're trying to recover the names of classes or methods, tough luck. That information is typically stripped out and there's no way to recover it.

If you are using one of our transformers, check out the [commonerrors](commonerrors) folder to check for tips.

Otherwise, check out [this guide](CUSTOMTRANSFORMER.md) on how to implement your own transformer (also, open a issue/PR so I can add support for it)

## Supported Obfuscators

* [Zelix Klassmaster](http://www.zelix.com/)  
* [Stringer](https://jfxstore.com/stringer/)  
* [Allatori](http://www.allatori.com/)  
* [DashO](https://www.preemptive.com/products/dasho/overview)  
* [DexGuard](https://www.guardsquare.com/dexguard)    
* [ClassGuard](https://www.zenofx.com/classguard/)  
* Smoke (dead, website archive [here](https://web.archive.org/web/20170918112921/https://newtownia.net/smoke/))   
* SkidSuite2 (dead, archive [here](https://github.com/GenericException/SkidSuite/tree/master/archive/skidsuite-2))

## List of Transformers

The automagic detection should be able to recommend the transformers you'll need to use. However, it may not be up to date. If you're familiar with Java reverse engineering, feel free to [take a look around](src/main/java/com/javadeobfuscator/deobfuscator/transformers) and use what you need. 

## FAQs

#### I got an error that says "Could not locate a class file"
You need to specify all the JARs that the input file references. You'll almost always need to add `rt.jar`
(which contains all the classes used by the Java Runtime)

#### I got an error that says "A StackOverflowError occurred during deobfuscation"
Increase your stack size. For example, `java -Xss128m -jar deobfuscator.jar`

#### Does this work on Android apps?
Technically, yes, you could use something like [dex2jar](https://github.com/pxb1988/dex2jar)
or [enjarify](https://github.com/storyyeller/enjarify). However, dex -> jar conversion is lossy at best.
Try [simplify](https://github.com/CalebFenton/simplify) or [dex-oracle](https://github.com/CalebFenton/dex-oracle) first.
They were written specifically for Android apps.

## Licensing

Java Deobfuscator is licensed under the Apache 2.0 license.
