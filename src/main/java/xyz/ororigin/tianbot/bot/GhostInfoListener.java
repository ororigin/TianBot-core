package xyz.ororigin.tianbot.bot;

import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/**
 * Ghost 假人实体同步监听器。
 * <p>
 * 不可见假人（visible=false）不在 PlayerList，因此服务端不会向玩家广播其 PlayerInfo，
 * 而客户端 {@code ClientPacketListener.createEntityFromPacket} 对 PLAYER 类型实体要求
 * playerInfoMap 中先存在该 UUID，否则实体不会被生成/渲染。
 * <p>
 * 这里借助 Paper 的实体追踪事件补上这一步：
 * <ul>
 *   <li>{@link PlayerTrackEntityEvent}：在 {@code addPairing} 发送 spawn bundle 之前（同一 connection
 *       有序）注入仅含 ADD_PLAYER action 的 PlayerInfo——客户端可渲染该实体，但不会进入 Tab 列表。</li>
 *   <li>{@link PlayerUntrackEntityEvent}：玩家离开视距时用 PlayerInfoRemove 清理客户端残留的 info 条目。</li>
 * </ul>
 * 事件在假人所在区域线程触发，{@code connection.send} 线程安全，且与随后的 spawn bundle 同线程同连接有序。
 */
public class GhostInfoListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        Bot bot = BotManager.botMap.get(event.getEntity().getUniqueId());
        if (bot == null || bot.visible || bot.serverPlayer == null) {
            return;
        }
        // 必须在 addPairing 发送 spawn bundle 之前把 PlayerInfo 入队（同一 connection 有序）
        ClientboundPlayerInfoUpdatePacket packet =
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, bot.serverPlayer);
        ((CraftPlayer) event.getPlayer()).getHandle().connection.send(packet);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUntrack(PlayerUntrackEntityEvent event) {
        Bot bot = BotManager.botMap.get(event.getEntity().getUniqueId());
        // 仅 ghost 假人需要手动清理 info；visible 假人走 vanilla（Tab 保留）
        if (bot == null || bot.visible || bot.serverPlayer == null) {
            return;
        }
        ClientboundPlayerInfoRemovePacket packet =
                new ClientboundPlayerInfoRemovePacket(List.of(bot.serverPlayer.getUUID()));
        ((CraftPlayer) event.getPlayer()).getHandle().connection.send(packet);
    }
}
