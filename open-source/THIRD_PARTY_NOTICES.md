# 第三方组件与许可说明

ToFA 自身使用根目录 [Apache License 2.0](../LICENSE)。本文件记录当前双端直接使用的运行时组件及已知传递依赖；版本以两个工程的 `app/build.gradle.kts` 和手机端“开源许可”页为准。

许可证归各组件原作者所有。分发应用时，应保留相应许可证、版权与归属声明。手机端 `app/src/main/assets/licenses/` 已包含 Apache-2.0 与 Bouncy Castle 等许可证文本。

## 双端运行时组件

| 组件 | 版本 | 使用端 | 许可证 | 项目地址 |
| --- | --- | --- | --- | --- |
| AndroidX Compose UI / Foundation | 1.7.3 | 手机、手表 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/compose-ui> |
| AndroidX Compose Material3 | 1.3.0 | 手机、手表 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/compose-material3> |
| AndroidX Compose Material Icons Extended | 1.7.3 | 手机 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/compose-material> |
| AndroidX Core KTX | 1.13.1 | 手机、手表 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/core> |
| AndroidX AppCompat | 1.7.0 | 手机 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/appcompat> |
| AndroidX Activity Compose | 1.9.2 | 手机、手表 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/activity> |
| AndroidX Lifecycle ViewModel Compose / KTX | 2.8.6 | 手机 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/lifecycle> |
| AndroidX Lifecycle Runtime Compose | 2.8.6 | 手机、手表 | Apache-2.0 | <https://developer.android.com/jetpack/androidx/releases/lifecycle> |
| Kotlin Standard Library | 2.1.0-RC2 | 手机、手表 | Apache-2.0 | <https://github.com/JetBrains/kotlin> |
| Kotlinx Coroutines Android | 1.8.1 | 手机、手表 | Apache-2.0 | <https://github.com/Kotlin/kotlinx.coroutines> |
| ZXing Core | 3.5.3 | 手机、手表 | Apache-2.0 | <https://github.com/zxing/zxing> |
| JourneyApps ZXing Android Embedded | 4.3.0 | 手机 | Apache-2.0 | <https://github.com/journeyapps/zxing-android-embedded> |
| Kadb | 1.2.1 | 手机 | Apache-2.0 | <https://github.com/flyfishxu/Kadb> |
| Bouncy Castle `bcprov-jdk18on` | 1.79 | 手机，Kadb 传递依赖 | MIT / Bouncy Castle Licence | <https://www.bouncycastle.org> |
| Bouncy Castle `bcpkix-jdk18on` | 1.79 | 手机，Kadb 传递依赖 | MIT / Bouncy Castle Licence | <https://www.bouncycastle.org> |
| Bouncy Castle `bcutil-jdk18on` | 1.79 | 手机，Kadb 传递依赖 | MIT / Bouncy Castle Licence | <https://www.bouncycastle.org> |

Compose 版本由 `androidx.compose:compose-bom:2024.09.03` 统一约束。AndroidX、Kotlin 和 Kadb 还可能引入各自的传递组件；升级依赖后应检查实际 `debugRuntimeClasspath` / `releaseRuntimeClasspath`，而不是只看直接依赖。

## 仅开发或测试使用

以下组件用于构建或测试，不应误写为应用运行时在线服务：

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| Android Gradle Plugin / Android SDK | 构建 Android 应用 | Apache-2.0 / 对应 SDK 许可 |
| Kotlin Android / Compose 编译器插件 | 编译 Kotlin 与 Compose | Apache-2.0 |
| JUnit 4.13.2 | 单元测试，不随 APK 分发 | EPL-1.0 |
| `org.json:json:20240303` | JVM 单元测试，不随 APK 分发 | JSON License |
| Compose UI Tooling | Debug 预览与调试 | Apache-2.0 |

## 维护要求

新增、删除或升级组件时，请同时完成：

1. 更新本文件的组件、版本、用途与许可证。
2. 更新手机端“设置 → 开源许可”页面。
3. 将需要随二进制分发的许可证全文加入应用 assets。
4. 检查传递依赖，尤其是加密、ADB、扫码和序列化组件。
5. 确认新许可证与 Apache-2.0 项目分发方式兼容。

本文件是工程归属清单，不替代各组件的许可证全文。
