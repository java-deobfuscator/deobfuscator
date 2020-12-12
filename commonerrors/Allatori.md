## Flow Obfuscation
The latest versions of Allatori contain some flow obfuscation which moves around some instructions, so it is necessary to run allatori.FlowObfuscationTransformer to fix this. This transformer should always work, but if it doesn't, open an issue (an exception is if there is another type of flow obfuscation layered over it)! Note: It is not necessary to run this before allatori.StringEncryptionTransformer.

## Transformers to Use
allatori.StringEncryptionTransformer works the fastest, although in rare cases where it doesn't work, you can use allatori.string.StringEncryptionTransformer.