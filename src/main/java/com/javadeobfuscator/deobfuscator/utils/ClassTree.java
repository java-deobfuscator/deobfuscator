package com.javadeobfuscator.deobfuscator.utils;

import java.util.ArrayList;
import java.util.List;

public class ClassTree {
    public String thisClass;

    public List<String> subClasses = new ArrayList<>();
    public List<String> parentClasses = new ArrayList<>();
}
