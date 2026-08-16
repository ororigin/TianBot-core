package xyz.ororigin.tianbot.bot.action.module;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import xyz.ororigin.tianbot.bot.action.Action;
import xyz.ororigin.tianbot.bot.action.FinishReason;

import java.util.function.Consumer;

public class ChatAction extends Action {

    private final String content;
    @Nullable
    private final CommandSource feedback;
    private FinishReason result;

    private ChatAction(String content, @Nullable CommandSource feedback) {
        this.content = content;
        this.feedback = feedback;
    }

    public static ChatAction say(String content) {
        return new ChatAction(content, null);
    }

    public static ChatAction say(String content, @Nullable CommandSource feedback) {
        return new ChatAction(content, feedback);
    }

    public FinishReason result() {
        return result;
    }

    @Override
    public void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        FinishReason reason;
        try {
            if (player == null || player.isRemoved()) {
                reason = FinishReason.FAILED;
            } else if (content == null || content.isEmpty()) {
                reason = FinishReason.FAILED;
            } else if (content.startsWith("/")) {
                reason = executeCommand(player);
            } else {
                reason = broadcastChat(player);
            }
        } catch (Throwable t) {
            reason = FinishReason.FAILED;
        }
        this.result = reason;
        onFinished.accept(reason);
    }

    private FinishReason executeCommand(ServerPlayer player) {
        String command = content.substring(1);
        if (command.isEmpty()) {
            return FinishReason.FAILED;
        }
        CommandSourceStack source = player.createCommandSourceStack();
        if (feedback != null) {
            source = source.withSource(feedback);
        }
        player.level().getServer().getCommands().performPrefixedCommand(source, command);
        return FinishReason.SUCCESS;
    }

    private FinishReason broadcastChat(ServerPlayer player) {
        PlayerChatMessage message = PlayerChatMessage.unsigned(player.getUUID(), content);
        ChatType.Bound chatType = ChatType.bind(ChatType.CHAT, player);
        player.level().getServer().getPlayerList().broadcastChatMessage(message, player, chatType);
        return FinishReason.SUCCESS;
    }
}
