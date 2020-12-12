## HideAccess Obfuscation
If you see an error while running the transformer, it means that you did not remove the string encryption. Run stringer.StringEncryptionTransformer first and you will be able decrypt Stringer's hide access. Note that you need to add all libraries that are used by the JAR, otherwise you will not be able to decrypt the hide access!

## Other Stringer Transformers
In general, you should only use stringer.HideAccessTransformer, stringer.StringEncryptionTransformer, and stringer.ResourceEncryptionTransformer. The stringer.vX transformers are meant for specific versions of Stringer and should not be combined with the stringer.StringEncryption or stringer.HideAccess transformers.
