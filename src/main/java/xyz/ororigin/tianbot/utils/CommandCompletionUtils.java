package xyz.ororigin.tianbot.utils;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public final class CommandCompletionUtils {

    private CommandCompletionUtils() {
    }

    public static CompletableFuture<List<String>> completeCommand(ServerPlayer player, String command) {
        if (player == null || player.isRemoved()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (command == null || command.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        try {
            CommandSourceStack source = player.createCommandSourceStack();
            ParseResults<CommandSourceStack> parse =
                    player.level().getServer().getCommands().getDispatcher().parse(new StringReader(command), source);
            return player.level().getServer().getCommands().getDispatcher()
                    .getCompletionSuggestions(parse)
                    .thenApply(suggestions -> {
                        List<String> result = new ArrayList<>(suggestions.getList().size());
                        for (Suggestion s : suggestions.getList()) {
                            result.add(s.getText());
                        }
                        return result;
                    });
        } catch (Throwable t) {
            return CompletableFuture.completedFuture(List.of());
        }
    }
}
