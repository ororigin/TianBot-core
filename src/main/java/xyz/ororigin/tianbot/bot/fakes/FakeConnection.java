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

    private static final java.lang.reflect.Field PACKET_LISTENER_FIELD = initPacketListenerField();

    private static java.lang.reflect.Field initPacketListenerField() {
        try {
            java.lang.reflect.Field field = Connection.class.getDeclaredField("packetListener");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Unable to access Connection.packetListener", e);
        }
    }

    public void setGamePacketListener(PacketListener listener) {
        try {
            PACKET_LISTENER_FIELD.set(this, listener);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to set Connection.packetListener", e);
        }
    }

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

    @Override
    public void runOnceConnected(Consumer<Connection> action) {
    }
}
