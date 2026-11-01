# PlayGuard 防沉迷 Mod

Minecraft Java **1.20.1** 专用服务端 Mod，提供 **Forge / NeoForge / Fabric** 三种独立安装包。客户端无需安装。Forge/NeoForge 放到客户端时不启用功能；Fabric 声明为仅服务端。

## 行为

- 按 UUID 累计实际在线时间（包含挂机），默认累计 **3 小时**后踢出并临时封禁 **1 小时**。
- 离线暂停计时，重连继续累计；普通玩家的计时在重启后继续保留。休息期满后重新计时。
- 时限前 **10 / 5 / 1 分钟**发送中文私聊提醒；每次登录也显示本轮已用时间。
- 休息期间无法登录；期限按现实时间计算，离线也会走完休息时间。在线计时使用单调时钟，不受系统校时或 TPS 降低影响。
- 每次启动解除全部带本 Mod 专用来源与原因标记的封禁，并清零这些玩家的本轮计时。管理员手动封禁不会被解除或覆盖。
- 原版 ban 列表记录带到期时间的临时封禁，每秒检查过期项；只要服务器运行便会自动清理。管理员 `/pardon` 不会取消仍在生效的防沉迷休息记录。
- 免封白名单支持 UUID 或玩家名，OP **不会自动豁免**。加入白名单会立即解除本 Mod 的封禁并清零计时。

## 安装

需要 Java 17 和 Minecraft 1.20.1 服务端。

1. 从本仓库 **Actions → 最新成功的 Build and test → Artifacts** 下载对应加载器的压缩包，解压取得 JAR；有版本标签时也可从 Releases 下载。
2. 按服务器加载器选择 **一个** JAR，放入服务端 `mods/`。不要同时安装三个版本。
3. Fabric 额外安装 **Fabric API 0.92.2+1.20.1**，Fabric Loader 0.15.11 或以上。Forge 基准 47.2.0；NeoForge 基准 47.1.106（此版本仍使用 `forge` 标识与 `net.minecraftforge` 包）。
4. 启动一次后生成 `config/playguard.json`。

```json
{
  "playMinutes": 180,
  "restMinutes": 60,
  "whitelist": ["YourPlayerName", "12345678-1234-4234-8234-123456789abc"]
}
```

时间单位为分钟，范围 1~525600。默认提醒固定在剩余 10、5、1 分钟时触发；自定义时限短于某个提醒时间时，该提醒不触发。配置重载导致已超过某个提醒阈值时，不补发历史提醒。

## 管理命令

需要权限等级 3（服务器控制台可直接执行，不带 `/`）。

```text
/playguard reload
/playguard whitelist list
/playguard whitelist add 玩家名或UUID
/playguard whitelist remove 玩家名或UUID
```

修改配置文件后执行 reload。加载失败会保留旧配置；启动时配置或存档损坏会明确报错，避免悄悄覆盖原文件。白名单不影响原版服务器登录白名单，也不会绕过管理员封禁。建议使用 UUID 白名单，避免玩家改名后丢失豁免；离线模式 UUID 由服务器身份规则决定。

计时保存在 `<世界目录>/playguard-state.json`，每 30 秒、退出和停服时保存。进程异常终止最多丢失约 30 秒普通计时；封禁时立即保存。请通过正常停服备份世界和计时文件。

## GitHub 构建

本项目不要求本地编译。推送 `main`、PR 或手动运行工作流后，GitHub Actions 使用 Java 17 / Gradle 8.6：

1. 运行独立计时单元测试，覆盖三个提醒阈值、重连恢复、达到时限和延迟跨阈值。
2. 矩阵编译三种加载器，并执行生产环境名称重映射。
3. 安装真正的对应加载器服务端，用生成的 JAR 启动两次，检查能正常加载、重启解封及手动封禁保留。
4. 上传三个安装包及测试报告。推送 `v*` 标签时，在全部测试成功后自动创建 Release。

这些检查不模拟完整的真人联机长时间游玩，建议上线前用测试服验收提醒与断线重连行为。不要修改封禁条目中的 PlayGuard 专用标记，否则将被视为管理员封禁。

## 参考

- [Fabric 官方开发资料](https://fabricmc.net/develop/)
- [NeoForge 1.20.1 官方发行目录](https://maven.neoforged.net/releases/net/neoforged/forge/1.20.1-47.1.106/)
- [ForgeGradle 官方文档](https://docs.minecraftforge.net/en/fg-6.x/)

MIT License.
