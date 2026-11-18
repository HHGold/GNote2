# 防止 Gson 解析的資料類別被混淆（Room 實體與備份用的模型）
-keep class com.chinhsiang.premiumnotes.data.Note { *; }
-keep class com.chinhsiang.premiumnotes.data.NoteFolder { *; }

# 防止 Gson 內部類別被混淆
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# 資料模型與 Room（以反射序列化 / 備份匯出使用）
-keep class com.chinhsiang.premiumnotes.data.** { *; }
