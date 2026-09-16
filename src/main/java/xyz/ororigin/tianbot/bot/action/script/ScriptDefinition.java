package xyz.ororigin.tianbot.bot.action.script;

import xyz.ororigin.tianbot.bot.action.Action;

import java.util.List;


public record ScriptDefinition(long loops, List<Action> steps) {

    public static final long INFINITE = -1L;

    public ScriptDefinition {
        steps = List.copyOf(steps);
    }
}
