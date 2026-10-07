package ru.rulhot.rAlchemist.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.menu.MenuHolder;
import ru.rulhot.rAlchemist.menu.MenuManager;

@RequiredArgsConstructor
public final class MenuListener implements Listener {

    private final @NotNull MenuManager menuManager;

    @EventHandler(ignoreCancelled = true)
    public void onClick(@NotNull InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        menuManager.handleClick(event, holder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(@NotNull InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        menuManager.handleDrag(event, holder);
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)
                || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        menuManager.handleClose(player, holder);
    }
}
