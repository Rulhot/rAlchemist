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
            ConfigManager.Bundle bundle = config.readAll();
            scheduler.runGlobal(() -> apply(sender, bundle));
        });
        return true;
    }

    private void apply(@NotNull CommandSender sender, @NotNull ConfigManager.Bundle bundle) {
        menuManager.closeAll();
        config.apply(bundle);
        if (!prices.reload(config.settings().costs(), server)) {
            config.send(sender, "reload.economy-failed");
        }
        config.send(sender, "reload.done");
    }
}
