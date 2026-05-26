package com.enterpriseim.server.ocr;

public interface OcrProvider {
    String name();
    String recognize(byte[] imageBytes, String format);
}
