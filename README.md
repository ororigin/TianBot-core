# FoliaBot

针对 **Arbor 26.2**（Luminol/Folia fork）的 Folia 插件开发环境脚手架。

## 环境要求

| 项目 | 版本 |
|------|------|
| Minecraft | 26.2 |
| 服务端 | Arbor 26.2 (`arbor-26.2-paperclip.jar`) |
| Java | 25（编译 toolchain，用 `D:\minecraft\java-runtime-epsilon` 完整 JDK 25） |
| Gradle | 9.4.1（wrapper 自带） |

## 依赖

- `zone.little.arbor:arbor-api:26.2.build.18-stable`（Arbor 官方 API，来自 `https://repo.littleovo.cn/releases`）
- 仅 `compileOnly`，无运行时依赖

## 构建

```bash
gradlew.bat build        # 编译并打包到 build/libs/FoliaBot-1.0.0.jar
gradlew.bat deploy       # 构建并复制到测试服 d:\minecraft\server\26.2\plugins\
```

## 测试服

- 位置：`D:\minecraft\server\26.2`
- 启动：双击 `runserver.bat`（使用 Java 25 runtime，`--nogui`）
- 核心：`arbor-26.2-paperclip.jar`（下载自 Arbor 官方 GitHub Release `26.2-95a5fb8`）
- 旧 26.1.2 插件已备份至 `plugins_backup_26.1.2/`

## Folia 调度器 API 注意事项（Arbor 26.2 / Luminol fork）

与上游 Folia API 的差异（编译实测）：

| 用途 | 本分支 API |
|------|-----------|
| 全局 tick 线程 | `Server#getGlobalRegionScheduler()` |
| 区域线程（按位置/区块） | `Server#getRegionScheduler()`（返回 `RegionScheduler`，**没有** `getRegionizedScheduler`） |
| 异步线程 | `Server#getAsyncScheduler()`（`runDelayed` 需 `long, TimeUnit`，无 `Duration` 重载） |
| 实体调度 | `Entity#getScheduler()`（`execute(plugin, run, retired, delayTicks)` 为 4 参） |
| 任务对象 | `ScheduledTask`（无 `getCurrentTick()`，用 `getExecutionState()`） |

## 示例

主类 `xyz.ororigin.tianbot.FoliaBotPlugin`，调度器示例 `scheduler.xyz.ororigin.tianbot.FoliaSchedulerExamples`。

游戏内：`/foliabot test`（演示全部调度器）/ `/foliabot info`
