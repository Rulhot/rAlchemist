package ru.rulhot.rAlchemist.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.menu.MenuManager;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class AlchemistCommand implements BasicCommand {

    public static final String ADMIN_PERMISSION = "ralchemist.admin";

    private static final String COLOR_PREFIX = "#";

    private final @NotNull ConfigManager config;
    private final @NotNull MenuManager menuManager;
    private final @NotNull List<SubCommand> subCommands;

    public AlchemistCommand(@NotNull ConfigManager config, @NotNull MenuManager menuManager,
                            @NotNull List<SubCommand> subCommands) {
        this.config = config;
        this.menuManager = menuManager;
        this.subCommands = List.copyOf(subCommands);
    }

    @Override
    public void execute(
            @NotNull CommandSourceStack source,
            @NotNull String[] args
    ) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(MenuManager.PERMISSION_USE)) {
            config.send(sender, "no-permission");
            return;
        }

        boolean color = args.length == 1 && args[0].startsWith(COLOR_PREFIX);
        if (args.length == 0 || color || !sender.hasPermission(ADMIN_PERMISSION)) {
            if (!(source.getExecutor() instanceof Player player)) {
                config.send(sender, "player-only");
                return;
            }
            if (color) {
                menuManager.selectCustomColor(player, args[0]);
                return;
            }
            menuManager.openMain(player);
            return;
        }

        SubCommand subCommand = find(args[0]);
        if (subCommand == null) {
            config.send(sender, "unknown");
            return;
        }
        subCommand.execute(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NotNull Collection<String> suggest(
            @NotNull CommandSourceStack source,
            @NotNull String[] args
    ) {
        CommandSender sender = source.getSender();
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }
        if (args.length <= 1) {
            return SubCommand.suggest(subCommands.stream()
                    .filter(command -> sender.hasPermission(command.getPermission()))
                    .map(SubCommand::getName), args);
        }

        SubCommand subCommand = find(args[0]);
        if (subCommand == null || !sender.hasPermission(subCommand.getPermission())) {
            return List.of();
        }
        return subCommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    private @Nullable SubCommand find(@NotNull String name) {
        return subCommands.stream()
                .filter(command -> command.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
