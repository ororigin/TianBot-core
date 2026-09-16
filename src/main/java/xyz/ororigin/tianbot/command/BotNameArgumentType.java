package xyz.ororigin.tianbot.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

public final class BotNameArgumentType implements CustomArgumentType<String, String> {

    public static final int MAX_NAME_LENGTH = 16;

    private BotNameArgumentType() {
    }

    public static BotNameArgumentType botName() {
        return new BotNameArgumentType();
    }

    public static String getString(final CommandContext<?> context, final String name) {
        return context.getArgument(name, String.class);
    }

    public static boolean isValidName(final String name) {
        return name != null && !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        final int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}
