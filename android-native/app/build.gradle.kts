import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 与 Flutter 工程共用同一套签名材料：本地读 ../android/keystore.properties，
// CI 用四个 RELEASE_* 环境变量。缺任一项就只出未签名的 release。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("../android/keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(key: String, vararg environmentNames: String): String? =
    keystoreProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: environmentNames.firstNotNullOfOrNull { name ->
            System.getenv(name)?.takeIf { it.isNotBlank() }
        }

val releaseStoreFile = signingValue("storeFile", "RELEASE_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it != null }

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fqapp.mobile"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.fqapp.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // 后端 Go 时代留下的约定：只出 arm64。Rust core 也只编这一份。
        ndk { abiFilters += "arm64-v8a" }
    }

    sourceSets {
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("main") {
            java.srcDirs("src/main/kotlin")
            // 运行时资源与 Flutter 工程共用同一份，改 config/filters 不会分叉。
            assets.srcDirs("src/main/assets", rootProject.file("../assets"))
        }
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                // storeFile 可能是绝对路径，也可能是相对 Flutter 工程 android/ 的路径。
                storeFile = rootProject.file(releaseStoreFile!!).takeIf { it.isFile }
                    ?: rootProject.file("../android/${releaseStoreFile}")
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    // 本机只有 JDK 25：不要用 jvmToolchain，否则 Gradle 会去下载 JDK 17。
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // 听书/阅读器要用 Pause、Headset、SkipNext 这些图标，core 集合里没有。
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // 单测跑在 JVM 上：android.jar 里的 org.json 只是桩，换成真实现才能验解析。
    testImplementation("org.json:json:20240303")
}