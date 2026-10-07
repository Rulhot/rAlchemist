package ru.rulhot.rAlchemist.service;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.database.repository.PlayerRepository;
import ru.rulhot.rAlchemist.model.PlayerData;
import ru.rulhot.rAlchemist.model.Settings;

import java.time.LocalDate;
import java.util.Map;

@RequiredArgsConstructor
public final class ProgressService {

    public static final String BYPASS_COOLDOWN_PERMISSION = "ralchemist.bypass.cooldown";
    public static final String BYPASS_LIMIT_PERMISSION = "ralchemist.bypass.limit";

    private static final long MILLIS_PER_SECOND = 1000L;

    private final @NotNull PlayerRepository repository;

    public boolean isLoaded(@NotNull Player player) {
        return repository.get(player.getUniqueId()) != null;
    }

    public int xp(@NotNull Player player) {
        return data(player).xp();
    }

    public void setXp(@NotNull Player player, int value) {
        repository.update(player.getUniqueId(), data -> data.withXp(Math.max(0, value)));
    }

    public void addXp(@NotNull Player player, int amount) {
        repository.update(player.getUniqueId(),
                data -> data.withXp((int) Math.min(Integer.MAX_VALUE, (long) data.xp() + amount)));
    }

    public @Nullable MixService.Result.Failure check(@NotNull Player player, @NotNull Settings.Limits rules) {
        Settings.Limits limits = rules.resolve(player::hasPermission);
        PlayerData data = data(player);
        long wait = cooldownLeft(player, data, limits);
        if (wait > 0L) {
            return new MixService.Result.Failure("mix.cooldown", Map.of("seconds", wait));
        }
        if (limits.dailyMixes() > 0 && !player.hasPermission(BYPASS_LIMIT_PERMISSION)
                && mixesToday(data) >= limits.dailyMixes()) {
            return new MixService.Result.Failure("mix.daily-limit", Map.of("limit", limits.dailyMixes()));
        }
        return null;
    }

    public int mixesLeft(@NotNull Player player, @NotNull Settings.Limits rules) {
        Settings.Limits limits = rules.resolve(player::hasPermission);
        if (limits.dailyMixes() <= 0 || player.hasPermission(BYPASS_LIMIT_PERMISSION)) {
            return -1;
        }
        return Math.max(0, limits.dailyMixes() - mixesToday(data(player)));
    }

    public void record(@NotNull Player player, boolean success) {
        repository.update(player.getUniqueId(), data -> data.withMix(System.currentTimeMillis(),
                LocalDate.now().toEpochDay(), mixesToday(data) + (success ? 1 : 0)));
    }

    public void requestLoad(@NotNull Player player) {
        repository.load(player.getUniqueId());
    }

    private @NotNull PlayerData data(@NotNull Player player) {
        PlayerData data = repository.get(player.getUniqueId());
        return data == null ? PlayerData.EMPTY : data;
    }

    private static long cooldownLeft(@NotNull Player player, @NotNull PlayerData data,
                                     @NotNull Settings.Limits limits) {
        if (limits.cooldownSeconds() <= 0 || player.hasPermission(BYPASS_COOLDOWN_PERMISSION)) {
            return 0L;
        }
        long left = data.lastMix() + limits.cooldownSeconds() * MILLIS_PER_SECOND - System.currentTimeMillis();
        return left <= 0L ? 0L : (left + MILLIS_PER_SECOND - 1L) / MILLIS_PER_SECOND;
    }

    private static int mixesToday(@NotNull PlayerData data) {
        return data.mixDay() == LocalDate.now().toEpochDay() ? data.mixCount() : 0;
    }
}
