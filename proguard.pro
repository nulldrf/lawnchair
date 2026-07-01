# The rules from AOSP are located in proguard.flags file, we can just maintain Lawnchair related rules here.

# Optimization options.
-allowaccessmodification
-dontusemixedcaseclassnames
-allowaccessmodification
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable

# Remove some Kotlin overhead
-processkotlinnullchecks remove

# Common rules.
-keep,allowshrinking class android.window.** { *; }
-keep,allowshrinking class android.view.** { *; }
-keep,allowshrinking class com.android.systemui.** { *; }
-keep,allowshrinking class com.android.wm.shell.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Lawnchair specific rules.
-keep,allowshrinking,allowoptimization class app.lawnchair.LawnchairProto$* { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.LawnchairApp { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.LawnchairLauncher { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.compatlib.** { *; }

-keep,allowshrinking,allowoptimization class com.google.protobuf.Timestamp { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

-keep class org.lsposed.hiddenapibypass.** { *; }

# HokoBlur
-keep class com.hoko.blur.** { *; }

# We intentionally remove it to replace Smartspacer's widget popup with our own Launcher3 popup
-dontwarn com.skydoves.balloon.*

# This shouldn't concern us much
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
