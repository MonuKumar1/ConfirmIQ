package com.meridian.confirmiq.document;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * QR codes printed on house paper carry {@code CIQ1|<tradeRef>|v<economicVersion>|<termsHash prefix>}.
 * On return, the code identifies the trade without OCR and shows whether the paper is current.
 */
public final class QrCodes {

    public static final String PREFIX = "CIQ1";

    private QrCodes() {}

    public static String payload(String tradeRef, int economicVersion, String termsHash) {
        return String.join("|", PREFIX, tradeRef, "v" + economicVersion, termsHash.substring(0, 16));
    }

    public static BufferedImage encode(String payload, int size) {
        try {
            var bits = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            return MatrixToImageWriter.toBufferedImage(bits);
        } catch (WriterException e) {
            throw new IllegalStateException("QR encoding failed", e);
        }
    }

    /** Renders each page and returns the first ConfirmIQ QR payload found. */
    public static Optional<Payload> readFromPdf(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int page = doc.getNumberOfPages() - 1; page >= 0; page--) {
                BufferedImage image = renderer.renderImageWithDPI(page, 150, ImageType.GRAY);
                Optional<Payload> found = decode(image).flatMap(Payload::parse);
                if (found.isPresent()) {
                    return found;
                }
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<String> decode(BufferedImage image) {
        try {
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            Result result = new MultiFormatReader().decode(bitmap, Map.of(
                    DecodeHintType.TRY_HARDER, Boolean.TRUE,
                    DecodeHintType.POSSIBLE_FORMATS, java.util.List.of(BarcodeFormat.QR_CODE)));
            return Optional.of(result.getText());
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }

    public record Payload(String tradeRef, int economicVersion, String termsHashPrefix) {
        static Optional<Payload> parse(String text) {
            String[] parts = text.split("\\|");
            if (parts.length != 4 || !PREFIX.equals(parts[0]) || !parts[2].startsWith("v")) {
                return Optional.empty();
            }
            try {
                return Optional.of(new Payload(parts[1], Integer.parseInt(parts[2].substring(1)), parts[3]));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
