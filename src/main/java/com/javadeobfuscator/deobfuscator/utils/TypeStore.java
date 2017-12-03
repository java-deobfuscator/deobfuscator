package com.javadeobfuscator.deobfuscator.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.javadeobfuscator.deobfuscator.executor.values.JavaObject;
import com.javadeobfuscator.deobfuscator.executor.values.JavaValue;

public class TypeStore
{
	 private static final Map<String, Entry<Object, String>> staticFields = Collections.synchronizedMap(new HashMap<>());
	 private static final Map<Object, Map<String, Entry<Object, String>>> instanceFields = new ConcurrentHashMap<>();
	 public static final Map<Long, JavaObject> returnObjects = new ConcurrentHashMap<>();
	 
	 public static Entry<Object, String> getFieldFromStore(String className, String fieldName, String fieldDesc, JavaValue targetObject) {
		 if (targetObject == null) {
			 return staticFields.get(className + fieldName + fieldDesc);
		 } else {
			 synchronized (instanceFields) {
				 Map<String, Entry<Object, String>> field = instanceFields.get(targetObject.value());
				 if (field != null) {
					 return field.get(className + fieldName + fieldDesc);
				 }
			 }
			 
			 return null;
		 }
	 }

	 public static void setFieldToStore(String className, String fieldName, String fieldDesc, JavaValue targetObject, Entry<Object, String> value) {
		 if (targetObject == null) {
			 staticFields.put(className + fieldName + fieldDesc, value);
		 } else {
			 synchronized (instanceFields) {
				 Map<String, Entry<Object, String>> field = instanceFields.get(targetObject.value());
				 if (field == null) {
					 field = new HashMap<>();
				 }
				 field.put(className + fieldName + fieldDesc, value);
				 instanceFields.put(targetObject.value(), field);
			 }
		 }
	 }
}
