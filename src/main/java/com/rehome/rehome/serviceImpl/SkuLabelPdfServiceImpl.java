package com.rehome.rehome.serviceImpl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.SkuLabelPdfService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
public class SkuLabelPdfServiceImpl implements SkuLabelPdfService {

    private static final String FONT_REGULAR = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/NotoSans-Bold.ttf";
    private static final int MAX_NAME_LENGTH = 40;
    // Tem 70x50mm (1mm = 2.835pt)
    private static final Rectangle LABEL_SIZE = new Rectangle(198.5f, 141.7f);
    private static final int QR_PIXELS = 300;

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "…";
    }

    @Override
    public byte[] generate(Product product) {
        return render(product);
    }

    // package-private để test riêng phần render
    byte[] render(Product p) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BaseFont regular = loadBaseFont(FONT_REGULAR);
            BaseFont bold = loadBaseFont(FONT_BOLD);

            Document doc = new Document(LABEL_SIZE, 8, 8, 8, 8);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // QR chứa SKU, Moderator quét ở API 41
            Image qr = Image.getInstance(qrPng(p.getId()));
            qr.scaleToFit(70, 70);
            qr.setAlignment(Element.ALIGN_CENTER);
            doc.add(qr);

            Paragraph sku = new Paragraph(p.getId(), new Font(bold, 11));
            sku.setAlignment(Element.ALIGN_CENTER);
            doc.add(sku);

            Paragraph name = new Paragraph(truncate(p.getName(), MAX_NAME_LENGTH), new Font(regular, 8));            name.setAlignment(Element.ALIGN_CENTER);
            doc.add(name);

            if (p.getSize() != null && !p.getSize().isBlank()) {
                Paragraph size = new Paragraph("Size: " + p.getSize(), new Font(regular, 8));
                size.setAlignment(Element.ALIGN_CENTER);
                doc.add(size);
            }

            doc.close();
            return out.toByteArray();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
        }
    }

    private byte[] qrPng(String content) throws Exception {
        BitMatrix matrix = new QRCodeWriter()
                .encode(content, BarcodeFormat.QR_CODE, QR_PIXELS, QR_PIXELS);
        try (ByteArrayOutputStream png = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(matrix, "PNG", png);
            return png.toByteArray();
        }
    }

    private BaseFont loadBaseFont(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new AppException(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
            }
            return BaseFont.createFont(resourcePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    false, in.readAllBytes(), null);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
        }
    }
}