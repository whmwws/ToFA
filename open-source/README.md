# ToFA 开源材料索引

本目录集中存放 ToFA 的社区治理、贡献、安全、隐私、发布与平台模板材料。根目录的 [OPEN_SOURCE.md](../OPEN_SOURCE.md) 是面向维护者的完整开源操作手册；根目录 [README.md](../README.md) 是面向使用者和开发者的项目入口。

## 材料清单

| 文件 | 用途 |
| --- | --- |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 开发环境、构建顺序、代码与 Pull Request 规范 |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | 社区行为边界、举报与处理原则 |
| [SECURITY.md](SECURITY.md) | 支持范围、私密漏洞报告与敏感信息规则 |
| [SUPPORT.md](SUPPORT.md) | 使用帮助、问题排查与提问渠道 |
| [PRIVACY.md](PRIVACY.md) | 权限、密钥、剪贴板、导入导出与本地 adb 说明 |
| [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) | 双端运行时第三方依赖、版本、许可证与来源 |
| [CHANGELOG.md](CHANGELOG.md) | 面向用户的版本变更记录 |
| [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) | 每次公开发布前的逐项检查表 |
| [RELEASE_TEMPLATE.md](RELEASE_TEMPLATE.md) | GitHub / Gitee Release 说明模板 |
| [templates/](templates) | Issue、Pull Request 与平台配置模板 |

## 模板如何启用

为保持开源材料集中管理，模板默认存放在本目录。需要启用平台功能时，再复制到平台约定位置：

### GitHub

在仓库根目录执行：

```powershell
Copy-Item -Recurse -Force "open-source/templates/github" ".github"
Copy-Item -Force "open-source/CONTRIBUTING.md","open-source/CODE_OF_CONDUCT.md","open-source/SECURITY.md","open-source/SUPPORT.md" ".github/"
```

复制后，Issue 表单、Pull Request 模板和社区健康文件才会被 GitHub 自动识别。本目录仍是这些材料的主版本，后续修改时需同步副本。

### Gitee

在仓库根目录执行：

```powershell
New-Item -ItemType Directory -Force ".gitee"
Copy-Item -Force "open-source/templates/gitee/*" ".gitee/"
```

Gitee 的模板识别规则可能随平台调整；若未自动显示，请在仓库的 Issue / Pull Request 设置中选择这些模板。

## 维护约定

- 根目录 `LICENSE` 是唯一的项目许可证全文，不在本目录复制第二份，避免版本不一致。
- 新增或升级依赖时，同步更新 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和手机端“开源许可”页。
- 发布新版本时，同步更新 [CHANGELOG.md](CHANGELOG.md)，并按 [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md) 检查。
- 不在任何材料中写入真实密钥、验证码、激活码、签名文件、密码、私人联系方式或本机绝对路径。
