# 参与 ToFA

感谢你为 ToFA 贡献代码、文档、翻译或测试。ToFA 是处理两步验证密钥的离线工具，正确性、隐私和可复现构建优先于功能数量。

## 先确认范围

当前维护的工程：

- `phone/`：Android 手机端，与手表端统一使用纯语义版本号 `x.y.z`。
- `watch-android/`：Android 手表端，与手机端统一使用纯语义版本号 `x.y.z`。

`watch/` 与 `phone-hmos/` 是早期实验工程，不属于当前双端发布范围。不要为它们新增构建产物或依赖。

以下改动应先开 Issue 讨论：

- 新增在线服务、账号体系、云同步、统计或广告能力。
- 修改密钥存储、加密备份、激活校验或导入导出格式。
- 引入新的第三方依赖或提升最低 Android 版本。
- 大范围修改圆屏布局、交互或双端同步协议。

## 开发环境

- JDK 17
- Android SDK 34
- 最低运行版本 Android 8.0 / API 26
- Windows 使用 `gradlew.bat`，macOS / Linux 使用 `./gradlew`

`local.properties` 只保存本机 SDK 路径，不得提交。签名密钥、密码和证书申请文件也不得写入仓库。

## 构建与测试

始终先构建手表端，再构建手机端，避免手机 assets 中打包旧手表 APK；不要并行构建两个工程。

```bash
cd watch-android
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest

cd ../phone
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

手机端 `preBuild` 会在手表 APK 存在时自动同步到 `phone/app/src/main/assets/watch-app.apk`。提交前确认该资产与实际手表源码一致。

至少验证：

1. 双端均编译成功，无新增崩溃日志。
2. 手机浅色 / 深色、手表方形 / 圆形页面均无文字遮挡。
3. 账号增删改、TOTP 倒计时、导入导出和应用锁未回退。
4. 涉及同步时，验证密钥、主题、语言和激活文件传输。
5. 涉及加密格式时，提供兼容性测试与脱敏测试样例。

## 代码与界面约定

- 遵循现有 Kotlin、Jetpack Compose 与 Material 3 结构，不做无关重构。
- 保持应用离线运行，不增加互联网后端、在线激活、统计或广告 SDK。
- 密钥、验证码、激活码和完整导出文件不得写入日志、Issue、截图或测试夹具。
- 手机与手表版本号分别以各自 `app/build.gradle.kts` 的 `versionName` 为唯一来源。
- 新功能不默认改变用户既有设置；圆屏、应用锁、隐私开关等行为不得回退。
- 用户可见文本应同时考虑中文和英文；修改内置语言时同步检查离线语言包机制。
- 注释说明“为什么”，不要添加无意义注释、死代码或兼容性空壳。

## 提交与 Pull Request

建议一个提交只解决一件事，提交信息简洁说明意图，例如：

```text
fix watch arc alignment on round screens
add offline language pack import validation
update open-source release documentation
```

Pull Request 应包含：

- 变更目的与范围。
- 涉及的端、页面和版本号。
- 测试命令、设备 / 模拟器与实际结果。
- UI 变更的脱敏前后截图。
- 对存储、权限、同步格式和旧数据的影响。
- 新增依赖的许可证与用途。

不要提交 `build/`、`.gradle/`、`.idea/`、`local.properties`、`dist/`、个人截图、签名材料或真实用户数据。

## 文档与许可

提交即表示你同意以根目录 [Apache-2.0 许可证](../LICENSE) 提供该贡献。修改文件时保留必要署名，并在 Pull Request 中说明主要改动。

新增或升级依赖时，同步更新：

- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- 手机端“设置 → 开源许可”页面
- 随应用分发的许可证文本（如适用）

安全问题不要公开提交，请阅读 [SECURITY.md](SECURITY.md)。
