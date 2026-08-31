package xyz.ororigin.tianbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.bot.BotManager;
import xyz.ororigin.tianbot.bot.BotNamePrefix;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public final class CommandSupport {

    public static final String PLAYER_ARG = "player";

    private CommandSupport() {
    }

    public static Bot resolveBot(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String displayName = StringArgumentType.getString(context, PLAYER_ARG);
        String realName = BotNamePrefix.getBotRealName(displayName);
        Bot bot = BotManager.getBot(realName).orElse(null);
        if (bot == null) {
            failure(sender, Lang.t("command.bot.not-found", "player", displayName));
            return null;
        }
        if (!bot.isSpawned()) {
            failure(sender, Lang.t("command.bot.not-spawned", "player", displayName));
            return null;
        }
        return bot;
    }

    public static void success(CommandSender sender, String message) {
        sender.sendPlainMessage(message);
    }

    public static void failure(CommandSender sender, String message) {
        sender.sendPlainMessage(message);
    }

    public static void respond(CompletableFuture<Void> future, CommandSender sender, String successMessage) {
        future.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                failure(sender, Lang.t("command.action.failed", "reason", throwable.getMessage()));
            } else {
                success(sender, successMessage);
            }
        });
    }

    /** 在线假人名补全*/
    public static SuggestionProvider<CommandSourceStack> suggestBots() {
        return (context, builder) -> {
            List<String> names = new ArrayList<>();
            for (Bot bot : BotManager.bots()) {
                names.add(BotNamePrefix.getDisplayName(bot.name()));
            }
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                builder.suggest(name);
            }
            return builder.buildFuture();
        };
    }
}
