package xyz.ororigin.tianbot.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.bot.BotManager;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /fplayer} 命令树支撑工具：假人解析、反馈回显、补全。
 *
 * <p>对标 Carpet {@code PlayerCommand} 的 {@code getPlayer}/{@code cantManipulate}/
 * {@code getPlayerSuggestions}/{@code Messenger}，但操控对象固定为 FoliaBot 假人：
 * 假人非真实玩家，因此无需 OP 身份判定，仅需假人存在且已生成。</p>
 */
public final class CommandSupport {

    /** 命令树中「假人名字」参数的固定名（与 Carpet /player 一致）。 */
    public static final String PLAYER_ARG = "player";

    private CommandSupport() {
    }

    /**
     * 从命令上下文解析已生成的假人。
     *
     * @param context 命令上下文（须含 {@value #PLAYER_ARG} 参数）
     * @return 已生成的假人；未找到或未生成时回显错误并返回 {@code null}
     */
    public static Bot resolveBot(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, PLAYER_ARG);
        Bot bot = BotManager.getBot(name).orElse(null);
        if (bot == null) {
            failure(sender, Lang.t("command.bot.not-found", "player", name));
            return null;
        }
        if (!bot.isSpawned()) {
            failure(sender, Lang.t("command.bot.not-spawned", "player", name));
            return null;
        }
        return bot;
    }

    /** 回显成功消息。 */
    public static void success(CommandSender sender, String message) {
        sender.sendPlainMessage(message);
    }

    /** 回显失败消息。 */
    public static void failure(CommandSender sender, String message) {
        sender.sendPlainMessage(message);
    }

    /**
     * 异步动作回显：future 完成时（可能在假人所在区域线程）把结果发回 sender。
     * Paper 的 {@code sendMessage} 线程安全，无需再切调度器。
     */
    public static void respond(CompletableFuture<Void> future, CommandSender sender, String successMessage) {
        future.whenComplete((unused, throwable) -> {
            if (throwable != null) {
                failure(sender, Lang.t("command.action.failed", "reason", throwable.getMessage()));
            } else {
                success(sender, successMessage);
            }
        });
    }

    /** 在线假人名补全（按名字字典序）。 */
    public static SuggestionProvider<CommandSourceStack> suggestBots() {
        return (context, builder) -> {
            List<String> names = new ArrayList<>();
            for (Bot bot : BotManager.bots()) {
                names.add(bot.name());
            }
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                builder.suggest(name);
            }
            return builder.buildFuture();
        };
    }
}
