package com.enterpriseim.server.auth.sms;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TencentSmsProvider implements SmsProvider {
    private static final Logger LOG = Logger.getLogger(TencentSmsProvider.class.getName());
    private static final String ENDPOINT = "sms.tencentcloudapi.com";
    private static final String SERVICE = "sms";
    private static final String VERSION = "2021-01-11";
    private static final String ACTION = "SendSms";

    private final String secretId;
    private final String secretKey;
    private final String sdkAppId;
    private final String signName;
    private final String templateId;
    private final ObjectMapper objectMapper;

    public TencentSmsProvider(ImProperties properties, ObjectMapper objectMapper) {
        ImProperties.TencentSms cfg = properties.getTencent().getSms();
        this.secretId = cfg.getSecretId();
        this.secretKey = cfg.getSecretKey();
        this.sdkAppId = cfg.getSdkAppId();
        this.signName = cfg.getSignName();
        this.templateId = cfg.getTemplateId();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean send(String phone, String code) {
        try {
            String payload = buildPayload(phone, code);
            String response = sendRequest(payload);
            JsonNode root = objectMapper.readTree(response);
            JsonNode error = root.path("Response").path("Error");
            if (!error.isMissingNode()) {
                LOG.log(Level.WARNING, "Tencent SMS send failed: {0}", error.path("Message").asText("unknown error"));
                return false;
            }
            LOG.log(Level.INFO, "Tencent SMS sent to {0}, requestId={1}",
                    new Object[]{phone, root.path("Response").path("RequestId").asText("")});
            return true;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Tencent SMS send error", e);
            return false;
        }
    }

    @Override
    public String name() {
        return "tencent";
    }

    private String buildPayload(String phone, String code) throws Exception {
        // Normalize phone: strip leading +86 if present, keep digits only
        String normalizedPhone = phone.startsWith("+86") ? phone.substring(3) : phone;
        // Tencent expects E.164 format: +8613800138000
        String e164Phone = "+86" + normalizedPhone;

        ObjectMapper mapper = new ObjectMapper();
        String[] phoneSet = new String[]{e164Phone};
        String[] templateParamSet = new String[]{code};

        String innerJson = mapper.writeValueAsString(new SendSmsRequest(phoneSet, sdkAppId, signName, templateId, templateParamSet));
        return innerJson;
    }

    private String sendRequest(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String date = formatDate(timestamp);

        // Step 1: build canonical request
        String httpRequestMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = "";
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + ENDPOINT + "\n";
        String signedHeaders = "content-type;host";
        String hashedRequestPayload = sha256Hex(payload);
        String canonicalRequest = httpRequestMethod + "\n"
                + canonicalUri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedRequestPayload;

        // Step 2: build string to sign
        String algorithm = "TC3-HMAC-SHA256";
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);
        String stringToSign = algorithm + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + hashedCanonicalRequest;

        // Step 3: calculate signature
        byte[] secretDate = hmacSHA256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSHA256(secretDate, SERVICE);
        byte[] secretSigning = hmacSHA256(secretService, "tc3_request");
        byte[] signature = hmacSHA256(secretSigning, stringToSign);
        String signatureHex = bytesToHex(signature);

        // Step 4: build authorization header
        String authorization = algorithm + " "
                + "Credential=" + secretId + "/" + credentialScope + ", "
                + "SignedHeaders=" + signedHeaders + ", "
                + "Signature=" + signatureHex;

        URL url = new URL("https://" + ENDPOINT);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Host", ENDPOINT);
        conn.setRequestProperty("X-TC-Action", ACTION);
        conn.setRequestProperty("X-TC-Version", VERSION);
        conn.setRequestProperty("X-TC-Timestamp", String.valueOf(timestamp));
        conn.setRequestProperty("X-TC-Region", "ap-guangzhou");
        conn.setRequestProperty("Authorization", authorization);
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
        return sb.toString();
    }

    private String formatDate(long timestamp) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(timestamp * 1000));
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static byte[] hmacSHA256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static class SendSmsRequest {
        public String[] PhoneNumberSet;
        public String SmsSdkAppId;
        public String SignName;
        public String TemplateId;
        public String[] TemplateParamSet;

        SendSmsRequest(String[] phoneNumberSet, String smsSdkAppId, String signName, String templateId, String[] templateParamSet) {
            this.PhoneNumberSet = phoneNumberSet;
            this.SmsSdkAppId = smsSdkAppId;
            this.SignName = signName;
            this.TemplateId = templateId;
            this.TemplateParamSet = templateParamSet;
        }
    }
}
