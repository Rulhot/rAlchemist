package ru.rulhot.rAlchemist.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.database.repository.PlayerRepository;

@RequiredArgsConstructor
public final class PlayerDataListener implements Listener {

    private final @NotNull PlayerRepository repository;

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        repository.load(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        repository.unload(event.getPlayer().getUniqueId());
    }
}
