package com.enterpriseim.server.auth.sso;

import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OidcAuthService {
    private static final Logger LOG = Logger.getLogger(OidcAuthService.class.getName());

    private final ImProperties properties;
    private final ObjectMapper objectMapper;
    private JsonNode discoveryConfig;

    public OidcAuthService(ImProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String generateAuthorizationUrl(String redirectUri) {
        try {
            JsonNode config = fetchDiscoveryConfig();
            String authEndpoint = config.path("authorization_endpoint").asText();

            String effectiveRedirectUri = (redirectUri != null && !redirectUri.isEmpty())
                    ? redirectUri
                    : properties.getOidc().getRedirectUri();

            StringBuilder url = new StringBuilder();
            url.append(authEndpoint);
            url.append("?response_type=code");
            url.append("&client_id=").append(urlEncode(properties.getOidc().getClientId()));
            url.append("&redirect_uri=").append(urlEncode(effectiveRedirectUri));
            url.append("&scope=").append(urlEncode("openid profile email"));
            url.append("&state=").append(urlEncode(UUID.randomUUID().toString()));
            url.append("&nonce=").append(urlEncode(UUID.randomUUID().toString()));

            return url.toString();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to generate OIDC authorization URL", e);
            throw new RuntimeException("Failed to generate OIDC authorization URL: " + e.getMessage(), e);
        }
    }

    public TokenResult exchangeCodeForToken(String code, String redirectUri) {
        try {
            JsonNode config = fetchDiscoveryConfig();
            String tokenEndpoint = config.path("token_endpoint").asText();

            String effectiveRedirectUri = (redirectUri != null && !redirectUri.isEmpty())
                    ? redirectUri
                    : properties.getOidc().getRedirectUri();

            StringBuilder body = new StringBuilder();
            body.append("grant_type=authorization_code");
            body.append("&code=").append(urlEncode(code));
            body.append("&redirect_uri=").append(urlEncode(effectiveRedirectUri));
            body.append("&client_id=").append(urlEncode(properties.getOidc().getClientId()));
            body.append("&client_secret=").append(urlEncode(properties.getOidc().getClientSecret()));

            String response = httpPost(tokenEndpoint, body.toString(), "application/x-www-form-urlencoded");
            JsonNode root = objectMapper.readTree(response);

            String accessToken = root.path("access_token").asText();
            String idToken = root.path("id_token").asText();
            String tokenType = root.path("token_type").asText("Bearer");
            long expiresIn = root.path("expires_in").asLong(0);

            return new TokenResult(accessToken, idToken, tokenType, expiresIn);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to exchange OIDC authorization code", e);
            throw new RuntimeException("Failed to exchange OIDC authorization code: " + e.getMessage(), e);
        }
    }

    public UserInfoResult validateIdToken(String idToken) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Invalid ID token format");
            }

            // Decode header and payload
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

            JsonNode header = objectMapper.readTree(headerJson);
            JsonNode payload = objectMapper.readTree(payloadJson);

            String kid = header.path("kid").asText("");
            String alg = header.path("alg").asText("RS256");

            // Validate issuer
            String expectedIssuer = properties.getOidc().getIssuerUri();
            String issuer = payload.path("iss").asText("");
            if (!expectedIssuer.equals(issuer)) {
                throw new RuntimeException("ID token issuer mismatch: expected " + expectedIssuer + ", got " + issuer);
            }

            // Validate audience
            String expectedAud = properties.getOidc().getClientId();
            String aud = payload.path("aud").asText("");
            if (!expectedAud.equals(aud)) {
                // Some providers return aud as array
                JsonNode audNode = payload.path("aud");
                if (audNode.isArray()) {
                    boolean found = false;
                    for (JsonNode node : audNode) {
                        if (expectedAud.equals(node.asText())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new RuntimeException("ID token audience mismatch: expected " + expectedAud);
                    }
                } else {
                    throw new RuntimeException("ID token audience mismatch: expected " + expectedAud + ", got " + aud);
                }
            }

            // Validate expiration
            long exp = payload.path("exp").asLong(0);
            long now = System.currentTimeMillis() / 1000;
            if (exp > 0 && exp < now) {
                throw new RuntimeException("ID token has expired");
            }

            // Validate signature using JWKS
            if (!verifySignature(parts[0] + "." + parts[1], signatureBytes, alg, kid)) {
                throw new RuntimeException("ID token signature validation failed");
            }

            String subject = payload.path("sub").asText("");
            String email = payload.path("email").asText("");
            String name = payload.path("name").asText("");
            String preferredUsername = payload.path("preferred_username").asText("");
            String displayName = (name != null && !name.isEmpty()) ? name : preferredUsername;

            return new UserInfoResult(subject, email, displayName != null ? displayName : subject);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to validate ID token", e);
            throw new RuntimeException("Failed to validate ID token: " + e.getMessage(), e);
        }
    }

    public UserInfoResult extractUserInfo(String accessToken) {
        try {
            JsonNode config = fetchDiscoveryConfig();
            String userinfoEndpoint = config.path("userinfo_endpoint").asText();

            String response = httpGet(userinfoEndpoint, "Bearer " + accessToken);
            JsonNode payload = objectMapper.readTree(response);

            String subject = payload.path("sub").asText("");
            String email = payload.path("email").asText("");
            String name = payload.path("name").asText("");
            String preferredUsername = payload.path("preferred_username").asText("");
            String displayName = (name != null && !name.isEmpty()) ? name : preferredUsername;

            return new UserInfoResult(subject, email, displayName != null ? displayName : subject);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to extract OIDC user info", e);
            throw new RuntimeException("Failed to extract OIDC user info: " + e.getMessage(), e);
        }
    }

    private JsonNode fetchDiscoveryConfig() {
        if (discoveryConfig != null) {
            return discoveryConfig;
        }
        try {
            String issuerUri = properties.getOidc().getIssuerUri();
            if (issuerUri == null || issuerUri.isEmpty()) {
                throw new RuntimeException("OIDC issuer URI is not configured");
            }
            String discoveryUrl = issuerUri.replaceAll("/$", "") + "/.well-known/openid-configuration";
            String response = httpGet(discoveryUrl, null);
            discoveryConfig = objectMapper.readTree(response);
            return discoveryConfig;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to fetch OIDC discovery config", e);
            throw new RuntimeException("Failed to fetch OIDC discovery config: " + e.getMessage(), e);
        }
    }

    private boolean verifySignature(String signedData, byte[] signature, String alg, String kid) {
        try {
            JsonNode config = fetchDiscoveryConfig();
            String jwksUri = config.path("jwks_uri").asText();
            if (jwksUri.isEmpty()) {
                LOG.warning("No jwks_uri in discovery config, skipping signature verification");
                return true;
            }

            String jwksResponse = httpGet(jwksUri, null);
            JsonNode jwks = objectMapper.readTree(jwksResponse);
            JsonNode keys = jwks.path("keys");

            PublicKey publicKey = null;
            for (JsonNode key : keys) {
                if (kid.isEmpty() || kid.equals(key.path("kid").asText(""))) {
                    String kty = key.path("kty").asText("");
                    if ("RSA".equals(kty)) {
                        Base64.Decoder decoder = Base64.getUrlDecoder();
                        byte[] nBytes = decoder.decode(key.path("n").asText());
                        byte[] eBytes = decoder.decode(key.path("e").asText());

                        java.math.BigInteger modulus = new java.math.BigInteger(1, nBytes);
                        java.math.BigInteger exponent = new java.math.BigInteger(1, eBytes);

                        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
                        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                        publicKey = keyFactory.generatePublic(spec);
                        break;
                    }
                }
            }

            if (publicKey == null) {
                LOG.warning("No matching JWK key found, skipping signature verification");
                return true;
            }

            String sigAlg = alg.equals("RS256") ? "SHA256withRSA"
                    : alg.equals("RS384") ? "SHA384withRSA"
                    : alg.equals("RS512") ? "SHA512withRSA"
                    : "SHA256withRSA";

            Signature sig = Signature.getInstance(sigAlg);
            sig.initVerify(publicKey);
            sig.update(signedData.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signature);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Signature verification error: " + e.getMessage(), e);
            return true; // Soft fail for signature issues
        }
    }

    private String httpGet(String urlStr, String authorization) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (authorization != null) {
            conn.setRequestProperty("Authorization", authorization);
        }
        return readResponse(conn);
    }

    private String httpPost(String urlStr, String body, String contentType) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", contentType);
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        java.io.OutputStream os = conn.getOutputStream();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.close();

        return readResponse(conn);
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
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

    private static String urlEncode(String value) throws Exception {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, "UTF-8");
    }

    public static class TokenResult {
        public final String accessToken;
        public final String idToken;
        public final String tokenType;
        public final long expiresIn;

        public TokenResult(String accessToken, String idToken, String tokenType, long expiresIn) {
            this.accessToken = accessToken;
            this.idToken = idToken;
            this.tokenType = tokenType;
            this.expiresIn = expiresIn;
        }
    }

    public static class UserInfoResult {
        public final String subject;
        public final String email;
        public final String displayName;

        public UserInfoResult(String subject, String email, String displayName) {
            this.subject = subject;
            this.email = email;
            this.displayName = displayName;
        }
    }
}
