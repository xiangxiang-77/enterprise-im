package com.enterpriseim.server.auth.sms;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AliyunSmsProvider implements SmsProvider {
    private static final Logger LOG = Logger.getLogger(AliyunSmsProvider.class.getName());
    private static final String ENDPOINT = "dysmsapi.aliyuncs.com";
    private static final String VERSION = "2017-05-25";
    private static final String ACTION = "SendSms";
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";
    private static final String SIGNATURE_VERSION = "1.0";

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String signName;
    private final String templateCode;
    private final ObjectMapper objectMapper;

    public AliyunSmsProvider(ImProperties properties, ObjectMapper objectMapper) {
        ImProperties.AliyunSms cfg = properties.getAliyun().getSms();
        this.accessKeyId = cfg.getAccessKeyId();
        this.accessKeySecret = cfg.getAccessKeySecret();
        this.signName = cfg.getSignName();
        this.templateCode = cfg.getTemplateCode();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean send(String phone, String code) {
        try {
            // Normalize phone
            String normalizedPhone = phone.startsWith("+86") ? phone.substring(3) : phone;

            Map<String, String> params = new TreeMap<>();
            params.put("Action", ACTION);
            params.put("Version", VERSION);
            params.put("AccessKeyId", accessKeyId);
            params.put("SignatureMethod", SIGNATURE_METHOD);
            params.put("SignatureVersion", SIGNATURE_VERSION);
            params.put("SignatureNonce", UUID.randomUUID().toString());
            params.put("Timestamp", formatTimestamp(new Date()));
            params.put("Format", "JSON");
            params.put("RegionId", "cn-hangzhou");
            params.put("PhoneNumbers", normalizedPhone);
            params.put("SignName", signName);
            params.put("TemplateCode", templateCode);
            params.put("TemplateParam", "{\"code\":\"" + code + "\"}");

            String signature = sign(params, accessKeySecret + "&");
            params.put("Signature", signature);

            String queryString = buildQueryString(params);
            String response = httpGet(queryString);
            JsonNode root = objectMapper.readTree(response);

            String respCode = root.path("Code").asText("");
            if ("OK".equals(respCode)) {
                LOG.log(Level.INFO, "Aliyun SMS sent to {0}, bizId={1}",
                        new Object[]{phone, root.path("BizId").asText("")});
                return true;
            } else {
                LOG.log(Level.WARNING, "Aliyun SMS send failed: {0} — {1}",
                        new Object[]{respCode, root.path("Message").asText("")});
                return false;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Aliyun SMS send error", e);
            return false;
        }
    }

    @Override
    public String name() {
        return "aliyun";
    }

    private String sign(Map<String, String> params, String keySecret) throws Exception {
        String stringToSign = "GET" + "&"
                + percentEncode("/") + "&"
                + percentEncode(buildQueryString(params));

        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signData);
    }

    private String buildQueryString(Map<String, String> params) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(percentEncode(entry.getKey()));
            sb.append("=");
            sb.append(percentEncode(entry.getValue()));
        }
        return sb.toString();
    }

    private String percentEncode(String value) throws Exception {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String httpGet(String queryString) throws Exception {
        URL url = new URL("https://" + ENDPOINT + "/?" + queryString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

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

    private String formatTimestamp(Date date) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(new SimpleTimeZone(0, "UTC"));
        return fmt.format(date);
    }
}
