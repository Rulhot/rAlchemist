package ru.rulhot.rAlchemist.economy;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

@RequiredArgsConstructor
public final class CoinsEngineEconomyProvider implements EconomyProvider {

    private final @NotNull ExcellentEconomyAPI api;
    private final @NotNull String currency;

    @Override
    public @NotNull String name() {
        return "CoinsEngine(" + currency + ")";
    }

    @Override
    public boolean has(@NotNull Player player, double amount) {
        return api.getBalance(player, currency) >= amount;
    }

    @Override
    public boolean withdraw(@NotNull Player player, double amount) {
        return api.withdraw(player, currency, amount);
    }

    @Override
    public void deposit(@NotNull Player player, double amount) {
        api.deposit(player, currency, amount);
    }
}
