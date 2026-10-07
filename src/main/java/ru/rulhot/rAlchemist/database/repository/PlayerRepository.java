package ru.rulhot.rAlchemist.database.repository;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.database.DatabaseManager;
import ru.rulhot.rAlchemist.database.model.PlayerRecord;
import ru.rulhot.rAlchemist.model.DatabaseSettings;
import ru.rulhot.rAlchemist.model.PlayerData;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

public final class PlayerRepository {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10L;
    private static final long RECONNECT_INTERVAL_MILLIS = 30_000L;

    private final @NotNull DatabaseManager databaseManager;
    private final @NotNull ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "rAlchemist-database");
        thread.setDaemon(true);
        return thread;
    });
    private final @NotNull Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private volatile @Nullable Dao<PlayerRecord, String> dao;
    private volatile @Nullable DatabaseSettings settings;
    private long lastAttempt;

    public PlayerRepository(@NotNull DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void start(@NotNull DatabaseSettings settings) {
        this.settings = settings;
        executor.execute(this::connect);
    }

    private @Nullable Dao<PlayerRecord, String> connect() {
        Dao<PlayerRecord, String> current = dao;
        DatabaseSettings target = settings;
        long now = System.currentTimeMillis();
        if (current != null || target == null || now - lastAttempt < RECONNECT_INTERVAL_MILLIS) {
            return current;
        }
        lastAttempt = now;
        databaseManager.close();
        try {
            ConnectionSource source = databaseManager.connect(target);
            TableUtils.createTableIfNotExists(source, PlayerRecord.class);
            current = DaoManager.createDao(source, PlayerRecord.class);
            dao = current;
        } catch (SQLException exception) {
            Logger.error("База данных недоступна, алхимик не откроется, пока она не заработает. "
                    + "Следующая попытка - при следующем обращении игрока", exception);
        }
        return current;
    }

    public @Nullable PlayerData get(@NotNull UUID uuid) {
        return cache.get(uuid);
    }

    public void load(@NotNull UUID uuid) {
        executor.execute(() -> {
            Dao<PlayerRecord, String> current = connect();
            if (current == null || cache.containsKey(uuid)) {
                return;
            }
            try {
                PlayerRecord record = current.queryForId(uuid.toString());
                cache.put(uuid, record == null ? PlayerData.EMPTY : new PlayerData(record.getXp(),
                        record.getLastMix(), record.getMixDay(), record.getMixCount(), record.getColor()));
            } catch (SQLException exception) {
                Logger.error("Не удалось загрузить данные игрока uuid=" + uuid, exception);
            }
        });
    }

    public void update(@NotNull UUID uuid, @NotNull UnaryOperator<PlayerData> change) {
        PlayerData updated = cache.computeIfPresent(uuid, (key, data) -> change.apply(data));
        if (updated == null) {
            return;
        }
        executor.execute(() -> {
            Dao<PlayerRecord, String> current = dao;
            if (current == null) {
                return;
            }
            try {
                current.createOrUpdate(new PlayerRecord(uuid.toString(), updated.xp(), updated.lastMix(),
                        updated.mixDay(), updated.mixCount(), updated.color()));
            } catch (SQLException exception) {
                Logger.error("Не удалось сохранить данные игрока uuid=" + uuid, exception);
            }
        });
    }

    public void unload(@NotNull UUID uuid) {
        executor.execute(() -> cache.remove(uuid));
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Logger.error("Сохранение данных игроков не завершилось за " + SHUTDOWN_TIMEOUT_SECONDS + " с");
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        databaseManager.close();
    }
}
