# ToFA 开源操作手册

这是仓库根目录的完整维护者手册，覆盖材料结构、安全检查、本地 Git、GitHub / Gitee、双端构建、Release 和后续维护。

**当前只整理本地文件。本手册中的 Git 远程命令均为操作示例，未代替仓库所有者创建远程仓库、提交或推送。**

---

## 一、当前目录与材料职责

```text
ToFA/
├─ LICENSE                    # Apache-2.0 唯一全文，保留在根目录供平台识别
├─ README.md                  # GitHub 主入口：功能、安装、构建与贡献
├─ README.osc.md              # Gitee 主入口：Gitee 优先的下载与说明（Gitee 优先显示该文件）
├─ OPEN_SOURCE.md             # 本手册
├─ .gitignore                 # 本机配置、签名、构建产物和私人文件排除规则
├─ open-source/               # 全部开源社区与发布材料
│  ├─ README.md               # 材料索引与模板启用方法
│  ├─ CONTRIBUTING.md
│  ├─ CODE_OF_CONDUCT.md
│  ├─ SECURITY.md
│  ├─ SUPPORT.md
│  ├─ PRIVACY.md
│  ├─ THIRD_PARTY_NOTICES.md
│  ├─ CHANGELOG.md
│  ├─ RELEASE_CHECKLIST.md
│  ├─ RELEASE_TEMPLATE.md
│  └─ templates/              # GitHub / Gitee Issue 与 PR 模板
├─ language-packs/            # 可选语言包，随 Release 附件发布
├─ phone/                     # 当前 Android 手机端
└─ watch-android/             # 当前 Android 手表端
```

`watch/` 与 `phone-hmos/` 是早期 HarmonyOS 实验工程，不属于当前发布范围。不要为了开源擅自删除；首次暂存时明确只添加当前工程即可。

### 版本规则

- 双端统一使用纯语义版本号 `x.y.z`，唯一来源为各自 `app/build.gradle.kts` 的 `versionName`。
- 手机与手表同步发布时使用同一个 Release 标签，并上传两个对应 APK。
- 当前发布线为 `0.2.5`。

### 许可证边界

项目使用 Apache-2.0。修改与再分发时保留许可证和必要署名，修改文件应注明改动；许可证不授予冒用 ToFA 名称或标识的权利。具体法律条款以根目录 `LICENSE` 原文为准。

第三方组件、版本与许可证见 [open-source/THIRD_PARTY_NOTICES.md](open-source/THIRD_PARTY_NOTICES.md)。新增或升级依赖时，同时更新手机端“开源许可”页和随包许可证文本。

---

## 二、公开前必须确认的安全边界

### 1. 当前本地状态

当前工作目录尚未初始化为 Git 仓库，因此没有本仓库历史需要清理。首次 `git init` 后，应通过明确暂存建立一个干净的首提交。

`.gitignore` 已排除主要敏感文件与产物，但**忽略不等于自动安全**：已跟踪文件、历史提交和手工强制添加仍可能泄露内容。

| 内容 | 发布要求 |
| --- | --- |
| `CSR/` 中的证书申请与私钥材料 | 保留在本机，绝不公开 |
| `.jks`、`.keystore`、`.p12`、`.p7b`、`.csr`、签名密码 | 不提交；签名配置放本机安全位置 |
| 各级 `local.properties` | 不提交，避免泄露 SDK 绝对路径 |
| `捐赠二维码/` | 不公开个人收款图片；需要赞助入口时使用公开文案 |
| `evidence/`、`screenshots/`、`watch_prefs.xml` | 不提交测试取证、设置导出和调试日志 |
| `dist/`、APK、AAB、构建缓存 | 不进源码历史；APK 作为 Release 附件单独上传 |
| `phone/app/src/main/assets/watch-app.apk` | **有意保留**，是手机安装手表端所需资产，需与手表源码一致 |
| 真实密钥、验证码、激活码、ADB 私钥、完整备份 | 不出现在源码、测试、截图、Issue 或 Release 说明中 |

### 2. 权限与“离线”表述

ToFA 不依赖互联网后端、账号、统计或广告服务。手机清单中的 `INTERNET` 权限用于本地 adb / 无线调试 socket，不能在文档中误写为“完全没有网络权限”。手表当前未声明该权限。

对外统一表述建议：

> 核心验证码功能完全离线；手机与手表的数据传递使用用户主动开启的本地 adb 连接，不依赖云端服务。

详细说明见 [open-source/PRIVACY.md](open-source/PRIVACY.md)。

### 3. 初始化后的敏感信息复查

在仓库根目录执行：

