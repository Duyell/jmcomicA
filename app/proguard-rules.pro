# JMComic PDF App - ProGuard Rules

# Keep Chaquopy Python classes
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep Python bridge module
-keep class **.jm_bridge { *; }

# Keep app classes that use reflection
-keep class com.jmcomic.pdfapp.** { *; }

# Keep FileProvider
-keep class androidx.core.content.FileProvider { *; }
