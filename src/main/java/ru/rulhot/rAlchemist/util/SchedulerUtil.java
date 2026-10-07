package ru.rulhot.rAlchemist.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.function.Consumer;

@RequiredArgsConstructor
public final class SchedulerUtil {

    private final @NotNull Plugin plugin;

    public void runAsync(@NotNull Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, guarded(task));
    }

    public void runGlobal(@NotNull Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, guarded(task));
    }

    public void runFor(@NotNull Entity entity, @NotNull Runnable task) {
        entity.getScheduler().run(plugin, guarded(task), null);
    }

    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    private static @NotNull Consumer<ScheduledTask> guarded(@NotNull Runnable task) {
        return scheduled -> {
            try {
                task.run();
            } catch (RuntimeException exception) {
                Logger.error("Ошибка в запланированной задаче", exception);
            }
        };
    }
}
