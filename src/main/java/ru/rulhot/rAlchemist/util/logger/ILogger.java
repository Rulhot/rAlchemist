package ru.rulhot.rAlchemist.util.logger;

import org.jetbrains.annotations.NotNull;

public interface ILogger {

    void info(@NotNull String message);

    void warn(@NotNull String message);

    void error(@NotNull String message);

    void error(@NotNull String message, @NotNull Throwable throwable);

    void debug(@NotNull String message);
}
