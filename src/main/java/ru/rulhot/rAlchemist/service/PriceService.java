package ru.rulhot.rAlchemist.service;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.economy.EconomyProvider;
import ru.rulhot.rAlchemist.economy.EconomyRegistry;
import ru.rulhot.rAlchemist.model.Settings;
import ru.rulhot.rAlchemist.util.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PriceService {

    private static final String DISCOUNT_PERMISSION = "ralchemist.discount.";
    private static final double MAX_DISCOUNT = 100.0D;
    private static final double CENTS = 100.0D;

    private volatile @NotNull List<Payment> payments = List.of();

    public boolean reload(@NotNull List<Settings.Cost> costs, @NotNull Server server) {
        List<Payment> created = new ArrayList<>();
        for (Settings.Cost cost : costs) {
            EconomyProvider provider = EconomyRegistry.create(cost, server);
            if (provider == null) {
                return false;
            }
            created.add(new Payment(cost, provider));
        }
        payments = List.copyOf(created);
        return true;
    }

    public double discount(@NotNull Player player, double levelDiscount) {
        double best = 0.0D;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!info.getValue() || !permission.startsWith(DISCOUNT_PERMISSION)) {
                continue;
            }
            try {
                best = Math.max(best, Double.parseDouble(permission.substring(DISCOUNT_PERMISSION.length())));
            } catch (NumberFormatException exception) {
                Logger.debug("Право " + permission + " не содержит числа скидки");
            }
        }
        return Math.clamp(best + levelDiscount, 0.0D, MAX_DISCOUNT);
    }

    public @NotNull List<Charge> quote(int potions, int effects, double discountPercent) {
        List<Charge> charges = new ArrayList<>();
        for (Payment payment : payments) {
            double raw = payment.cost().amount(potions, effects, discountPercent);
            double amount = payment.provider().normalize(Math.round(raw * CENTS) / CENTS);
            if (amount > 0.0D) {
                charges.add(new Charge(payment, amount));
            }
        }
        return charges;
    }

    public boolean has(@NotNull Player player, @NotNull List<Charge> charges) {
        return charges.stream().allMatch(charge -> charge.payment().provider().has(player, charge.amount()));
    }

    public boolean withdraw(@NotNull Player player, @NotNull List<Charge> charges) {
        List<Charge> taken = new ArrayList<>();
        for (Charge charge : charges) {
            EconomyProvider provider = charge.payment().provider();
            if (provider.withdraw(player, charge.amount())) {
                taken.add(charge);
                continue;
            }
            Logger.warn("Экономика " + provider.name() + " отклонила списание: player=" + player.getName()
                    + ", amount=" + charge.amount() + ", уже списанное возвращено");
            taken.forEach(done -> done.payment().provider().deposit(player, done.amount()));
            return false;
        }
        return true;
    }

    public void refund(@NotNull Player player, @NotNull List<Charge> charges) {
        charges.forEach(charge -> charge.payment().provider().deposit(player, charge.amount()));
    }

    public record Payment(@NotNull Settings.Cost cost, @NotNull EconomyProvider provider) {
    }

    public record Charge(@NotNull Payment payment, double amount) {
    }
}
