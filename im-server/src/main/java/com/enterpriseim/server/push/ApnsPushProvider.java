package com.enterpriseim.server.push;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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

public class ApnsPushProvider implements PushProvider {
    private static final Logger LOG = Logger.getLogger(ApnsPushProvider.class.getName());
    private static final String APNS_BASE_URL = "https://api.push.apple.com";
    private static final String APNS_DEV_URL = "https://api.sandbox.push.apple.com";

    private final String teamId;
    private final String keyId;
    private final PrivateKey privateKey;
    private final String topic;
    private final boolean production;
    private volatile String cachedJwt;
    private volatile long jwtExpiry;

    public ApnsPushProvider(ImProperties properties) {
        ImProperties.Apns cfg = properties.getApns();
        this.teamId = cfg.getTeamId();
        this.keyId = cfg.getKeyId();
        this.topic = cfg.getTopic();
        this.production = cfg.isProduction();

        String keyPath = cfg.getPrivateKeyPath();
        if (teamId == null || teamId.isEmpty() || keyId == null || keyId.isEmpty() || keyPath == null || keyPath.isEmpty()) {
            this.privateKey = null;
            LOG.warning("APNs not fully configured; push will be unavailable");
            return;
        }
        try {
            this.privateKey = loadEcPrivateKey(keyPath);
            LOG.info("APNs provider initialized for topic: " + topic);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load APNs private key from " + keyPath, e);
        }
    }

    @Override
    public String name() {
        return "apns";
    }

    @Override
    public boolean sendPush(String deviceToken, String title, String body, Map<String, String> data) {
        if (privateKey == null || topic == null || topic.isEmpty()) {
            LOG.warning("APNs not configured; skipping push");
            return false;
        }
        try {
            String jwt = getProviderToken();
            String apnsUrl = (production ? APNS_BASE_URL : APNS_DEV_URL) + "/3/device/" + deviceToken;

            ObjectNode aps = JsonNodeFactory.instance.objectNode();
            ObjectNode alert = JsonNodeFactory.instance.objectNode();
            if (title != null) alert.put("title", title);
            if (body != null) alert.put("body", body);
            aps.set("alert", alert);
            aps.put("sound", "default");
            if (data != null) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    aps.put(entry.getKey(), entry.getValue());
                }
            }

            ObjectNode root = JsonNodeFactory.instance.objectNode();
            root.set("aps", aps);

            String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(root);
            byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

            URL url = new URL(apnsUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "bearer " + jwt);
            conn.setRequestProperty("apns-topic", topic);
            conn.setRequestProperty("apns-push-type", "alert");
            conn.setRequestProperty("apns-priority", "10");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(payloadBytes);
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
                LOG.log(Level.INFO, "APNs push sent to device: {0}", deviceToken);
            } else {
                LOG.log(Level.WARNING, "APNs push failed status={0} response={1}", new Object[]{status, sb.toString()});
            }
            return success;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "APNs push error", e);
            return false;
        }
    }

    @Override
    public boolean sendToTopic(String topic, String title, String body, Map<String, String> data) {
        LOG.warning("APNs does not support topic-based push; use sendPush with device token");
        return false;
    }

    private String getProviderToken() throws Exception {
        if (cachedJwt != null && System.currentTimeMillis() < jwtExpiry) {
            return cachedJwt;
        }
        long now = System.currentTimeMillis() / 1000;
        ObjectNode header = JsonNodeFactory.instance.objectNode();
        header.put("alg", "ES256");
        header.put("kid", keyId);

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("iss", teamId);
        payload.put("iat", now);

        String headerB64 = base64url(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(header));
        String payloadB64 = base64url(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        String signingInput = headerB64 + "." + payloadB64;

        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = base64url(sig.sign());

        cachedJwt = signingInput + "." + signature;
        jwtExpiry = System.currentTimeMillis() + 3000000; // 50 min
        return cachedJwt;
    }

    private static PrivateKey loadEcPrivateKey(String path) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        String cleaned = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("EC");
        return kf.generatePrivate(spec);
    }

    private static String base64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String base64url(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
}
