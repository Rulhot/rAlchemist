package ru.rulhot.rAlchemist.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class MessageUtil {

    private final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)}");

    public @NotNull Component parseText(@NotNull String raw, @NotNull TagResolver... resolvers) {
        TagResolver resolver = TagResolver.resolver(resolvers);
        String text = PLACEHOLDER.matcher(raw).replaceAll(match -> Matcher.quoteReplacement(
                resolver.has(match.group(1)) ? "<" + match.group(1) + ">" : match.group()));
        return MINI_MESSAGE.deserialize(text, resolver);
    }

    public @NotNull Component parseItemText(@NotNull String raw, @NotNull TagResolver... resolvers) {
        return parseText(raw, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public @NotNull TagResolver placeholders(@NotNull Map<String, ?> values) {
        TagResolver.Builder builder = TagResolver.builder();
        values.forEach((name, value) -> builder.resolver(value instanceof ComponentLike component
                ? Placeholder.component(name, component)
                : Placeholder.unparsed(name, String.valueOf(value))));
        return builder.build();
    }
}
