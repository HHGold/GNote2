# 防止 Gson 解析的數據類被混淆
-keep class com.gacc.app.Expense { *; }

# 防止 Gson 內部類別被混淆
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# 防止 Compose 相關類別被過度優化 (通常 Android Gradle Plugin 會處理，但保險起見)
-keep class androidx.compose.** { *; }
