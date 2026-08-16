package xyz.ororigin.tianbot.bot.action;

import io.papermc.paper.threadedregions.RegionizedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xyz.ororigin.tianbot.bot.Bot;
import xyz.ororigin.tianbot.utils.Lang;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class ActionHandler {
    private final Bot bot;
    private final Queue<Action> actionQueue = new LinkedList<>();
    private final LinkedList<TickContainer> tickLinkList = new LinkedList<>();
    private final TerminateController terminateController = new TerminateController();
    private final EnumMap<ActionResource, Action> resourceOwners = new EnumMap<>(ActionResource.class);
    private boolean blockState = false;
    private boolean isHandlingQueue = false;
    private boolean shutdown = false;

    public ActionHandler(Bot bot) {
        this.bot = bot;
    }

    public TerminateController terminateController() {
        return terminateController;
    }

    public boolean isBlocked() {
        return blockState;
    }

    public int activePersistentCount() {
        return tickLinkList.size();
    }

    public CompletableFuture<Void> addAction(Action action) {
        return runOnBotRegion(() -> doAddAction(action));
    }

    public CompletableFuture<Void> blockAddAction(PersistentAction action) {
        return runOnBotRegion(() -> doAddAction(action));
    }


    public CompletableFuture<Void> addSequence(List<Action> steps, long loops) {
        return runOnBotRegion(() -> doAddAction(new SequenceAction(this, steps, loops)));
    }


    void enqueueActions(List<Action> actions) {
        actionQueue.addAll(actions);
    }

    public CompletableFuture<Void> terminate() {
        return runOnBotRegion(this::doTerminate);
    }

    public CompletableFuture<Void> shutdown() {
        return runOnBotRegion(() -> {
            this.shutdown = true;
            doTerminate();
        });
    }


    public CompletableFuture<Void> stopCurrent() {
        return runOnBotRegion(this::doStopCurrent);
    }

    private void doAddAction(Action action) {
        if (shutdown) {
            throw new IllegalStateException(Lang.get("error.handler-closed"));
        }
        ServerPlayer sp = activePlayer();
        if (sp == null) {
            throw new IllegalStateException(Lang.get("error.bot-not-spawned-action"));
        }

        if (action.isPersistent() && !action.isBlocking()) {
            replaceRunningOfSameType((PersistentAction) action);
        }

        actionQueue.offer(action);
        if (!isHandlingQueue && !blockState) {
            handleActionQueue();
        }
    }

    private void replaceRunningOfSameType(PersistentAction fresh) {
        actionQueue.removeIf(a -> a.isPersistent()
                && !a.isBlocking()
                && a.getClass() == fresh.getClass());

        List<TickContainer> toReplace = new ArrayList<>();
        for (TickContainer tc : tickLinkList) {
            if (tc.action().isBlocking()) {
                continue;
            }
            if (tc.action().getClass() == fresh.getClass()) {
                toReplace.add(tc);
            }
        }
        tickLinkList.removeAll(toReplace);
        for (TickContainer tc : toReplace) {
            tc.action().finish(FinishReason.CANCELLED);
        }
    }

    private void handleActionQueue() {
        if (isHandlingQueue) {
            return;
        }
        isHandlingQueue = true;
        try {
            while (!actionQueue.isEmpty()) {
                Action action = actionQueue.poll();
                resolveResources(action);
                if (action.isPersistent()) {
                    PersistentAction pa = (PersistentAction) action;
                    UUID id = UUID.randomUUID();
                    pa.id(id);
                    tickLinkList.add(new TickContainer(pa, id));
                }
                action.exec(activePlayer(), reason -> onActionFinished(action, reason));
                if (action.isBlocking()) {
                    blockState = true;
                    return;
                }
            }
        } finally {
            isHandlingQueue = false;
        }
    }

    private void resolveResources(Action action) {
        for (ActionResource resource : action.releasedResources()) {
            releaseResource(resource);
        }
        if (!action.isBlocking()) {
            for (ActionResource resource : action.occupiedResources()) {
                releaseResource(resource);
            }
        }
        for (ActionResource resource : action.occupiedResources()) {
            resourceOwners.put(resource, action);
        }
    }

    private void releaseResource(ActionResource resource) {
        Action owner = resourceOwners.get(resource);
        if (owner == null) {
            return;
        }
        if (owner instanceof ToggleAction toggle) {
            toggle.release(activePlayer(), reason -> { });
        }
        if (owner instanceof PersistentAction persistent) {
            persistent.finish(FinishReason.CANCELLED);
        }
        resourceOwners.remove(resource);
    }

    private void onActionFinished(Action action, FinishReason reason) {
        if (action.isPersistent()) {
            PersistentAction pa = (PersistentAction) action;
            tickLinkList.removeIf(tc -> Objects.equals(pa.id(), tc.id()));
        }
        if (!(action instanceof ToggleAction)) {
            resourceOwners.entrySet().removeIf(entry -> entry.getValue() == action);
        }
        if (action.isBlocking()) {
            blockState = false;
            if (!actionQueue.isEmpty() && !isHandlingQueue) {
                handleActionQueue();
            }
        }
    }

    public void tick() {
        for (TickContainer tc : new ArrayList<>(tickLinkList)) {
            PersistentAction pa = tc.action();
            if (terminateController.isTerminated()) {
                pa.finish(FinishReason.CANCELLED);
                continue;
            }
            if (pa.hasTimedOut()) {
                pa.finish(FinishReason.TIMEOUT);
                continue;
            }
            tc.runTick();
        }
    }

    private void doTerminate() {
        terminateController.requestTerminate();
        // 复位粘性占用（Sneak/Mount 等一次性 ToggleAction 不在 tickLinkList，需显式 release 复位姿态）
        for (ActionResource resource : ActionResource.values()) {
            Action owner = resourceOwners.get(resource);
            if (owner instanceof ToggleAction toggle) {
                toggle.release(activePlayer(), reason -> { });
            }
        }
        resourceOwners.clear();
        List<TickContainer> snapshot = new ArrayList<>(tickLinkList);
        tickLinkList.clear();
        actionQueue.clear();
        blockState = false;
        isHandlingQueue = false;
        for (TickContainer tc : snapshot) {
            tc.action().finish(FinishReason.CANCELLED);
        }
        terminateController.reset();
    }


    void doStopCurrent() {
        List<TickContainer> toCancel = new ArrayList<>();
        for (TickContainer tc : tickLinkList) {
            if (!tc.action().isBlocking()) {
                toCancel.add(tc);
            }
        }
        tickLinkList.removeAll(toCancel);
        for (TickContainer tc : toCancel) {
            tc.action().finish(FinishReason.CANCELLED);
        }
    }

    private ServerPlayer activePlayer() {
        return bot.serverPlayer;
    }

    private CompletableFuture<Void> runOnBotRegion(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ServerPlayer sp = bot.serverPlayer;
        ServerLevel level = sp != null ? sp.level() : null;
        if (level != null) {
            int chunkX = SectionPos.blockToSectionCoord(sp.getBlockX());
            int chunkZ = SectionPos.blockToSectionCoord(sp.getBlockZ());
            RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(level, chunkX, chunkZ, () -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } else {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }
}
