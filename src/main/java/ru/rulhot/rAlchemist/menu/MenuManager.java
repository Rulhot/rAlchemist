package ru.rulhot.rAlchemist.menu;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.service.ColorService;
import ru.rulhot.rAlchemist.service.MixService;
import ru.rulhot.rAlchemist.service.PriceService;
import ru.rulhot.rAlchemist.service.ProgressService;
import ru.rulhot.rAlchemist.util.MessageUtil;
import ru.rulhot.rAlchemist.util.SchedulerUtil;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RequiredArgsConstructor
public final class MenuManager {

    public static final String PERMISSION_USE = "ralchemist.use";

    private static final String PERCENT_PATTERN = "0.#";
    private static final String AMOUNT_PATTERN = "0.##";
    private static final double MAX_PERCENT = 100.0D;

    private final @NotNull SchedulerUtil scheduler;
    private final @NotNull ConfigManager config;
    private final @NotNull MixService mixService;
    private final @NotNull ColorService colorService;
    private final @NotNull PriceService prices;
    private final @NotNull ProgressService progress;

    public void selectCustomColor(@NotNull Player player, @NotNull String input) {
        MenuConfig.PotionColor color = colorService.custom(input);
        if (color == null) {
            config.send(player, "color.invalid");
        } else if (progress.isLoaded(player)) {
            colorService.select(player, color);
        }
        openMain(player);
    }

