package ru.rulhot.rAlchemist.menu;

import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.service.ColorService;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record MenuConfig(
        @NotNull String title,
        int size,
        @NotNull Map<Integer, Button> buttons,
        @NotNull List<Integer> inputSlots,
        int resultSlot,
        int previewSlot,
        @NotNull Map<Integer, PotionColor> colors,
        @Nullable ItemTemplate colorIcon,
        @Nullable ItemTemplate lockedSlot,
        @NotNull List<String> previewLore
) {

    public static final int NO_SLOT = -1;

    private static final int ROW_SIZE = 9;
    private static final int MAX_SIZE = 54;

    public static @NotNull MenuConfig parse(@NotNull String file, @NotNull ConfigurationSection root) {
        int size = root.getInt("size", MAX_SIZE);
        if (size % ROW_SIZE != 0 || size < ROW_SIZE || size > MAX_SIZE) {
            Logger.warn(file + ": size должен быть 9, 18, ..., 54, используется " + MAX_SIZE);
            size = MAX_SIZE;
        }
        Set<Integer> inputSlots = parseSlots(root.getList("input-slots", List.of()), size, file + " input-slots");
        Set<Integer> used = new HashSet<>(inputSlots);

        int resultSlot = root.getInt("result-slot", NO_SLOT);
        if (resultSlot != NO_SLOT && (resultSlot < 0 || resultSlot >= size || !used.add(resultSlot))) {
            Logger.warn(file + ": result-slot " + resultSlot + " занят или вне меню, результат выдаётся в инвентарь");
            resultSlot = NO_SLOT;
        }

        int previewSlot = root.getInt("preview-slot", resultSlot);
        if (previewSlot != resultSlot && previewSlot != NO_SLOT
                && (previewSlot < 0 || previewSlot >= size || !used.add(previewSlot))) {
            Logger.warn(file + ": preview-slot " + previewSlot + " занят или вне меню, предпросмотр отключён");
            previewSlot = NO_SLOT;
        }

        ItemTemplate colorIcon = template(root, "color-item", file);
        Map<Integer, PotionColor> colors = new HashMap<>();
        ConfigurationSection colorSection = root.getConfigurationSection("colors");
        if (colorSection != null && colorIcon != null) {
            for (String key : colorSection.getKeys(false)) {
                String context = file + " colors." + key;
                ConfigurationSection section = colorSection.getConfigurationSection(key);
                Color color = section == null ? null : ColorService.parseHex(section.getString("color"));
                if (color == null) {
                    Logger.warn(context + ": color должен быть в формате #RRGGBB, цвет пропущен");
                    continue;
                }
                PotionColor potionColor = new PotionColor(key, section.getString("name", key), color);
                for (int slot : slots(section, size, context)) {
                    if (claim(used, slot, context)) {
                        colors.put(slot, potionColor);
                    }
                }
            }
        }

        Map<Integer, Button> buttons = new HashMap<>();
        ConfigurationSection buttonSection = root.getConfigurationSection("buttons");
        if (buttonSection != null) {
            for (String key : buttonSection.getKeys(false)) {
                String context = file + " buttons." + key;
                ConfigurationSection section = buttonSection.getConfigurationSection(key);
                ItemTemplate icon = section == null ? null : ItemTemplate.parse(section, context);
                if (icon == null) {
                    continue;
                }
                Button button = new Button(icon, parseAction(section.getString("action"), context));
                for (int slot : slots(section, size, context)) {
                    if (claim(used, slot, context)) {
                        buttons.put(slot, button);
                    }
                }
            }
        }
        return new MenuConfig(root.getString("title", ""), size, Map.copyOf(buttons), List.copyOf(inputSlots),
                resultSlot, previewSlot, Map.copyOf(colors), colorIcon, template(root, "locked-slot", file),
                List.copyOf(root.getStringList("preview-lore")));
    }

    public @Nullable PotionColor color(@NotNull String id) {
        for (PotionColor color : colors.values()) {
            if (color.id().equals(id)) {
                return color;
            }
        }
        return null;
    }

    private static @Nullable ItemTemplate template(@NotNull ConfigurationSection root, @NotNull String path,
                                                   @NotNull String file) {
        ConfigurationSection section = root.getConfigurationSection(path);
        return section == null ? null : ItemTemplate.parse(section, file + " " + path);
    }

    private static boolean claim(@NotNull Set<Integer> used, int slot, @NotNull String context) {
        if (used.add(slot)) {
            return true;
        }
        Logger.warn(context + ": слот " + slot + " уже занят, пропущен");
        return false;
    }

    private static @NotNull Action parseAction(@Nullable String raw, @NotNull String context) {
        if (raw == null || raw.isBlank()) {
            return Action.NONE;
        }
        try {
            return Action.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Logger.warn(context + ": неизвестный action '" + raw + "', кнопка без действия");
            return Action.NONE;
        }
    }

    private static @NotNull Set<Integer> slots(@NotNull ConfigurationSection section, int size,
                                               @NotNull String context) {
        if (section.isInt("slot")) {
            return parseSlots(List.of(section.getInt("slot")), size, context);
        }
        return parseSlots(section.getList("slots", List.of()), size, context);
    }

    private static @NotNull Set<Integer> parseSlots(@NotNull List<?> raw, int size, @NotNull String context) {
        Set<Integer> result = new LinkedHashSet<>();
        for (Object entry : raw) {
            String text = String.valueOf(entry).trim();
            int dash = text.indexOf('-', 1);
            try {
                int first = Integer.parseInt(dash < 0 ? text : text.substring(0, dash).trim());
                int second = dash < 0 ? first : Integer.parseInt(text.substring(dash + 1).trim());
                int from = Math.min(first, second);
                int to = Math.max(first, second);
                if (from < 0 || to >= size) {
                    Logger.warn(context + ": слоты " + from + "-" + to + " выходят за 0-" + (size - 1)
                            + ", лишние пропущены");
                }
                for (int slot = Math.max(0, from); slot <= Math.min(to, size - 1); slot++) {
                    result.add(slot);
                }
            } catch (NumberFormatException exception) {
                Logger.warn(context + ": неверный слот '" + text + "', пропущен");
            }
        }
        return result;
    }

    public enum Action {
        NONE,
        MIX,
        COLOR,
        CUSTOM_COLOR,
        RESET_COLOR,
        BACK,
        CLOSE
    }

    public record Button(@NotNull ItemTemplate icon, @NotNull Action action) {
    }

    public record PotionColor(@NotNull String id, @NotNull String name, @NotNull Color color) {
    }
}
