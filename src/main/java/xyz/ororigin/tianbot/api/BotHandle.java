package xyz.ororigin.tianbot.api;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 对外暴露的假人（Bot）句柄接口。
 *
 * <p>所有动作方法均为异步，返回 {@link CompletableFuture}：
 * 成功时正常完成；假人未生成/已下线或动作失败时以异常完成。
 * 动作的执行与现有的 {@code ActionHandler} 语义一致（Folia 区域线程安全）。</p>
 *
 * <p>本接口刻意不暴露任何 NMS（net.minecraft.*）类型，仅使用 JDK 与 Bukkit 类型，
 * 便于其他插件在 compileOnly 依赖 FoliaBot 后安全调用。</p>
 */
public interface BotHandle {

    // ==================== 状态查询 ====================

    /** 假人名字。 */
    String name();

    /** 假人离线 UUID（由名字按 OfflinePlayer 规则推导）。 */
    UUID uuid();

    /** 是否可见（已注册进 PlayerList）；false 表示 ghost 假人。 */
    boolean isVisible();

    /** 假人是否已成功生成且未下线（SPAWNED 或 TICKING）。 */
    boolean isSpawned();

    // ==================== 攻击（左键） ====================

    /** 攻击一次（单击），自动结束。 */
    CompletableFuture<Void> attack();

    /** 持续按住左键（挖方块/持续攻击），直到 {@link #stopActions()}。 */
    CompletableFuture<Void> attackContinuous();

    /** 每 {@code ticks} tick 攻击一次，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> attackInterval(int ticks);

    // ==================== 右键使用 ====================

    /** 右键一次，自动结束。 */
    CompletableFuture<Void> use();

    /** 持续按住右键（开门/熔炉/吃食物等），直到 {@link #stopActions()}。 */
    CompletableFuture<Void> useContinuous();

    /** 每 {@code ticks} tick 右键一次，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> useInterval(int ticks);

    // ==================== 丢弃物品 ====================

    /** 丢手持格 1 个物品，自动结束。 */
    CompletableFuture<Void> dropItem();

    /** 丢手持格整组物品，自动结束。 */
    CompletableFuture<Void> dropStack();

    /** 持续丢手持格物品（每 tick 1 个），直到 {@link #stopActions()}。 */
    CompletableFuture<Void> dropItemContinuous();

    /** 持续丢手持格整组物品（每 tick 1 组），直到 {@link #stopActions()}。 */
    CompletableFuture<Void> dropStackContinuous();

    /** 每 {@code ticks} tick 丢 1 个手持物品，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> dropItemInterval(int ticks);

    /** 每 {@code ticks} tick 丢 1 组手持物品，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> dropStackInterval(int ticks);

    // ==================== 跳跃 ====================

    /** 跳一下（落地跳跃/空中开鞘翅滑翔），自动结束。 */
    CompletableFuture<Void> jump();

    /** 持续按住跳跃键，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> jumpContinuous();

    /** 每 {@code ticks} tick 跳一次，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> jumpInterval(int ticks);

    // ==================== 交换主副手 ====================

    /** 交换主副手一次，自动结束。 */
    CompletableFuture<Void> swapHand();

    /** 持续交换主副手（每 tick），直到 {@link #stopActions()}。 */
    CompletableFuture<Void> swapHandContinuous();

    /** 每 {@code ticks} tick 交换一次主副手，直到 {@link #stopActions()}。 */
    CompletableFuture<Void> swapHandInterval(int ticks);

    // ==================== 潜行 / 疾跑 ====================

    /** 进入潜行（持续到 {@link #unsneak()}）。 */
    CompletableFuture<Void> sneak();

    /** 取消潜行。 */
    CompletableFuture<Void> unsneak();

    /** 疾跑（持续到 {@link #unsprint()}）。 */
    CompletableFuture<Void> sprint();

    /** 取消疾跑。 */
    CompletableFuture<Void> unsprint();

    // ==================== 骑乘 ====================

    /** 骑最近的矿车/船/马（RIDEABLE）。 */
    CompletableFuture<Void> mount();

    /** 骑最近的任意实体。 */
    CompletableFuture<Void> mountAny();

    /** 下马。 */
    CompletableFuture<Void> dismount();

    // ==================== 朝向 ====================

