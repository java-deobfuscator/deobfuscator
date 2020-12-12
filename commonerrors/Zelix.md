## String Encryption
The string encryption transformer (zelix.StringEncryptionTransformer) supports ZKM 5-8. If the file was obfuscated using ZKM 9 and up, the strings will only be decrypted if the file lacks method parameter changes.

## Reference/Reflection Obfuscation
The reference obfuscation transformer supports both the non-invokedynamic and invokedynamic variants of the obfuscation, but only if method parameter changes aren't enabled. Note that you must use the Zelix string encryption transformer first, or the deobfuscation will fail. Also note that you must add the libraries to path or the deobfuscation will also fail!

## Flow Obfuscation
The flow obfuscation transformer should generally be applied after the string encryption transformer, but if a file was obfuscated with multiple layers of ZKM, you should instead use FlowObfuscationTransformer first. Also, the flow deobfuscation will be limited if the code was obfuscated with method parameter changes.

## Transformers to Use
You should stick to using zelix.StringEncryptionTransformer as it is the fastest and decrypts the most cases. It is not recommended to combine the zelix.string transformers with zelix.StringEncryptionTransformer.