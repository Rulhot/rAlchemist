package ru.rulhot.rAlchemist.economy;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.by1337.bvault.api.BEconomy;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public final class BVaultEconomyProvider implements EconomyProvider {

    private final @NotNull BEconomy economy;
    private final @NotNull String bank;

    @Override
    public @NotNull String name() {
        return "BVault(" + bank + ")";
    }

    @Override
    public boolean has(@NotNull Player player, double amount) {
        return economy.getBalance(bank, player.getUniqueId()).join() >= amount;
    }

    @Override
    public boolean withdraw(@NotNull Player player, double amount) {
        if (!has(player, amount)) {
            return false;
        }
        if (economy.withdraw(bank, player.getUniqueId(), amount).join() >= 0.0D) {
            return true;
        }
        deposit(player, amount);
        return false;
    }

    @Override
    public void deposit(@NotNull Player player, double amount) {
        economy.deposit(bank, player.getUniqueId(), amount);
    }
}
