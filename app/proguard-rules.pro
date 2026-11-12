# ===== P6-02 R8 / ProGuard 规则 =====
# 原则：自用 App 不追求极致体积，宁可少混淆几个数据类，也不能出现
# 「debug 好好的、release 一点播就崩」。各库的 consumer rules 已由 AGP
# 自动合并，这里只补反射与编译期生成代码最容易翻车的部分。

-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn kotlinx.coroutines.**

# --- kotlinx.serialization（最高风险） ---
# ApiEnvelope<T> / FsItem / FsListData 的 $$serializer 由编译器插件生成，
# R8 看不到任何静态引用，删掉后运行时会报 "Serializer for class ... is not found"。
-keep class com.primuse.lite.core.network.** { *; }
-keep class com.primuse.lite.core.model.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    *** INSTANCE;
}
-keepclasseswithmembers class com.primuse.lite.core.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.primuse.lite.core.network.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.primuse.lite.core.network.**$$serializer { *; }
-dontnote kotlinx.serialization.**

# --- Room ---
# 实体字段被 KSP 生成的 DAO 实现按名访问；实体与数据库子类都保留原名。
-keep class com.primuse.lite.core.database.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# --- manifest 组件与 WorkManager Worker（系统按类名反射创建） ---
-keep class com.primuse.lite.PrimuseApplication { *; }
-keep class com.primuse.lite.MainActivity { *; }
-keep class com.primuse.lite.feature.player.PlaybackService { *; }
-keep class com.primuse.lite.feature.local.work.LocalScanWorker { *; }

# --- Media3 ---
# MediaSessionService 由系统按 intent-filter 启动；session 包整体保留，
# 避免 Media3 内部的跨进程/反射调用被裁掉。
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# --- Coil 3（远程封面）---
-dontwarn coil3.**
-dontwarn androidx.compose.**
# Coil 通过 ServiceLoader 发现网络 fetcher，R8 看不到静态引用会把实现类删掉，
# shrinkResources 还会删 META-INF/services 注册文件 —— 结果是 debug 封面正常、
# release 静默变成占位图。Application 里已显式 new ImageLoader，这里再兜一层。
-keep class coil3.network.okhttp.** { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.KeyerServiceLoaderTarget { *; }
