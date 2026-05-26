package com.enterpriseim.server.ocr;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BaiduOcrProvider implements OcrProvider {
    private static final Logger LOG = Logger.getLogger(BaiduOcrProvider.class.getName());
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";

    private final String apiKey;
    private final String secretKey;
    private final ObjectMapper objectMapper;

    public BaiduOcrProvider(ImProperties properties, ObjectMapper objectMapper) {
        ImProperties.BaiduOcr cfg = properties.getBaidu().getOcr();
        this.apiKey = cfg.getApiKey();
        this.secretKey = cfg.getSecretKey();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "baidu";
    }

    @Override
    public String recognize(byte[] imageBytes, String format) {
        if (apiKey == null || apiKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
            LOG.warning("Baidu OCR not configured");
            return null;
        }
        try {
            String accessToken = getAccessToken();
            String imageBase64 = Base64.getEncoder().encodeToString(imageBytes);
            String body = "image=" + URLEncoder.encode(imageBase64, "UTF-8")
                    + "&language_type=CHN_ENG"
                    + "&detect_direction=false"
                    + "&paragraph=false"
                    + "&probability=false";

            URL url = new URL(OCR_URL + "?access_token=" + URLEncoder.encode(accessToken, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

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
                LOG.log(Level.WARNING, "Baidu OCR request failed status={0} response={1}", new Object[]{status, sb.toString()});
                return null;
            }

            JsonNode root = objectMapper.readTree(sb.toString());
            StringBuilder text = new StringBuilder();
            JsonNode wordsResult = root.path("words_result");
            if (wordsResult.isArray()) {
                for (JsonNode item : wordsResult) {
                    String word = item.path("words").asText();
                    if (word != null && !word.isEmpty()) {
                        if (text.length() > 0) text.append("\n");
                        text.append(word);
                    }
                }
            }
            String result = text.toString();
            LOG.log(Level.INFO, "Baidu OCR recognized {0} lines", new Object[]{result.isEmpty() ? 0 : result.split("\n").length});
            return result;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Baidu OCR recognize error", e);
            return null;
        }
    }

    private String getAccessToken() throws Exception {
        String tokenUrl = TOKEN_URL + "?grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(apiKey, "UTF-8")
                + "&client_secret=" + URLEncoder.encode(secretKey, "UTF-8");

        URL url = new URL(tokenUrl);
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

        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Baidu OCR OAuth2 failed: " + sb.toString());
        }
        JsonNode resp = objectMapper.readTree(sb.toString());
        String token = resp.path("access_token").asText();
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("Baidu OCR OAuth2 did not return access_token");
        }
        return token;
    }
}