```bash
git status --short
git ls-files "*.jks" "*.keystore" "*.p12" "*.p7b" "*.csr" "local.properties" "**/local.properties"
git ls-files "*.apk" "*.aab" "*.idsig"
git grep -n -I -E "storePassword|keyPassword|-----BEGIN|sdk\.dir" -- . ":!*.md"
git log --all --name-only --pretty=format:
```

检查要求：

- 第一条敏感文件列表应为空。
- APK 列表只允许出现有意保留的 `phone/app/src/main/assets/watch-app.apk`。
- 密钥、密码、私钥和 `sdk.dir` 搜索不应出现真实内容。
- 历史文件列表需要人工检查；若曾提交敏感文件，仅加 `.gitignore` 不够，应重建干净仓库或使用可靠工具清理全历史。
- 历史重写会改变提交哈希并影响协作者，不要在未备份、未确认时强制推送。

完整逐项检查见 [open-source/RELEASE_CHECKLIST.md](open-source/RELEASE_CHECKLIST.md)。

---

## 三、建立干净的本地首提交

以下命令供仓库所有者确认后自行执行：

```bash
cd /path/to/ToFA
git init
git branch -M main
git add .gitignore README.md README.osc.md OPEN_SOURCE.md LICENSE open-source phone watch-android
git status --short
git diff --cached --stat
git diff --cached
```

逐项确认暂存内容，尤其检查：

- 没有 `CSR/`、`捐赠二维码/`、`dist/`、`evidence/`、`watch_prefs.xml`。
- 没有早期实验工程、本机配置或构建缓存。
- 手表 APK 资产存在且为本次构建版本。
- 文档链接使用仓库内相对路径，不包含本机 `E:\...` 路径。

确认后再提交：

```bash
git commit -m "Initial commit: ToFA offline TOTP for phone and watch"
git log -1 --stat
```

不要习惯性地使用 `git add -A` 或 `git add .`；本项目根目录含私人材料和多个历史工程，明确列出路径更安全。

---

## 四、启用 Issue 与 Pull Request 模板

材料集中保存在 `open-source/templates/`。平台只识别自己的约定目录，因此在需要启用时复制。

### GitHub

```powershell
Copy-Item -Recurse -Force "open-source/templates/github" ".github"
Copy-Item -Force "open-source/CONTRIBUTING.md","open-source/CODE_OF_CONDUCT.md","open-source/SECURITY.md","open-source/SUPPORT.md" ".github/"
```

第二条命令让 GitHub 能自动识别贡献指南、行为准则、安全政策和支持入口；`open-source/` 中仍保留主版本，后续修改需同步副本。

然后：

1. 打开 `.github/ISSUE_TEMPLATE/config.yml`。
2. 将 `OWNER` 替换为实际 GitHub 用户名或组织名。
3. 检查 Issue 表单、标签和安全公告链接。
4. 将 `.github/` 明确加入暂存。

### Gitee

```powershell
New-Item -ItemType Directory -Force ".gitee"
Copy-Item -Force "open-source/templates/gitee/*" ".gitee/"
```

若 Gitee 未自动识别，在仓库 Issue / Pull Request 设置中手动选择模板。平台规则可能调整，以 Gitee 当前提示为准。

模板只复制到目标平台目录；`open-source/templates/` 中保留主版本，后续修改时同步更新。

---

## 五、GitHub 发布流程

### 1. 创建空仓库

由仓库所有者在 GitHub 页面创建 `ToFA`：

- 选择公开或先私有检查后再公开。
- 不勾选自动 README、`.gitignore` 或 LICENSE，避免与本地首提交冲突。
- Description：

  `Offline TOTP authenticator for Android phone and watch — no account, no cloud, secrets stay on device.`

- Topics：

  `totp` `2fa` `authenticator` `android` `wear-os` `jetpack-compose` `offline` `privacy` `kotlin`

### 2. 关联并推送

```bash
git remote add origin https://github.com/<用户名或组织>/ToFA.git
git remote -v
git push -u origin main
```

如果远程已有提交，先检查差异，不要直接强推覆盖。

### 3. 仓库设置

- 确认许可证被识别为 Apache-2.0。
- 默认分支设为 `main`，按需启用分支保护。
- 在 **Settings → Advanced Security → Private vulnerability reporting** 启用私密漏洞报告。
- 确认 **Security → Advisories → Report a vulnerability** 可用，与 `SECURITY.md` 一致。
- 根据维护能力启用 Issues、Discussions 和 PR 模板。
- 官网设为 `https://whmwws.icu/tools/ToFA`。

---

## 六、Gitee 发布流程

