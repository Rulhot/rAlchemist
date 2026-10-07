package ru.rulhot.rAlchemist.command;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public interface SubCommand {

    @NotNull String getName();

    @NotNull String getPermission();

    boolean execute(@NotNull CommandSender sender, @NotNull String[] args);

    default @NotNull List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String[] args
    ) {
        return List.of();
    }

    static @NotNull List<String> suggest(@NotNull Stream<String> options, @NotNull String[] args) {
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
