package ru.rulhot.rAlchemist.economy;

import lombok.experimental.UtilityClass;
import net.milkbowl.vault.economy.Economy;
import org.black_ixx.playerpoints.PlayerPoints;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.util.logger.Logger;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

@UtilityClass
public class EconomyRegistry {

    public @Nullable EconomyProvider create(@NotNull Settings.Cost cost, @NotNull Server server) {
        String pluginName = switch (cost.type()) {
            case VAULT -> "Vault";
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
                case COINSENGINE -> coinsEngine(server, cost.currency());
                case PLAYERPOINTS -> new PlayerPointsEconomyProvider(PlayerPoints.getInstance().getAPI());
            };
        } catch (Throwable throwable) {
            Logger.error("Не удалось подключиться к экономике " + pluginName + ", возможно, несовместимая версия", throwable);
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

    private @Nullable EconomyProvider coinsEngine(@NotNull Server server, @NotNull String currency) {
        RegisteredServiceProvider<ExcellentEconomyAPI> registration =
                server.getServicesManager().getRegistration(ExcellentEconomyAPI.class);
        if (registration == null) {
            Logger.error("API CoinsEngine не зарегистрировано");
            return null;
        }
        ExcellentEconomyAPI api = registration.getProvider();
        if (!api.hasCurrency(currency)) {
            Logger.error("В CoinsEngine нет валюты '" + currency + "', проверьте currency в price.costs");
            return null;
        }
        return new CoinsEngineEconomyProvider(api, currency);
    }
}
