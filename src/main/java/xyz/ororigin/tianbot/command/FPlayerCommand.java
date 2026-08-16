package xyz.ororigin.tianbot.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.RotationResolver;
import io.papermc.paper.math.FinePosition;
import io.papermc.paper.math.Rotation;
import org.bukkit.command.CommandSender;
import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.bot.BotManager;
import xyz.ororigin.tianbot.bot.action.script.ScriptParseException;
import xyz.ororigin.tianbot.data.DatabaseManager;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static xyz.ororigin.tianbot.command.CommandSupport.PLAYER_ARG;
import static xyz.ororigin.tianbot.command.CommandSupport.failure;
import static xyz.ororigin.tianbot.command.CommandSupport.respond;
import static xyz.ororigin.tianbot.command.CommandSupport.resolveBot;
import static xyz.ororigin.tianbot.command.CommandSupport.success;
import static xyz.ororigin.tianbot.command.CommandSupport.suggestBots;


public final class FPlayerCommand {

    private FPlayerCommand() {
    }

    public static void register(Commands commands) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("tianbotadmin")
                .requires(source -> source.getSender().hasPermission("tianbot.admin"))
                .then(Commands.argument(PLAYER_ARG, StringArgumentType.word())
                        .suggests(suggestBots())
                        .then(Commands.literal("stop").executes(FPlayerCommand::stop))
                        .then(Commands.literal("kill").executes(FPlayerCommand::kill))
                        .then(actionCommand("use", "action.use", Bot::use, Bot::useContinuous, Bot::useInterval))
                        .then(actionCommand("jump", "action.jump", Bot::jump, Bot::jumpContinuous, Bot::jumpInterval))
                        .then(actionCommand("attack", "action.attack", Bot::attack, Bot::attackContinuous, Bot::attackInterval))
                        .then(actionCommand("drop", "action.drop", Bot::dropItem, Bot::dropItemContinuous, Bot::dropItemInterval))
                        .then(actionCommand("dropStack", "action.dropStack", Bot::dropStack, Bot::dropStackContinuous, Bot::dropStackInterval))
                        .then(actionCommand("swapHands", "action.swapHands", Bot::swapHand, Bot::swapHandContinuous, Bot::swapHandInterval))
                        .then(Commands.literal("mount")
                                .executes(manipulate("action.mount", Bot::mount))
                                .then(Commands.literal("anything").executes(manipulate("action.mount-anything", Bot::mountAny))))
                        .then(Commands.literal("dismount").executes(manipulate("action.dismount", Bot::dismount)))
                        .then(Commands.literal("sneak").executes(manipulate("action.sneak", Bot::sneak)))
                        .then(Commands.literal("unsneak").executes(manipulate("action.unsneak", Bot::unsneak)))
                        .then(Commands.literal("sprint").executes(manipulate("action.sprint", Bot::sprint)))
                        .then(Commands.literal("unsprint").executes(manipulate("action.unsprint", Bot::unsprint)))
                        .then(lookSubtree())
                        .then(turnSubtree())
                        .then(moveSubtree())
                        .then(Commands.literal("ghostmode")
                                .then(Commands.argument("mode", BoolArgumentType.bool())
                                        .executes(FPlayerCommand::ghostMode)))
                        .then(Commands.literal("spawn").executes(FPlayerCommand::spawn))
                        .then(Commands.literal("chat")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(FPlayerCommand::chat)))
                        .then(Commands.literal("script")
                                .then(Commands.argument("script", StringArgumentType.greedyString())
                                        .executes(FPlayerCommand::script))));
        commands.register(root.build(), Lang.get("command.description"), List.of());
    }
    /** 切换假人 ghost（隐藏）模式：ghostmode true → 隐藏（ghost）；false → 可见。 */
    private static int ghostMode(CommandContext<CommandSourceStack> context) {
        Bot bot = resolveBot(context);
        if (bot == null) {
            return 0;
        }
        boolean ghost = BoolArgumentType.getBool(context, "mode");
        CommandSender sender = context.getSource().getSender();
        respond(bot.setVisible(!ghost), sender,
                Lang.t("command.ghost.switched", "player", bot.name(), "mode",
                        Lang.get(ghost ? "command.mode.ghost" : "command.mode.visible")));
        return 1;
    }
    private static int spawn(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, PLAYER_ARG);
        Bot existing = BotManager.getBot(name).orElse(null);
        if (existing != null && existing.isSpawned()) {
            failure(sender, Lang.t("command.spawn.already-online", "player", name));
            return 0;
        }
        if (!DatabaseManager.isReady()) {
            success(sender, Lang.get("command.spawn.db-unavailable"));
        }
        success(sender, Lang.t("command.spawn.starting", "player", name));
        TianBotPlugin.getApi().spawnBot(name).whenComplete((bot, throwable) -> {
            if (throwable != null) {
                failure(sender, Lang.t("command.spawn.failed", "player", name, "reason", throwable.getMessage()));
            } else {
                success(sender, Lang.t("command.spawn.success", "player", name));
            }
        });
        return 1;
    }


    private static int chat(CommandContext<CommandSourceStack> context) {
        Bot bot = resolveBot(context);
        if (bot == null) {
            return 0;
        }
        CommandSender sender = context.getSource().getSender();
        String content = StringArgumentType.getString(context, "message");
        respond(bot.say(content, sender), sender,
                Lang.t("command.chat.output", "player", bot.name(), "message", content));
        return 1;
    }

    /** 解析并运行一段 JSON 脚本文本（script 模式）。非法 JSON 同步回显解析失败。 */
    private static int script(CommandContext<CommandSourceStack> context) {
        Bot bot = resolveBot(context);
        if (bot == null) {
            return 0;
        }
        CommandSender sender = context.getSource().getSender();
        String json = StringArgumentType.getString(context, "script");
        try {
            respond(bot.runScript(json), sender,
                    Lang.t("command.script.started", "player", bot.name()));
        } catch (ScriptParseException e) {
            failure(sender, Lang.t("command.script.parse-failed", "reason", e.getMessage()));
        }
        return 1;
    }

    /** 终止该假人所有行为（对标 Carpet /player stop）。 */
    private static int stop(CommandContext<CommandSourceStack> context) {
        Bot bot = resolveBot(context);
        if (bot == null) {
            return 0;
        }
        respond(bot.stopActions(), context.getSource().getSender(),
                Lang.t("command.stop.done", "player", bot.name()));
        return 1;
    }

    /** 下线（kill）该假人：触发 PlayerQuitEvent、存档、广播离开消息并清理登记。 */
    private static int kill(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String name = StringArgumentType.getString(context, PLAYER_ARG);
        if (resolveBot(context) == null) {
            return 0;
        }
        respond(TianBotPlugin.getApi().stopBot(name), sender,
                Lang.t("command.kill.done", "player", name));
        return 1;
    }


    private static LiteralArgumentBuilder<CommandSourceStack> lookSubtree() {
        return Commands.literal("look")
                .then(Commands.literal("north").executes(manipulate("action.look-north", Bot::lookNorth)))
                .then(Commands.literal("south").executes(manipulate("action.look-south", Bot::lookSouth)))
                .then(Commands.literal("east").executes(manipulate("action.look-east", Bot::lookEast)))
                .then(Commands.literal("west").executes(manipulate("action.look-west", Bot::lookWest)))
                .then(Commands.literal("up").executes(manipulate("action.look-up", Bot::lookUp)))
                .then(Commands.literal("down").executes(manipulate("action.look-down", Bot::lookDown)))
                .then(Commands.literal("at")
                        .then(Commands.argument("position", ArgumentTypes.finePosition())
                                .executes(context -> {
                                    Bot bot = resolveBot(context);
                                    if (bot == null) {
                                        return 0;
                                    }
                                    FinePosition position = context.getArgument("position", FinePositionResolver.class)
                                            .resolve(context.getSource());
                                    respond(bot.lookAt(position.x(), position.y(), position.z()),
                                            context.getSource().getSender(),
                                            Lang.t("command.action.added", "player", bot.name(), "action",
                                                    Lang.get("action.look-at")));
                                    return 1;
                                })))
                .then(Commands.argument("direction", ArgumentTypes.rotation())
                        .executes(context -> {
                            Bot bot = resolveBot(context);
                            if (bot == null) {
                                return 0;
                            }
                            Rotation rotation = context.getArgument("direction", RotationResolver.class)
                                    .resolve(context.getSource());
                            respond(bot.look(rotation.yaw(), rotation.pitch()),
                                    context.getSource().getSender(),
                                    Lang.t("command.action.added", "player", bot.name(), "action",
                                            Lang.get("action.look")));
                            return 1;
                        }));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> turnSubtree() {
        return Commands.literal("turn")
                .then(Commands.literal("left").executes(manipulate("action.turn-left", bot -> bot.turn(-90, 0))))
                .then(Commands.literal("right").executes(manipulate("action.turn-right", bot -> bot.turn(90, 0))))
                .then(Commands.literal("back").executes(manipulate("action.turn-back", bot -> bot.turn(180, 0))))
                .then(Commands.argument("rotation", ArgumentTypes.rotation())
                        .executes(context -> {
                            Bot bot = resolveBot(context);
                            if (bot == null) {
                                return 0;
                            }
                            Rotation rotation = context.getArgument("rotation", RotationResolver.class)
                                    .resolve(context.getSource());
                            respond(bot.turn(rotation.yaw(), rotation.pitch()),
                                    context.getSource().getSender(),
                                    Lang.t("command.action.added", "player", bot.name(), "action",
                                            Lang.get("action.turn")));
                            return 1;
                        }));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> moveSubtree() {
        return Commands.literal("move")
                .executes(manipulate("action.move-stop", Bot::stopMoving))
                .then(Commands.literal("forward").executes(manipulate("action.move-forward", Bot::moveForward)))
                .then(Commands.literal("backward").executes(manipulate("action.move-backward", Bot::moveBackward)))
                .then(Commands.literal("left").executes(manipulate("action.move-left", Bot::moveLeft)))
                .then(Commands.literal("right").executes(manipulate("action.move-right", Bot::moveRight)));
    }


    private static LiteralArgumentBuilder<CommandSourceStack> actionCommand(
            String literal,
            String display,
            Function<Bot, CompletableFuture<Void>> once,
            Function<Bot, CompletableFuture<Void>> continuous,
            IntervalAction interval) {
        return Commands.literal(literal)
                .executes(manipulate(display, once))
                .then(Commands.literal("once").executes(manipulate(display, once)))
                .then(Commands.literal("continuous").executes(manipulate(display, continuous)))
                .then(Commands.literal("interval")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    Bot bot = resolveBot(context);
                                    if (bot == null) {
                                        return 0;
                                    }
                                    int ticks = IntegerArgumentType.getInteger(context, "ticks");
                                    respond(interval.apply(bot, ticks), context.getSource().getSender(),
                                            Lang.t("command.action.added.interval",
                                                    "player", bot.name(), "action", Lang.get(display), "ticks", ticks));
                                    return 1;
                                })));
    }

    /** 区间动作执行器（Bot + tick 数 → future）。 */
    @FunctionalInterface
    private interface IntervalAction {
        CompletableFuture<Void> apply(Bot bot, int ticks);
    }

    /** 单发动作执行器（对标 Carpet manipulation）：解析假人 → 执行 → 异步回显。 */
    private static Command<CommandSourceStack> manipulate(
            String display, Function<Bot, CompletableFuture<Void>> action) {
        return context -> {
            Bot bot = resolveBot(context);
            if (bot == null) {
                return 0;
            }
            respond(action.apply(bot), context.getSource().getSender(),
                    Lang.t("command.action.added", "player", bot.name(), "action", Lang.get(display)));
            return 1;
        };
    }
}
