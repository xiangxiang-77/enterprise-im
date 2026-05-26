package com.enterpriseim.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "im")
public class ImProperties {
    private final Tcp tcp = new Tcp();
    private final Auth auth = new Auth();
    private final Aliyun aliyun = new Aliyun();
    private final Tencent tencent = new Tencent();
    private final Oidc oidc = new Oidc();
    private final Redis redis = new Redis();
    private final Storage storage = new Storage();
    private final Realtime realtime = new Realtime();
    private final Theme theme = new Theme();
    private final Launch launch = new Launch();
    private final I18n i18n = new I18n();
    private final Legal legal = new Legal();
    private final Fcm fcm = new Fcm();
    private final Apns apns = new Apns();
    private final Baidu baidu = new Baidu();
    private final OnlyOffice onlyoffice = new OnlyOffice();
    private final Push push = new Push();

    public Tcp getTcp() {
        return tcp;
    }

    public Auth getAuth() {
        return auth;
    }

    public Aliyun getAliyun() {
        return aliyun;
    }

    public Tencent getTencent() {
        return tencent;
    }

    public Oidc getOidc() {
        return oidc;
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

    public Theme getTheme() {
        return theme;
    }

    public Launch getLaunch() {
        return launch;
    }

    public I18n getI18n() {
        return i18n;
    }

    public Legal getLegal() {
        return legal;
    }

    public Fcm getFcm() {
        return fcm;
    }

    public Apns getApns() {
        return apns;
    }

    public Baidu getBaidu() {
        return baidu;
    }

    public OnlyOffice getOnlyoffice() {
        return onlyoffice;
    }

    public Push getPush() {
        return push;
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
        private String jwtIssuer = "enterprise-im";
        private String jwtSecret = "change-me-enterprise-im-jwt-secret";
        private long accessTokenTtlSeconds = 86400;
        private String userDemoPassword = "demo123";
        private boolean acceptDemoTokens = false;
        private boolean demoCallEndpointsEnabled = false;
        private String smsProvider = "demo";
        private long smsCodeTtlSeconds = 300;
        private String ssoProvider = "disabled";
        private String biometricProvider = "client_unavailable";

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

        public String getJwtIssuer() {
            return jwtIssuer;
        }

        public void setJwtIssuer(String jwtIssuer) {
            this.jwtIssuer = jwtIssuer;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getAccessTokenTtlSeconds() {
            return accessTokenTtlSeconds;
        }

        public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
            this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        }

        public String getUserDemoPassword() {
            return userDemoPassword;
        }

        public void setUserDemoPassword(String userDemoPassword) {
            this.userDemoPassword = userDemoPassword;
        }

        public boolean isAcceptDemoTokens() {
            return acceptDemoTokens;
        }

        public void setAcceptDemoTokens(boolean acceptDemoTokens) {
            this.acceptDemoTokens = acceptDemoTokens;
        }

        public boolean isDemoCallEndpointsEnabled() {
            return demoCallEndpointsEnabled;
        }

        public void setDemoCallEndpointsEnabled(boolean demoCallEndpointsEnabled) {
            this.demoCallEndpointsEnabled = demoCallEndpointsEnabled;
        }

        public String getSmsProvider() {
            return smsProvider;
        }

        public void setSmsProvider(String smsProvider) {
            this.smsProvider = smsProvider;
        }

        public long getSmsCodeTtlSeconds() {
            return smsCodeTtlSeconds;
        }

        public void setSmsCodeTtlSeconds(long smsCodeTtlSeconds) {
            this.smsCodeTtlSeconds = smsCodeTtlSeconds;
        }

        public String getSsoProvider() {
            return ssoProvider;
        }

        public void setSsoProvider(String ssoProvider) {
            this.ssoProvider = ssoProvider;
        }

        public String getBiometricProvider() {
            return biometricProvider;
        }

        public void setBiometricProvider(String biometricProvider) {
            this.biometricProvider = biometricProvider;
        }
    }

    public static class Aliyun {
        private final AliyunSms sms = new AliyunSms();

        public AliyunSms getSms() {
            return sms;
        }
    }

    public static class AliyunSms {
        private String accessKeyId = "";
        private String accessKeySecret = "";
        private String signName = "";
        private String templateCode = "";

        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getSignName() { return signName; }
        public void setSignName(String signName) { this.signName = signName; }
        public String getTemplateCode() { return templateCode; }
        public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    }

    public static class Tencent {
        private final TencentSms sms = new TencentSms();

        public TencentSms getSms() {
            return sms;
        }
    }

    public static class TencentSms {
        private String secretId = "";
        private String secretKey = "";
        private String sdkAppId = "";
        private String signName = "";
        private String templateId = "";

        public String getSecretId() { return secretId; }
        public void setSecretId(String secretId) { this.secretId = secretId; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getSdkAppId() { return sdkAppId; }
        public void setSdkAppId(String sdkAppId) { this.sdkAppId = sdkAppId; }
        public String getSignName() { return signName; }
        public void setSignName(String signName) { this.signName = signName; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
    }

    public static class Oidc {
        private String issuerUri = "";
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";

        public String getIssuerUri() { return issuerUri; }
        public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
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
        private String localRoot = "./data/storage";

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

        public String getLocalRoot() {
            return localRoot;
        }

        public void setLocalRoot(String localRoot) {
            this.localRoot = localRoot;
        }
    }

    public static class Realtime {
        private String turnUrl = "turn:localhost:3478";
        private String turnUsername = "enterprise-im";
        private String turnPassword = "enterprise-im-secret";
        private String pjsipSignalUrl = "http://localhost:7070";
        private String sipDomain = "enterprise-im.local";
        private String sipRegistrar = "sip:127.0.0.1:5060";
        private String sipAndroidRegistrar = "sip:10.200.71.31:5060";
        private String sipRealm = "asterisk";
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

    public static class Theme {
        private String primaryColor = "#2563eb";

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }
    }

    public static class Launch {
        private String logoUrl = "";
        private String slogan = "";

        public String getLogoUrl() {
            return logoUrl;
        }

        public void setLogoUrl(String logoUrl) {
            this.logoUrl = logoUrl;
        }

        public String getSlogan() {
            return slogan;
        }

        public void setSlogan(String slogan) {
            this.slogan = slogan;
        }
    }

    public static class I18n {
        private String defaultLanguage = "zh-CN";

        public String getDefaultLanguage() {
            return defaultLanguage;
        }

        public void setDefaultLanguage(String defaultLanguage) {
            this.defaultLanguage = defaultLanguage;
        }
    }

    public static class Legal {
        private String termsUrl = "";
        private String privacyUrl = "";

        public String getTermsUrl() {
            return termsUrl;
        }

        public void setTermsUrl(String termsUrl) {
            this.termsUrl = termsUrl;
        }

        public String getPrivacyUrl() {
            return privacyUrl;
        }

        public void setPrivacyUrl(String privacyUrl) {
            this.privacyUrl = privacyUrl;
        }
    }

    public static class Fcm {
        private String serviceAccountKey = "";
        private String projectId = "";

        public String getServiceAccountKey() { return serviceAccountKey; }
        public void setServiceAccountKey(String serviceAccountKey) { this.serviceAccountKey = serviceAccountKey; }
        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
    }

    public static class Apns {
        private String teamId = "";
        private String keyId = "";
        private String privateKeyPath = "";
        private String topic = "";
        private boolean production = true;

        public String getTeamId() { return teamId; }
        public void setTeamId(String teamId) { this.teamId = teamId; }
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public boolean isProduction() { return production; }
        public void setProduction(boolean production) { this.production = production; }
    }

    public static class Baidu {
        private final BaiduOcr ocr = new BaiduOcr();

        public BaiduOcr getOcr() { return ocr; }
    }

    public static class BaiduOcr {
        private String apiKey = "";
        private String secretKey = "";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }

    public static class OnlyOffice {
        private String apiUrl = "";
        private String secretKey = "";

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }

    public static class Push {
        private String provider = "mock";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
    }
}
