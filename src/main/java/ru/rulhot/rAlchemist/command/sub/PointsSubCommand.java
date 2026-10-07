package ru.rulhot.rAlchemist.command.sub;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.command.AlchemistCommand;
import ru.rulhot.rAlchemist.command.SubCommand;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.menu.MenuManager;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.service.ProgressService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@RequiredArgsConstructor
public final class PointsSubCommand implements SubCommand {

    private static final String ADD = "add";
    private static final String SET = "set";
    private static final String QUERY = "query";
    private static final String TAKE = "take";
    private static final String POINTS = "points";
    private static final String LEVELS = "levels";
    private static final int MAX_AMOUNT = 1_000_000_000;

    private final @NotNull ConfigManager config;
    private final @NotNull ProgressService progress;
    private final @NotNull MenuManager menuManager;

    @Override
    public @NotNull String getName() {
        return "points";
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
        String action = args.length < 2 ? "" : args[0].toLowerCase(Locale.ROOT);
        boolean query = action.equals(QUERY);
        int unitIndex = query ? 2 : 3;
        String unit = args.length > unitIndex ? args[unitIndex].toLowerCase(Locale.ROOT) : POINTS;
        boolean known = query || action.equals(ADD) || action.equals(SET);
        if (!known || args.length < unitIndex || args.length > unitIndex + 1
                || !unit.equals(POINTS) && !unit.equals(LEVELS)) {
            config.send(sender, "points.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            config.send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }
        if (!progress.isLoaded(target)) {
            progress.requestLoad(target);
            config.send(sender, "data-not-loaded");
            return true;
        }
        boolean levels = unit.equals(LEVELS);
        int before = progress.xp(target);
        if (query) {
            config.send(sender, levels ? "points.query-levels" : "points.query-points", Map.of(
                    "player", target.getName(),
                    "amount", levels ? level(before).number() : before));
            return true;
        }
        Integer amount = parse(args[2], action.equals(ADD));
        if (amount == null) {
            config.send(sender, "invalid-number", Map.of("value", args[2], "max", MAX_AMOUNT));
            return true;
        }
        Integer updated = action.equals(ADD) ? added(before, amount, levels) : exact(amount, levels);
        if (updated == null) {
            config.send(sender, "points.unknown-level", Map.of("level", amount));
            return true;
        }
        progress.setXp(target, updated);
        String result = action.equals(ADD) && amount < 0 ? TAKE : action;
        config.send(sender, "points." + result + "-" + unit,
                Map.of("player", target.getName(), "amount", Math.abs(amount)));
        Settings.Level reached = level(progress.xp(target));
        if (reached.xp() > level(before).xp()) {
            config.send(target, "level.up", Map.of("level", reached.number()));
        }
        menuManager.refreshOpen(target);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        boolean query = args.length > 0 && args[0].equalsIgnoreCase(QUERY);
        Stream<String> options;
        if (args.length == 1) {
            options = Stream.of(ADD, SET, QUERY);
        } else if (args.length == 2) {
            options = Bukkit.getOnlinePlayers().stream().map(Player::getName);
        } else if (args.length == (query ? 3 : 4)) {
            options = Stream.of(POINTS, LEVELS);
        } else {
            return List.of();
        }
        return SubCommand.suggest(options, args);
    }

    private @NotNull Integer added(int points, int amount, boolean levels) {
        if (!levels) {
            return (int) Math.clamp((long) points + amount, 0L, MAX_AMOUNT);
        }
        List<Settings.Level> list = config.settings().levels().list();
        int index = Math.clamp((long) list.indexOf(level(points)) + amount, 0, list.size() - 1);
        return list.get(index).xp();
    }

    private @Nullable Integer exact(int amount, boolean levels) {
        if (!levels) {
            return amount;
        }
        List<Settings.Level> list = config.settings().levels().list();
        if (amount == 0) {
            return list.getFirst().xp();
        }
        for (Settings.Level level : list) {
            if (level.number() == amount) {
                return level.xp();
            }
        }
        return null;
    }

    private @NotNull Settings.Level level(int points) {
        return config.settings().levels().levelFor(points);
    }

    private @Nullable Integer parse(@NotNull String value, boolean allowNegative) {
        try {
            int number = Integer.parseInt(value);
            return number <= MAX_AMOUNT && number >= (allowNegative ? -MAX_AMOUNT : 0) ? number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