    public void refreshOpen(@NotNull Player player) {
        scheduler.runFor(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder holder
                    && !holder.isReturned()) {
                refresh(player, holder);
            }
        });
    }

    public void openMain(@NotNull Player player) {
        if (!progress.isLoaded(player)) {
            progress.requestLoad(player);
            config.send(player, "data-not-loaded");
            return;
        }
        open(player, MenuHolder.View.MAIN, colorService.selected(player), Map.of());
    }

    public void handleClick(@NotNull InventoryClickEvent event, @NotNull MenuHolder holder) {
        if (!(event.getWhoClicked() instanceof Player player) || holder.isPaying()) {
            event.setCancelled(true);
            return;
        }
        MenuConfig menu = holder.getConfig();
        Inventory inventory = holder.getInventory();
        int slot = event.getRawSlot();
        boolean editable = holder.getView() == MenuHolder.View.MAIN;
        if (slot < 0 || slot >= menu.size()) {
            if (!editable || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            if (event.isShiftClick()) {
                event.setCancelled(true);
                moveToInput(event, holder);
                queueRefresh(player, holder);
            }
            return;
        }
        if (editable && holder.getInputSlots().contains(slot)) {
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            queueRefresh(player, holder);
            return;
        }
        event.setCancelled(true);
        if (!holder.acceptClick()) {
            return;
        }
        Sound click = config.settings().sounds().click();
        if (editable && slot == menu.resultSlot()) {
            ItemStack result = inventory.getItem(slot);
            if (holder.isResultReady() && !isEmpty(result)) {
                inventory.setItem(slot, null);
                holder.setResultReady(false);
                give(player, List.of(result));
                play(player, click);
                refresh(player, holder);
            }
            return;
        }
        MenuConfig.PotionColor picked = menu.colors().get(slot);
        if (picked != null) {
            colorService.select(player, picked);
            play(player, click);
            reopen(player, holder, MenuHolder.View.MAIN, picked);
            return;
        }
        MenuConfig.Button button = menu.buttons().get(slot);
        if (button == null || button.action() == MenuConfig.Action.NONE) {
            return;
        }
        if (button.action() == MenuConfig.Action.MIX) {
            mix(player, holder);
            return;
        }
        play(player, click);
        switch (button.action()) {
            case COLOR -> reopen(player, holder, MenuHolder.View.COLOR, holder.getColor());
            case BACK -> reopen(player, holder, MenuHolder.View.MAIN, holder.getColor());
            case RESET_COLOR -> {
                colorService.reset(player);
                reopen(player, holder, MenuHolder.View.MAIN, null);
            }
            case CUSTOM_COLOR -> scheduler.runFor(player, () -> {
                player.closeInventory();
                config.send(player, "color.input");
            });
            case CLOSE -> scheduler.runFor(player, () -> player.closeInventory());
            case NONE, MIX -> {
            }
        }
    }

    public void handleDrag(@NotNull InventoryDragEvent event, @NotNull MenuHolder holder) {
        if (holder.isPaying()) {
            event.setCancelled(true);
            return;
        }
        MenuConfig menu = holder.getConfig();
        boolean editable = holder.getView() == MenuHolder.View.MAIN;
        boolean touched = false;
        for (int slot : event.getRawSlots()) {
            if (slot >= menu.size()) {
                continue;
            }
            if (!editable || !holder.getInputSlots().contains(slot)) {
                event.setCancelled(true);
                return;
            }
            touched = true;
        }
        if (touched && event.getWhoClicked() instanceof Player player) {
            queueRefresh(player, holder);
        }
    }

    public void handleClose(@NotNull Player player, @NotNull MenuHolder holder) {
        if (holder.markReturned()) {
            give(player, takeItems(holder).values());
        }
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.runFor(player, () -> close(player));
        }
    }

    public void shutdown() {
        Bukkit.getOnlinePlayers().forEach(this::close);
    }

    private void close(@NotNull Player player) {
        if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }
        handleClose(player, holder);
        player.closeInventory();
    }

    private void open(@NotNull Player player, @NotNull MenuHolder.View view, @Nullable MenuConfig.PotionColor color,
                      @NotNull Map<Integer, ItemStack> carried) {
        MenuConfig menu = view == MenuHolder.View.MAIN ? config.alchemistMenu() : config.colorMenu();
        MenuHolder holder = new MenuHolder(view, menu, color, carried, MessageUtil.parseText(menu.title()));
        if (view == MenuHolder.View.MAIN) {
            unlockSlots(holder, level(player));
            carried.forEach(holder.getInventory()::setItem);
        }
        refresh(player, holder);
        if (player.openInventory(holder.getInventory()) == null) {
            handleClose(player, holder);
        }
    }

    private void reopen(@NotNull Player player, @NotNull MenuHolder from, @NotNull MenuHolder.View view,
                        @Nullable MenuConfig.PotionColor color) {
        scheduler.runFor(player, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder(false) != from || !from.markReturned()) {
                return;
            }
            open(player, view, color, takeItems(from));
        });
    }

    private void queueRefresh(@NotNull Player player, @NotNull MenuHolder holder) {
        if (!holder.queueRefresh()) {
            return;
        }
        scheduler.runFor(player, () -> {
            holder.refreshDone();
            if (!holder.isReturned()) {
                refresh(player, holder);
            }
        });
    }

    private void refresh(@NotNull Player player, @NotNull MenuHolder holder) {
        Inventory inventory = holder.getInventory();
        MenuConfig menu = holder.getConfig();
        boolean main = holder.getView() == MenuHolder.View.MAIN;
        Settings settings = config.settings();
        Settings.Level level = level(player);
        if (main) {
            unlockSlots(holder, level);
        }
        Quote quote = quote(player, holder, level);
        MenuConfig.PotionColor color = holder.getColor();
        TagResolver resolver = MessageUtil.placeholders(placeholders(player, quote, color));

        menu.buttons().forEach((slot, button) -> inventory.setItem(slot, button.icon().render(resolver,
                button.action() == MenuConfig.Action.COLOR && color != null ? color.color() : null)));
        ItemTemplate colorIcon = menu.colorIcon();
        if (colorIcon != null) {
            menu.colors().forEach((slot, option) -> inventory.setItem(slot, colorIcon.render(
                    MessageUtil.placeholders(Map.of("color", MessageUtil.parseText(option.name()))),
                    option.color())));
        }
        if (!main) {
            return;
        }
        ItemTemplate locked = menu.lockedSlot();
        List<Integer> slots = menu.inputSlots();
        for (int index = holder.getInputSlots().size(); index < slots.size() && locked != null; index++) {
            Settings.Level unlocking = settings.levels().unlocking(index);
            inventory.setItem(slots.get(index), locked.render(MessageUtil.placeholders(Map.of("level",
                    unlocking == null ? config.message("level.never", Map.of())
                            : Component.text(unlocking.number()))), null));
        }
        int previewSlot = menu.previewSlot();
        if (previewSlot == MenuConfig.NO_SLOT || previewSlot == menu.resultSlot() && holder.isResultReady()) {
            return;
        }
        inventory.setItem(previewSlot, quote.result() instanceof MixService.Result.Success success
                ? preview(success.potion(), menu.previewLore(), resolver)
                : null);
    }

    private @NotNull Map<String, Object> placeholders(@NotNull Player player, @NotNull Quote quote,
                                                       @Nullable MenuConfig.PotionColor color) {
        Settings settings = config.settings();
        Settings.Level next = settings.levels().next(quote.level());
        int left = progress.mixesLeft(player, settings.limits());
        DecimalFormat percent = new DecimalFormat(PERCENT_PATTERN, DecimalFormatSymbols.getInstance(Locale.ROOT));
        Map<String, Object> values = new HashMap<>();
        values.put("price", price(quote.charges()));
        values.put("chance", percent.format(quote.chance()));
        values.put("discount", percent.format(quote.discount()));
        values.put("color", color == null ? config.message("color.default", Map.of())
                : MessageUtil.parseText(color.name()));
        values.put("level", quote.level().number());
        Component needed = next == null ? config.message("level.max", Map.of()) : Component.text(next.xp());
        values.put("points", progress.xp(player));
        values.put("points_needed", needed);
        values.put("xp", progress.xp(player));
        values.put("next_xp", needed);
        values.put("left", left < 0 ? config.message("limits.unlimited", Map.of()) : Component.text(left));
        return values;
    }

    private @NotNull Component price(@NotNull List<PriceService.Charge> charges) {
        if (charges.isEmpty()) {
            return config.message("price.free", Map.of());
        }
        DecimalFormat amount = new DecimalFormat(AMOUNT_PATTERN, DecimalFormatSymbols.getInstance(Locale.ROOT));
        return Component.join(JoinConfiguration.commas(true), charges.stream()
                .map(charge -> config.message(
                        "price." + charge.payment().cost().type().name().toLowerCase(Locale.ROOT),
                        Map.of("amount", amount.format(charge.amount()),
                                "currency", charge.payment().cost().currency())))
                .toList());
    }

    private @NotNull ItemStack preview(@NotNull ItemStack potion, @NotNull List<String> extraLore,
                                       @NotNull TagResolver resolver) {
        ItemStack preview = potion.clone();
        preview.editMeta(meta -> {
            List<Component> lore = new ArrayList<>();
            List<Component> present = meta.lore();
            if (present != null) {
                lore.addAll(present);
            }
            extraLore.forEach(line -> lore.add(MessageUtil.parseItemText(line, resolver)));
            meta.lore(lore);
        });
        return preview;
    }

    private @NotNull Settings.Level level(@NotNull Player player) {
        return config.settings().levels().levelFor(progress.xp(player));
    }

    private void unlockSlots(@NotNull MenuHolder holder, @NotNull Settings.Level level) {
        List<Integer> slots = holder.getConfig().inputSlots();
        for (int index = holder.getInputSlots().size(); index < Math.min(level.slots(), slots.size()); index++) {
            holder.getInputSlots().add(slots.get(index));
            holder.getInventory().setItem(slots.get(index), null);
        }
    }

    private @NotNull Quote quote(@NotNull Player player, @NotNull MenuHolder holder, @NotNull Settings.Level level) {
        Settings settings = config.settings();
        Inventory inventory = holder.getInventory();
        List<ItemStack> inputs = holder.getInputSlots().stream()
                .map(inventory::getItem)
                .filter(item -> !isEmpty(item))
                .toList();
        MenuConfig.PotionColor color = holder.getColor();
        MixService.Result result = mixService.mix(inputs, color == null ? null : color.color(), settings.mixing());
        int effects = result instanceof MixService.Result.Success success ? success.effects() : 0;
        double discount = prices.discount(player, level.discount());
        return new Quote(result, inputs.isEmpty() ? Material.POTION : inputs.getFirst().getType(), level, discount,
                prices.quote(inputs.size(), effects, discount),
                effects == 0 ? 0.0D : settings.failure().chance(effects, level.failReduction()));
    }

    private void mix(@NotNull Player player, @NotNull MenuHolder holder) {
        if (holder.getView() != MenuHolder.View.MAIN) {
            return;
        }
        if (holder.isPaying()) {
            config.send(player, "mix.busy");
            return;
        }
        Settings settings = config.settings();
        Quote quote = quote(player, holder, level(player));
        MixService.Result.Failure denied = check(player, holder, settings, quote);
        if (denied != null) {
            fail(player, settings, quote, denied);
            refresh(player, holder);
            return;
        }
        holder.setPaying(true);
        scheduler.runAsync(() -> {
            MixService.Result.Failure paid = pay(player, quote.charges());
            scheduler.runFor(player, () -> finish(player, holder, settings, quote, paid),
                    () -> refund(player, quote, paid));
        });
    }

    private @Nullable MixService.Result.Failure check(@NotNull Player player, @NotNull MenuHolder holder,
                                                      @NotNull Settings settings, @NotNull Quote quote) {
        if (!player.hasPermission(PERMISSION_USE)) {
            return new MixService.Result.Failure("no-permission", Map.of());
        }
        if (holder.isResultReady()) {
            return new MixService.Result.Failure("mix.result-busy", Map.of());
        }
        if (quote.result() instanceof MixService.Result.Failure failure) {
            return failure;
        }
        return progress.check(player, settings.limits());
    }

    private @Nullable MixService.Result.Failure pay(@NotNull Player player,
                                                    @NotNull List<PriceService.Charge> charges) {
        if (!prices.has(player, charges)) {
            return new MixService.Result.Failure("mix.not-enough-money", Map.of());
        }
        if (!prices.withdraw(player, charges)) {
            return new MixService.Result.Failure("mix.economy-error", Map.of());
        }
        return null;
    }

    private void refund(@NotNull Player player, @NotNull Quote quote, @Nullable MixService.Result.Failure paid) {
        if (paid == null) {
            scheduler.runAsync(() -> prices.refund(player, quote.charges()));
        }
    }

    private void finish(@NotNull Player player, @NotNull MenuHolder holder, @NotNull Settings settings,
                        @NotNull Quote quote, @Nullable MixService.Result.Failure paid) {
        holder.setPaying(false);
        if (paid != null) {
            fail(player, settings, quote, paid);
            refresh(player, holder);
            return;
        }
        if (holder.isReturned()) {
            refund(player, quote, null);
            config.send(player, "mix.cancelled");
            return;
        }
        complete(player, holder, settings, quote);
        refresh(player, holder);
    }

    private void fail(@NotNull Player player, @NotNull Settings settings, @NotNull Quote quote,
                      @NotNull MixService.Result.Failure failure) {
        play(player, settings.sounds().mixFail());
        Map<String, Object> placeholders = new HashMap<>(failure.placeholders());
        placeholders.put("price", price(quote.charges()));
        config.send(player, failure.messageKey(), placeholders);
    }

    private void complete(@NotNull Player player, @NotNull MenuHolder holder, @NotNull Settings settings,
                          @NotNull Quote quote) {
        MixService.Result.Success success = (MixService.Result.Success) quote.result();
        Inventory inventory = holder.getInventory();
        holder.getInputSlots().forEach(slot -> inventory.setItem(slot, null));
        boolean failed = ThreadLocalRandom.current().nextDouble() * MAX_PERCENT < quote.chance();
        progress.record(player, !failed);

        Map<String, Object> placeholders = Map.of("price", price(quote.charges()));
        Logger.debug("Скрещивание: player=" + player.getName() + ", effects=" + success.effects() + ", chance="
                + quote.chance() + ", failed=" + failed);
        if (failed) {
            play(player, settings.sounds().mixFail());
            if (settings.failure().mode() == Settings.FailMode.SPOIL) {
                deliver(player, holder, mixService.spoiled(quote.material(), settings.spoiled()));
                config.send(player, "mix.spoiled", placeholders);
            } else {
                config.send(player, "mix.burned", placeholders);
            }
            return;
        }
        deliver(player, holder, success.potion());
        play(player, settings.sounds().mixSuccess());
        config.send(player, "mix.success", placeholders);
        progress.addXp(player, settings.levels().xpPerMix());
        Settings.Level reached = level(player);
        if (reached.xp() > quote.level().xp()) {
            config.send(player, "level.up", Map.of("level", reached.number()));
        }
    }

    private void deliver(@NotNull Player player, @NotNull MenuHolder holder, @NotNull ItemStack potion) {
        int resultSlot = holder.getConfig().resultSlot();
        if (resultSlot == MenuConfig.NO_SLOT) {
            give(player, List.of(potion));
            return;
        }
        holder.getInventory().setItem(resultSlot, potion);
        holder.setResultReady(true);
    }

    private void moveToInput(@NotNull InventoryClickEvent event, @NotNull MenuHolder holder) {
        ItemStack item = event.getCurrentItem();
        if (isEmpty(item)) {
            return;
        }
        Inventory inventory = holder.getInventory();
        for (int slot : holder.getInputSlots()) {
            if (isEmpty(inventory.getItem(slot))) {
                inventory.setItem(slot, item);
                event.setCurrentItem(null);
                return;
            }
        }
    }

    private @NotNull Map<Integer, ItemStack> takeItems(@NotNull MenuHolder holder) {
        if (holder.getView() != MenuHolder.View.MAIN) {
            return holder.getCarried();
        }
        Inventory inventory = holder.getInventory();
        Map<Integer, ItemStack> items = new HashMap<>();
        for (int slot : holder.getInputSlots()) {
            take(inventory, slot, items);
        }
        if (holder.isResultReady()) {
            take(inventory, holder.getConfig().resultSlot(), items);
        }
        return items;
    }

    private static void take(@NotNull Inventory inventory, int slot, @NotNull Map<Integer, ItemStack> items) {
        ItemStack item = inventory.getItem(slot);
        if (isEmpty(item)) {
            return;
        }
        items.put(slot, item);
        inventory.setItem(slot, null);
    }

    private static void give(@NotNull Player player, @NotNull Collection<ItemStack> items) {
        player.getInventory().addItem(items.toArray(ItemStack[]::new)).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static void play(@NotNull Player player, @Nullable Sound sound) {
        if (sound != null) {
            player.playSound(sound);
        }
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.isEmpty();
    }

    private record Quote(@NotNull MixService.Result result, @NotNull Material material,
                         @NotNull Settings.Level level, double discount,
                         @NotNull List<PriceService.Charge> charges, double chance) {
    }
}
