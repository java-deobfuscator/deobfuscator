## String Encryption
The string encryption transformer supports ZKM 5-8. If the file was obfuscated using ZKM 9 and up, the strings will only be decrypted if the file lacks method parameter changes.

## Reference Obfuscation
The reference obfuscation transformer supports both the non-invokedynamic and invokedynamic variants of the obfuscation, but only if method parameter changes aren't enabled. Note that you must use the string encryption transformer first if the decryptor methods are in a seperate class (or just always use string encryption transformer before this).