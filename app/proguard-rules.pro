# Firestore maps documents onto these classes by reflection (toObject / set),
# so their names, fields, getters/setters and no-arg constructors must survive R8.
-keep class com.deadfest.data.model.** { *; }
-keepclassmembers class com.deadfest.data.model.** {
    public <init>();
}

# Needed for Firestore's @PropertyName and generic type (List<T>/Map<K,V>) mapping.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep readable stack traces in crash logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