1. 新建 `ToFA`，不要自动初始化 README。
2. 可见性按发布计划选择；正式开源前再次确认是“公开”而非“私有”。
3. 在仓库基本信息中选择 Apache-2.0，并填写与 GitHub 一致的简介和官网。
4. 关联并推送：

   ```bash
   git remote add gitee https://gitee.com/<用户名>/ToFA.git
   git remote -v
   git push -u gitee main
   ```

5. 检查 README、LICENSE、`open-source/` 链接和模板。

注意事项：

- 图片使用仓库内相对路径，不依赖 GitHub `raw.githubusercontent.com` 直链。
- 从 GitHub 导入仓库会带入历史；历史不干净时不要直接导入。
- 平台镜像和定时同步通常是单向能力，配置前确认来源与目标，避免覆盖另一端的提交。
- Gitee Release 附件限制与 GitHub 不同；过大时可保留官网或主 Release 的明确链接。
- 不依赖 Gitee Pages 作为唯一官网，现有官网继续使用 `whmwws.icu/tools/ToFA`。

---

## 七、GitHub / Gitee 同步维护

推荐本地双 remote，由维护者明确推送：

```bash
git fetch origin
git fetch gitee
git status
git push origin main
git push gitee main
```

推送标签时分别执行：

```bash
git push origin <标签>
git push gitee <标签>
```

建议指定一个主平台处理 Issue 与 PR，另一平台在 README 或仓库说明中明确入口。不要对两边执行未经检查的强制同步，也不要使用强制推送解决日常分歧。

---

## 八、双端构建与发布产物

### 1. 环境

- JDK 17
- Android SDK 34
- 最低 Android 8.0 / API 26
- 本机 `local.properties` 不提交

### 2. 固定顺序：watch → phone

Windows：

```powershell
cd watch-android
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest

cd ..\phone
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

macOS / Linux 使用 `./gradlew`。不要并行构建两个工程。

手机 `preBuild` 会自动把已存在的手表 Debug APK 同步为 `phone/app/src/main/assets/watch-app.apk`。如果手表源码有变化，必须先成功构建手表，再构建手机。

### 3. Release 与签名

- Debug APK 适合验证，不应被误认为正式签名版本。
- Release 签名文件、密码和 Gradle 属性只放本机安全位置，不写入仓库。
- 未配置签名时，不要把未签名 APK 当作可覆盖安装的正式版本发布。
- 发布前在实际设备验证安装、覆盖升级、首次启动和旧数据保留情况。

### 4. SHA-256

对最终准备上传的文件计算，而不是对中间构建产物计算：

```powershell
Get-FileHash -Algorithm SHA256 "dist/ToFA-0.2.2-phone.apk"
Get-FileHash -Algorithm SHA256 "dist/ToFA-0.2.2-watch.apk"
```

上传后重新下载附件并复核一次。Release 文本使用 [open-source/RELEASE_TEMPLATE.md](open-source/RELEASE_TEMPLATE.md)。

---

## 九、创建 Release

双端同步发布时创建统一标签 `x.y.z`，上传 `ToFA-x.y.z-phone.apk` 与 `ToFA-x.y.z-watch.apk`。

每个 Release 写明：

- 变更内容与关联 Issue。
- 最低 Android 版本、构建类型和签名状态。
- 安装与升级注意事项。
- 测试设备、系统与结果。
- APK SHA-256。
- 已知问题和安全报告入口。

`.idsig`、构建缓存、签名材料和本机配置不上传。`dist/` 可以继续作为本机发布暂存目录，但不进入源码提交。

---

## 十、日常维护规则

### Issue 与 PR

- 普通 Bug 使用问题模板，功能建议先说明场景和兼容性影响。
- 安全漏洞只允许私密报告，公开内容不得含真实凭据或可利用细节。
- PR 必须说明测试与影响；加密、存储、同步格式改动需要脱敏测试样例。
- 社区行为按 [CODE_OF_CONDUCT.md](open-source/CODE_OF_CONDUCT.md) 处理。

### 功能与隐私

- 保持核心离线，不新增云账号、在线激活、统计或广告。
- 权限、数据格式和同步行为变化时更新 `PRIVACY.md` 与 README。
- 版本号只在实际发布时更新，保持双端统一的纯语义版本号规则。
- 手机与手表的浅色、深色、圆屏、长文本和隐私开关都要回归验证。

### 依赖与许可证

- 新依赖先确认用途、离线行为和许可证兼容性。
- 同步更新 `THIRD_PARTY_NOTICES.md`、应用内许可页和随包许可证。
- 定期核查传递依赖，但不能用未经核实的自动生成清单替代人工确认。

### 每次发布

更新 [CHANGELOG.md](open-source/CHANGELOG.md)，逐项完成 [RELEASE_CHECKLIST.md](open-source/RELEASE_CHECKLIST.md)，最后由仓库所有者亲自确认公开与推送操作。
