package xyz.ororigin.tianbot.bot;

import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.player.ChunkSystemServerPlayer;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkHolderManager;
import com.mojang.authlib.GameProfile;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ororigin.tianbot.TianBotPlugin;
import xyz.ororigin.tianbot.api.BotHandle;
import xyz.ororigin.tianbot.bot.action.ActionHandler;
import xyz.ororigin.tianbot.bot.action.script.ScriptParser;
import xyz.ororigin.tianbot.bot.action.module.AttackAction;
import xyz.ororigin.tianbot.bot.action.module.ChatAction;
import xyz.ororigin.tianbot.bot.action.module.DismountAction;
import xyz.ororigin.tianbot.bot.action.module.DropAction;
import xyz.ororigin.tianbot.bot.action.module.JumpAction;
import xyz.ororigin.tianbot.bot.action.module.LookAction;
import xyz.ororigin.tianbot.bot.action.module.MountAction;
import xyz.ororigin.tianbot.bot.action.module.MoveAction;
import xyz.ororigin.tianbot.bot.action.module.WaitAction;
import xyz.ororigin.tianbot.bot.action.module.SneakAction;
import xyz.ororigin.tianbot.bot.action.module.SprintAction;
import xyz.ororigin.tianbot.bot.fakes.FakeConnection;
import xyz.ororigin.tianbot.bot.fakes.FakeServerGamePacketListenerImpl;
import xyz.ororigin.tianbot.utils.AuthMeCompat;
import xyz.ororigin.tianbot.utils.Lang;
import xyz.ororigin.tianbot.bot.action.module.SwapHandAction;
import xyz.ororigin.tianbot.bot.action.module.UnSneakAction;
import xyz.ororigin.tianbot.bot.action.module.UnSprintAction;
import xyz.ororigin.tianbot.bot.action.module.UseAction;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class Bot implements BotHandle {
    public String botName;
    public GameProfile gameProfile;
    public MinecraftServer minecraftServer = ((CraftServer) Bukkit.getServer()).getServer();
    public ClientInformation cookie = ClientInformation.createDefault();
    public ServerPlayer serverPlayer;
    public NameAndId nameAndId;
    public FakeConnection fakeConnection;
    public BotConfig config;
    public ServerPlayer.SavedPosition savedPosition;
    public BotSpawnState spawnState = BotSpawnState.NONE;
    public ChunkPos spawnChunkPos;
    public ScheduledTask tickTask;
    public final ActionHandler actions = new ActionHandler(this);
    private boolean firstPhysicsTick = true;
    public boolean chunkLoaderActive = false;
    public int loaderGraceTicks = 20;
    public boolean visible;
    public boolean worldAdded = false;
    public long spawnedAtMillis = -1L;

    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

    public Bot(@NotNull BotConfig config) {
        this.botName = config.getBotName();
        this.config=config;
        this.visible = !config.isGhost();
    }

    //[工具方法]用于获取主维度
    public static @NotNull World getMainWorld() {
        return Optional.ofNullable(Bukkit.getWorld("world"))
                .orElseGet(() -> Bukkit.getWorlds().get(0));
    }

    /**阶段1：生成假人实例，加载生成点区块，移交至对应线程**/
    public CompletableFuture<Void> prepare() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if(spawnState==BotSpawnState.NONE){
            //主线程中处理
            RegionizedServer.getInstance().addTask(() -> {
                try {
                    this.gameProfile = new GameProfile(getUUID(botName), botName);
                    this.nameAndId = new NameAndId(gameProfile);
                    // 获取假人数据
                    Optional<ValueInput> playerData = this.minecraftServer.getPlayerList()
                            .loadPlayerData(nameAndId)
                            .map(tag -> TagValueInput.create(ProblemReporter.DISCARDING, minecraftServer.registryAccess(), tag));
                    // 读取假人上次下线所在位置
                    savedPosition = playerData
                            .flatMap(data -> data.read(ServerPlayer.SavedPosition.MAP_CODEC))
                            .orElse(ServerPlayer.SavedPosition.EMPTY);
                    // 新假人生成出生点坐标
                    if (savedPosition.position().isEmpty()) {
                        LevelData.RespawnData respawnData = this.minecraftServer.getWorldData().overworldData().getRespawnData();
                        savedPosition = new ServerPlayer.SavedPosition(
                                Optional.of(respawnData.dimension()),
                                Optional.of(Vec3.atBottomCenterOf(respawnData.pos())),
                                Optional.of(new Vec2(respawnData.yaw(), respawnData.pitch()))
                        );
                    }
                    // 获取生成点维度
                    ResourceKey levelResourceKey = savedPosition.dimension().orElse(null);
                    ServerLevel spawnLevel;
                    if (levelResourceKey != null) {
                        spawnLevel = this.minecraftServer.getLevel(levelResourceKey);
                    } else {
                        spawnLevel = ((CraftWorld) getMainWorld()).getHandle();
                    }
                    if (spawnLevel == null) {
                        future.completeExceptionally(new IllegalStateException(Lang.get("error.spawn-no-dimension")));
                        return;
                    }
                    // 用来存储玩家信息的数据结构的实例
                    this.serverPlayer = new ServerPlayer(minecraftServer, spawnLevel, gameProfile, cookie);
                    this.fakeConnection = new FakeConnection(new InetSocketAddress(config.getAddress(),config.getPort()),config.getHost());
                    FakeServerGamePacketListenerImpl listener = new FakeServerGamePacketListenerImpl(
                            minecraftServer, this.fakeConnection, this.serverPlayer,
                            CommonListenerCookie.createInitial(gameProfile, false)
                    );
                    // 绑定所属假人：disconnect（kick）需要回调 Bot 执行真正踢出
                    listener.bot = this;
                    this.serverPlayer.connection = listener;
                    // 生成点所在区块坐标
                    SectionPos spawnChunkPos = SectionPos.of(savedPosition.position().orElse(new Vec3(0, 0, 0)));
                    this.spawnChunkPos = new ChunkPos(spawnChunkPos.getX(), spawnChunkPos.getZ());
                    // 加载假人生成点区块并将任务移交给对应区块线程
                    loadChunkEntityTickingAsync(spawnLevel, spawnChunkPos.getX(), spawnChunkPos.getZ()).whenComplete((chunk, e) -> {
                        if (e != null) {
                            future.completeExceptionally(e);
                            return;
                        }
                        if (chunk == null) {
                            future.completeExceptionally(new IllegalStateException(Lang.get("error.spawn-no-chunk")));
                            return;
                        }
                        try {
                            // 给生成点区块添加 PLAYER_SPAWN 加载票
                            ((ChunkSystemServerLevel) spawnLevel).moonrise$getChunkTaskScheduler().chunkHolderManager.addTicketAtLevel(
                                    TicketType.PLAYER_SPAWN,
                                    new ChunkPos(spawnChunkPos.getX(), spawnChunkPos.getZ()), ChunkHolderManager.ENTITY_TICKING_TICKET_LEVEL,
                                    null
                            );
                            // 剩下的事情移交给生成点区块的 tick 线程处理，至此准备完成~
                            RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                                    spawnLevel, spawnChunkPos.getX(),
                                    spawnChunkPos.getZ(),
                                    () -> {
                                        this.spawnState=BotSpawnState.PREPARE;
                                        future.complete(null);
                                    }
                            );
                        } catch (Throwable e2) {
                            future.completeExceptionally(e2);
                        }
                    });
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        }
        return future;
    }

    /**阶段2：载入假人数据，设置假人位置**/
    public CompletableFuture<Void> ready(){
        CompletableFuture<Void> future = new CompletableFuture<>();
        if(spawnState==BotSpawnState.PREPARE){
            // 获取假人数据
            Optional<ValueInput> playerData = this.minecraftServer.getPlayerList()
                    .loadPlayerData(nameAndId)
                    .map(tag -> TagValueInput.create(ProblemReporter.DISCARDING, minecraftServer.registryAccess(), tag));
            //为假人实例加载数据
            playerData.ifPresent(serverPlayer::load);
            if(playerData.isEmpty()){
                //(paper)新玩家生成原因写入
                serverPlayer.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
            }
            //设置假人位置
            Vec2 spawnAngle=savedPosition.rotation().orElse(new Vec2(0,0));
            serverPlayer.snapTo(savedPosition.position().orElse(new Vec3(0,0,0)),spawnAngle.x,spawnAngle.y);
            //放置假人至世界，这交给下一阶段处理
            this.spawnState=BotSpawnState.READY;
            future.complete(null);
            //放置完成后处理末影珍珠和载具
            if(playerData.isPresent()){
                ValueInput tag = playerData.orElse(null);
                if(tag!=null){
                    serverPlayer.loadAndSpawnEnderPearls(tag);
                    serverPlayer.loadAndSpawnParentVehicle(tag);
                }
            }
        }
        return future;
    }

    /**阶段3：放置假人至世界**/
    public CompletableFuture<Void> spawn() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (spawnState == BotSpawnState.READY) {
            try {
                // 登录状态标记
                serverPlayer.isRealPlayer = true;
                serverPlayer.loginTime = System.currentTimeMillis();
                PlayerList playerList = minecraftServer.getPlayerList();
                // 注册名字uuid对应表
                this.minecraftServer.services().nameToIdCache().add(nameAndId);
                // 构建加入消息
                MutableComponent component = Component.translatable("multiplayer.player.joined", serverPlayer.getDisplayName());
                component.withStyle(ChatFormatting.YELLOW);
                // 注册进玩家索引表（仅 visible 假人；ghost 不进注册表 -> /list、RCON、Tab 均不可见）
                if (this.visible) {
                    playerList.getPlayers().add(serverPlayer);
                    playerList.getPlayersByUUID().put(serverPlayer.getUUID(), serverPlayer);
                    // 这个表只能通过反射注册
                    try {
                        Field byName = PlayerList.class.getDeclaredField("playersByName");
                        byName.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<String, ServerPlayer> playersByName = (Map<String, ServerPlayer>) byName.get(playerList);
                        playersByName.put(
                                serverPlayer.getScoreboardName().toLowerCase(Locale.ROOT),
                                serverPlayer
                        );
                    } catch (ReflectiveOperationException e) {
                        TianBotPlugin.instance.getLogger().severe(Lang.get("log.bot-name-register-failed"));
                        TianBotPlugin.instance.getLogger().severe(Lang.t("log.bot-name-register-failed-detail", "reason", e));
                    }
                }
                // 抑制实体跟踪
                serverPlayer.suppressTrackerForLogin = true;
                ServerLevel level = serverPlayer.level();
                // 加入世界实体列表
                level.addNewPlayer(serverPlayer);
                this.worldAdded = true;
                minecraftServer.getCustomBossEvents().onPlayerConnect(serverPlayer);
                // 初始化背包界面
                serverPlayer.initInventoryMenu();
                //初始设置
                serverPlayer.setHealth(20);
                // 广播加入事件
                org.bukkit.craftbukkit.entity.CraftPlayer bukkitPlayer = serverPlayer.getBukkitEntity();
                org.bukkit.event.player.PlayerJoinEvent playerJoinEvent = new org.bukkit.event.player.PlayerJoinEvent(
                        bukkitPlayer,
                        PaperAdventure.asAdventure(component)
                );
                ((CraftServer) Bukkit.getServer()).getPluginManager().callEvent(playerJoinEvent);
                // AuthMe 兼容：强制跳过 AuthMe 登录/注册流程（config authme.enabled 开关，未装 AuthMe 自动忽略）
                AuthMeCompat.forceSkipLogin(bukkitPlayer);
                // 广播加入消息（ghost 不广播）
                final net.kyori.adventure.text.Component jm = playerJoinEvent.joinMessage();
                if (this.visible && jm != null && !jm.equals(net.kyori.adventure.text.Component.empty())) {
                    playerList.broadcastSystemMessage(PaperAdventure.asVanilla(jm), false);
                }
                ClientboundPlayerInfoUpdatePacket packet = this.visible
                        ? ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(serverPlayer))
                        : new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, serverPlayer);
                for (ServerPlayer other : playerList.getPlayers()) {
                    if (other == serverPlayer) continue;
                    if (other.getBukkitEntity().canSee(bukkitPlayer)) {
                        other.connection.send(packet);
                    }
                }
                // 恢复实体跟踪
                serverPlayer.sentListPacket = true;
                serverPlayer.suppressTrackerForLogin = false;
                serverPlayer.level().getChunkSource().addEntity(serverPlayer);
                this.chunkLoaderActive = ((ChunkSystemServerPlayer) serverPlayer).moonrise$getChunkLoader() != null;
                if (this.chunkLoaderActive) {
                    LOGGER.info("{}[BOT] player chunk loader active, view distance {}",
                            serverPlayer.getPlainTextName(),
                            RegionizedPlayerChunkLoader.getAPIViewDistance(serverPlayer));
                }
                // 服务端日志
                minecraftServer.notificationManager().playerJoined(serverPlayer);
                LOGGER.info("{}[BOT] logged in with entity id {} at ([{}]{}, {}, {})",
                        serverPlayer.getPlainTextName(),
                        serverPlayer.getId(),
                        level.dimension().identifier(),
                        serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ());
                this.spawnedAtMillis = System.currentTimeMillis();
                this.spawnState = BotSpawnState.SPAWNED;
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    public CompletableFuture<Void> spawnBot() {
        return this.prepare()
                .thenCompose(unused -> this.ready())
                .thenCompose(unused -> this.spawn())
                .thenCompose(unused -> this.start());
    }

    //加载区块并移交区块线程方法的CompletableFuture封装
    public CompletableFuture<LevelChunk> loadChunkEntityTickingAsync(ServerLevel level, int chunkX, int chunkZ) {
        CompletableFuture<LevelChunk> future = new CompletableFuture<>();
        level.moonrise$getChunkTaskScheduler().scheduleTickingState(
                chunkX, chunkZ,
                net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING,
                true,
                ca.spottedleaf.concurrentutil.util.Priority.HIGHER,
                future::complete
        );
        return future.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS);
    }

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (spawnState == BotSpawnState.SPAWNED && serverPlayer != null && !serverPlayer.isRemoved()) {
            try {
                // 实体调度器：每 tick 在 bot 所在区域线程调用，自动跟随跨区块，实体移除自动 retired
                this.tickTask = serverPlayer.getBukkitEntity().getScheduler().runAtFixedRate(
                        TianBotPlugin.instance,
                        this::tickPhysics,
                        this::onTickRetired,
                        1,
                        1
                );
                if (this.tickTask == null) {
                    future.completeExceptionally(new IllegalStateException(Lang.get("error.tick-entity-removed")));
                    return future;
                }
                this.firstPhysicsTick = true;
                this.spawnState = BotSpawnState.TICKING;
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        } else {
            future.completeExceptionally(new IllegalStateException(Lang.t("error.tick-state", "state", spawnState)));
        }
        return future;
    }


    private void tickPhysics(ScheduledTask task) {
        if (serverPlayer == null || serverPlayer.isRemoved() || serverPlayer.isDeadOrDying()) {
            if (serverPlayer != null) {
                LOGGER.info("{}[BOT] bot dead or removed, logging out", serverPlayer.getPlainTextName());
            }
            this.logout();
            return;
        }
        // 动作先于物理（模拟carpet假人行为）
        this.actions.tick();
        if (this.firstPhysicsTick) {
            double x = serverPlayer.getX();
            double y = serverPlayer.getY();
            double z = serverPlayer.getZ();
            float yRot = serverPlayer.getYRot();
            float xRot = serverPlayer.getXRot();
            serverPlayer.xo = x;
            serverPlayer.yo = y;
            serverPlayer.zo = z;
            serverPlayer.doTick();
            serverPlayer.absSnapTo(x, y, z, yRot, xRot);
            this.firstPhysicsTick = false;
        } else {
            serverPlayer.doTick();
        }
        if (this.chunkLoaderActive && this.spawnChunkPos != null && --this.loaderGraceTicks <= 0) {
            this.removeSpawnChunkTicket();
        }
    }

    //移除出生点 PLAYER_SPAWN 加载票
    private void removeSpawnChunkTicket() {
        if (this.spawnChunkPos == null) {
            return;
        }
        try {
            ((ChunkSystemServerLevel) this.serverPlayer.level()).moonrise$getChunkTaskScheduler().chunkHolderManager.removeTicketAtLevel(
                    TicketType.PLAYER_SPAWN, this.spawnChunkPos, ChunkHolderManager.ENTITY_TICKING_TICKET_LEVEL, null
            );
        } finally {
            this.spawnChunkPos = null;
        }
    }

    private void onTickRetired() {
        this.tickTask = null;
        if (this.spawnState != BotSpawnState.REMOVED) {
            this.logout();
        }
        this.spawnState = BotSpawnState.REMOVED;
    }

    public UUID getUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    // ==================== BotHandle：状态查询 ====================

    @Override
    public String name() {
        return botName;
    }

    @Override
    public UUID uuid() {
        return getUUID(botName);
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public boolean isSpawned() {
        return spawnState == BotSpawnState.SPAWNED || spawnState == BotSpawnState.TICKING;
    }

    // ==================== BotHandle：动作便捷方法（异步，转发至 ActionHandler） ====================

    @Override
    public CompletableFuture<Void> attack() {
        return actions.addAction(AttackAction.once());
    }

    @Override
    public CompletableFuture<Void> attackContinuous() {
        return actions.addAction(AttackAction.continuous());
    }

    @Override
    public CompletableFuture<Void> attackInterval(int ticks) {
        return actions.addAction(AttackAction.interval(ticks));
    }

    @Override
    public CompletableFuture<Void> use() {
        return actions.addAction(UseAction.once());
    }

    @Override
    public CompletableFuture<Void> useContinuous() {
        return actions.addAction(UseAction.continuous());
    }

    @Override
    public CompletableFuture<Void> useInterval(int ticks) {
        return actions.addAction(UseAction.interval(ticks));
    }

    @Override
    public CompletableFuture<Void> dropItem() {
        return actions.addAction(DropAction.item());
    }

    @Override
    public CompletableFuture<Void> dropStack() {
        return actions.addAction(DropAction.stack());
    }

    @Override
    public CompletableFuture<Void> dropItemContinuous() {
        return actions.addAction(DropAction.itemContinuous());
    }

    @Override
    public CompletableFuture<Void> dropStackContinuous() {
        return actions.addAction(DropAction.stackContinuous());
    }

    @Override
    public CompletableFuture<Void> dropItemInterval(int ticks) {
        return actions.addAction(DropAction.itemInterval(ticks));
    }

    @Override
    public CompletableFuture<Void> dropStackInterval(int ticks) {
        return actions.addAction(DropAction.stackInterval(ticks));
    }

    @Override
    public CompletableFuture<Void> jump() {
        return actions.addAction(JumpAction.once());
    }

    @Override
    public CompletableFuture<Void> jumpContinuous() {
        return actions.addAction(JumpAction.continuous());
    }

    @Override
    public CompletableFuture<Void> jumpInterval(int ticks) {
        return actions.addAction(JumpAction.interval(ticks));
    }

    @Override
    public CompletableFuture<Void> swapHand() {
        return actions.addAction(SwapHandAction.once());
    }

    @Override
    public CompletableFuture<Void> swapHandContinuous() {
        return actions.addAction(SwapHandAction.continuous());
    }

    @Override
    public CompletableFuture<Void> swapHandInterval(int ticks) {
        return actions.addAction(SwapHandAction.interval(ticks));
    }

    @Override
    public CompletableFuture<Void> sneak() {
        return actions.addAction(SneakAction.once());
    }

    @Override
    public CompletableFuture<Void> unsneak() {
        return actions.addAction(UnSneakAction.once());
    }

    @Override
    public CompletableFuture<Void> sprint() {
        return actions.addAction(SprintAction.once());
    }

    @Override
    public CompletableFuture<Void> unsprint() {
        return actions.addAction(UnSprintAction.once());
    }

    @Override
    public CompletableFuture<Void> mount() {
        return actions.addAction(MountAction.rideable());
    }

    @Override
    public CompletableFuture<Void> mountAny() {
        return actions.addAction(MountAction.any());
    }

    @Override
    public CompletableFuture<Void> dismount() {
        return actions.addAction(DismountAction.once());
    }

    @Override
    public CompletableFuture<Void> lookNorth() {
        return actions.addAction(LookAction.north());
    }

    @Override
    public CompletableFuture<Void> lookSouth() {
        return actions.addAction(LookAction.south());
    }

    @Override
    public CompletableFuture<Void> lookEast() {
        return actions.addAction(LookAction.east());
    }

    @Override
    public CompletableFuture<Void> lookWest() {
        return actions.addAction(LookAction.west());
    }

    @Override
    public CompletableFuture<Void> lookUp() {
        return actions.addAction(LookAction.up());
    }

    @Override
    public CompletableFuture<Void> lookDown() {
        return actions.addAction(LookAction.down());
    }

    @Override
    public CompletableFuture<Void> lookAt(double x, double y, double z) {
        return actions.addAction(LookAction.lookAt(new Vec3(x, y, z)));
    }

    @Override
    public CompletableFuture<Void> look(float yaw, float pitch) {
        return actions.addAction(LookAction.rotation(yaw, pitch));
    }

    @Override
    public CompletableFuture<Void> turn(float deltaYaw, float deltaPitch) {
        return actions.addAction(LookAction.turn(deltaYaw, deltaPitch));
    }

    @Override
    public CompletableFuture<Void> moveForward() {
        return actions.addAction(MoveAction.forward());
    }

    @Override
    public CompletableFuture<Void> moveBackward() {
        return actions.addAction(MoveAction.backward());
    }

    @Override
    public CompletableFuture<Void> moveLeft() {
        return actions.addAction(MoveAction.left());
    }

    @Override
    public CompletableFuture<Void> moveRight() {
        return actions.addAction(MoveAction.right());
    }

    @Override
    public CompletableFuture<Void> moveVector(float forward, float strafing) {
        return actions.addAction(MoveAction.vector(forward, strafing));
    }

    @Override
    public CompletableFuture<Void> stopMoving() {
        return actions.addAction(MoveAction.stop());
    }

    @Override
    public CompletableFuture<Void> sleep(int ticks) {
        // blockAddAction：sleep 是阻塞动作，靠 isBlocking() 暂停队列（功能等同 addAction，仅表达意图）
        return actions.blockAddAction(WaitAction.of(ticks));
    }

    @Override
    public CompletableFuture<Void> stopActions() {
        return actions.terminate();
    }

    @Override
    public CompletableFuture<Void> stopCurrentActions() {
        return actions.stopCurrent();
    }

    @Override
    public CompletableFuture<Void> say(String content) {
        return actions.addAction(ChatAction.say(content));
    }

    @Override
    public CompletableFuture<Void> say(String content, @Nullable CommandSender feedbackTo) {
        return actions.addAction(ChatAction.say(content, feedbackSourceOf(feedbackTo)));
    }

    /** 把 Bukkit CommandSender 包成 NMS CommandSource：命令反馈（sendSuccess/sendFailure）转发给发送者。 */
    @Nullable
    private CommandSource feedbackSourceOf(@Nullable CommandSender feedbackTo) {
        if (feedbackTo == null) {
            return null;
        }
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                feedbackTo.sendMessage(PaperAdventure.asAdventure(message));
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return true;
            }

            @Override
            public CommandSender getBukkitSender(CommandSourceStack wrapper) {
                return feedbackTo;
            }
        };
    }

    @Override
    public CompletableFuture<Void> runScript(String json) {
        return ScriptParser.parseAndRun(actions, json);
    }

    /**假人下线方法**/
    public void logout() {
        this.logout(null);
    }

    public void logout(net.kyori.adventure.text.Component leaveMessage) {
        // 终止当前假人的所有行为
        this.actions.terminate();
        if (this.serverPlayer == null) {
            this.spawnState = BotSpawnState.REMOVED;
            return;
        }
        if (this.spawnState == BotSpawnState.REMOVED) {
            return;
        }
        ServerLevel level = this.serverPlayer.level();
        if (level == null) {
            this.spawnState = BotSpawnState.REMOVED;
            return;
        }
        int chunkX = SectionPos.blockToSectionCoord(this.serverPlayer.getBlockX());
        int chunkZ = SectionPos.blockToSectionCoord(this.serverPlayer.getBlockZ());
        RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(level, chunkX, chunkZ, () -> doLogout(leaveMessage));
    }

    private void doLogout(net.kyori.adventure.text.Component leaveMessage) {
        if (this.spawnState == BotSpawnState.REMOVED || this.serverPlayer == null) {
            return;
        }
        ServerLevel level = this.serverPlayer.level();
        if (level == null || level.getCurrentWorldData() == null) {
            LOGGER.info("{}[BOT] skip logout (server shutting down)", this.serverPlayer.getPlainTextName());
            this.spawnState = BotSpawnState.REMOVED;
            return;
        }
        this.spawnState = BotSpawnState.REMOVED;
        PlayerList playerList = minecraftServer.getPlayerList();
        if (!this.worldAdded) {
            LOGGER.info("{}[BOT] logout while never added to world, lightweight cleanup", this.serverPlayer.getPlainTextName());
            this.cleanupAfterRemoval();
            return;
        }
        this.serverPlayer.disconnect();
        net.kyori.adventure.text.Component quitMessage;
        if (this.serverPlayer.isRemoved()) {
            // 已经下线：软下线，补发事件、存档、清注册表、广播
            quitMessage = this.softQuit(playerList, leaveMessage);
        } else {
            // 仍在世界：完整下线
            quitMessage = leaveMessage == null
                    ? playerList.remove(this.serverPlayer)
                    : playerList.remove(this.serverPlayer, leaveMessage);
        }
        // 广播离开消息
        if (this.visible && quitMessage != null && !quitMessage.equals(net.kyori.adventure.text.Component.empty())) {
            playerList.broadcastSystemMessage(PaperAdventure.asVanilla(quitMessage), false);
        }
        LOGGER.info("{}[BOT] logged out", this.serverPlayer.getPlainTextName());
        this.cleanupAfterRemoval();
    }


    private net.kyori.adventure.text.Component softQuit(PlayerList playerList, net.kyori.adventure.text.Component leaveMessage) {
        if (leaveMessage == null) {
            leaveMessage = net.kyori.adventure.text.Component.translatable(
                    "multiplayer.player.left",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                    net.kyori.adventure.text.Component.text(this.serverPlayer.getScoreboardName())
            );
        }
        // PlayerQuitEvent（与 PlayerList.remove 一致，reason 可空）
        PlayerQuitEvent event = new PlayerQuitEvent(
                this.serverPlayer.getBukkitEntity(), leaveMessage, this.serverPlayer.quitReason
        );
        ((CraftServer) Bukkit.getServer()).getPluginManager().callEvent(event);
        this.serverPlayer.getBukkitEntity().disconnect();
        // 存档（对应 PlayerList.save；playerIo 为 public 字段，stats/advancements 为空则跳过）
        if (this.serverPlayer.getBukkitEntity().isPersistent()) {
            playerList.playerIo.save(this.serverPlayer);
        }
        ServerStatsCounter stats = this.serverPlayer.getStats();
        if (stats != null) {
            stats.save();
        }
        PlayerAdvancements advancements = this.serverPlayer.getAdvancements();
        if (advancements != null) {
            advancements.save();
        }
        // 清 PlayerList 注册表
        playerList.getPlayers().remove(this.serverPlayer);
        playerList.getPlayersByUUID().remove(this.serverPlayer.getUUID());
        this.removeFromNameMap(playerList);
        // 广播 PlayerInfoRemove（Tab 列表移除，与 PlayerList.remove 一致）
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(this.serverPlayer.getUUID()));
        for (ServerPlayer other : playerList.getPlayers()) {
            if (other.getBukkitEntity().canSee(this.serverPlayer.getBukkitEntity())) {
                other.connection.send(packet);
            }
        }
        return event.quitMessage();
    }

    /** 从 PlayerList.playersByName 移除 */
    private void removeFromNameMap(PlayerList playerList) {
        try {
            Field byName = PlayerList.class.getDeclaredField("playersByName");
            byName.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ServerPlayer> playersByName = (Map<String, ServerPlayer>) byName.get(playerList);
            playersByName.remove(this.serverPlayer.getScoreboardName().toLowerCase(Locale.ROOT));
        } catch (ReflectiveOperationException ignored) {
        }
    }


    public CompletableFuture<Void> setVisible(boolean target) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (serverPlayer == null || spawnState == BotSpawnState.REMOVED) {
            future.completeExceptionally(new IllegalStateException(Lang.get("error.not-spawned")));
            return future;
        }
        if (this.visible == target) {
            future.complete(null);
            return future;
        }
        ServerLevel level = serverPlayer.level();
        if (level == null) {
            future.completeExceptionally(new IllegalStateException(Lang.get("error.world-unavailable")));
            return future;
        }
        int chunkX = SectionPos.blockToSectionCoord(serverPlayer.getBlockX());
        int chunkZ = SectionPos.blockToSectionCoord(serverPlayer.getBlockZ());
        RegionizedServer.getInstance().taskQueue.queueOrExecuteTickTask(level, chunkX, chunkZ, () -> {
            try {
                if (target) {
                    this.setVisibleTrue();
                } else {
                    this.setVisibleFalse();
                }
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private void setVisibleTrue() {
        PlayerList playerList = minecraftServer.getPlayerList();
        if (!playerList.getPlayers().contains(serverPlayer)) {
            playerList.getPlayers().add(serverPlayer);
        }
        playerList.getPlayersByUUID().put(serverPlayer.getUUID(), serverPlayer);
        try {
            Field byName = PlayerList.class.getDeclaredField("playersByName");
            byName.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ServerPlayer> playersByName = (Map<String, ServerPlayer>) byName.get(playerList);
            playersByName.put(
                    serverPlayer.getScoreboardName().toLowerCase(Locale.ROOT),
                    serverPlayer
            );
        } catch (ReflectiveOperationException e) {
            TianBotPlugin.instance.getLogger().severe(Lang.t("log.bot-name-register-failed-v2", "reason", e));
        }
        // 完整初始化包：已持有 info 的客户端经 UPDATE_LISTED 进 Tab，未持有的新建并进 Tab
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(serverPlayer));
        for (ServerPlayer other : playerList.getPlayers()) {
            if (other == serverPlayer) continue;
            if (other.getBukkitEntity().canSee(serverPlayer.getBukkitEntity())) {
                other.connection.send(packet);
            }
        }
        this.visible = true;
        LOGGER.info("{}[BOT] now visible (registered into PlayerList)", serverPlayer.getPlainTextName());
    }

    private void setVisibleFalse() {
        PlayerList playerList = minecraftServer.getPlayerList();
        playerList.getPlayers().remove(serverPlayer);
        playerList.getPlayersByUUID().remove(serverPlayer.getUUID());
        this.removeFromNameMap(playerList);
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(serverPlayer.getUUID()));
        for (ServerPlayer other : playerList.getPlayers()) {
            if (other.getBukkitEntity().canSee(serverPlayer.getBukkitEntity())) {
                other.connection.send(packet);
            }
        }
        this.visible = false;
        LOGGER.info("{}[BOT] now ghost (removed from PlayerList)", serverPlayer.getPlainTextName());
    }

    /**假人被踢出**/
    public void kick(net.minecraft.network.chat.Component reason, net.kyori.adventure.text.Component leaveMessage) {
        if (this.serverPlayer == null || this.spawnState == BotSpawnState.REMOVED) {
            return;
        }
        this.serverPlayer.quitReason = PlayerQuitEvent.QuitReason.KICKED;
        LOGGER.info("{}[BOT] kicking: {}", this.serverPlayer.getPlainTextName(), reason.getString());
        this.logout(leaveMessage);
    }

    /** 下线后清理：取消 tick、释放出生点票、摘除 BotManager 登记 */
    private void cleanupAfterRemoval() {
        this.actions.shutdown();
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        this.chunkLoaderActive = false;
        this.worldAdded = false;
        this.spawnState = BotSpawnState.REMOVED;
        this.removeSpawnChunkTicket();
        if (this.serverPlayer != null) {
            BotManager.botMap.remove(this.serverPlayer.getUUID());
        }
    }

}
