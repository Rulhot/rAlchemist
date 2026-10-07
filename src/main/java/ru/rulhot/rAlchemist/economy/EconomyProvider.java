package ru.rulhot.rAlchemist.economy;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface EconomyProvider {

    @NotNull String name();

    boolean has(@NotNull Player player, double amount);

    boolean withdraw(@NotNull Player player, double amount);

    void deposit(@NotNull Player player, double amount);

    default double normalize(double amount) {
        return amount;
    }
}
