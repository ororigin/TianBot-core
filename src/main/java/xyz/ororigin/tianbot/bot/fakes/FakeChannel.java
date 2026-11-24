package xyz.ororigin.tianbot.bot.fakes;

import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.DefaultEventLoop;
import io.netty.channel.EventLoop;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;


public class FakeChannel extends AbstractChannel {

    private static EventLoop sharedEventLoop;

    private static int activeUsers;

    private static long releaseTicket;

    private static EventLoop sharedEventLoop() {
        synchronized (FakeChannel.class) {
            // 已进入 shutting down 的循环不可复用（可能是关闭竞态，也可能是被误关）
            if (sharedEventLoop == null || sharedEventLoop.isShuttingDown()) {
                sharedEventLoop = new DefaultEventLoop();
                activeUsers = 0;
            }
            activeUsers++;
            releaseTicket++;
            return sharedEventLoop;
        }
    }

    public static void releaseEventLoop() {
        synchronized (FakeChannel.class) {
            if (activeUsers > 0) {
                activeUsers--;
            }
            if (activeUsers > 0) {
                return;
            }
            final long ticket = ++releaseTicket;
            final EventLoop loop = sharedEventLoop;
            if (loop == null) {
                return;
            }
            new Thread(() -> {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                synchronized (FakeChannel.class) {
                    if (ticket != releaseTicket || activeUsers > 0 || sharedEventLoop != loop) {
                        return;
                    }
                    sharedEventLoop = null;
                }
                try {
                    loop.shutdownGracefully(0L, 1L, TimeUnit.SECONDS);
                } catch (Throwable ignored) {
                    // 关闭失败不影响后续流程
                }
            }, "TianBot-EventLoop-Reaper").start();
        }
    }
    public static void shutdownEventLoop() {
        synchronized (FakeChannel.class) {
            if (activeUsers > 0) {
                return;
            }
        }
        releaseEventLoop();
    }

    public static synchronized EventLoop currentEventLoop() {
        return sharedEventLoop;
    }

    public static synchronized int activeUsers() {
        return activeUsers;
    }

    private final ChannelConfig config = new DefaultChannelConfig(this);
    private final ChannelPipeline pipeline = new FakeChannelPipeline(this);
    private final InetAddress address;

    public FakeChannel(Channel parent, InetAddress address) {
        super(parent);
        this.address = address;
    }

    @Override
    public ChannelConfig config() {
        config.setAutoRead(true);
        return config;
    }

    @Override
    protected void doBeginRead() throws Exception {
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
    }

    @Override
    protected void doDisconnect() throws Exception {
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        for (; ; ) {
            Object msg = in.current();
            if (msg == null) {
                break;
            }
            in.remove();
        }
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    protected boolean isCompatible(EventLoop eventLoop) {
        return true;
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public ChannelPipeline pipeline() {
        return pipeline;
    }

    @Override
    protected SocketAddress localAddress0() {
        return new InetSocketAddress(address, 25565);
    }

    @Override
    public ChannelMetadata metadata() {
        return new ChannelMetadata(true);
    }

    @Override
    protected AbstractUnsafe newUnsafe() {
        return new AbstractUnsafe() {
            @Override
            public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                safeSetSuccess(promise);
            }
        };
    }

    @Override
    protected SocketAddress remoteAddress0() {
        return new InetSocketAddress(address, 25565);
    }

    @Override
    public EventLoop eventLoop() {
        return sharedEventLoop();
    }

    @Override
    protected void doClose() throws Exception {
        releaseEventLoop();
    }
}
