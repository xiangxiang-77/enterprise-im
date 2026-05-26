package com.enterpriseim.server.auth.sms;

public interface SmsProvider {
    boolean send(String phone, String code);
    String name();
}
