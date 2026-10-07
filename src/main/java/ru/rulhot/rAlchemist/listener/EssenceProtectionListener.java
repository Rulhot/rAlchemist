package ru.rulhot.rAlchemist.listener;

import io.papermc.paper.event.entity.EntityCompostItemEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Crafter;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.service.EssenceService;

import java.util.Set;

@RequiredArgsConstructor
public final class EssenceProtectionListener implements Listener {

    private static final Set<Material> OPENABLE_BLOCKS = Set.of(Material.CRAFTING_TABLE, Material.ENDER_CHEST,
            Material.LEVER);

    private final @NotNull EssenceService essences;

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        boolean block = event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!block && event.getAction() != Action.RIGHT_CLICK_AIR || !isEssence(event.getItem())) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        if (block && event.getClickedBlock() != null && !isOpenable(event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(@NotNull PlayerInteractEntityEvent event) {
        if (isEssence(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractAtEntity(@NotNull PlayerInteractAtEntityEvent event) {
        if (isEssence(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(@NotNull PlayerItemConsumeEvent event) {
        if (isEssence(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(@NotNull BlockPlaceEvent event) {
        if (isEssence(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(@NotNull BlockDispenseEvent event) {
        if (event.getBlock().getType() == Material.DISPENSER && isEssence(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCompost(@NotNull EntityCompostItemEvent event) {
        if (isEssence(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(@NotNull PrepareItemCraftEvent event) {
        if (hasEssence(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(@NotNull CrafterCraftEvent event) {
        if (event.getBlock().getState(false) instanceof Crafter crafter
                && hasEssence(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(@NotNull PrepareSmithingEvent event) {
        if (hasEssence(event.getInventory().getStorageContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(@NotNull PrepareGrindstoneEvent event) {
        if (hasEssence(event.getInventory().getStorageContents())) {
            event.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStonecutter(@NotNull InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.STONECUTTER
                && event.getSlotType() == InventoryType.SlotType.RESULT
                && hasEssence(event.getInventory().getStorageContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(@NotNull PrepareItemEnchantEvent event) {
        if (hasEssence(event.getInventory().getStorageContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(@NotNull EnchantItemEvent event) {
        if (hasEssence(event.getInventory().getStorageContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceFuel(@NotNull FurnaceBurnEvent event) {
        if (isEssence(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceSmelt(@NotNull FurnaceSmeltEvent event) {
        if (isEssence(event.getSource())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrewingFuel(@NotNull BrewingStandFuelEvent event) {
        if (isEssence(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(@NotNull BrewEvent event) {
        if (hasEssence(event.getContents().getContents())) {
            event.setCancelled(true);
        }
    }

    private static boolean isOpenable(@NotNull Block block) {
        Material type = block.getType();
        return OPENABLE_BLOCKS.contains(type) || Tag.ANVIL.isTagged(type) || Tag.DOORS.isTagged(type)
                || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || Tag.BUTTONS.isTagged(type)
                || Tag.BEDS.isTagged(type) || block.getState(false) instanceof Container;
    }

    private boolean isEssence(@Nullable ItemStack item) {
        return essences.typeOf(item) != null;
    }

    private boolean hasEssence(@Nullable ItemStack @NotNull [] items) {
        for (ItemStack item : items) {
            if (isEssence(item)) {
                return true;
            }
        }
        return false;
    }
}
