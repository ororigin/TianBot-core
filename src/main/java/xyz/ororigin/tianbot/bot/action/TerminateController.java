package xyz.ororigin.tianbot.bot.action;

public class TerminateController {
    private volatile boolean terminated = false;

    public void requestTerminate() {
        terminated = true;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public void reset() {
        terminated = false;
    }
}
