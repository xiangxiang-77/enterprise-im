package com.enterpriseim.server.auth.sms;

import java.util.logging.Logger;

public class DemoSmsProvider implements SmsProvider {
    private static final Logger LOG = Logger.getLogger(DemoSmsProvider.class.getName());

    @Override
    public boolean send(String phone, String code) {
        LOG.info(String.format("[DEMO SMS] Sending code %s to phone %s — no real SMS sent.", code, phone));
        return true;
    }

    @Override
    public String name() {
        return "demo";
    }
}
