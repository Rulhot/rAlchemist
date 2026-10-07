package ru.rulhot.rAlchemist;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.command.AlchemistCommand;
import ru.rulhot.rAlchemist.command.sub.GiveSubCommand;
import ru.rulhot.rAlchemist.command.sub.HelpSubCommand;
import ru.rulhot.rAlchemist.command.sub.ReloadSubCommand;
import ru.rulhot.rAlchemist.command.sub.PointsSubCommand;
import ru.rulhot.rAlchemist.database.DatabaseManager;
import ru.rulhot.rAlchemist.database.repository.PlayerRepository;
import ru.rulhot.rAlchemist.listener.EssenceListener;
import ru.rulhot.rAlchemist.listener.EssenceProtectionListener;
import ru.rulhot.rAlchemist.listener.MenuListener;
import ru.rulhot.rAlchemist.listener.PlayerDataListener;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.menu.MenuManager;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.service.ColorService;
import ru.rulhot.rAlchemist.service.EssenceService;
import ru.rulhot.rAlchemist.service.MixService;
import ru.rulhot.rAlchemist.service.PriceService;
import ru.rulhot.rAlchemist.service.ProgressService;
import ru.rulhot.rAlchemist.util.Metrics;
import ru.rulhot.rAlchemist.util.SchedulerUtil;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.List;

public final class RAlchemist extends JavaPlugin {

    private static final int BSTATS_ID = 34501;
    private static final List<Permission> PERMISSIONS = List.of(
            new Permission(MenuManager.PERMISSION_USE, PermissionDefault.TRUE),
            new Permission(AlchemistCommand.ADMIN_PERMISSION, PermissionDefault.OP),
            new Permission(ProgressService.BYPASS_COOLDOWN_PERMISSION, PermissionDefault.OP),
            new Permission(ProgressService.BYPASS_LIMIT_PERMISSION, PermissionDefault.OP));

    private @Nullable SchedulerUtil scheduler;
    private @Nullable MenuManager menuManager;
    private @Nullable PlayerRepository repository;
    private @Nullable Metrics metrics;

    @Override
    public void onEnable() {
        Logger.setup(this);
        SchedulerUtil scheduler = new SchedulerUtil(this);
        this.scheduler = scheduler;

        ConfigManager configManager = new ConfigManager(this);
        configManager.apply(configManager.readAll());
        if (configManager.settings().metrics()) {
            metrics = new Metrics(this, BSTATS_ID);
        }

        PriceService priceService = new PriceService();
        if (!priceService.reload(configManager.settings().costs(), getServer())) {
            Logger.error("Не удалось подключить плату, rAlchemist выключен. Проверьте price.costs в config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PlayerRepository repository = new PlayerRepository(new DatabaseManager(getDataFolder()));
        this.repository = repository;
        repository.start(configManager.readDatabase());

        ColorService colorService = new ColorService(repository, configManager);
        ProgressService progressService = new ProgressService(repository);
        MenuManager menuManager = new MenuManager(scheduler, configManager, new MixService(this), colorService,
                priceService, progressService);
        this.menuManager = menuManager;

        PluginManager pluginManager = getServer().getPluginManager();
        for (Permission permission : PERMISSIONS) {
            if (pluginManager.getPermission(permission.getName()) == null) {
                pluginManager.addPermission(permission);
            }
        }
        EssenceService essenceService = new EssenceService(this, configManager);
        pluginManager.registerEvents(new PlayerDataListener(repository), this);
        pluginManager.registerEvents(new MenuListener(menuManager), this);
        pluginManager.registerEvents(new EssenceListener(essenceService), this);
        pluginManager.registerEvents(new EssenceProtectionListener(essenceService), this);

        Settings.Command label = configManager.settings().command();
        AlchemistCommand command = new AlchemistCommand(configManager, menuManager, List.of(
                new HelpSubCommand(configManager),
                new GiveSubCommand(configManager, essenceService, scheduler),
                new PointsSubCommand(configManager, progressService, menuManager),
                new ReloadSubCommand(getServer(), configManager, menuManager, priceService, scheduler)));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register(label.name(), label.aliases(), command));

        for (Player player : getServer().getOnlinePlayers()) {
            repository.load(player.getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        if (menuManager != null) {
            menuManager.shutdown();
        }
        if (repository != null) {
            repository.shutdown();
        }
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        PERMISSIONS.forEach(permission -> getServer().getPluginManager().removePermission(permission));
    }
}
