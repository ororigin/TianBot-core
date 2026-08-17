package xyz.ororigin.tianbot.bot.fakes;

import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.connection.DisconnectionReason;
import io.papermc.paper.threadedregions.RegionizedServer;
import net.kyori.adventure.text.Component;
import net.minecraft.core.SectionPos;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.utils.Lang;
import xyz.ororigin.tianbot.utils.PreventKickingCompat;

public class FakeServerGamePacketListenerImpl extends ServerGamePacketListenerImpl {

    /** 所属假人（用于执行真正的踢出/移除） */
    public Bot bot;
    /** 防重入：kick 只执行一次 */
    private boolean kickHandled;

    public FakeServerGamePacketListenerImpl(
            MinecraftServer server,
            Connection connection,
            ServerPlayer player,
            CommonListenerCookie cookie
    ) {
        super(server, connection, player, cookie);
    }


    @Override
    public void disconnect(final DisconnectionDetails details) {
        if (this.bot == null || this.player == null || this.player.isRemoved()) {
            return;
        }
        ServerLevel level = this.player.level();
        if (level == null) {
            return;
        }
        int chunkX = SectionPos.blockToSectionCoord(this.player.getBlockX());
        int chunkZ = SectionPos.blockToSectionCoord(this.player.getBlockZ());
        // 与 Bot.logout() 一致：已在区域线程则立即执行，否则投递到假人所在区域线程
        RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(level, chunkX, chunkZ, () -> processKick(details));
    }

    private void processKick(final DisconnectionDetails details) {
        if (this.kickHandled || this.player.isRemoved()) {
            return;
        }
        // 防踢出：按 prevent-kicking 策略忽略
        if (PreventKickingCompat.shouldPrevent(this.bot, details.reason())) {
            TianBotPlugin.instance.getLogger().info(Lang.t(
                    "log.kick-prevented",
                    "player", this.player.getScoreboardName(),
                    "reason", details.reason() == null ? "" : details.reason().getString()
            ));
            return;
        }
        // 构建默认离开消息（与 Paper 默认一致）
        Component leaveMessage = Component.translatable(
                "multiplayer.player.left",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                Component.text(this.player.getScoreboardName())
        );
        PlayerKickEvent.Cause cause = details.disconnectionReason()
                .flatMap(DisconnectionReason::game)
                .orElse(PlayerKickEvent.Cause.UNKNOWN);
        PlayerKickEvent event = new PlayerKickEvent(
                this.player.getBukkitEntity(),
                PaperAdventure.asAdventure(details.reason()),
                leaveMessage,
                cause
        );
        if (this.server.isRunning()) {
            ((CraftServer) Bukkit.getServer()).getPluginManager().callEvent(event);
        }
        if (event.isCancelled()) {
            // 插件取消了踢出：假人保持在线
            return;
        }
        this.player.quitReason = PlayerQuitEvent.QuitReason.KICKED;
        this.kickHandled = true;
        this.bot.kick(PaperAdventure.asVanilla(event.reason()), event.leaveMessage());
    }

    @Override
    public void send(Packet<?> packet) {
        if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == this.player.getId()) {
            ServerLevel level = this.player.level();
            if (level != null) {
                int chunkX = SectionPos.blockToSectionCoord(this.player.getBlockX());
                int chunkZ = SectionPos.blockToSectionCoord(this.player.getBlockZ());
                RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(level, chunkX, chunkZ, () -> {
                    this.player.lerpMotion(motion.movement());
                });
            }
        }
    }
}
