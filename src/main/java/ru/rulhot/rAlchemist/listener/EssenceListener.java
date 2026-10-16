package ru.rulhot.rAlchemist.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.service.EssenceService;

@RequiredArgsConstructor
public final class EssenceListener implements Listener {

    private static final int ESSENCES_PER_USE = 1;

    private final @NotNull EssenceService essences;

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(@NotNull PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack secondItem = inventory.getSecondItem();

        if (secondItem == null || secondItem.isEmpty()) {
            return;
        }

        EssenceService.Type type = essences.typeOf(secondItem);
        if (type == null) {
            return;
        }

        ItemStack firstItem = inventory.getFirstItem();
        if (firstItem == null || firstItem.isEmpty()) {
            return;
        }

        ItemStack result = essences.apply(type, firstItem);
        if (result == null || result.isEmpty()) {
            event.setResult(null);
            return;
        }

        event.setResult(result);

        AnvilView view = event.getView();
        view.setRepairItemCountCost(ESSENCES_PER_USE);
        view.setRepairCost(essences.settings(type).levelCost());
    }
}
