package ru.rulhot.rAlchemist.economy;

import lombok.RequiredArgsConstructor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class VaultEconomyProvider implements EconomyProvider {

    private final @NotNull Economy economy;

    @Override
    public @NotNull String name() {
        return "Vault(" + economy.getName() + ")";
    }

    @Override
    public boolean has(@NotNull Player player, double amount) {
        return economy.has(player, amount);
    }

    @Override
    public boolean withdraw(@NotNull Player player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void deposit(@NotNull Player player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
