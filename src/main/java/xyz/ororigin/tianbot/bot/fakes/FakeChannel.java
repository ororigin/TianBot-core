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


public class FakeChannel extends AbstractChannel {

    /** 所有假人共用一个独立事件循环（静态，避免每假人一个线程） */
    private static final EventLoop EVENT_LOOP = new DefaultEventLoop();
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
    protected void doClose() throws Exception {
    }

    @Override
    protected void doDisconnect() throws Exception {
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        // 假连接没有对端，写出的数据直接丢弃
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
        return EVENT_LOOP;
    }
}
