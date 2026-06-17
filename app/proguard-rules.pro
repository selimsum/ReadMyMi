# Keep Room entities
-keep class com.example.readmymi.data.SensorEntity { *; }

# Keep data classes used in serialization
-keep class com.example.readmymi.SensorData { *; }

# HelloCharts
-keep class lecho.lib.hellocharts.** { *; }

# Keep Compose-related classes
-keep class androidx.compose.** { *; }

# Standard Android rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
