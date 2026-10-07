package ru.rulhot.rAlchemist.util.logger.impl;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import ru.rulhot.rAlchemist.util.logger.ILogger;

@RequiredArgsConstructor
public final class LegacyLogger implements ILogger {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String DEBUG_PREFIX = "<gray>[debug]</gray> ";

    private final @NotNull ComponentLogger logger;

    @Override
    public void info(@NotNull String message) {
        logger.info(parse(message));
    }

    @Override
    public void warn(@NotNull String message) {
        logger.warn(parse(message));
    }

    @Override
    public void error(@NotNull String message) {
        logger.error(parse(message));
    }

    @Override
    public void error(@NotNull String message, @NotNull Throwable throwable) {
        logger.error(parse(message), throwable);
    }

    @Override
    public void debug(@NotNull String message) {
        logger.info(parse(DEBUG_PREFIX + message));
    }

    private @NotNull Component parse(@NotNull String message) {
        return MINI_MESSAGE.deserialize(message);
    }
}
