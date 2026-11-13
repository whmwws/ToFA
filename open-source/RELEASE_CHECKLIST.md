# 发布前检查表

每次公开源码或 APK 前复制本清单到 Release 草稿逐项确认。不要仅凭“构建成功”发布。

## 1. 版本与范围

- [ ] 手机 `phone/app/build.gradle.kts` 的 `versionName` 符合 `p-x.y.z`。
- [ ] 手表 `watch-android/app/build.gradle.kts` 的 `versionName` 符合 `w-x.y.z`。
- [ ] 未误改早期 HarmonyOS 实验工程，发布范围仅为 `phone/` 与 `watch-android/`。
- [ ] [CHANGELOG.md](CHANGELOG.md) 已更新，日期和双端版本正确。
- [ ] 官网仍为 `https://whmwws.icu/tools/ToFA`，应用内激活说明使用 `/activate` 的展示规则保持一致。

## 2. 敏感信息

- [ ] `CSR/`、签名密钥、`.jks`、`.keystore`、`.p12`、`.p7b`、`.csr` 未被跟踪。
- [ ] 所有 `local.properties`、签名密码和本机绝对路径未被跟踪。
- [ ] `捐赠二维码/`、`evidence/`、`screenshots/`、`watch_prefs.xml` 未进入公开提交。
- [ ] Issue、截图、日志和测试数据不含真实密钥、验证码、激活码或用户账号。
- [ ] Git 全历史均干净；如果曾提交敏感文件，已重建仓库或清理历史，而非只加 `.gitignore`。

## 3. 功能与安全

- [ ] 账号增删改、TOTP 计算、倒计时和排序正常。
- [ ] 扫码、手输、加密导出和导入恢复正常。
- [ ] 应用锁、紧急密码、最近任务隐私和剪贴板保护未回退。
- [ ] 本地 adb 配对、同步、安装、主题和语言推送正常。
- [ ] 未新增互联网后端、在线激活、统计、广告或无关 SDK。
- [ ] 权限说明与 [PRIVACY.md](PRIVACY.md) 一致。
- [ ] 崩溃日志已检查，未修复安全问题未出现在公开 Issue 中。

## 4. 界面与设备

- [ ] 手机浅色 / 深色、不同语言与长文本无遮挡。
- [ ] 手表方形 / 圆形页面、圆屏边缘与滚动进度条正常。
- [ ] 主题色在双端一致，按钮文字清晰，设置预览与实际效果一致。
- [ ] 手表进度条滚动时显示、静止后隐藏，不遮挡内容。
- [ ] 至少在手机模拟器或真机、手表真机或模拟器完成目标版本验证。

## 5. 构建

- [ ] 先执行手表 `:app:assembleDebug` / Release 构建，再执行手机构建，没有并行。
- [ ] `phone/app/src/main/assets/watch-app.apk` 来自本次手表源码。
- [ ] 双端 `:app:testDebugUnitTest` 通过，或明确记录未覆盖项。
- [ ] Release APK 使用本地安全配置签名，密钥和密码未写入仓库。
- [ ] 在干净环境或新模拟器验证安装与首次启动。

## 6. 许可证与社区材料

- [ ] 根目录 `LICENSE` 保持 Apache-2.0，GitHub / Gitee 仓库信息已选择相同许可证。
- [ ] [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与双端实际依赖一致。
- [ ] 手机“开源许可”页和随包许可证文本已同步。
- [ ] 贡献、安全、支持、隐私与行为准则链接有效。
- [ ] 平台模板中的 `OWNER`、仓库地址和占位内容已替换。

## 7. 发布文件

- [ ] APK 文件名与版本对应，未上传 `.idsig`、构建缓存或本地配置。
- [ ] 已生成并在 Release 中公布每个 APK 的 SHA-256。
- [ ] Release 注明最低 Android 版本、构建类型、签名状态和验证结果。
- [ ] 手机与手表分别使用 `p-x.y.z`、`w-x.y.z` 标签，未混淆版本。
- [ ] 下载 Release 附件后再次核对 SHA-256，并做一次安装验证。

## 8. 发布后

- [ ] GitHub / Gitee 默认分支、Release 和附件均可公开访问。
- [ ] 仓库内相对链接与图片在两个平台显示正常。
- [ ] GitHub 私密漏洞报告入口可用，安全联系流程已验证。
- [ ] 未代替用户创建远程仓库、推送或公开任何内容；最终公开操作由仓库所有者确认。
