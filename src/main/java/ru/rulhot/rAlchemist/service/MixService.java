package ru.rulhot.rAlchemist.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionEffectTypeCategory;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.util.MessageUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class MixService {

    public static final Set<Material> POTIONS = Set.of(Material.POTION, Material.SPLASH_POTION,
            Material.LINGERING_POTION);

    public static final String CROSSED_KEY = "crossed";
    public static final String MODIFIED_KEY = "essence-applied";

    private static final Comparator<PotionEffect> STRENGTH = Comparator.comparingInt(PotionEffect::getAmplifier)
            .thenComparingInt(effect -> effect.isInfinite() ? Integer.MAX_VALUE : effect.getDuration());

    private final @NotNull NamespacedKey crossedKey;
    private final @NotNull NamespacedKey modifiedKey;

    public MixService(@NotNull Plugin plugin) {
        this.crossedKey = new NamespacedKey(plugin, CROSSED_KEY);
        this.modifiedKey = new NamespacedKey(plugin, MODIFIED_KEY);
    }

    public @NotNull Result mix(@NotNull List<ItemStack> inputs, @Nullable Color color,
                               @NotNull Settings.Mixing settings) {
        if (inputs.size() < settings.minPotions()) {
            return new Result.Failure("mix.too-few", Map.of("required", settings.minPotions()));
        }
        Material material = inputs.getFirst().getType();
        List<PotionEffect> effects = new ArrayList<>();
        Set<PotionEffectType> seen = new HashSet<>();
        for (ItemStack input : inputs) {
            if (!POTIONS.contains(input.getType()) || input.getAmount() != 1
                    || !(input.getItemMeta() instanceof PotionMeta meta)) {
                return new Result.Failure("mix.not-potion", Map.of());
            }
            if (input.getType() != material) {
                return new Result.Failure("mix.different-types", Map.of());
            }
            if (!settings.allowCrossed() && meta.getPersistentDataContainer().has(crossedKey)) {
                return new Result.Failure("mix.already-crossed", Map.of());
            }
            if (!settings.combineEssences() && meta.getPersistentDataContainer().has(modifiedKey)) {
                return new Result.Failure("mix.essence-potion", Map.of());
            }
            List<PotionEffect> own = effectsOf(meta);
            if (own.isEmpty() || !own.stream().allMatch(MixService::isPositive)) {
                return new Result.Failure("mix.not-positive", Map.of());
            }
            Set<PotionEffectType> types = own.stream().map(PotionEffect::getType).collect(Collectors.toSet());
            if (!settings.allowDuplicates()) {
                for (PotionEffectType type : types) {
                    if (seen.contains(type)) {
                        return new Result.Failure("mix.duplicate-effect",
                                Map.of("effect", Component.translatable(type)));
                    }
                }
            }
            seen.addAll(types);
            effects.addAll(own);
        }
        for (Set<PotionEffectType> group : settings.incompatible()) {
            List<PotionEffectType> clash = seen.stream().filter(group::contains).limit(2).toList();
            if (clash.size() > 1) {
                return new Result.Failure("mix.incompatible", Map.of(
                        "first", Component.translatable(clash.getFirst()),
                        "second", Component.translatable(clash.getLast())));
            }
        }
        Collection<PotionEffect> merged = effects.stream()
                .collect(Collectors.toMap(PotionEffect::getType, Function.identity(), BinaryOperator.maxBy(STRENGTH),
                        LinkedHashMap<PotionEffectType, PotionEffect>::new))
                .values();
        ItemStack potion = potion(material, merged, color, settings.resultName(), settings.resultLore(),
                settings.resultModelData());
        potion.editMeta(meta -> meta.getPersistentDataContainer().set(crossedKey, PersistentDataType.BYTE, (byte) 1));
        return new Result.Success(potion, merged.size());
    }

    public @NotNull ItemStack spoiled(@NotNull Material material, @NotNull Settings.Spoiled settings) {
        return potion(material, settings.effects(), null, settings.name(), settings.lore(), settings.modelData());
    }

    private static @NotNull ItemStack potion(@NotNull Material material, @NotNull Collection<PotionEffect> effects,
                                             @Nullable Color color, @Nullable String name,
                                             @NotNull List<String> lore, int modelData) {
        ItemStack potion = new ItemStack(material);
        potion.editMeta(PotionMeta.class, meta -> {
            effects.forEach(effect -> meta.addCustomEffect(effect, true));
            if (color != null) {
                meta.setColor(color);
            }
            if (modelData > 0) {
                meta.setCustomModelData(modelData);
            }
            if (name != null && !name.isBlank()) {
                meta.displayName(MessageUtil.parseItemText(name));
            }
            if (!lore.isEmpty()) {
                meta.lore(lore.stream().map(line -> MessageUtil.parseItemText(line)).toList());
            }
        });
        return potion;
    }

    private static @NotNull List<PotionEffect> effectsOf(@NotNull PotionMeta meta) {
        List<PotionEffect> effects = new ArrayList<>();
        PotionType base = meta.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        effects.addAll(meta.getCustomEffects());
        return effects;
    }

    private static boolean isPositive(@NotNull PotionEffect effect) {
        return effect.getType().getCategory() == PotionEffectTypeCategory.BENEFICIAL;
    }

    public sealed interface Result {

        record Success(@NotNull ItemStack potion, int effects) implements Result {
        }

        record Failure(@NotNull String messageKey, @NotNull Map<String, ?> placeholders) implements Result {
        }
    }
}
