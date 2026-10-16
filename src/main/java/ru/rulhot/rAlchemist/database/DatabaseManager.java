package ru.rulhot.rAlchemist.database;

import com.j256.ormlite.jdbc.DataSourceConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.model.DatabaseSettings;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;

@RequiredArgsConstructor
public final class DatabaseManager {

    private static final String SQLITE_FOLDER = "data";
    private static final String SQLITE_FILE = "data.db";
    private static final String SQLITE_BUSY_TIMEOUT_MS = "5000";
    private static final String HIKARI_LOGGER = "com.zaxxer.hikari";
    private static final String ORMLITE_LOGGER = "com.j256.ormlite";

    private final @NotNull File dataFolder;
    private @Nullable HikariDataSource dataSource;
    private @Nullable ConnectionSource connectionSource;

    public @NotNull ConnectionSource connect(@NotNull DatabaseSettings settings) throws SQLException {
        quietLibraries();
        HikariConfig config = new HikariConfig();
        config.setPoolName("rAlchemist");
        switch (settings.type()) {
            case SQLITE -> {
                config.setDriverClassName("org.sqlite.JDBC");
                File folder = new File(dataFolder, SQLITE_FOLDER);
                if (!folder.isDirectory() && !folder.mkdirs()) {
                    throw new SQLException("Не удалось создать папку " + folder.getAbsolutePath());
                }
                config.setJdbcUrl("jdbc:sqlite:" + new File(folder, SQLITE_FILE).getAbsolutePath());
                config.addDataSourceProperty("journal_mode", "DELETE");
                config.addDataSourceProperty("synchronous", "FULL");
                config.addDataSourceProperty("busy_timeout", SQLITE_BUSY_TIMEOUT_MS);
            }
            case MYSQL -> {
                DatabaseSettings.MySql mysql = settings.mysql();
                config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                config.setJdbcUrl("jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/" + mysql.database()
                        + "?useSSL=" + mysql.useSsl() + "&rewriteBatchedStatements=true&characterEncoding=utf8");
                config.setUsername(mysql.username());
                config.setPassword(mysql.password());
            }
        }
        DatabaseSettings.Pool pool = settings.pool();
        config.setMaximumPoolSize(pool.maximumPoolSize());
        config.setMinimumIdle(pool.minimumIdle());
        config.setConnectionTimeout(pool.connectionTimeoutMs());
        config.setIdleTimeout(pool.idleTimeoutMs());
        config.setMaxLifetime(pool.maxLifetimeMs());
        try {
            dataSource = new HikariDataSource(config);
        } catch (HikariPool.PoolInitializationException exception) {
            throw new SQLException("Не удалось открыть пул соединений", exception);
        }
        connectionSource = new DataSourceConnectionSource(dataSource, config.getJdbcUrl());
        return connectionSource;
    }

    private static void quietLibraries() {
        try {
            Configurator.setLevel(Map.of(HIKARI_LOGGER, Level.WARN, ORMLITE_LOGGER, Level.WARN));
        } catch (LinkageError | RuntimeException exception) {
            Logger.debug("Не удалось понизить уровень логов HikariCP и ORMLite: " + exception.getMessage());
        }
    }

    public void close() {
        if (connectionSource != null) {
            connectionSource.closeQuietly();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
