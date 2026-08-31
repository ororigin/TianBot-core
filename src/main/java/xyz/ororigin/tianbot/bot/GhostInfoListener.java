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

public class GhostInfoListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrack(PlayerTrackEntityEvent event) {
        Bot bot = BotManager.botMap.get(event.getEntity().getUniqueId());
        if (bot == null || bot.visible || bot.serverPlayer == null) {
            return;
        }
        ClientboundPlayerInfoUpdatePacket packet =
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, bot.serverPlayer);
        ((CraftPlayer) event.getPlayer()).getHandle().connection.send(packet);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUntrack(PlayerUntrackEntityEvent event) {
        Bot bot = BotManager.botMap.get(event.getEntity().getUniqueId());
        if (bot == null || bot.visible || bot.serverPlayer == null) {
            return;
        }
        ClientboundPlayerInfoRemovePacket packet =
                new ClientboundPlayerInfoRemovePacket(List.of(bot.serverPlayer.getUUID()));
        ((CraftPlayer) event.getPlayer()).getHandle().connection.send(packet);
    }
}
