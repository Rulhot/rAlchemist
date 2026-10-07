package ru.rulhot.rAlchemist.menu;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class MenuHolder implements InventoryHolder {

    private static final long CLICK_COOLDOWN_MILLIS = 150L;

    @Getter
    private final @NotNull View view;
    @Getter
    private final @NotNull MenuConfig config;
    @Getter
    private final @Nullable MenuConfig.PotionColor color;
    @Getter
    private final @NotNull Map<Integer, ItemStack> carried;
    @Getter
    private final @NotNull Set<Integer> inputSlots = new LinkedHashSet<>();
    private final @NotNull Inventory inventory;
    @Getter
    @Setter
    private boolean resultReady;
    @Getter
    private boolean returned;
    private boolean refreshQueued;
    private long lastClick;

    public MenuHolder(@NotNull View view, @NotNull MenuConfig config, @Nullable MenuConfig.PotionColor color,
                      @NotNull Map<Integer, ItemStack> carried, @NotNull Component title) {
        this.view = view;
        this.config = config;
        this.color = color;
        this.carried = carried;
        this.resultReady = view == View.MAIN && carried.containsKey(config.resultSlot());
        this.inventory = Bukkit.createInventory(this, config.size(), title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public boolean markReturned() {
        if (returned) {
            return false;
        }
        returned = true;
        return true;
    }

    public boolean queueRefresh() {
        if (refreshQueued) {
            return false;
        }
        refreshQueued = true;
        return true;
    }

    public void refreshDone() {
        refreshQueued = false;
    }

    public boolean acceptClick() {
        long now = System.currentTimeMillis();
        if (now - lastClick < CLICK_COOLDOWN_MILLIS) {
            return false;
        }
        lastClick = now;
        return true;
    }

    public enum View {
        MAIN,
        COLOR
    }
}
