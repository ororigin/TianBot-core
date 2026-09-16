package xyz.ororigin.tianbot.command;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.ororigin.tianbot.utils.Lang;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public final class CommandRegistration {

    private static final String PAPER_COMMANDS_CLASS =
            "io.papermc.paper.command.brigadier.PaperCommands";

    private static final String INVALID_FIELD = "invalid";

    private static final Class<?>[] REGISTER_SIGNATURE = {
            PluginMeta.class, LiteralCommandNode.class, String.class, Collection.class
    };

    private final JavaPlugin plugin;
    private final Set<String> labels = new LinkedHashSet<>();
    private boolean registered;
    private String label = "";

    public CommandRegistration(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean register(LiteralCommandNode<CommandSourceStack> node,
                            String description,
                            List<String> aliases) {
        if (registered) {
            return true;
        }
        label = node == null ? label : node.getLiteral();
        Collection<String> result;
        try {
            Object paperCommands = resolvePaperCommands();
            if (paperCommands == null) {
                return false;
            }
            Method register = paperCommands.getClass().getMethod(
                    "register", REGISTER_SIGNATURE);
            Object value = withDispatcherAccess(paperCommands, () ->
                    register.invoke(paperCommands, plugin.getPluginMeta(),
                            node, description, aliases));
            if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
                plugin.getLogger().log(Level.WARNING,
                        Lang.t("log.command-register-failed", "command", label,
                                "reason", "dispatcher 未接受该命令（可能被同名命令占用）"));
                return false;
            }
            result = new ArrayList<>(collection.size());
            for (Object element : collection) {
                result.add(String.valueOf(element));
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    Lang.t("log.command-register-failed", "command", label,
                            "reason", unwrap(t)), t);
            return false;
        }
        labels.clear();
        labels.addAll(result);
        registered = true;
        refreshPlayerCommandTrees();
        return true;
    }

    public void unregister() {
        if (!registered) {
            return;
        }
        registered = false;
        if (labels.isEmpty()) {
            return;
        }
        try {
            Object paperCommands = resolvePaperCommands();
            if (paperCommands != null) {
                withDispatcherAccess(paperCommands, () -> {
                    Object dispatcher = paperCommands.getClass()
                            .getMethod("getDispatcher").invoke(paperCommands);
                    Object root = dispatcher.getClass()
                            .getMethod("getRoot").invoke(dispatcher);
                    Method removeCommand = root.getClass()
                            .getMethod("removeCommand", String.class);
                    for (String registeredLabel : labels) {
                        removeCommand.invoke(root, registeredLabel);
                    }
                    return null;
                });
            }
            labels.clear();
            refreshPlayerCommandTrees();
        } catch (Throwable t) {
            labels.clear();
            plugin.getLogger().log(Level.FINE,
                    Lang.t("log.command-unregister-skipped", "command", label, "reason", t));
        }
    }

    private Object resolvePaperCommands() {
        try {
            Class<?> type = Class.forName(PAPER_COMMANDS_CLASS);
            return type.getField("INSTANCE").get(null);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    Lang.t("log.command-register-failed", "command", label, "reason",
                            "无法访问 " + PAPER_COMMANDS_CLASS + ": " + t),
                    t);
            return null;
        }
    }

    private static <T> T withDispatcherAccess(Object paperCommands, DispatcherAction<T> action)
            throws Throwable {
        boolean restoreLock = isDispatcherLocked(paperCommands);
        invokeNoArg(paperCommands, "setValid");
        try {
            return action.run();
        } finally {
            if (restoreLock) {
                try {
                    invokeNoArg(paperCommands, "invalidate");
                } catch (Throwable ignored) {
                    // 恢复锁定失败不影响本次操作结果
                }
            }
        }
    }

    private static boolean isDispatcherLocked(Object paperCommands) {
        try {
            Field field = paperCommands.getClass().getDeclaredField(INVALID_FIELD);
            field.setAccessible(true);
            return field.getBoolean(paperCommands);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void invokeNoArg(Object target, String methodName) throws Exception {
        target.getClass().getMethod(methodName).invoke(target);
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof InvocationTargetException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    @FunctionalInterface
    private interface DispatcherAction<T> {
        T run() throws Throwable;
    }

    private void refreshPlayerCommandTrees() {
        if (!plugin.isEnabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.getScheduler().run(plugin, task -> player.updateCommands(), null);
            } catch (Throwable ignored) {
                // 玩家已下线、调度器不可用或环境不支持：忽略
            }
        }
    }
}
