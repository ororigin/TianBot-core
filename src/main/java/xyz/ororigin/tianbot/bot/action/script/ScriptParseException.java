package xyz.ororigin.tianbot.bot.action.script;


public class ScriptParseException extends RuntimeException {

    public ScriptParseException(String message) {
        super(message);
    }

    public ScriptParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
