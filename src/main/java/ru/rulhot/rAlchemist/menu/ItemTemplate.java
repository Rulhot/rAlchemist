package ru.rulhot.rAlchemist.menu;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.util.MessageUtil;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ItemTemplate(@NotNull ItemStack base, @Nullable String name, @NotNull List<String> lore) {

    public static @Nullable ItemTemplate parse(@NotNull ConfigurationSection section, @NotNull String context) {
        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);
        if (material == null || material.isAir() || !material.isItem()) {
            Logger.warn(context + ": неизвестный материал '" + materialName + "', пропускаю");
            return null;
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (String flag : section.getStringList("item-flags")) {
            try {
                flags.add(ItemFlag.valueOf(flag.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                Logger.warn(context + ": неизвестный item-flag '" + flag + "', пропускаю");
            }
        }
        boolean glow = section.getBoolean("glow", false);
        int modelData = section.getInt("model-data", 0);
        boolean hideTooltip = section.getBoolean("hide-tooltip", false);
        ItemStack base = new ItemStack(material);
        base.editMeta(meta -> {
            meta.addItemFlags(flags.toArray(ItemFlag[]::new));
            meta.setHideTooltip(hideTooltip);
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
        });
        return new ItemTemplate(base, section.isString("name") ? section.getString("name") : null,
                List.copyOf(section.getStringList("lore")));
    }

    public @NotNull ItemStack render(@NotNull TagResolver resolver, @Nullable Color color) {
        ItemStack item = base.clone();
        item.editMeta(meta -> {
            if (name != null) {
                meta.displayName(MessageUtil.parseItemText(name, resolver));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(line -> MessageUtil.parseItemText(line, resolver)).toList());
            }
            if (color != null && meta instanceof PotionMeta potion) {
                potion.setColor(color);
            }
        });
        return item;
    }
}
