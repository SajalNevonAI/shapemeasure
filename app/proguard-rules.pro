# kotlinx.serialization models are stored as JSON in the database.
-keep,includedescriptorclasses class com.nevonai.smartshape.model.**$$serializer { *; }
-keepclassmembers class com.nevonai.smartshape.model.** { *** Companion; }
