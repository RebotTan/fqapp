# JNI 入口由 Kotlin 直接调用，名字必须原样保留给 native 侧查找。
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.fqapp.mobile.core.FqCoreBridge { *; }
