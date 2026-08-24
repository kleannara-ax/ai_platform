package com.company.module.kims.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * 문자열(주로 URL)을 QR 코드 PNG 이미지(byte[])로 변환하는 헬퍼.
 */
@Component
public class QrCodeGenerator {

    public byte[] pngOf(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", out);
                return out.toByteArray();
            }
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("QR 이미지 생성 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            throw new IllegalStateException("QR 코드 생성에 실패했습니다.", e);
        }
    }
}
