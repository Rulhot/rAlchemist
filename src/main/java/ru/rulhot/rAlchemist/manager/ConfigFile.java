package ru.rulhot.rAlchemist.manager;

import lombok.experimental.UtilityClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@UtilityClass
public class ConfigFile {

    private final String VERSION_KEY = "config-version";
    private final String PATH_SEPARATOR = ".";

    public synchronized @NotNull YamlConfiguration read(@NotNull JavaPlugin plugin, @NotNull String path,
                                                        @NotNull Set<String> livePaths) {
        File target = new File(plugin.getDataFolder(), path);
        if (!target.exists()) {
            plugin.saveResource(path, false);
        }
        YamlConfiguration defaults = readDefaults(plugin, path);
        YamlConfiguration current = new YamlConfiguration();
        try {
            current.load(target);
        } catch (IOException | InvalidConfigurationException exception) {
            Logger.error("Ошибка в " + path + ", до исправления файла используются значения по умолчанию", exception);
            return defaults != null ? defaults : current;
        }
        if (defaults == null) {
            return current;
        }
        boolean outdated = current.getInt(VERSION_KEY, 0) < defaults.getInt(VERSION_KEY, 0);
        if (!outdated && !hasMissingKeys(current, defaults, livePaths)) {
            return current;
        }
        YamlConfiguration merged = merge(current, defaults, livePaths);
        try {
            merged.save(target);
        } catch (IOException exception) {
            Logger.error("Не удалось сохранить обновлённый " + path, exception);
        }
        return merged;
    }

    private boolean hasMissingKeys(@NotNull YamlConfiguration current, @NotNull YamlConfiguration defaults,
                                   @NotNull Set<String> livePaths) {
        for (String key : defaults.getKeys(true)) {
            if (!isUnderLivePath(key, livePaths) && !current.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private @NotNull YamlConfiguration merge(@NotNull YamlConfiguration current, @NotNull YamlConfiguration defaults,
                                             @NotNull Set<String> livePaths) {
        YamlConfiguration merged = new YamlConfiguration();
        merged.options().setHeader(defaults.options().getHeader());
        merged.options().setFooter(defaults.options().getFooter());
        for (String key : defaults.getKeys(true)) {
            if (key.equals(VERSION_KEY) || isUnderLivePath(key, livePaths)) {
                continue;
            }
            if (livePaths.contains(key)) {
                copySubtree(current.contains(key) ? current : defaults, merged, key);
            } else if (defaults.isConfigurationSection(key)) {
                if (!merged.isConfigurationSection(key)) {
                    merged.createSection(key);
                }
            } else {
                boolean keepCurrent = current.contains(key) && !current.isConfigurationSection(key);
                merged.set(key, keepCurrent ? current.get(key) : defaults.get(key));
            }
            merged.setComments(key, defaults.getComments(key));
            merged.setInlineComments(key, defaults.getInlineComments(key));
        }
        for (String key : current.getKeys(true)) {
            if (!key.equals(VERSION_KEY) && !merged.contains(key) && !current.isConfigurationSection(key)) {
                merged.set(key, current.get(key));
            }
        }
        merged.set(VERSION_KEY, defaults.getInt(VERSION_KEY, 0));
        merged.setComments(VERSION_KEY, defaults.getComments(VERSION_KEY));
        merged.setInlineComments(VERSION_KEY, defaults.getInlineComments(VERSION_KEY));
        return merged;
    }

    private @Nullable YamlConfiguration readDefaults(@NotNull JavaPlugin plugin, @NotNull String path) {
        try (InputStream stream = plugin.getResource(path)) {
            if (stream == null) {
                Logger.error("В jar плагина нет файла " + path + ", пересоберите плагин");
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            Logger.error("Не удалось прочитать " + path + " из jar плагина", exception);
            return null;
        }
    }

    private void copySubtree(@NotNull ConfigurationSection source, @NotNull YamlConfiguration target,
                             @NotNull String path) {
        ConfigurationSection section = source.getConfigurationSection(path);
        if (section == null) {
            target.set(path, source.get(path));
            return;
        }
        target.createSection(path);
        for (String key : section.getKeys(true)) {
            String fullKey = path + PATH_SEPARATOR + key;
            if (section.isConfigurationSection(key)) {
                target.createSection(fullKey);
            } else {
                target.set(fullKey, section.get(key));
            }
            target.setComments(fullKey, section.getComments(key));
            target.setInlineComments(fullKey, section.getInlineComments(key));
        }
    }

    private boolean isUnderLivePath(@NotNull String key, @NotNull Set<String> livePaths) {
        for (String livePath : livePaths) {
            if (key.startsWith(livePath + PATH_SEPARATOR)) {
                return true;
            }
        }
        return false;
    }
}
