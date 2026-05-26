package com.enterpriseim.server.tcp;

import lombok.val;

import io.netty.channel.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OnlineSessionRegistry {
    private final Map<String, Channel> userChannels = new ConcurrentHashMap<>();
    private final Map<Channel, String> channelUsers = new ConcurrentHashMap<>();

    public void bind(String userId, Channel channel) {
        userChannels.put(userId, channel);
        channelUsers.put(channel, userId);
    }

    public Optional<Channel> findChannel(String userId) {
        return Optional.ofNullable(userChannels.get(userId)).filter(Channel::isActive);
    }

    public Optional<String> findUser(Channel channel) {
        return Optional.ofNullable(channelUsers.get(channel));
    }

    public void remove(Channel channel) {
        val userId = channelUsers.remove(channel);
        if (userId != null) {
            userChannels.remove(userId, channel);
        }
    }

    public int onlineCount() {
        return userChannels.size();
    }

    public boolean isOnline(String userId) {
        Channel ch = userChannels.get(userId);
        return ch != null && ch.isActive();
    }

    public boolean disconnect(String userId) {
        Channel ch = userChannels.remove(userId);
        if (ch == null) return false;
        channelUsers.remove(ch);
        ch.close();
        return true;
    }
}

