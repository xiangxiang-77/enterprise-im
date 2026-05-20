package com.enterpriseim.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im")
public class ImProperties {
    private final Tcp tcp = new Tcp();
    private final Auth auth = new Auth();
    private final Redis redis = new Redis();
    private final Storage storage = new Storage();
    private final Realtime realtime = new Realtime();

    public Tcp getTcp() {
        return tcp;
    }

    public Auth getAuth() {
        return auth;
    }

    public Redis getRedis() {
        return redis;
    }

    public Storage getStorage() {
        return storage;
    }

    public Realtime getRealtime() {
        return realtime;
    }

    public static class Tcp {
        private int port = 9000;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    public static class Auth {
        private String demoTokenPrefix = "demo-token-";
        private String adminPassword = "admin123";

        public String getDemoTokenPrefix() {
            return demoTokenPrefix;
        }

        public void setDemoTokenPrefix(String demoTokenPrefix) {
            this.demoTokenPrefix = demoTokenPrefix;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }

    public static class Redis {
        private String url = "redis://localhost:6379";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Storage {
        private String endpoint = "http://localhost:9001";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "enterprise-im";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class Realtime {
        private String turnUrl = "turn:localhost:3478";
        private String turnUsername = "enterprise-im";
        private String turnPassword = "enterprise-im-secret";
        private String pjsipSignalUrl = "http://localhost:7070";
        private String sipDomain = "enterprise-im.local";
        private String sipRegistrar = "sip:127.0.0.1:5060";
        private String sipAndroidRegistrar = "sip:10.200.39.178:5060";
        private String sipRealm = "enterprise-im.local";
        private String sipPassword = "enterprise-im-sip-secret";
        private int probeTimeoutMs = 500;

        public String getTurnUrl() {
            return turnUrl;
        }

        public void setTurnUrl(String turnUrl) {
            this.turnUrl = turnUrl;
        }

        public String getTurnUsername() {
            return turnUsername;
        }

        public void setTurnUsername(String turnUsername) {
            this.turnUsername = turnUsername;
        }

        public String getTurnPassword() {
            return turnPassword;
        }

        public void setTurnPassword(String turnPassword) {
            this.turnPassword = turnPassword;
        }

        public String getPjsipSignalUrl() {
            return pjsipSignalUrl;
        }

        public void setPjsipSignalUrl(String pjsipSignalUrl) {
            this.pjsipSignalUrl = pjsipSignalUrl;
        }

        public String getSipDomain() {
            return sipDomain;
        }

        public void setSipDomain(String sipDomain) {
            this.sipDomain = sipDomain;
        }

        public String getSipRegistrar() {
            return sipRegistrar;
        }

        public void setSipRegistrar(String sipRegistrar) {
            this.sipRegistrar = sipRegistrar;
        }

        public String getSipAndroidRegistrar() {
            return sipAndroidRegistrar;
        }

        public void setSipAndroidRegistrar(String sipAndroidRegistrar) {
            this.sipAndroidRegistrar = sipAndroidRegistrar;
        }

        public String getSipRealm() {
            return sipRealm;
        }

        public void setSipRealm(String sipRealm) {
            this.sipRealm = sipRealm;
        }

        public String getSipPassword() {
            return sipPassword;
        }

        public void setSipPassword(String sipPassword) {
            this.sipPassword = sipPassword;
        }

        public int getProbeTimeoutMs() {
            return probeTimeoutMs;
        }

        public void setProbeTimeoutMs(int probeTimeoutMs) {
            this.probeTimeoutMs = probeTimeoutMs;
        }
    }
}
