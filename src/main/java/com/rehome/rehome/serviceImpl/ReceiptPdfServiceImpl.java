package com.rehome.rehome.serviceImpl;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.ReceiptPdfService;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ReceiptPdfServiceImpl implements ReceiptPdfService {

    private static final String FONT_REGULAR = "/fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/NotoSans-Bold.ttf";
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color BORDER_COLOR = new Color(200, 200, 200);

    @Override
    public byte[] generate(ConsignmentReceipt receipt) {
        return render(receipt);
    }

    // package-private để test riêng phần render
    byte[] render(ConsignmentReceipt r) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Fonts fonts = loadFonts();

            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter.getInstance(doc, out);
            doc.open();

            // Tiêu đề
            Paragraph title = new Paragraph("BIÊN NHẬN KÝ GỬI", fonts.title);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph number = new Paragraph("Số: " + safe(r.getReceiptNo()), fonts.normal);
            number.setAlignment(Element.ALIGN_CENTER);
            doc.add(number);

            int termDays = r.getTermLengthDays();
            int totalDays = termDays * (r.getMaxRenewals() + 1);

            // I. Các bên
            doc.add(heading("I. Các bên", fonts));
            PdfPTable parties = table();
            row(parties, "Bên A (Moderator tiếp nhận)", safe(r.getModerator().getUsername()), fonts);
            row(parties, "Bên B (Người ký gửi)", safe(r.getMember().getUsername()), fonts);
            doc.add(parties);

            // II. Sản phẩm và kho
            doc.add(heading("II. Sản phẩm và kho nhận hàng", fonts));
            PdfPTable goods = table();
            row(goods, "Sản phẩm", safe(r.getConsignmentForm().getProductName()), fonts);
            row(goods, "Kho nhận hàng",
                    safe(r.getWarehouse().getName()) + " - " + safe(r.getWarehouse().getAddress()), fonts);
            doc.add(goods);

            // III. Giá và phí
            doc.add(heading("III. Giá và phí", fonts));
            PdfPTable prices = table();
            row(prices, "Giá thỏa thuận", money(r.getAgreedPrice()), fonts);
            row(prices, "Giá sàn (không được hạ thấp hơn)", money(r.getFloorPrice()), fonts);
            row(prices, "Phí rút hàng ngoài cửa sổ miễn phí", money(r.getWithdrawalFee()), fonts);
            row(prices, "Tỷ lệ đền bù khi nhận lại hàng xuống cấp", percent(r.getDegradedCompensationRate()), fonts);
            doc.add(prices);

            // IV. Kỳ ký gửi và hoa hồng
            doc.add(heading("IV. Kỳ ký gửi và hoa hồng", fonts));
            PdfPTable terms = table();
            row(terms, "Độ dài mỗi kỳ", termDays + " ngày", fonts);
            row(terms, "Số lần gia hạn tối đa", r.getMaxRenewals() + " lần", fonts);
            row(terms, "Tổng thời gian ký gửi tối đa", totalDays + " ngày", fonts);
            row(terms, "Rút hàng miễn phí trong",
                    r.getFreeWithdrawalWindowDays() + " ngày đầu của mỗi kỳ", fonts);
            row(terms, "Hoa hồng kỳ 1 (ngày 0-" + termDays + ")",
                    percent(r.getCommissionRateTerm1()), fonts);
            row(terms, "Hoa hồng kỳ 2 (ngày " + (termDays + 1) + "-" + (termDays * 2) + ")",
                    percent(r.getCommissionRateTerm2()), fonts);
            row(terms, "Hoa hồng kỳ 3 (ngày " + (termDays * 2 + 1) + "-" + (termDays * 3) + ")",
                    percent(r.getCommissionRateTerm3()), fonts);
            doc.add(terms);

            // V. Xác nhận
            doc.add(heading("V. Xác nhận", fonts));
            String confirmedAt = r.getConfirmedAt() == null
                    ? "-"
                    : DATE_TIME.format(r.getConfirmedAt().atZone(VN_ZONE));
            Paragraph confirm = new Paragraph(
                    "Bên B đã xác nhận đồng ý toàn bộ nội dung trên bằng thao tác xác nhận điện tử vào lúc "
                            + confirmedAt + " (giờ Việt Nam). Các mức giá, phí và tỷ lệ trong phiếu được "
                            + "ghi nhận tại thời điểm xác nhận và không thay đổi theo cấu hình hệ thống về sau.",
                    fonts.normal);
            confirm.setSpacingBefore(4);
            doc.add(confirm);

            doc.close();
            return out.toByteArray();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Fonts(Font title, Font heading, Font normal, Font bold) {
    }

    private Fonts loadFonts() {
        BaseFont regular = loadBaseFont(FONT_REGULAR);
        BaseFont bold = loadBaseFont(FONT_BOLD);
        return new Fonts(
                new Font(bold, 18),
                new Font(bold, 12),
                new Font(regular, 10),
                new Font(bold, 10));
    }

    private BaseFont loadBaseFont(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new AppException(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
            }
            byte[] bytes = in.readAllBytes();
            // Tên phải kết thúc bằng .ttf; IDENTITY_H + EMBEDDED để hiển thị đủ dấu tiếng Việt
            return BaseFont.createFont(resourcePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    false, bytes, null);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
        }
    }

    private Paragraph heading(String text, Fonts fonts) {
        Paragraph p = new Paragraph(text, fonts.heading);
        p.setSpacingBefore(14);
        p.setSpacingAfter(4);
        return p;
    }

    private PdfPTable table() {
        PdfPTable t = new PdfPTable(new float[] {40, 60});
        t.setWidthPercentage(100);
        return t;
    }

    private void row(PdfPTable t, String label, String value, Fonts fonts) {
        t.addCell(cell(label, fonts.bold));
        t.addCell(cell(value, fonts.normal));
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(5);
        c.setBorderColor(BORDER_COLOR);
        return c;
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        NumberFormat nf = NumberFormat.getInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(value) + " VNĐ";
    }

    private String percent(BigDecimal rate) {
        if (rate == null) {
            return "-";
        }
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}