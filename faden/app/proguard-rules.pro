# App-specific R8 rules. Libraries (kotlinx.serialization, OkHttp, Coil, Media3) bring their own.

# WorkManager opens its Room database by reflection (WorkDatabase_Impl's no-arg constructor). Its bundled rule no longer
# keeps that constructor under AGP 9's R8, and release builds crashed on launch in WorkManagerInitializer.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Keep line numbers in stack traces (crash reports), with every file named "SourceFile". Class and method names are
# still obfuscated: retrace them with the release's mapping.txt (attached to each GitHub release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
