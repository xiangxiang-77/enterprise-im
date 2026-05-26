package com.enterpriseim.server.preview;

import com.enterpriseim.server.config.ImProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OnlyOfficePreviewProvider implements OfficePreviewProvider {
    private static final Logger LOG = Logger.getLogger(OnlyOfficePreviewProvider.class.getName());

    private final String apiUrl;
    private final String secretKey;

    public OnlyOfficePreviewProvider(ImProperties properties) {
        ImProperties.OnlyOffice cfg = properties.getOnlyoffice();
        this.apiUrl = cfg.getApiUrl();
        this.secretKey = cfg.getSecretKey();
    }

    @Override
    public String name() {
        return "onlyoffice";
    }

    @Override
    public String getPreviewUrl(String fileUrl, String fileType) {
        if (apiUrl == null || apiUrl.isEmpty()) {
            LOG.warning("OnlyOffice API URL not configured");
            return null;
        }
        try {
            String encodedFileUrl = URLEncoder.encode(fileUrl, StandardCharsets.UTF_8.name());
            String encodedType = URLEncoder.encode(fileType != null ? fileType : "docx", StandardCharsets.UTF_8.name());

            StringBuilder previewUrl = new StringBuilder();
            previewUrl.append(apiUrl);
            if (!apiUrl.endsWith("/")) {
                previewUrl.append("/");
            }
            previewUrl.append("doceditor?fileUrl=").append(encodedFileUrl);
            previewUrl.append("&fileType=").append(encodedType);
            previewUrl.append("&mode=view");
            previewUrl.append("&action=view");

            LOG.log(Level.INFO, "OnlyOffice preview URL generated for file: {0}", fileUrl);
            return previewUrl.toString();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "OnlyOffice preview URL generation error", e);
            return null;
        }
    }
}
