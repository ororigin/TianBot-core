package xyz.ororigin.tianbot.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import xyz.ororigin.tianbot.api.BotHandle;

/**
 * 假人下线事件。
 *
 * <p>在假人下线（触发 {@code PlayerQuitEvent}、存档、广播离开消息）后、从 {@code BotManager}
 * 移除登记之前触发。事件在假人所在区域线程触发，仅暴露 {@link BotHandle}（不暴露任何 NMS 类型），
 * 其他插件可通过 {@code @EventHandler} 监听：</p>
 * <pre>{@code
 * @EventHandler
 * public void onBotQuit(BotQuitEvent event) {
 *     BotHandle bot = event.getBot();
 *     // bot.name(), bot.uuid(), bot.isSpawned() ...
 * }
 * }</pre>
 *
 * <p>本事件不可取消（假人此时已下线，无取消语义）。</p>
 */
public class BotQuitEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BotHandle bot;

    public BotQuitEvent(@NotNull BotHandle bot) {
        this.bot = bot;
    }

    /** 返回下线的假人句柄。 */
    @NotNull
    public BotHandle getBot() {
        return bot;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
