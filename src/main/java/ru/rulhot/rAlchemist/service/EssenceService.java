package ru.rulhot.rAlchemist.service;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import io.papermc.paper.persistence.PersistentDataContainerView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.manager.ConfigManager;
import ru.rulhot.rAlchemist.model.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

public final class EssenceService {

    private static final int TICKS_PER_MINUTE = 20 * 60;
    private static final int LINGERING_DURATION_DIVIDER = 4;
    private static final Color WATER_COLOR = Color.fromRGB(0x385DC6);
    private static final String WATER_NAME = "water";
    private static final List<String> POTION_NAME_PREFIXES = List.of("long_", "strong_");

    private final @NotNull NamespacedKey legacyKey;
    private final @NotNull NamespacedKey typeKey;
    private final @NotNull NamespacedKey crossedKey;
    private final @NotNull NamespacedKey modifiedKey;
    private final @NotNull ConfigManager config;

    public EssenceService(@NotNull Plugin plugin, @NotNull ConfigManager config) {
        this.legacyKey = new NamespacedKey(plugin, "essence");
        this.typeKey = new NamespacedKey(plugin, "essence-type");
        this.crossedKey = new NamespacedKey(plugin, MixService.CROSSED_KEY);
        this.modifiedKey = new NamespacedKey(plugin, MixService.MODIFIED_KEY);
        this.config = config;
    }

    public @NotNull Settings.Essence settings(@NotNull Type type) {
        return switch (type) {
            case TIME -> config.settings().timeEssence();
            case POWER -> config.settings().powerEssence();
            case STEALTH -> config.settings().stealthEssence();
            case MYSTERY -> config.settings().mysteryEssence();
            case ETERNITY -> config.settings().eternityEssence();
        };
    }

    public @Nullable ItemStack create(@NotNull Type type, int amount) {
        Settings.Essence settings = settings(type);
        if (settings.item() == null) {
            return null;
        }
        String value = String.valueOf(settings.value());
        ItemStack item = settings.item().render(TagResolver.resolver(
                Placeholder.unparsed("minutes", value), Placeholder.unparsed("max", value)), null);
        item.setAmount(amount);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id()));
        return item;
    }

    public @Nullable Type typeOf(@Nullable ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        PersistentDataContainerView data = item.getPersistentDataContainer();
        String id = data.get(typeKey, PersistentDataType.STRING);
        if (id == null) {
            return data.has(legacyKey) ? Type.TIME : null;
        }
        return Type.find(id);
    }

    public @Nullable ItemStack apply(@NotNull Type type, @NotNull ItemStack potion) {
        if (!config.settings().mixing().combineEssences() && potion.getPersistentDataContainer().has(crossedKey)) {
            return null;
        }
        ItemStack result = transform(type, potion);
        if (result != null) {
            result.editMeta(meta -> meta.getPersistentDataContainer().set(modifiedKey, PersistentDataType.BYTE, (byte) 1));
        }
        return result;
    }

    private @Nullable ItemStack transform(@NotNull Type type, @NotNull ItemStack potion) {
        Settings.Essence settings = settings(type);
        int value = settings.value();
        return switch (type) {
            case STEALTH -> rewrite(potion, settings.requiredEffects(), effect -> effect.withParticles(false));
            case MYSTERY -> hideEffects(potion);
            case ETERNITY -> rewrite(potion, Set.of(), effect -> isTimed(effect)
                    && effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL
                    ? effect.withDuration(PotionEffect.INFINITE_DURATION)
                    : effect);
            case POWER -> rewrite(potion, Set.of(), effect -> effect.getAmplifier() < value - 1
                    && !settings.ignoredEffects().contains(effect.getType())
                    ? effect.withAmplifier(effect.getAmplifier() + 1)
                    : effect);
            case TIME -> {
                int target = value * TICKS_PER_MINUTE
                        * (potion.getType() == Material.LINGERING_POTION ? LINGERING_DURATION_DIVIDER : 1);
                yield rewrite(potion, Set.of(), effect -> isTimed(effect) && effect.getDuration() < target
                        ? effect.withDuration(target)
                        : effect);
            }
        };
    }

    private static boolean isTimed(@NotNull PotionEffect effect) {
        return !effect.getType().isInstant() && !effect.isInfinite();
    }

    private static boolean isSinglePotion(@NotNull ItemStack item) {
        return MixService.POTIONS.contains(item.getType()) && item.getAmount() == 1;
    }

    private @Nullable ItemStack hideEffects(@NotNull ItemStack potion) {
        if (!isSinglePotion(potion) || potion.hasItemFlag(ItemFlag.HIDE_ADDITIONAL_TOOLTIP)) {
            return null;
        }
        ItemStack result = potion.clone();
        result.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        result.editMeta(PotionMeta.class, meta -> {
            meta.setColor(WATER_COLOR);
            meta.setCustomPotionName(WATER_NAME);
        });
        return result;
    }

    private @Nullable ItemStack rewrite(@NotNull ItemStack potion, @NotNull Set<PotionEffectType> required,
                                         @NotNull UnaryOperator<PotionEffect> change) {
        if (!isSinglePotion(potion) || !(potion.getItemMeta() instanceof PotionMeta source)) {
            return null;
        }
        PotionType base = source.getBasePotionType();
        List<PotionEffect> effects = new ArrayList<>();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        effects.addAll(source.getCustomEffects());
        if (!required.isEmpty() && effects.stream().noneMatch(effect -> required.contains(effect.getType()))) {
            return null;
        }

        List<PotionEffect> updated = effects.stream().map(change).toList();
        if (updated.equals(effects)) {
            return null;
        }
        ItemStack result = potion.clone();
        result.editMeta(PotionMeta.class, meta -> {
            if (base != null && !meta.hasCustomPotionName()) {
                meta.setCustomPotionName(potionName(base));
            }
            if (!meta.hasColor()) {
                meta.setColor(mixedColor(effects));
            }
            meta.setBasePotionType(null);
            meta.clearCustomEffects();
            updated.forEach(effect -> meta.addCustomEffect(effect, true));
        });
        return result;
    }

    private static @NotNull Color mixedColor(@NotNull List<PotionEffect> effects) {
        int red = 0;
        int green = 0;
        int blue = 0;
        int weight = 0;
        for (PotionEffect effect : effects) {
            Color color = effect.getType().getColor();
            int level = effect.getAmplifier() + 1;
            red += color.getRed() * level;
            green += color.getGreen() * level;
            blue += color.getBlue() * level;
            weight += level;
        }
        return weight == 0 ? WATER_COLOR : Color.fromRGB(red / weight, green / weight, blue / weight);
    }

    private static @NotNull String potionName(@NotNull PotionType type) {
        String name = type.getKey().getKey();
        for (String prefix : POTION_NAME_PREFIXES) {
            if (name.startsWith(prefix)) {
                return name.substring(prefix.length());
            }
        }
        return name;
    }

    public enum Type {
        TIME,
        POWER,
        STEALTH,
        MYSTERY,
        ETERNITY;

        public @NotNull String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Type find(@NotNull String id) {
            for (Type type : values()) {
                if (type.id().equalsIgnoreCase(id)) {
                    return type;
                }
            }
            return null;
        }
    }
}
