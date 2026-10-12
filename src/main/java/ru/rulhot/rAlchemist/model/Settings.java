package ru.rulhot.rAlchemist.model;

import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.menu.ItemTemplate;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public record Settings(boolean debug, boolean metrics, boolean updateCheck, @NotNull Command command, @NotNull List<Cost> costs, @NotNull Mixing mixing,
                       @NotNull Failure failure, @NotNull Limits limits, @NotNull Levels levels,
                       @NotNull Sounds sounds, @NotNull Essence timeEssence, @NotNull Essence powerEssence,
                       @NotNull Essence stealthEssence, @NotNull Essence mysteryEssence,
                       @NotNull Essence eternityEssence,
                       @NotNull Spoiled spoiled) {

    private static final String DEFAULT_COMMAND = "alchemist";
    private static final int DEFAULT_MIN_POTIONS = 2;
    private static final int DEFAULT_ESSENCE_MINUTES = 5;
    private static final int MAX_ESSENCE_MINUTES = 60 * 24;
    private static final int DEFAULT_ESSENCE_LEVEL = 2;
    private static final int MAX_EFFECT_LEVEL = 255;
    private static final int MAX_ANVIL_COST = 39;
    private static final int TICKS_PER_SECOND = 20;
    private static final int SPOILED_EFFECT_PARTS = 3;
    private static final double MAX_PERCENT = 100.0D;
    private static final double MIN_VOLUME = 0.0D;
    private static final double MAX_VOLUME = 1.0D;
    private static final double MIN_PITCH = 0.5D;
    private static final double MAX_PITCH = 2.0D;

    public static @NotNull Settings from(@NotNull ConfigurationSection root, @NotNull ConfigurationSection items,
                                         @NotNull ConfigurationSection mixing,
                                         @NotNull ConfigurationSection levels) {
        String name = root.getString("command.main", DEFAULT_COMMAND).trim().toLowerCase(Locale.ROOT);
        String resultName = items.getString("crossed-potion.name", "");
        return new Settings(
                root.getBoolean("settings.debug", false),
                root.getBoolean("settings.metrics", true),
                root.getBoolean("settings.update-check", true),
                new Command(name.isEmpty() ? DEFAULT_COMMAND : name, List.copyOf(root.getStringList("command.aliases"))),
                costs(root.getMapList("price.costs")),
                new Mixing(Math.max(1, mixing.getInt("min-potions", DEFAULT_MIN_POTIONS)),
                        mixing.getBoolean("allow-crossed", false),
                        mixing.getBoolean("allow-duplicates", false),
                        mixing.getBoolean("combine-essences", false),
                        incompatible(mixing.getList("incompatible", List.of())),
                        resultName.isBlank() ? null : resultName,
                        List.copyOf(items.getStringList("crossed-potion.lore")),
                        items.getInt("crossed-potion.model-data", 0)),
                new Failure(failMode(mixing.getString("failure.mode")),
                        percent(mixing.get("failure.base-chance"), "mixing.yml failure.base-chance"),
                        percent(mixing.get("failure.chance-per-effect"), "mixing.yml failure.chance-per-effect"),
                        percent(mixing.get("failure.max-chance"), "mixing.yml failure.max-chance")),
                limits(mixing),
                levels(levels),
                new Sounds(sound(root, "sounds.click"), sound(root, "sounds.mix-success"),
                        sound(root, "sounds.mix-fail")),
                essence(items, "essence", "duration-minutes", DEFAULT_ESSENCE_MINUTES, MAX_ESSENCE_MINUTES),
                essence(items, "power-essence", "max-level", DEFAULT_ESSENCE_LEVEL, MAX_EFFECT_LEVEL),
                essence(items, "stealth-essence", "value", 1, 1),
                essence(items, "mystery-essence", "value", 1, 1),
                essence(items, "eternity-essence", "value", 1, 1),
                new Spoiled(items.getString("spoiled-potion.name"),
                        List.copyOf(items.getStringList("spoiled-potion.lore")),
                        items.getInt("spoiled-potion.model-data", 0),
                        spoiledEffects(items.getStringList("spoiled-potion.effects"))));
    }

    private static @NotNull List<Cost> costs(@NotNull List<Map<?, ?>> raw) {
        List<Cost> costs = new ArrayList<>();
        for (Map<?, ?> entry : raw) {
            String context = "config.yml price.costs[" + costs.size() + "]";
            Object currency = entry.get("currency");
            costs.add(new Cost(parseType(entry.get("provider")),
                    currency == null ? "money" : String.valueOf(currency),
                    Math.max(0.0D, number(entry.get("base"), 0.0D, context + ".base")),
                    Math.max(0.0D, number(entry.get("per-potion"), 0.0D, context + ".per-potion")),
                    Math.max(0.0D, number(entry.get("per-effect"), 0.0D, context + ".per-effect"))));
        }
        return List.copyOf(costs);
    }

    private static @NotNull Limits limits(@NotNull ConfigurationSection mixing) {
        List<Limits.Group> groups = new ArrayList<>();
        ConfigurationSection section = mixing.getConfigurationSection("limits.groups");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                groups.add(new Limits.Group(Limits.GROUP_PERMISSION + name.toLowerCase(Locale.ROOT),
                        Math.max(0, section.getInt(name + ".cooldown-seconds", 0)),
                        Math.max(0, section.getInt(name + ".daily-mixes", 0))));
            }
        }
        return new Limits(Math.max(0, mixing.getInt("limits.cooldown-seconds", 0)),
                Math.max(0, mixing.getInt("limits.daily-mixes", 0)), List.copyOf(groups));
    }

    private static @NotNull Levels levels(@NotNull ConfigurationSection root) {
        List<Level> list = new ArrayList<>();
        ConfigurationSection section = root.getConfigurationSection("list");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String context = "levels.yml list." + key;
                try {
                    list.add(new Level(Integer.parseInt(key),
                            Math.max(0, section.getInt(key + ".points", section.getInt(key + ".xp", 0))),
                            Math.max(1, section.getInt(key + ".slots", Integer.MAX_VALUE)),
                            percent(section.get(key + ".discount"), context + ".discount"),
                            percent(section.get(key + ".fail-reduction"), context + ".fail-reduction")));
                } catch (NumberFormatException exception) {
                    Logger.warn(context + ": номер уровня должен быть числом, уровень пропущен");
                }
            }
        }
        list.sort(Comparator.comparingInt(Level::xp));
        if (list.isEmpty()) {
            list.add(new Level(1, 0, Integer.MAX_VALUE, 0.0D, 0.0D));
        } else if (list.getFirst().xp() > 0) {
            list.addFirst(new Level(0, 0, list.getFirst().slots(), 0.0D, 0.0D));
        }
        return new Levels(Math.max(0, root.getInt("points-per-mix", root.getInt("xp-per-mix", 0))), List.copyOf(list));
    }

    private static @NotNull Essence essence(@NotNull ConfigurationSection items, @NotNull String path,
                                            @NotNull String valueKey, int fallback, int max) {
        ConfigurationSection section = items.getConfigurationSection(path);
        if (section == null) {
            Logger.warn("items.yml: нет секции " + path + ", предмет отключён");
            return new Essence(null, fallback, 1, Set.of(), Set.of());
        }
        return new Essence(ItemTemplate.parse(section, "items.yml " + path),
                Math.clamp(section.getInt(valueKey, fallback), 1, max),
                Math.clamp(section.getInt("level-cost", 1), 1, MAX_ANVIL_COST),
                effects(section.getStringList("required-effects"), path + ".required-effects"),
                effects(section.getStringList("ignored-effects"), path + ".ignored-effects"));
    }

    private static @NotNull Set<PotionEffectType> effects(@NotNull List<String> raw, @NotNull String path) {
        Set<PotionEffectType> effects = new HashSet<>();
        for (String name : raw) {
            PotionEffectType type = effectType(name);
            if (type == null) {
                Logger.warn("items.yml " + path + ": неизвестный эффект '" + name + "', пропущен");
                continue;
            }
            effects.add(type);
        }
        return Set.copyOf(effects);
    }

    private static @NotNull List<PotionEffect> spoiledEffects(@NotNull List<String> raw) {
        List<PotionEffect> effects = new ArrayList<>();
        for (String entry : raw) {
            String[] parts = entry.trim().split(":");
            PotionEffectType type = parts.length == SPOILED_EFFECT_PARTS ? effectType(parts[0]) : null;
            if (type == null) {
                Logger.warn("items.yml spoiled-potion.effects: '" + entry + "' должно быть эффект:уровень:секунды, пропущено");
                continue;
            }
            try {
                effects.add(new PotionEffect(type,
                        Math.max(1, Integer.parseInt(parts[2].trim())) * TICKS_PER_SECOND,
                        Math.clamp(Integer.parseInt(parts[1].trim()), 1, MAX_EFFECT_LEVEL) - 1));
            } catch (NumberFormatException exception) {
                Logger.warn("items.yml spoiled-potion.effects: в '" + entry + "' уровень и секунды должны быть числами, пропущено");
            }
        }
        return List.copyOf(effects);
    }

    private static @NotNull List<Set<PotionEffectType>> incompatible(@NotNull List<?> raw) {
        List<Set<PotionEffectType>> groups = new ArrayList<>();
        for (Object entry : raw) {
            Set<PotionEffectType> group = new HashSet<>();
            for (Object name : entry instanceof List<?> list ? list : List.of(entry)) {
                PotionEffectType type = effectType(String.valueOf(name));
                if (type == null) {
                    Logger.warn("mixing.yml incompatible: неизвестный эффект '" + name + "', пропущен");
                    continue;
                }
                group.add(type);
            }
            if (group.size() < 2) {
                Logger.warn("mixing.yml incompatible: в группе " + entry + " меньше двух эффектов, пропущена");
                continue;
            }
            groups.add(Set.copyOf(group));
        }
        return List.copyOf(groups);
    }

    private static @Nullable PotionEffectType effectType(@NotNull String raw) {
        NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        return key == null ? null : Registry.MOB_EFFECT.get(key);
    }

    private static double percent(@Nullable Object raw, @NotNull String context) {
        return Math.clamp(number(raw, 0.0D, context), 0.0D, MAX_PERCENT);
    }

    private static double number(@Nullable Object raw, double fallback, @NotNull String context) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(raw).trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            Logger.warn(context + ": '" + raw + "' не число, используется " + fallback);
            return fallback;
        }
    }

    private static @Nullable Sound sound(@NotNull ConfigurationSection root, @NotNull String path) {
        String raw = root.getString(path + ".key", "");
        if (raw.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
        if (key == null || Registry.SOUND_EVENT.get(key) == null) {
            Logger.warn("config.yml " + path + ": неизвестный звук '" + raw + "', звук отключён");
            return null;
        }
        return Sound.sound(key, Sound.Source.MASTER,
                (float) Math.clamp(number(root.get(path + ".volume"), MAX_VOLUME, "config.yml " + path + ".volume"),
                        MIN_VOLUME, MAX_VOLUME),
                (float) Math.clamp(number(root.get(path + ".pitch"), 1.0D, "config.yml " + path + ".pitch"), MIN_PITCH, MAX_PITCH));
    }

    private static @NotNull EconomyType parseType(@Nullable Object raw) {
        try {
            return EconomyType.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Logger.warn("config.yml price.costs: неизвестный provider '" + raw + "', используется VAULT");
            return EconomyType.VAULT;
        }
    }

    private static @NotNull FailMode failMode(@Nullable String raw) {
        try {
            return FailMode.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Logger.warn("mixing.yml failure.mode: неизвестное значение '" + raw + "', используется BURN");
            return FailMode.BURN;
        }
    }

    public enum EconomyType {
        VAULT,
        BVAULT,
        COINSENGINE,
        PLAYERPOINTS
    }

    public enum FailMode {
        BURN,
        SPOIL
    }

    public record Command(@NotNull String name, @NotNull List<String> aliases) {
    }

    public record Cost(@NotNull EconomyType type, @NotNull String currency, double base, double perPotion,
                       double perEffect) {

        public double amount(int potions, int effects, double discountPercent) {
            return (base + perPotion * potions + perEffect * effects) * (MAX_PERCENT - discountPercent) / MAX_PERCENT;
        }
    }

    public record Mixing(int minPotions, boolean allowCrossed, boolean allowDuplicates, boolean combineEssences,
                         @NotNull List<Set<PotionEffectType>> incompatible, @Nullable String resultName,
                         @NotNull List<String> resultLore, int resultModelData) {
    }

    public record Failure(@NotNull FailMode mode, double baseChance, double chancePerEffect, double maxChance) {

        public double chance(int effects, double reduction) {
            return Math.clamp(baseChance + chancePerEffect * effects - reduction, 0.0D, maxChance);
        }
    }

    public record Limits(int cooldownSeconds, int dailyMixes, @NotNull List<Group> groups) {

        public static final String GROUP_PERMISSION = "ralchemist.limits.";

        public @NotNull Limits resolve(@NotNull Predicate<String> hasPermission) {
            int cooldown = cooldownSeconds;
            int daily = dailyMixes;
            for (Group group : groups) {
                if (!hasPermission.test(group.permission())) {
                    continue;
                }
                cooldown = Math.min(cooldown, group.cooldownSeconds());
                daily = daily == 0 || group.dailyMixes() == 0 ? 0 : Math.max(daily, group.dailyMixes());
            }
            return new Limits(cooldown, daily, List.of());
        }

        public record Group(@NotNull String permission, int cooldownSeconds, int dailyMixes) {
        }
    }

    public record Level(int number, int xp, int slots, double discount, double failReduction) {
    }

    public record Levels(int xpPerMix, @NotNull List<Level> list) {

        public @NotNull Level levelFor(int xp) {
            Level current = list.getFirst();
            for (Level level : list) {
                if (level.xp() <= xp) {
                    current = level;
                }
            }
            return current;
        }

        public @Nullable Level next(@NotNull Level level) {
            int index = list.indexOf(level) + 1;
            return index < list.size() ? list.get(index) : null;
        }

        public @Nullable Level unlocking(int slotIndex) {
            for (Level level : list) {
                if (level.slots() > slotIndex) {
                    return level;
                }
            }
            return null;
        }
    }

    public record Essence(@Nullable ItemTemplate item, int value, int levelCost,
                          @NotNull Set<PotionEffectType> requiredEffects,
                          @NotNull Set<PotionEffectType> ignoredEffects) {
    }

    public record Spoiled(@Nullable String name, @NotNull List<String> lore, int modelData,
                          @NotNull List<PotionEffect> effects) {
    }

    public record Sounds(@Nullable Sound click, @Nullable Sound mixSuccess, @Nullable Sound mixFail) {
    }
}
