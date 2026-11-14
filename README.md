# ToFA

离线 TOTP 双端验证器：Android 手机端 + Android 手表端。密钥只保存在本机，不需要账号，不联网也能用。

## 下载

GitHub Release 是主发布渠道，每个版本同时提供手机端与手表端 APK：

- [ToFA 0.2.3 发布页](https://github.com/whmwws/ToFA/releases/tag/0.2.3)
- [手机端 APK（0.2.3）](https://github.com/whmwws/ToFA/releases/download/0.2.3/ToFA-0.2.3-phone.apk)
- [手表端 APK（0.2.3）](https://github.com/whmwws/ToFA/releases/download/0.2.3/ToFA-0.2.3-watch.apk)

国内网络访问 GitHub 较慢时，请改用国内镜像：

- [Gitee 发布页（国内镜像）](https://gitee.com/whmwws/ToFA/releases)

每次发布的同时会更新 [版本更新日志](open-source/CHANGELOG.md)；应用内「设置 → 关于 → 版本更新日志」也能离线查看同一份内容。

## 定位

ToFA 是一个纯本地、可离线的两步验证（TOTP）工具，面向「验证码随时在手腕上」但不想把密钥交给任何云服务的用户。手机端负责导入、管理、加密备份与向手表推送；手表端负责抬手看码、复制、手输添加，并可在断网状态下独立运行。

- **无在线依赖**：核心功能离线运行，没有互联网后端；激活、导入、导出都走本机，双端同步使用本地 adb 直连。
- **零账号**：不注册、不上传、不统计。
- **双端协同**：手机通过无线调试（adb）把密钥、主题色、界面语言推送到手表；手表可独立生成验证码。

## 仓库结构

### 手机端

目录：[`phone/`](phone)

Android 手机端，Jetpack Compose 实现，负责账号管理、扫码导入、加密备份、应用锁与手表同步。

### 手表端

目录：[`watch-android/`](watch-android)

Android 手表端，Jetpack Compose 实现，支持圆屏与方屏，单账号一页，可独立生成验证码。

### 开源材料

目录：[`open-source/`](open-source)

贡献指南、安全政策、隐私说明、第三方许可、发布清单与平台模板。

### 早期实验工程

目录：`watch/`、`phone-hmos/`

早期 HarmonyOS 实验工程，不参与当前构建与发布，仅作历史保留。

版本号唯一来源是两端各自的 `app/build.gradle.kts` 中的 `versionName`，界面通过 `PackageManager` 读取，不在代码里硬编码。

## 功能

### 手机端

- TOTP 账号增删改、拖动排序、卡片显示名档位
- 扫码 / 相册识别、手动输入、`otpauth://` 与通用导出格式导入
- 导出方式：唯一码、自定义密钥、JSON 明文、TXT；TXT 每行一条标准 `otpauth://` 链接
- 编辑模式点按条目可查看密钥，密钥只读，点按即可复制
- 应用锁、连续错误自毁、紧急密码、最近任务隐私、禁止截屏
- 深浅色与主题色、界面语言（简中 / English / 繁體中文 / 日本語，可导入离线语言包）
- 通过无线调试把密钥、激活文件、主题色、语言推送到手表，并可安装 / 启动手表端
- 首启隐私协议、激活说明、版本更新日志与开源许可页

### 手表端

- 单账号一页，上下滑动 / 旋转表冠翻页，倒计时环形进度
- 「圆形页面」可选：默认方形布局，开启后贴合圆屏轮廓
- 复制验证码、二维码展示、手输添加与密钥管理
- 独立的应用锁、密码与紧急密码
- 内置中文 / English；手机推送语言包后可在手表上循环切换
- 全局主题色、深浅色、启动遮挡、最近任务隐私

## 隐私声明

- 所有密钥只写入本机应用私有存储，导出文件由用户自行决定存放位置。
- 手机端 `INTERNET` 权限仅用于用户主动开启的本地 adb / 无线调试连接；不依赖互联网后端，也不包含统计或广告 SDK。
- 复制验证码与密钥会设置剪贴板自动清空；最近任务缩略图可隐藏验证码。
- 激活信息仅在本机校验，激活码由官网页面离线生成。

## 构建

环境要求：

- JDK 17
- Android SDK 34

构建顺序固定，先手表端、再手机端：

```bash
# 1. 先构建手表端
cd watch-android
./gradlew :app:assembleDebug

# 2. 再构建手机端，会自动同步最新手表 APK 资产
cd ../phone
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat :app:assembleDebug`。首次构建前在各自目录创建 `local.properties`（不要提交）：

```properties
sdk.dir=/path/to/Android/Sdk
```

手机端会把 `phone/app/src/main/assets/watch-app.apk` 作为内置手表安装包推送到手表，因此该文件需要随源码保留；自行构建时请先构建手表端并把产物复制到该路径。

## 安装

- 手机端：安装 Release APK，最低 Android 8.0（API 26）。
- 手表端：最低 API 26，已适配圆屏；手机端「导入导出」页可通过无线调试安装并启动手表端。

## 开源许可

本项目使用 [Apache License 2.0](LICENSE)。允许商用与闭源分发，同时保留专利授权与署名要求，适合需要被广泛集成或二次分发的离线安全工具。应用内「开源许可」页列出了实际使用的第三方库、许可证与项目地址。

更多文档：

- [完整开源操作手册](OPEN_SOURCE.md)
- [开源材料索引](open-source/README.md)

## 参与贡献

欢迎提交 Issue 与 Pull Request，请先阅读 [贡献指南](open-source/CONTRIBUTING.md)。涉及密钥处理、加密格式的改动请附带测试说明；请勿在 Issue 中粘贴真实密钥或验证码。安全漏洞请按 [安全政策](open-source/SECURITY.md) 私密报告。

## 相关链接

- [项目官网](https://whmwws.icu/tools/ToFA)
- [离线激活页面](https://whmwws.icu/tools/ToFA/activate)

如果 ToFA 对你有帮助，欢迎通过[捐赠页面](https://whmwws.icu/tools/ToFA/donate)支持后续维护。
