package com.enterpriseim.server.push;

import java.util.Map;

public interface PushProvider {
    String name();
    boolean sendPush(String deviceToken, String title, String body, Map<String, String> data);
    boolean sendToTopic(String topic, String title, String body, Map<String, String> data);
}
