package xyz.ororigin.tianbot.bot;

import org.jetbrains.annotations.NotNull;

public class BotConfig {
    //服务器域名
    public String host = "example.com";

    //玩家ip
    @NotNull
    public String address;

    //玩家端口
    @NotNull
    public int port;

    //假人名字
    @NotNull
    public String botName;

    public boolean isGhost = false;

    public String getHost() {
        return host;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getBotName() {
        return botName;
    }

    public boolean isGhost() {
        return isGhost;
    }
}
