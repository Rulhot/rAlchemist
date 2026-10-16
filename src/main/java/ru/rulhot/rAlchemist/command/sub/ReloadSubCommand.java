package ru.rulhot.rAlchemist.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.command.AlchemistCommand;
import ru.rulhot.rAlchemist.command.SubCommand;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.menu.MenuManager;
import ru.rulhot.rAlchemist.service.PriceService;
import ru.rulhot.rAlchemist.util.SchedulerUtil;
import ru.rulhot.rAlchemist.util.logger.Logger;

@RequiredArgsConstructor
public final class ReloadSubCommand implements SubCommand {

    private final @NotNull Server server;
    private final @NotNull ConfigManager config;
    private final @NotNull MenuManager menuManager;
    private final @NotNull PriceService prices;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "reload";
    }

    @Override
    public @NotNull String getPermission() {
        return AlchemistCommand.ADMIN_PERMISSION;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            config.send(sender, "no-permission");
            return true;
        }
        if (args.length != 0) {
            config.send(sender, "reload.usage");
            return true;
        }
        scheduler.runAsync(() -> {
            ConfigManager.Bundle bundle;
            try {
                bundle = config.readAll();
            } catch (RuntimeException exception) {
                Logger.error("Не удалось прочитать конфиги, оставляю прежние", exception);
                config.send(sender, "reload.failed");
                return;
            }
            scheduler.runGlobal(() -> apply(sender, bundle));
        });
        return true;
    }

    private void apply(@NotNull CommandSender sender, @NotNull ConfigManager.Bundle bundle) {
        menuManager.closeAll();
        try {
            config.apply(bundle);
        } catch (RuntimeException exception) {
            Logger.error("Не удалось применить конфиги, оставляю прежние", exception);
            config.send(sender, "reload.failed");
            return;
        }
        if (!prices.reload(config.settings().costs(), server)) {
            config.send(sender, "reload.economy-failed");
        }
        config.send(sender, "reload.done");
    }
}
