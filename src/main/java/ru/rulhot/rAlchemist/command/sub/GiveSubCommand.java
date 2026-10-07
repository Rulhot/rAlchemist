package ru.rulhot.rAlchemist.command.sub;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.command.AlchemistCommand;
import ru.rulhot.rAlchemist.command.SubCommand;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.service.EssenceService;
import ru.rulhot.rAlchemist.util.SchedulerUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class GiveSubCommand implements SubCommand {

    private static final int MAX_AMOUNT = 64;

    private final @NotNull ConfigManager config;
    private final @NotNull EssenceService essences;
    private final @NotNull SchedulerUtil scheduler;

    @Override
    public @NotNull String getName() {
        return "give";
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
        EssenceService.Type type = args.length < 2 ? null : EssenceService.Type.find(args[1]);
        if (type == null || args.length > 3) {
            config.send(sender, "give.usage");
            return true;
        }
        Integer amount = args.length == 3 ? parse(args[2]) : Integer.valueOf(1);
        if (amount == null) {
            config.send(sender, "invalid-number", Map.of("value", args[2], "max", MAX_AMOUNT));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            config.send(sender, "player-not-found", Map.of("player", args[0]));
            return true;
        }
        ItemStack essence = essences.create(type, amount);
        if (essence == null) {
            config.send(sender, "give.unavailable");
            return true;
        }
        Component displayName = essence.getItemMeta().displayName();
        Component name = displayName == null ? Component.text(type.id()) : displayName;
        scheduler.runFor(target, () -> {
            target.getInventory().addItem(essence).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            config.send(target, "give.target", Map.of("amount", amount, "essence", name));
        });
        if (!sender.equals(target)) {
            config.send(sender, "give.sender", Map.of("player", target.getName(), "amount", amount, "essence", name));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        if (args.length < 1 || args.length > 2) {
            return List.of();
        }
        return SubCommand.suggest(args.length == 1
                ? Bukkit.getOnlinePlayers().stream().map(Player::getName)
                : Arrays.stream(EssenceService.Type.values()).map(EssenceService.Type::id), args);
    }

    private @Nullable Integer parse(@NotNull String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 && number <= MAX_AMOUNT ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
