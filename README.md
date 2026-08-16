# TianBot-core

针对 **Folia 26.2** 的假人（Fake Player）插件 —— Carpet 假人功能在 Folia 服务端上的插件移植。

在 Folia 的线程化区域体系下，通过伪造网络连接直接构造 `ServerPlayer` 生成假人，提供完整的
动作指令、JSON 动作脚本、自定义属性持久化与第三方插件 API。

## 功能特性

- **假人直接生成**：伪造连接/网络栈生成真实 `ServerPlayer`，支持 `visible` 与 `ghost`（隐藏、不入 PlayerList）双模式
- **完整动作系统**：攻击 / 右键使用 / 跳跃 / 丢弃 / 交换副手 / 骑乘 / 潜行 / 疾跑 / 朝向 / 移动 / 等待 / 聊天
- **Script 模式**：JSON 动作脚本，支持循环重放（`loops=-1` 无限循环）
- **自定义属性 API**：第三方插件可注册假人自定义属性，SQLite 持久化，离线假人也可读写
- **AuthMe 兼容**：假人上线自动跳过 AuthMe 登录，可自动注册（全反射可选集成）
- **SQLite 持久化**：假人属性数据库（WAL），数据库不可用不阻断插件运行
- **Folia 区域线程安全**：所有操作均调度到假人所在区域/实体线程执行
- **本地化消息**：基于 `messages.yml` 的中文反馈体系，可热编辑

## 环境要求

| 项目 | 版本 |
|------|------|
| 服务端 | Folia 26.2（`26.2.build.4-beta`，运行时即 Mojang 映射，无需 reobf） |
| Java | 25（编译 toolchain；Gradle 9.4.1 wrapper 自带） |
| Gradle | 9.4.1（wrapper 自带） |

> 插件在非 Folia 服务端上会自动禁用（启动时检测
> `io.papermc.paper.threadedregions.RegionizedServerInitEvent`）。

## 构建与部署

```bash
gradlew.bat build        # 编译并打包
gradlew.bat shadowJar    # 打包运行时依赖（sqlite-jdbc）→ build/libs/TianBot-core-26.2-beta-1-all.jar
gradlew.bat deploy       # shadowJar 后复制到测试服 plugins 目录
```

`deploy` 目标目录默认 `D:/minecraft/server/26.2/plugins`，可在
`gradle.properties` 的 `foliaBotTestServerPlugins` 覆盖。

## 使用命令

所有命令以 `/tianbotadmin <假人> ...` 开头，需要权限 `tianbot.admin`（默认 OP）。
`<假人>` 参数支持按名字补全（仅在线假人）。

### 生命周期

| 命令 | 说明 |
|------|------|
| `/tianbotadmin <假人> spawn` | 以默认配置生成假人（可见） |
| `/tianbotadmin <假人> ghostmode true\|false` | 运行时切换可见 / 隐藏 |
| `/tianbotadmin <假人> stop` | 终止该假人的所有行为（清空队列） |
| `/tianbotadmin <假人> kill` | 下线假人（触发 PlayerQuitEvent、存档、广播离开） |

### 动作

带 `once / continuous / interval <ticks>` 三种模式的动作：
`use`、`jump`、`attack`、`drop`、`dropStack`、`swapHands`。
不带参数时默认为 `once`。

其他动作：

| 命令 | 说明 |
|------|------|
| `/tianbotadmin <假人> mount [anything]` | 骑最近的矿车/船/马；`anything` 骑任意实体 |
| `/tianbotadmin <假人> dismount` | 下马 |
| `/tianbotadmin <假人> sneak` / `unsneak` | 潜行 / 取消潜行 |
| `/tianbotadmin <假人> sprint` / `unsprint` | 疾跑 / 取消疾跑 |
| `/tianbotadmin <假人> look north\|south\|east\|west\|up\|down` | 朝向固定方向 |
| `/tianbotadmin <假人> look at <位置>` | 看向指定坐标 |
| `/tianbotadmin <假人> look <yaw> <pitch>` | 设置绝对朝向 |
| `/tianbotadmin <假人> turn left\|right\|back\|<旋转>` | 相对旋转 |
| `/tianbotadmin <假人> move [forward\|backward\|left\|right]` | 持续移动；无参数为停止移动 |
| `/tianbotadmin <假人> chat <内容>` | 让假人聊天：`/` 开头当命令执行，否则全局广播 |

## Script 模式

`/tianbotadmin <假人> script <JSON>` 可为假人运行一段 JSON 动作脚本（可循环重放）。
JSON 非法时回显解析失败；`stop` 可随时停止。

```json
{
  "loops": 3,
  "steps": [
    { "action": "jump" },
    { "action": "move", "dir": "forward" },
    { "action": "wait", "ticks": 20 },
    { "action": "move", "dir": "stop" }
  ]
}
```

字段说明：

| 字段 | 说明 |
|------|------|
| `loops` | 循环次数，`-1` 表示无限循环，缺省为 1 |
| `steps` | 动作步骤数组 |

支持的动作：

| 动作 | 参数 |
|------|------|
| `attack` / `use` / `jump` / `swapHands` / `drop` / `dropStack` | `mode`: `once`(默认) / `continuous` / `interval`（`interval` 需 `ticks`） |
| `wait` | `ticks`（必填） |
| `move` | `dir`: `forward`/`backward`/`left`/`right`/`stop`；或 `forward`/`strafe` 浮点向量 |
| `look` | `target`: 方向；`at`: `{x,y,z}`；或 `yaw`/`pitch` |
| `turn` | `yaw`、`pitch`（必填，增量） |
| `sneak` / `unsneak` / `sprint` / `unsprint` / `dismount` | — |
| `mount` | `any`: `true` 骑任意实体（缺省只骑可骑乘） |
| `say` | `message`（必填） |
| `stopCurrent` | 停止当前动作，保留队列 |

