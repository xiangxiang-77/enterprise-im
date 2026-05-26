package com.enterpriseim.server.feature;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.auth.UserAuthService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.web.bind.annotation.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api")
public class LinkPreviewController {
    private final UserAuthService authService;
    private final Map<String, CachedPreview> cache = new ConcurrentHashMap<>();
    private static final int MAX_BODY_BYTES = 512 * 1024;
    private static final int FETCH_TIMEOUT_MS = 5000;
    private static final long CACHE_TTL_MS = 3600_000;

    public LinkPreviewController(UserAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/link-preview")
    public ApiResponse<LinkPreviewDto> preview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                @RequestParam String url) {
        authService.requireUser(authorization);

        if (url == null || url.trim().isEmpty()) {
            return ApiResponse.ok(null);
        }

        String cleanUrl = url.trim();
        CachedPreview cached = cache.get(cleanUrl);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            return ApiResponse.ok(cached.dto);
        }

        LinkPreviewDto dto = fetchPreview(cleanUrl);
        cache.put(cleanUrl, new CachedPreview(dto, System.currentTimeMillis()));
        return ApiResponse.ok(dto);
    }

    private LinkPreviewDto fetchPreview(String url) {
        try {
            URI uri = new URI(url);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(FETCH_TIMEOUT_MS);
            conn.setReadTimeout(FETCH_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "EnterpriseIM-LinkPreview/1.0");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            conn.setInstanceFollowRedirects(true);

            String contentType = conn.getContentType();
            if (contentType == null || !contentType.toLowerCase().contains("html")) {
                conn.disconnect();
                return new LinkPreviewDto(url, null, null, null, null, null);
            }

            InputStream stream = conn.getInputStream();
            byte[] buf = new byte[MAX_BODY_BYTES];
            int total = 0;
            int read;
            while (total < buf.length && (read = stream.read(buf, total, buf.length - total)) >= 0) {
                total += read;
            }
            stream.close();
            conn.disconnect();

            String html = new String(buf, 0, total, StandardCharsets.UTF_8);
            return parseOpenGraph(url, html);
        } catch (Exception e) {
            return new LinkPreviewDto(url, null, null, null, null, "unreachable");
        }
    }

    private LinkPreviewDto parseOpenGraph(String url, String html) {
        String title = metaContent(html, "og:title");
        if (title == null) title = match(html, "<title[^>]*>([^<]+)</title>");
        String description = metaContent(html, "og:description");
        if (description == null) description = metaAttr(html, "name", "description");
        String imageUrl = metaContent(html, "og:image");
        String siteName = metaContent(html, "og:site_name");
        String favicon = extractBase(url) + "/favicon.ico";

        return new LinkPreviewDto(url, title != null ? truncate(title, 200) : null,
                description != null ? truncate(description, 500) : null,
                imageUrl, siteName, null);
    }

    private String metaContent(String html, String property) {
        String value = match(html, "<meta[^>]*property=[\"']" + Pattern.quote(property) + "[\"'][^>]*content=[\"']([^\"']+)[\"']");
        if (value == null) value = match(html, "<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*property=[\"']" + Pattern.quote(property) + "[\"']");
        return value;
    }

    private String metaAttr(String html, String attr, String val) {
        String content = match(html, "<meta[^>]*" + attr + "=[\"']" + Pattern.quote(val) + "[\"'][^>]*content=[\"']([^\"']+)[\"']");
        if (content == null) content = match(html, "<meta[^>]*content=[\"']([^\"']+)[\"'][^>]*" + attr + "=[\"']" + Pattern.quote(val) + "[\"']");
        return content;
    }

    private String match(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractBase(String url) {
        try {
            URI uri = new URI(url);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class LinkPreviewDto {
        private String url;
        private String title;
        private String description;
        private String imageUrl;
        private String siteName;
        private String error;
    }

    private static class CachedPreview {
        final LinkPreviewDto dto;
        final long timestamp;
        CachedPreview(LinkPreviewDto dto, long ts) { this.dto = dto; this.timestamp = ts; }
    }
}
