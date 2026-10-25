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

# WorkManager (background check-ins) stores its jobs in a Room database, and Room creates
# WorkDatabase_Impl by reflection through its no-arg constructor. Room 2.6's own rule keeps only
# the class name, so R8 full mode stripped the constructor and the R8 build crashed on launch
# ("Failed to create an instance of androidx.work.impl.WorkDatabase"). Keep the constructor.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep readable stack traces in crash logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
