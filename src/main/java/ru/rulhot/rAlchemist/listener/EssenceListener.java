package ru.rulhot.rAlchemist.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
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
        EssenceService.Type type = essences.typeOf(event.getInventory().getSecondItem());
        if (type == null) {
            return;
        }
        ItemStack potion = event.getInventory().getFirstItem();
        ItemStack result = potion == null ? null : essences.apply(type, potion);
        event.setResult(result);
        if (result == null) {
            return;
        }
        AnvilView view = event.getView();
        view.setRepairItemCountCost(ESSENCES_PER_USE);
        view.setRepairCost(essences.settings(type).levelCost());
    }
}
