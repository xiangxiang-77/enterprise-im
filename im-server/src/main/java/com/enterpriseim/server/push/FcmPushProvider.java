package com.enterpriseim.server.push;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FcmPushProvider implements PushProvider {
    private static final Logger LOG = Logger.getLogger(FcmPushProvider.class.getName());
    private static final String OAUTH_URL = "https://oauth2.googleapis.com/token";
    private static final String FCM_SEND_URL = "https://fcm.googleapis.com/v1/projects/%s/messages:send";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final String projectId;
    private final String clientEmail;
    private final PrivateKey privateKey;
    private final ObjectMapper objectMapper;
    private volatile String cachedAccessToken;
    private volatile long tokenExpiry;

    public FcmPushProvider(ImProperties properties, ObjectMapper objectMapper) {
        ImProperties.Fcm cfg = properties.getFcm();
        this.projectId = cfg.getProjectId();
        this.objectMapper = objectMapper;

        JsonNode sa = loadServiceAccount(cfg.getServiceAccountKey());
        if (sa == null) {
            this.clientEmail = null;
            this.privateKey = null;
            LOG.warning("FCM service account key not configured; push will be unavailable");
            return;
        }
        this.clientEmail = sa.path("client_email").asText();
        String pkText = sa.path("private_key").asText();
        if (clientEmail.isEmpty() || pkText.isEmpty()) {
            throw new IllegalArgumentException("FCM service account JSON must contain client_email and private_key");
        }
        try {
            this.privateKey = parseRsaPrivateKey(pkText);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse FCM service account private key", e);
        }
    }

    @Override
    public String name() {
        return "fcm";
    }

    @Override
    public boolean sendPush(String deviceToken, String title, String body, Map<String, String> data) {
        if (privateKey == null || projectId == null || projectId.isEmpty()) {
            LOG.warning("FCM not configured; skipping push");
            return false;
        }
        try {
            String accessToken = getAccessToken();
            String fcmUrl = String.format(FCM_SEND_URL, projectId);
            ObjectNode message = JsonNodeFactory.instance.objectNode();
            ObjectNode msgContent = JsonNodeFactory.instance.objectNode();
            msgContent.put("token", deviceToken);
            if (title != null || body != null) {
                ObjectNode notification = JsonNodeFactory.instance.objectNode();
                if (title != null) notification.put("title", title);
                if (body != null) notification.put("body", body);
                msgContent.set("notification", notification);
            }
            if (data != null && !data.isEmpty()) {
                ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    dataNode.put(entry.getKey(), entry.getValue());
                }
                msgContent.set("data", dataNode);
            }
            message.set("message", msgContent);
            String payload = objectMapper.writeValueAsString(message);

            URL url = new URL(fcmUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = new byte[4096];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = is.read(bytes)) != -1) {
                sb.append(new String(bytes, 0, len, StandardCharsets.UTF_8));
            }
            is.close();
            conn.disconnect();

            boolean success = (status >= 200 && status < 300);
            if (success) {
                LOG.log(Level.INFO, "FCM push sent to device: {0}", deviceToken);
            } else {
                LOG.log(Level.WARNING, "FCM push failed status={0} response={1}", new Object[]{status, sb.toString()});
            }
            return success;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "FCM push error", e);
            return false;
        }
    }

    @Override
    public boolean sendToTopic(String topic, String title, String body, Map<String, String> data) {
        if (privateKey == null || projectId == null || projectId.isEmpty()) {
            LOG.warning("FCM not configured; skipping topic push");
            return false;
        }
        try {
            String accessToken = getAccessToken();
            String fcmUrl = String.format(FCM_SEND_URL, projectId);
            ObjectNode message = JsonNodeFactory.instance.objectNode();
            ObjectNode msgContent = JsonNodeFactory.instance.objectNode();
            msgContent.put("topic", topic);
            if (title != null || body != null) {
                ObjectNode notification = JsonNodeFactory.instance.objectNode();
                if (title != null) notification.put("title", title);
                if (body != null) notification.put("body", body);
                msgContent.set("notification", notification);
            }
            if (data != null && !data.isEmpty()) {
                ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    dataNode.put(entry.getKey(), entry.getValue());
                }
                msgContent.set("data", dataNode);
            }
            message.set("message", msgContent);
            String payload = objectMapper.writeValueAsString(message);

            URL url = new URL(fcmUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
            byte[] bytes = new byte[4096];
            int len;
            StringBuilder sb = new StringBuilder();
            while ((len = is.read(bytes)) != -1) {
                sb.append(new String(bytes, 0, len, StandardCharsets.UTF_8));
            }
            is.close();
            conn.disconnect();

            boolean success = (status >= 200 && status < 300);
            if (success) {
                LOG.log(Level.INFO, "FCM topic push sent to topic: {0}", topic);
            } else {
                LOG.log(Level.WARNING, "FCM topic push failed status={0} response={1}", new Object[]{status, sb.toString()});
            }
            return success;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "FCM topic push error", e);
            return false;
        }
    }

    private String getAccessToken() throws Exception {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return cachedAccessToken;
        }
        String jwt = createJwt();
        String body = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8")
                + "&assertion=" + URLEncoder.encode(jwt, "UTF-8");

        URL url = new URL(OAUTH_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        byte[] bytes = new byte[4096];
        int len;
        StringBuilder sb = new StringBuilder();
        while ((len = is.read(bytes)) != -1) {
            sb.append(new String(bytes, 0, len, StandardCharsets.UTF_8));
        }
        is.close();
        conn.disconnect();

        if (status < 200 || status >= 300) {
            throw new IllegalStateException("FCM OAuth2 failed: " + sb.toString());
        }
        JsonNode resp = objectMapper.readTree(sb.toString());
        cachedAccessToken = resp.path("access_token").asText();
        long expiresIn = resp.path("expires_in").asLong(3600);
        tokenExpiry = System.currentTimeMillis() + (expiresIn * 1000) - 60000;
        return cachedAccessToken;
    }

    private String createJwt() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        ObjectNode header = JsonNodeFactory.instance.objectNode();
        header.put("alg", "RS256");
        header.put("typ", "JWT");

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("iss", clientEmail);
        payload.put("scope", SCOPE);
        payload.put("aud", OAUTH_URL);
        payload.put("exp", now + 3600);
        payload.put("iat", now);

        String headerB64 = base64url(objectMapper.writeValueAsString(header));
        String payloadB64 = base64url(objectMapper.writeValueAsString(payload));
        String signingInput = headerB64 + "." + payloadB64;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = base64url(sig.sign());

        return signingInput + "." + signature;
    }

    private JsonNode loadServiceAccount(String key) {
        if (key == null || key.trim().isEmpty()) return null;
        try {
            return objectMapper.readTree(key);
        } catch (Exception e) {
            try {
                byte[] fileBytes = Files.readAllBytes(Paths.get(key.trim()));
                return objectMapper.readTree(new String(fileBytes, StandardCharsets.UTF_8));
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Failed to load FCM service account key", ex);
                return null;
            }
        }
    }

    private static PrivateKey parseRsaPrivateKey(String pkText) throws Exception {
        String cleaned = pkText
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String base64url(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
}
