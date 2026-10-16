package ru.rulhot.rAlchemist.economy;

import lombok.experimental.UtilityClass;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Server;
import org.by1337.bvault.api.BEconomy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.util.logger.Logger;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

@UtilityClass
public class EconomyRegistry {

    private final int BVAULT_MAX_BANK_LENGTH = 16;

    public @Nullable EconomyProvider create(@NotNull Settings.Cost cost, @NotNull Server server) {
        String pluginName = switch (cost.type()) {
            case VAULT -> "Vault";
            case BVAULT -> "BVault";
            case COINSENGINE -> "CoinsEngine";
            case PLAYERPOINTS -> "PlayerPoints";
        };
        if (!server.getPluginManager().isPluginEnabled(pluginName)) {
            Logger.error("Плата " + cost.type() + " требует плагин " + pluginName + ", но он не включён");
            return null;
        }
        try {
            return switch (cost.type()) {
                case VAULT -> vault(server);
                case BVAULT -> bVault(server, cost.currency());
                case COINSENGINE -> coinsEngine(server, cost.currency());
                case PLAYERPOINTS -> new PlayerPointsEconomyProvider(PlayerPoints.getInstance().getAPI());
            };
        } catch (Throwable throwable) {
            Logger.error("Не удалось подключить экономику " + pluginName + ". Проверь, что версия плагина подходит",
                    throwable);
            return null;
        }
    }

    private @Nullable EconomyProvider vault(@NotNull Server server) {
        RegisteredServiceProvider<Economy> registration = server.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            Logger.error("Vault включён, но в нём не зарегистрирован ни один плагин экономики");
            return null;
        }
        return new VaultEconomyProvider(registration.getProvider());
    }

    private @Nullable EconomyProvider bVault(@NotNull Server server, @NotNull String bank) {
        RegisteredServiceProvider<BEconomy> registration = server.getServicesManager().getRegistration(BEconomy.class);
        if (registration == null) {
            Logger.error("API BVault не зарегистрировано");
            return null;
        }
        if (bank.length() > BVAULT_MAX_BANK_LENGTH) {
            Logger.error("Счёт BVault '" + bank + "' длиннее " + BVAULT_MAX_BANK_LENGTH
                    + " символов, проверьте currency в price.costs");
            return null;
        }
        BEconomy economy = registration.getProvider();
        if (!economy.getKnownBanks().contains(bank)) {
            Logger.warn("В BVault пока нет счёта '" + bank + "', известные счета: " + economy.getKnownBanks()
                    + ". Проверьте currency в price.costs");
        }
        return new BVaultEconomyProvider(economy, bank);
    }

    private @Nullable EconomyProvider coinsEngine(@NotNull Server server, @NotNull String currency) {
        RegisteredServiceProvider<ExcellentEconomyAPI> registration =
                server.getServicesManager().getRegistration(ExcellentEconomyAPI.class);
        if (registration == null) {
            Logger.error("API CoinsEngine не зарегистрировано");
            return null;
        }
        ExcellentEconomyAPI api = registration.getProvider();
        if (!api.hasCurrency(currency)) {
            Logger.error("В CoinsEngine нет валюты '" + currency + "', проверь currency в price.costs");
            return null;
        }
        return new CoinsEngineEconomyProvider(api, currency);
    }
}
