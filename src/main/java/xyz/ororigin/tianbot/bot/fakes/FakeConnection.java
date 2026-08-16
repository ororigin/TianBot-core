package xyz.ororigin.tianbot.bot.fakes;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;

public class FakeConnection extends Connection {

    public FakeConnection(InetSocketAddress address, String serverHost) {
        super(PacketFlow.SERVERBOUND);
        this.channel = new FakeChannel(null, resolveAddress(address));
        this.address = address;
        this.hostname = serverHost;
    }

    /** 解析假人地址为 InetAddress（未解析的 hostname 兜底为回环地址，避免伪通道 remoteAddress 构造失败） */
    private static InetAddress resolveAddress(InetSocketAddress address) {
        InetAddress resolved = address.getAddress();
        if (resolved != null) {
            return resolved;
        }
        try {
            return InetAddress.getByName(address.getHostString());
        } catch (UnknownHostException e) {
            return InetAddress.getLoopbackAddress();
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public boolean isMemoryConnection() {
        return true;
    }

    @Override
    public void send(Packet<?> packet) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener) {
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Override
    public void tick() {
    }

    @Override
    public void disconnect(Component reason) {
    }

    @Override
    public void disconnect(DisconnectionDetails details) {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocol, T packetListener) {
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocol) {
    }

    // 注：Folia 26.2 的 Connection 没有 setupInboundProtocolAsync / setupOutboundProtocolAsync
    // （Arbor 26.2 有），故此处不再覆写这两个空操作方法。

    @Override
    public void runOnceConnected(Consumer<Connection> action) {
    }
}
