package ru.rulhot.rAlchemist.model;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.Locale;

public record DatabaseSettings(@NotNull Type type, @NotNull Pool pool, @NotNull MySql mysql) {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final int POOL_SIZE = 4;
    private static final int POOL_IDLE = 2;
    private static final long CONNECT_TIMEOUT_SECONDS = 30L;
    private static final long IDLE_TIMEOUT_MINUTES = 10L;
    private static final long LIFETIME_MINUTES = 30L;

    public static @NotNull DatabaseSettings from(@NotNull ConfigurationSection root) {
        return new DatabaseSettings(
                type(root.getString("storage", "sqlite")),
                new Pool(POOL_SIZE, POOL_IDLE, CONNECT_TIMEOUT_SECONDS * MILLIS_PER_SECOND,
                        IDLE_TIMEOUT_MINUTES * MILLIS_PER_MINUTE, LIFETIME_MINUTES * MILLIS_PER_MINUTE),
                new MySql(
                        root.getString("mysql.host", "localhost"),
                        root.getInt("mysql.port", 3306),
                        root.getString("mysql.database", "ralchemist"),
                        root.getString("mysql.user", "root"),
                        root.getString("mysql.password", ""),
                        false));
    }

    private static @NotNull Type type(@NotNull String raw) {
        try {
            return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            Logger.warn("database.yml storage: неизвестное значение '" + raw + "', используется SQLITE");
            return Type.SQLITE;
        }
    }

    public enum Type {
        SQLITE,
        MYSQL
    }

    public record Pool(int maximumPoolSize, int minimumIdle, long connectionTimeoutMs, long idleTimeoutMs,
                       long maxLifetimeMs) {

        private static final int MIN_POOL_SIZE = 2;

        public Pool {
            maximumPoolSize = Math.max(MIN_POOL_SIZE, maximumPoolSize);
            minimumIdle = Math.clamp(minimumIdle, 0, maximumPoolSize);
        }
    }

    public record MySql(@NotNull String host, int port, @NotNull String database, @NotNull String username,
                        @NotNull String password, boolean useSsl) {

        @Override
        public @NotNull String toString() {
            return "MySql[host=" + host + ", port=" + port + ", database=" + database + "]";
        }
    }
}
