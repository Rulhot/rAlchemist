package ru.rulhot.rAlchemist.manager;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.menu.MenuConfig;
import ru.rulhot.rAlchemist.model.DatabaseSettings;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.util.MessageUtil;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ConfigManager {

    private static final String CONFIG = "config.yml";
    private static final String DATABASE = "database.yml";
    private static final String ITEMS = "items.yml";
    private static final String MIXING = "mixing.yml";
    private static final String LEVELS = "levels.yml";
    private static final String MESSAGES = "messages.yml";
    private static final String ALCHEMIST_MENU = "menus/alchemist.yml";
    private static final String COLOR_MENU = "menus/color.yml";
    private static final Set<String> MENU_LIVE_PATHS = Set.of("buttons", "colors");
    private static final Set<String> LEVELS_LIVE_PATHS = Set.of("list");
    private static final String PREFIX_KEY = "prefix";
    private static final NamedTextColor CONSOLE_PREFIX_COLOR = NamedTextColor.GOLD;
    private static final Pattern HEX_TAG = Pattern.compile("</?#[0-9a-fA-F]{6}>");
    private static final String LONG_DASH = String.valueOf((char) 0x2014);
    private static final Map<String, String> CONSOLE_SYMBOLS = Map.of("»", ">", LONG_DASH, "-", " ⛃", "", "▌", "|");

    private final @NotNull JavaPlugin plugin;
    private volatile @Nullable Snapshot snapshot;

    public ConfigManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public @NotNull DatabaseSettings readDatabase() {
        return DatabaseSettings.from(ConfigFile.read(plugin, DATABASE, Set.of()));
    }

    public @NotNull Bundle readAll() {
        return new Bundle(
                ConfigFile.read(plugin, CONFIG, Set.of()),
                ConfigFile.read(plugin, ITEMS, Set.of()),
                ConfigFile.read(plugin, MIXING, Set.of("limits.groups")),
                ConfigFile.read(plugin, LEVELS, LEVELS_LIVE_PATHS),
                ConfigFile.read(plugin, MESSAGES, Set.of()),
                ConfigFile.read(plugin, ALCHEMIST_MENU, MENU_LIVE_PATHS),
                ConfigFile.read(plugin, COLOR_MENU, MENU_LIVE_PATHS));
    }

    public void apply(@NotNull Bundle bundle) {
        Map<String, String> messages = new HashMap<>();
        for (String key : bundle.messages().getKeys(true)) {
            if (bundle.messages().isString(key)) {
                messages.put(key, bundle.messages().getString(key, ""));
            } else if (bundle.messages().isList(key)) {
                messages.put(key, String.join("\n", bundle.messages().getStringList(key)));
            }
        }
        Settings settings = Settings.from(bundle.config(), bundle.items(), bundle.mixing(), bundle.levels());
        Logger.setDebugEnabled(settings.debug());
        snapshot = new Snapshot(settings, Map.copyOf(messages),
                MenuConfig.parse(ALCHEMIST_MENU, bundle.alchemistMenu()),
                MenuConfig.parse(COLOR_MENU, bundle.colorMenu()));
    }

    public @NotNull Settings settings() {
        return snapshot().settings();
    }

    public @NotNull MenuConfig alchemistMenu() {
        return snapshot().alchemistMenu();
    }

    public @NotNull MenuConfig colorMenu() {
        return snapshot().colorMenu();
    }

    public @NotNull Component message(@NotNull String key, @NotNull Map<String, ?> placeholders) {
        Snapshot current = snapshot();
        return parse(raw(key), Placeholder.parsed(PREFIX_KEY, current.messages().getOrDefault(PREFIX_KEY, "")),
                placeholders);
    }

    private @NotNull Component parse(@NotNull String raw, @NotNull TagResolver prefix,
                                     @NotNull Map<String, ?> placeholders) {
        return MessageUtil.parseText(raw,
                prefix,
                Placeholder.unparsed("command", snapshot().settings().command().name()),
                MessageUtil.placeholders(placeholders));
    }

    private static @NotNull String consoleSafe(@NotNull String text) {
        String result = HEX_TAG.matcher(text).replaceAll("");
        for (Map.Entry<String, String> symbol : CONSOLE_SYMBOLS.entrySet()) {
            result = result.replace(symbol.getKey(), symbol.getValue());
        }
        return result;
    }

    public void send(@NotNull Audience audience, @NotNull String key) {
        send(audience, key, Map.of());
    }

    public void send(@NotNull Audience audience, @NotNull String key, @NotNull Map<String, ?> placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return;
        }
        if (!(audience instanceof ConsoleCommandSender)) {
            audience.sendMessage(message(key, placeholders));
            return;
        }
        String prefix = PlainTextComponentSerializer.plainText().serialize(
                MessageUtil.parseText(snapshot().messages().getOrDefault(PREFIX_KEY, "")));
        audience.sendMessage(parse(consoleSafe(raw), Placeholder.component(PREFIX_KEY,
                Component.text(consoleSafe(prefix), CONSOLE_PREFIX_COLOR)), placeholders));
    }

    private @NotNull String raw(@NotNull String key) {
        String value = snapshot().messages().get(key);
        if (value == null) {
            Logger.warn("В messages.yml нет ключа '" + key + "'");
            return "";
        }
        return value;
    }

    private @NotNull Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("Настройки ещё не загружены");
        }
        return current;
    }

    public record Bundle(@NotNull YamlConfiguration config, @NotNull YamlConfiguration items,
                         @NotNull YamlConfiguration mixing,
                         @NotNull YamlConfiguration levels,
                         @NotNull YamlConfiguration messages,
                         @NotNull YamlConfiguration alchemistMenu, @NotNull YamlConfiguration colorMenu) {
    }

    private record Snapshot(@NotNull Settings settings, @NotNull Map<String, String> messages,
                            @NotNull MenuConfig alchemistMenu, @NotNull MenuConfig colorMenu) {
    }
}
