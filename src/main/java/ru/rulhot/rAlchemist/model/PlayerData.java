package ru.rulhot.rAlchemist.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerData(int xp, long lastMix, long mixDay, int mixCount, @Nullable String color) {

    public static final PlayerData EMPTY = new PlayerData(0, 0L, 0L, 0, null);

    public @NotNull PlayerData withXp(int value) {
        return new PlayerData(value, lastMix, mixDay, mixCount, color);
    }

    public @NotNull PlayerData withMix(long time, long day, int count) {
        return new PlayerData(xp, time, day, count, color);
    }

    public @NotNull PlayerData withColor(@Nullable String value) {
        return new PlayerData(xp, lastMix, mixDay, mixCount, value);
    }
}
