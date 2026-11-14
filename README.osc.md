# ToFA

国产开源的双端 TOTP 验证器：一部 Android 手机加一块 Android 手表，验证码抬手就能看。密钥全部留在本机，注册、登录、联网都不需要。

## 下载

Gitee 是本项目的主发布渠道，国内下载速度快，附件与 Release 说明同时更新：

- [ToFA 0.2.3 发布页](https://gitee.com/whmwws/ToFA/releases/tag/0.2.3)
- [手机端 APK（0.2.3）](https://gitee.com/whmwws/ToFA/releases/download/0.2.3/ToFA-0.2.3-phone.apk)
- [手表端 APK（0.2.3）](https://gitee.com/whmwws/ToFA/releases/download/0.2.3/ToFA-0.2.3-watch.apk)

海外网络或从境外访问时，可以改用海外镜像：

- [GitHub 发布页（海外镜像）](https://github.com/whmwws/ToFA/releases)

安装包为 Debug 构建，使用 Android Debug 签名，可与同一签名的旧版本直接覆盖升级。每个版本的 SHA-256 与验证范围写在对应 Release 说明里。

## 快速上手

1. 在手机上安装手机端 APK，首次启动阅读隐私说明后即可使用。
2. 用扫码、相册识别或手动输入导入已有密钥，也可以从其他验证器导出的文件导入。
3. 手机上打开「设置 → 导入导出」，通过无线调试把密钥推送到手表。
4. 手表端安装后即可离线看码；断网、手机不在身边也不影响使用。

手表端默认方形布局；圆屏设备可在手表设置中开启「圆形页面」，页面会贴合表盘轮廓。

## 双端分工

### Android 手机端

- 负责密钥的导入、整理、加密备份与恢复。
- 负责应用锁、剪贴板保护、最近任务隐私等安全设置。
- 通过本地 adb / 无线调试把密钥、主题色、语言包推送到手表，也能安装并启动手表端。

### Android 手表端

- 单账号一页，上下滑动或旋转表冠翻页，环形倒计时随时可见。
- 支持复制验证码、二维码展示、手输添加与密钥管理。
- 拥有独立的应用锁、密码与紧急密码，不依赖手机在线。

两端版本号统一使用纯语义版本号，取值来源分别是各自 `app/build.gradle.kts` 中的 `versionName`。

## 功能一览

### 手机端

- TOTP 账号增删改、拖动排序、卡片显示名档位
- 扫码 / 相册识别、手动输入、`otpauth://` 与通用导出格式导入
- 导出方式：唯一码、自定义密钥、JSON 明文、TXT
- TXT 导出每行包含一条标准 `otpauth://` 链接，便于迁移到其他验证器
- 编辑模式点按条目可查看密钥，密钥只读，点按即可复制
- 应用锁、连续错误自毁、紧急密码、禁止截屏
- 深浅色与主题色、简中 / English / 繁體中文 / 日本語，可导入离线语言包
- 首启隐私协议、激活说明、版本更新日志与开源许可

### 手表端

- 单账号一页、倒计时环、圆屏与方屏两套布局
- 复制验证码、二维码展示、手输添加
- 独立应用锁与紧急密码
- 内置中文 / English，支持手机推送语言包后循环切换
- 全局主题色、深浅色、启动遮挡与最近任务隐私

## 数据与隐私

- 密钥只写入手机或手表本机的应用私有存储，不上传任何服务器。
- 导出文件的存放位置由用户自己选择，明文导出会二次确认风险。
- 复制验证码或密钥后，剪贴板会在设定时间后自动清空。
- 手机端只在用户主动开启无线调试时使用网络能力，不含统计、广告或第三方联网 SDK。
- 激活信息在本机校验，激活码由官网页面离线生成。

## 从源码构建

环境要求：

- JDK 17
- Android SDK 34

请先在本地克隆 Gitee 仓库：

```bash
git clone https://gitee.com/whmwws/ToFA.git
```

构建顺序固定，先手表端、再手机端：

```bash
# 1. 先构建手表端
cd watch-android
./gradlew :app:assembleDebug

# 2. 再构建手机端，会自动同步最新手表 APK 资产
cd ../phone
./gradlew :app:assembleDebug
```

Windows 使用 `gradlew.bat :app:assembleDebug`。首次构建前，在 `watch-android/` 与 `phone/` 下分别创建 `local.properties`（不要提交）：

```properties
sdk.dir=/path/to/Android/Sdk
```

手机端会把 `phone/app/src/main/assets/watch-app.apk` 作为内置手表安装包推到手表，这个文件需要随源码保留；自行构建时请先构建手表端并复制产物到该路径。

## 常见问题

**问：需要联网吗？**

不需要。导入、生成验证码、备份全在本机完成，手机推送到手表走的是本地无线调试通道。

**问：密钥会被上传或收集吗？**

不会。项目没有互联网后端，也不含统计和广告 SDK。

**问：手机和手表可以分开用吗？**

可以。手表端能独立生成验证码，手机不在身边也不影响；手机只负责推送和管理。

**问：从其他验证器迁移麻烦吗？**

不麻烦。支持扫码、相册识别、`otpauth://` 链接、TXT 每行一条链接，也支持本应用导出的加密文件与明文文件。

## 更新日志

每个版本的用户可见改动都记录在仓库的 [更新日志](open-source/CHANGELOG.md) 中，Gitee Release 说明里会同步列出该版本的条目。应用内「设置 → 关于 → 版本更新日志」可以离线查看，覆盖到当前安装的版本。

## 开源许可与致谢

项目使用 [Apache License 2.0](LICENSE)，允许商用与闭源分发，同时保留专利授权与署名要求。第三方库清单与许可证全文见应用内「开源许可」页。

更多材料：

- [完整开源操作手册](OPEN_SOURCE.md)
- [开源材料索引](open-source/README.md)
- [贡献指南](open-source/CONTRIBUTING.md)
- [安全政策](open-source/SECURITY.md)

## 反馈与参与

在 Gitee 提交 Issue 或 Pull Request 都可以，提交前建议先看[贡献指南](open-source/CONTRIBUTING.md)。涉及密钥处理与加密格式的改动请附测试说明；请不要在 Issue 里粘贴真实密钥或验证码。安全问题请按[安全政策](open-source/SECURITY.md)私密报告。

相关页面：

- [项目官网](https://whmwws.icu/tools/ToFA)
- [离线激活页面](https://whmwws.icu/tools/ToFA/activate)

如果 ToFA 对你有帮助，欢迎通过[捐赠页面](https://whmwws.icu/tools/ToFA/donate)支持后续维护。
