# 安全与凭据处理

本 Mod 不需要 GitHub 令牌、个人访问令牌、GitHub App 私钥或任何付费服务密钥。

工作流仅在发布任务中将 GitHub 自动注入的短期 `github.token` 作为 `GH_TOKEN` 环境变量交给 GitHub CLI。令牌不写入源码、不写入安装包，也不进行固定长度检查、截断或格式解析。普通构建及扫描仅有 `contents: read` 权限；发布任务单独申请 `contents: write`。

GitHub App 安装令牌的新 `ghs_` 格式是平台兼容性变更通知，并非本仓库泄露凭据的告警。本仓库没有自建 GitHub App，也没有调用创建安装令牌的 API，因此无需添加临时格式覆盖请求头。

每次推送和 PR 自动使用校验过 SHA-256 的 Gitleaks 8.30.1 扫描完整获取的 Git 历史，结果脱敏。除内置规则外，还覆盖 GitHub 官方建议的可变长度安装令牌模式。发布依赖扫描通过。检出代码时关闭持久化 Git 凭据。

请勿提交真实服务器的玩家数据、封禁文件、私钥、`.env` 或个人访问令牌。自动扫描不能作为绝对不存在泄露或安全缺陷的保证；发现有效凭据时，应先撤销并轮换，再处理历史记录。

参考：[GitHub 令牌迁移公告](https://github.blog/changelog/2026-10-02-stateless-github-app-installation-tokens-rolled-out/)。
