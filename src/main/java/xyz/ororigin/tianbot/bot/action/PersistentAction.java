package xyz.ororigin.tianbot.bot.action;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.function.Consumer;

public class PersistentAction extends Action {
    public enum State { INIT, RUNNING, FINISHED }

    private final boolean block;
    private long timeoutMs;

    private UUID id;
    private boolean isActive = false;
    private State state = State.INIT;
    private ServerPlayer player;
    private long deadline = Long.MAX_VALUE;
    private Consumer<FinishReason> onFinished;

    public PersistentAction() {
        this(false, 0);
    }

    public PersistentAction(boolean block, long timeoutMs) {
        this.block = block;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public final boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isBlocking() {
        return block;
    }

    @Override
    public final void exec(ServerPlayer player, Consumer<FinishReason> onFinished) {
        this.player = player;
        this.onFinished = onFinished;
        this.deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        this.isActive = true;
        this.state = State.RUNNING;
        onStart();
    }

    public void setTimeoutMs(long timeoutMs){
        this.timeoutMs=timeoutMs;
    }

    public ServerPlayer player() {
        return player;
    }

    public UUID id() {
        return id;
    }

    public void id(UUID id) {
        this.id = id;
    }

    public boolean isActive() {
        return isActive;
    }

    public State state() {
        return state;
    }

    public boolean hasTimedOut() {
        return timeoutMs > 0 && System.currentTimeMillis() >= deadline;
    }

    protected void onStart() {
    }

    protected void onTick() {
    }

    protected void onFinish(FinishReason reason) {
    }

    public void tick() {
        if (!isActive || state == State.FINISHED) {
            return;
        }
        onTick();
    }

    public final void finish(FinishReason reason) {
        if (!isActive || state == State.FINISHED) {
            return;
        }
        isActive = false;
        state = State.FINISHED;
        try {
            onFinish(reason);
        } finally {
            Consumer<FinishReason> cb = onFinished;
            onFinished = null;
            if (cb != null) {
                cb.accept(reason);
            }
        }
    }
}