## 开发者 API

所有公开 API 经 Bukkit **Services Manager** 注册，可通过
`Bukkit.getServicesManager().load(...)` 获取；插件自身也可用 `TianBotPlugin.getApi()`。

### TianBotApi —— 服务层

```java
TianBotApi api = Bukkit.getServicesManager().load(TianBotApi.class);

// 生成假人并等待上线（异步）
api.spawnBot("bot1").thenAccept(bot -> bot.attack());

// 指定 host/port/ghost 生成
api.spawnBot("bot2", "127.0.0.1", 25565, true);

// 下线 / 切换可见性 / 查询
api.stopBot("bot1");
api.setVisible("bot1", false);
Optional<BotHandle> bot = api.getBot("bot1");
Collection<BotHandle> bots = api.getBots();

// 运行脚本
api.runScript("bot1", "{\"loops\":2,\"steps\":[{\"action\":\"jump\"}]}");
```

### BotHandle —— 单假人句柄

刻意 **不暴露任何 NMS 类型**（仅 JDK + Bukkit），方便其他插件 `compileOnly` 依赖后安全调用。
所有方法返回 `CompletableFuture`，假人未生成/已下线或动作失败时以异常完成。

```java
BotHandle bot = api.getBot("bot1").orElseThrow();

bot.attack();                 // 攻击一次
bot.attackContinuous();       // 持续攻击，直到 stopActions()
bot.attackInterval(10);       // 每 10 tick 攻击一次
bot.use();                    // 右键使用
bot.jump();                   // 跳跃
bot.sneak();  bot.unsneak();  // 潜行 / 取消潜行
bot.sprint(); bot.unsprint(); // 疾跑 / 取消疾跑
bot.mount();  bot.dismount(); // 骑乘 / 下马
bot.lookAt(x, y, z);          // 看向坐标
bot.moveForward();            // 持续前进
bot.sleep(20);                // 阻塞队列 20 tick
bot.say("hello");             // 聊天（"/" 开头执行命令）
bot.runScript("{...}");       // 运行 JSON 脚本
bot.stopActions();            // 终止所有行为
bot.stopCurrentActions();     // 仅停止当前动作，保留队列
```

### BotPropertyApi —— 自定义属性

属性键格式 `[a-z0-9_]+`（保留键 `uuid` / `bot_name` / `name`）；值仅支持标量类型
（`String` / `UUID` / `byte` / `short` / `int` / `long` / `boolean` / `float` / `double`）。

```java
BotPropertyApi props = Bukkit.getServicesManager().load(BotPropertyApi.class);

props.register(BotProperty.of("home_world", String.class, "overworld"));
props.setProperty("bot1", "home_world", "nether");
props.getProperty("bot1", "home_world").thenAccept(v -> { /* ... */ });
```

按名字读写时以 OfflinePlayer 规则推导 UUID，因此**离线假人也同样可读可写**。

## 配置文件（`config.yml`）

```yaml
database:
  file: bots.db              # SQLite 数据库文件名（plugins/Tianbot-core/ 下）

authme:
  enabled: true              # AuthMe 兼容开关
  auto-register: true        # 未注册假人自动注册并登录
  password: "TianBot_AutoRegister"

script:
  max-burst-per-tick: 128    # 单 tick 内连续即时动作上限（SequenceAction 分片阀门）
```

消息文案在 `plugins/Tianbot-core/messages.yml`（首次启动导出，中文默认，可编辑）。

## 数据持久化

- SQLite 数据库（`bot_properties` 表 + 自定义属性 EAV 表 `bot_custom_properties`，WAL 模式）
- 单线程 executor `TianBot-Sqlite` 异步写入
- 数据库初始化失败时插件继续运行，属性读写降级（读返回空、写抛异常）

## 项目结构

```
src/main/java/xyz/ororigin/tianbot/
├── TianBotPlugin.java        # 主类（JavaPlugin，Folia 检测、API 注册）
├── api/                      # 对外接口：TianBotApi / BotHandle / BotPropertyApi / BotProperty
├── service/                  # 服务层实现（TianBotServiceImpl / BotPropertyServiceImpl）
├── bot/                      # 假人核心：Bot / BotManager / BotConfig / GhostInfoListener
│   ├── fakes/                # 伪造连接栈：FakeConnection / FakeChannel / FakeChannelPipeline ...
│   └── action/               # 动作框架：Action / PersistentAction / ToggleAction / ActionHandler
│       ├── module/           # 具体动作：Attack / Use / Jump / Drop / Move / Look / Mount ...
│       └── script/           # Script 解析：ScriptParser / ScriptDefinition
├── command/                  # Paper Brigadier 命令：FPlayerCommand / CommandSupport
├── data/                     # 数据层：DatabaseManager / SqliteBotDatabase / BotPropertyRegistry ...
└── utils/                    # Lang（本地化）/ AuthMeCompat / ThreadUtils
```

## 备注

- **构建产物**：`TianBot-core-26.2-beta-1.jar`（薄）与 `TianBot-core-26.2-beta-1-all.jar`（shadow，含 sqlite-jdbc）
- **Folia 调度**：假人 tick 用实体调度器 `getScheduler().runAtFixedRate(...)`，自动跟随跨区块；区块操作走 Moonrise 区块任务调度器
- 插件无 `paper-plugin.yml`，仅 `plugin.yml`（`folia-supported: true`，`load: STARTUP`）
- 未自定义 Bukkit 事件：外部插件可监听假人生命周期触发的标准 `PlayerJoinEvent` / `PlayerQuitEvent`
