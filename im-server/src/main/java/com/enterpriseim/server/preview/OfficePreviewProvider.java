package com.enterpriseim.server.preview;

public interface OfficePreviewProvider {
    String name();
    String getPreviewUrl(String fileUrl, String fileType);
}
