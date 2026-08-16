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
import xyz.ororigin.tianbot.bot.Bot;

/**
 * 假人专用包监听器：拦截服务端发给假人自己的 {@link ClientboundSetEntityMotionPacket} 并回施速度。
 * <p>
 * 真实玩家的击退/爆炸等"外力位移"由客户端收到速度包后本地集成速度完成（客户端参与，见 Meteor Velocity 模块）；
 * 假人没有客户端，因此在这里手动把包里的速度 lerpMotion 回实体，随后由
 * {@code tickPhysics() -> serverPlayer.doTick() -> travel()} 在服务端集成出位移。
 * <p>
 * 注意：Player.causeExtraKnockback 在发完速度包后会立刻用 setDeltaMovement(oldMovement) 回退击退速度，
 * 因此本拦截必须把 lerpMotion 延迟（queueTickTaskQueue）到回退之后执行，不能同步或同线程立即执行。
 * <p>
 * 参考 minecraft-fakeplayer 的 FakeServerGamePacketListenerImpl。
 * 差异：Arbor 的 {@code ServerEntity.tick()} 在 sendToTrackingPlayersAndSelf 之前已把 hurtMarked 清为 false，
 * 故这里不再依赖 hurtMarked 判断，仅按包 id（发给假人自己）过滤即可——
 * 服务端只有 hurtMarked 路径会把速度包发给实体自己（needsSync 路径只发追踪者），按 id 过滤等价且更稳。
 */
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

    /**
     * 假连接没有真实通道：{@link FakeConnection#send} 为空操作，导致父类 disconnect0 中
     * {@code connection.send(ClientboundDisconnectPacket, thenRun(connection.disconnect))} 的
     * thenRun 永不触发，整条断开链（handleDisconnection -> onDisconnect -> removePlayerFromWorld）断掉，
     * 假人永远不会被移除。这里覆写 disconnect：在假人所在区域线程触发可取消的 PlayerKickEvent，
     * 若未被取消则调用 {@link Bot#kick} 走标准 PlayerList.remove 完成真正踢出。
     * <p>
     * 所有 kick 入口（vanilla /kick、Bukkit kickPlayer/kick、ban、disconnectAsync 等）最终都会汇聚到
     * {@code ServerCommonPacketListenerImpl.disconnect(DisconnectionDetails)} 的虚调用，覆写此方法即可全覆盖。
     */
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
            // Player.causeExtraKnockback 在 connection.send(...) 返回后会立刻执行
            // entity.setDeltaMovement(oldMovement) 把击退速度回退（服务端不为真人保留击退速度，位移由客户端负责）。
            // 因此必须把 lerpMotion 延迟到 bot 所在区域的后续 tick（在回退之后）重新施加，
            // 再由 tickPhysics -> doTick() -> travel() 集成出位移。
            // 不能用 Bukkit.getScheduler()（Folia 的 CraftScheduler 会抛 UnsupportedOperationException），
            // 也不能用 queueOrExecuteTickTask（同区域线程时会立即执行、仍在回退之前），必须用 queueTickTaskQueue 恒投递。
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
