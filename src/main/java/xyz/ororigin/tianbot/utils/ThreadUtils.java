package xyz.ororigin.tianbot.utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ThreadUtils {
    public static <T> CompletableFuture<T> runGlobalTask(Plugin plugin, Supplier<T> task){
        CompletableFuture<T> future = new CompletableFuture<>();

        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> {
            try{
                T result = task.get();
                future.complete(result);
            } catch (Throwable e){
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}
