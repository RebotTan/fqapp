// 原生客户端的独立 Gradle 工程。与 Flutter 的 ../android 互不干扰。
pluginManagement {
    repositories {
        // 与 Flutter 工程同一套镜像策略：国内默认走 aliyun，CI 里把
        // FQAPP_USE_MAVEN_MIRRORS 设成 false 就只用官方源。
        val useMirrors = providers.environmentVariable("FQAPP_USE_MAVEN_MIRRORS")
            .orNull?.equals("false", ignoreCase = true) != true
        if (useMirrors) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        val useMirrors = providers.environmentVariable("FQAPP_USE_MAVEN_MIRRORS")
            .orNull?.equals("false", ignoreCase = true) != true
        if (useMirrors) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "fqapp-native"

plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}

include(":app")