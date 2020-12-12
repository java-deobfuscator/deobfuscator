## String Encryption
If you get an error relating to SourceResult, it is because DashO's flow obfuscation was applied, which messes with the string decryptor. Run dasho.FlowObfuscationTransformer first, and you will able to decrypt all the strings.

## Transformers to Use
dasho.StringEncryptionTransformer works the fastest (and it cleans up properly), although in rare cases where it doesn't work (you get an error not related to SourceResult), you can use dasho.string.StringEncryptionTransformer.