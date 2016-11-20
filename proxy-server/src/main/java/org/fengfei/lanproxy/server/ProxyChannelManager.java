package org.fengfei.lanproxy.server;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;

public class ProxyChannelManager {

    private static Logger logger = LoggerFactory.getLogger(ProxyChannelManager.class);

    private static final AttributeKey<Map<String, Channel>> USER_CHANNELS = AttributeKey.newInstance("user_channels");

    private static final AttributeKey<String> USER_ID = AttributeKey.newInstance("user_id");

    private static final AttributeKey<List<Integer>> CHANNEL_PORT = AttributeKey.newInstance("channel_port");

    private static final AttributeKey<Boolean> PROXY_CHANNEL_WRITEABLE = AttributeKey
            .newInstance("proxy_channel_writeable");

    private static final AttributeKey<Boolean> CLIENT_CHANNEL_WRITEABLE = AttributeKey
            .newInstance("client_channel_writeable");

    private static Map<Integer, Channel> proxyChannels = new ConcurrentHashMap<Integer, Channel>();

    public static void addChannel(List<Integer> ports, Channel channel) {
        if (ports == null) {
            ports = channel.attr(CHANNEL_PORT).get();
        }
        if (ports == null) {
            throw new IllegalArgumentException("port can not be null");
        }
        synchronized (proxyChannels) {
            for (int port : ports) {
                proxyChannels.put(port, channel);
            }
        }
        channel.attr(CHANNEL_PORT).set(ports);
        if (channel.attr(USER_CHANNELS).get() == null) {
            channel.attr(USER_CHANNELS).set(new ConcurrentHashMap<String, Channel>());
        }
    }

    public static void removeChannel(Channel channel) {
        logger.warn("channel closed, clear user channels, {}", channel);
        if (channel.attr(CHANNEL_PORT).get() == null) {
            return;
        }
        synchronized (proxyChannels) {
            List<Integer> ports = channel.attr(CHANNEL_PORT).get();
            for (int port : ports) {
                Channel proxyChannel = proxyChannels.remove(port);
                if (proxyChannel.isActive()) {
                    proxyChannel.close();
                }
            }
        }
        Map<String, Channel> userChannels = getUserChannels(channel);
        Iterator<String> ite = userChannels.keySet().iterator();
        while (ite.hasNext()) {
            userChannels.get(ite.next()).close();
        }
    }

    public static Channel getChannel(Integer port) {
        return proxyChannels.get(port);
    }

    public static void addUserChannel(Channel proxyChannel, String userId, Channel userChannel) {
        synchronized (proxyChannel) {
            userChannel.attr(USER_ID).set(userId);
            proxyChannel.attr(USER_CHANNELS).get().put(userId, userChannel);
        }
    }

    public static Channel removeUserChannel(Channel proxyChannel, String userId) {
        synchronized (proxyChannel) {
            return proxyChannel.attr(USER_CHANNELS).get().get(userId);
        }
    }

    public static Channel getUserChannel(Channel proxyChannel, String userId) {
        return proxyChannel.attr(USER_CHANNELS).get().get(userId);
    }

    public static String getUserChannelUserId(Channel userChannel) {
        return userChannel.attr(USER_ID).get();
    }

    public static Map<String, Channel> getUserChannels(Channel proxyChannel) {
        return proxyChannel.attr(USER_CHANNELS).get();
    }

    public static void setUserChannelReadability(Channel userChannel, Boolean client, Boolean proxy) {
        logger.info("update user channel readability, {} {} {}", userChannel, client, proxy);
        synchronized (userChannel) {
            if (client != null) {
                userChannel.attr(CLIENT_CHANNEL_WRITEABLE).set(client);
            }
            if (proxy != null) {
                userChannel.attr(PROXY_CHANNEL_WRITEABLE).set(proxy);
            }
            if (userChannel.attr(CLIENT_CHANNEL_WRITEABLE).get() && userChannel.attr(PROXY_CHANNEL_WRITEABLE).get()) {
                userChannel.config().setOption(ChannelOption.AUTO_READ, true);
            } else {
                userChannel.config().setOption(ChannelOption.AUTO_READ, false);
            }
        }
    }

    public static void notifyChannelWritabilityChanged(Channel proxyChannel) {
        synchronized (proxyChannel) {
            Map<String, Channel> userChannels = getUserChannels(proxyChannel);
            Iterator<String> ite = userChannels.keySet().iterator();
            while (ite.hasNext()) {
                Channel userChannel = userChannels.get(ite.next());
                setUserChannelReadability(userChannel, null, proxyChannel.isWritable());
            }
        }
    }

}
