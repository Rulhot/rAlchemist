package ru.rulhot.rAlchemist.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.database.repository.PlayerRepository;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.menu.MenuConfig;
import ru.rulhot.rAlchemist.model.PlayerData;

import java.util.Locale;

@RequiredArgsConstructor
public final class ColorService {

    private static final String HEX_PREFIX = "#";
    private static final int HEX_LENGTH = 7;
    private static final int HEX_RADIX = 16;

    private final @NotNull PlayerRepository repository;
    private final @NotNull ConfigManager config;

    public static @Nullable Color parseHex(@Nullable String raw) {
        if (raw == null || raw.length() != HEX_LENGTH || !raw.startsWith(HEX_PREFIX)) {
            return null;
        }
        try {
            return Color.fromRGB(Integer.parseInt(raw.substring(1), HEX_RADIX));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public @Nullable MenuConfig.PotionColor custom(@NotNull String raw) {
        String hex = raw.trim().toUpperCase(Locale.ROOT);
        Color color = parseHex(hex);
        return color == null ? null : new MenuConfig.PotionColor(hex, "<" + hex + ">" + hex, color);
    }

    public @Nullable MenuConfig.PotionColor selected(@NotNull Player player) {
        PlayerData data = repository.get(player.getUniqueId());
        String id = data == null ? null : data.color();
        if (id == null) {
            return null;
        }
        return id.startsWith(HEX_PREFIX) ? custom(id) : config.colorMenu().color(id);
    }

    public void select(@NotNull Player player, @NotNull MenuConfig.PotionColor color) {
        repository.update(player.getUniqueId(), data -> data.withColor(color.id()));
    }

    public void reset(@NotNull Player player) {
        repository.update(player.getUniqueId(), data -> data.withColor(null));
    }
}
