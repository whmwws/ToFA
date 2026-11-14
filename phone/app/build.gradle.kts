plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ToFA.Android.whmwws.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "ToFA.Android.whmwws.phone"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        // 版本号唯一来源：界面通过 PackageManager 读取这里的 versionName
        versionName = "0.2.3"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // kadb 依赖 BouncyCastle，其多版本 OSGi 清单在 Android 打包阶段会重复
        resources.excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        resources.excludes += "/META-INF/OSGI-INF/MANIFEST.MF"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 扫码：ZXing 内核 + 封装好的相机界面（离线可用，无需 GMS）
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // 手机侧直连手表的 ADB 客户端：支持 Android 11+ 无线调试配对、push、装包
    implementation("com.flyfishxu:kadb:1.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// 手表端安装包随手机 App 一起打包：手表工程出包后自动同步到 assets/watch-app.apk
val syncWatchApkAsset = tasks.register<Copy>("syncWatchApkAsset") {
    val watchApk = rootProject.file("../watch-android/app/build/outputs/apk/debug/app-debug.apk")
    onlyIf { watchApk.exists() }
    from(watchApk)
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "watch-app.apk" }
}

tasks.named("preBuild").configure { dependsOn(syncWatchApkAsset) }

