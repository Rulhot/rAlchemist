package ru.rulhot.rAlchemist.economy;

import lombok.RequiredArgsConstructor;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class PlayerPointsEconomyProvider implements EconomyProvider {

    private final @NotNull PlayerPointsAPI api;

    @Override
    public @NotNull String name() {
        return "PlayerPoints";
    }

    @Override
    public boolean has(@NotNull Player player, double amount) {
        return api.look(player.getUniqueId()) >= (int) amount;
    }

    @Override
    public boolean withdraw(@NotNull Player player, double amount) {
        return api.take(player.getUniqueId(), (int) amount);
    }

    @Override
    public void deposit(@NotNull Player player, double amount) {
        api.give(player.getUniqueId(), (int) amount);
    }

    @Override
    public double normalize(double amount) {
        return Math.ceil(amount);
    }
}