    /** 看向北/南/东/西/上/下。 */
    CompletableFuture<Void> lookNorth();

    CompletableFuture<Void> lookSouth();

    CompletableFuture<Void> lookEast();

    CompletableFuture<Void> lookWest();

    CompletableFuture<Void> lookUp();

    CompletableFuture<Void> lookDown();

    /** 看向指定坐标（世界坐标）。 */
    CompletableFuture<Void> lookAt(double x, double y, double z);

    /** 设置绝对朝向（yaw/pitch）。 */
    CompletableFuture<Void> look(float yaw, float pitch);

    /** 相对旋转（dyaw/dpitch 增量）。 */
    CompletableFuture<Void> turn(float deltaYaw, float deltaPitch);

    // ==================== 移动 ====================

    /** 持续前进。 */
    CompletableFuture<Void> moveForward();

    /** 持续后退。 */
    CompletableFuture<Void> moveBackward();

    /** 持续左移。 */
    CompletableFuture<Void> moveLeft();

    /** 持续右移。 */
    CompletableFuture<Void> moveRight();

    /** 通用移动向量（forward/strafing 可同时非零 → 斜向移动）。 */
    CompletableFuture<Void> moveVector(float forward, float strafing);

    /** 停止移动（清空 zza/xxa）。 */
    CompletableFuture<Void> stopMoving();

    // ==================== 等待 / 阻塞队列 ====================

    /**
     * 阻塞该假人的动作队列 {@code ticks} 个游戏 tick（等待/暂停）。
     * 期间后续加入的动作排队等待，倒计时结束后自动放行；多个 sleep 按入队顺序依次阻塞。
     */
    CompletableFuture<Void> sleep(int ticks);

    // ==================== 聊天栏输出 ====================

    /**
     * 以假人身份在聊天栏输出内容：以 {@code "/"} 开头则当命令执行（自动去 "/" 前缀），
     * 否则在全局聊天广播消息（显示 {@code "<假人> 内容"}）。
     * 命令反馈发给假人自身（假人无客户端，不可见）。
     */
    CompletableFuture<Void> say(String content);

    /**
     * 同上，但命令反馈（成功/失败消息）回显给指定发送者。
     *
     * @param content    聊天内容或命令（"/" 开头为命令）
     * @param feedbackTo 接收命令反馈的发送者；为 {@code null} 表示不回显
     */
    CompletableFuture<Void> say(String content, @Nullable CommandSender feedbackTo);

    // ==================== 脚本（script 模式） ====================

    /**
     * 解析并运行一段 JSON 脚本文本（动作序列，支持循环重放）。
     *
     * <p>脚本格式（与 {@code ScriptParser} 一致）：
     * <pre>{@code
     * {
     *   "loops": 3,                          // 循环次数，-1 表示无限循环
     *   "steps": [
     *     { "action": "attack", "mode": "once" },   // mode: once / continuous / interval(需 ticks)
     *     { "action": "wait", "ticks": 20 },
     *     { "action": "move", "dir": "forward" },
     *     { "action": "look", "target": "north" },
     *     { "action": "say", "message": "hi" }
     *   ]
     * }
     * }</pre>
     * 支持的动作：attack/use/jump/swapHands/drop/dropStack/wait/move/look/turn/
     * sneak/unsneak/sprint/unsprint/mount/dismount/say/stopCurrent。</p>
     *
     * <p>JSON 非法或步骤不合法时同步抛出 {@link java.lang.RuntimeException}（{@code ScriptParseException}）；
     * 解析成功后，动作在假人所在区域线程排队执行，返回的 future 在动作入队后完成。</p>
     *
     * @param json 脚本文本（JSON）
     * @return 动作入队完成的 future
     */
    CompletableFuture<Void> runScript(String json);

    // ==================== 其他 ====================

    /** 终止该假人的所有行为（等价于旧的 {@code actions.terminate()}）。 */
    CompletableFuture<Void> stopActions();

    /**
     * 仅停止假人当前正在执行的动作（持续攻击/移动/丢/跳/换手等），保留动作队列。
     * 不会清除队列，不会打断阻塞中的 sleep，也不会复位潜行/疾跑/骑乘等粘性姿态。
     * 与 {@link #stopActions()}（清空所有行为）不同。
     */
    CompletableFuture<Void> stopCurrentActions();
}
