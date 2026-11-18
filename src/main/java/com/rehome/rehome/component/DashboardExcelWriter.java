package com.rehome.rehome.component;

import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminReportQueryRepository.SoldItemRow;
import com.rehome.rehome.service.AdminDashboardService.ExportSections;
import com.rehome.rehome.service.AdminDashboardService.UsersExportData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** F-159: dựng file Excel báo cáo dashboard. Khối Doanh thu/Người dùng/Kho xuất theo `sections` Admin đã
 * tick; bảng "Món đã bán" ở sheet riêng luôn có mặt, không phụ thuộc lựa chọn khối. */
@Component
public class DashboardExcelWriter {

    private static final DateTimeFormatter COMPLETED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final short BRAND_GREEN = IndexedColors.DARK_GREEN.getIndex();

    public byte[] write(AdminDashboardResponse d, ExportSections sections, UsersExportData users,
                        List<SoldItemRow> soldItems, LocalDate from, LocalDate to) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);

            writeDashboardSheet(wb, d, sections, users, from, to, styles);
            writeSoldItemsSheet(wb, soldItems, from, to, styles);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.DASHBOARD_EXPORT_FAILED);
        }
    }

    /** Tập CellStyle dùng chung cho cả 2 sheet, tạo 1 lần để tránh lỗi "quá nhiều style" của Excel. */
    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle label;
        final CellStyle money;
        final CellStyle percent;
        final CellStyle count;
        final CellStyle rating;
        final CellStyle text;

        Styles(Workbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            title = wb.createCellStyle();
            title.setFont(titleFont);
            title.setFillForegroundColor(BRAND_GREEN);
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            title.setAlignment(HorizontalAlignment.LEFT);

            Font sectionFont = wb.createFont();
            sectionFont.setBold(true);
            sectionFont.setColor(IndexedColors.WHITE.getIndex());
            section = wb.createCellStyle();
            section.setFont(sectionFont);
            section.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            border(section);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            border(header);

            label = wb.createCellStyle();
            border(label);

            text = wb.createCellStyle();
            border(text);

            money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0 \"đ\""));
            money.setAlignment(HorizontalAlignment.RIGHT);
            border(money);

            percent = wb.createCellStyle();
            percent.setDataFormat(wb.createDataFormat().getFormat("0.00\"%\""));
            percent.setAlignment(HorizontalAlignment.RIGHT);
            border(percent);

            count = wb.createCellStyle();
            count.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            count.setAlignment(HorizontalAlignment.RIGHT);
            border(count);

            rating = wb.createCellStyle();
            rating.setDataFormat(wb.createDataFormat().getFormat("0.0"));
            rating.setAlignment(HorizontalAlignment.RIGHT);
            border(rating);
        }

        private static void border(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }

    private void writeDashboardSheet(Workbook wb, AdminDashboardResponse d, ExportSections sections,
                                     UsersExportData users, LocalDate from, LocalDate to, Styles s) {
        Sheet sheet = wb.createSheet("Dashboard");

        titleRow(sheet, 0, 1, "BÁO CÁO DASHBOARD REHOME — " + fmt(from) + " đến " + fmt(to), s.title);

        Row head = sheet.createRow(2);
        cell(head, 0, "Chỉ số", s.header);
        cell(head, 1, "Giá trị", s.header);

        int r = 3;
        if (sections.revenue()) {
            r = sectionRow(sheet, r, "DOANH THU & LỢI NHUẬN", s.section);
            r = line(sheet, r, "Doanh thu", d.getRevenue(), s.label, s.money);
            r = line(sheet, r, "Số đơn", d.getOrdersCount(), s.label, s.count);
            r = line(sheet, r, "Tiền đang giữ hộ (đơn đã trả, chưa hoàn tất)", d.getHeldOrderAmount(), s.label, s.money);
            r = line(sheet, r, "Số dư ví đang giữ cho Member (hiện tại)", d.getWalletBalanceHeld(), s.label, s.money);
            r = line(sheet, r, "Doanh thu nền tảng", d.getPlatformRevenue(), s.label, s.money);
            r = line(sheet, r, "— Hoa hồng ký gửi", d.getCommissionRevenue(), s.label, s.money);
            r = line(sheet, r, "— Phí rút hàng", d.getWithdrawalFeeRevenue(), s.label, s.money);
            r = line(sheet, r, "— Doanh thu gói thành viên", d.getMembershipRevenue(), s.label, s.money);
            r = line(sheet, r, "Số dư quỹ nền tảng (hiện tại)", d.getFundBalance(), s.label, s.money);
            r = line(sheet, r, "Tổng số món đã bán", d.getTotalSoldItemsCount(), s.label, s.count);
            r = line(sheet, r, "Tỷ suất lợi nhuận ròng", d.getNetProfitMarginPercent(), s.label, s.percent);
            r = line(sheet, r, "Doanh thu bán kỳ 1", d.getTerm1SalesRevenue(), s.label, s.money);
            r = line(sheet, r, "Hoa hồng kỳ 1", d.getTerm1Commission(), s.label, s.money);
            r = line(sheet, r, "Doanh thu bán kỳ 2", d.getTerm2SalesRevenue(), s.label, s.money);
            r = line(sheet, r, "Hoa hồng kỳ 2", d.getTerm2Commission(), s.label, s.money);
            r = line(sheet, r, "Doanh thu bán kỳ 3", d.getTerm3SalesRevenue(), s.label, s.money);
            r = line(sheet, r, "Hoa hồng kỳ 3", d.getTerm3Commission(), s.label, s.money);
        }

        if (users != null) {
            r = sectionRow(sheet, r, "NGƯỜI DÙNG", s.section);
            r = line(sheet, r, "Tổng số Member (hiện tại)", users.memberCount(), s.label, s.count);
            r = line(sheet, r, "Tổng số tổ chức đối tác (hiện tại)", users.organizationCount(), s.label, s.count);
            r = line(sheet, r, "Tổng số nhân viên Moderator (hiện tại)", users.moderatorCount(), s.label, s.count);
            r = line(sheet, r, "Tài khoản mới trong khoảng đã chọn", users.newAccountsCount(), s.label, s.count);
        }

        if (sections.warehouse()) {
            r = sectionRow(sheet, r, "KHO VẬN HÀNH", s.section);
            r = line(sheet, r, "Số món tồn kho (hiện tại)", d.getInventoryCount(), s.label, s.count);
            r = line(sheet, r, "Điểm đánh giá dịch vụ trung bình", d.getAvgServiceRating(), s.label, s.rating);
            r = line(sheet, r, "Số việc quá hạn (hiện tại)", d.getOverdueTaskCount(), s.label, s.count);
            r = line(sheet, r, "Số món tồn kho kỳ 1 (hiện tại)", d.getTerm1StockCount(), s.label, s.count);
            r = line(sheet, r, "Số món tồn kho kỳ 2 (hiện tại)", d.getTerm2StockCount(), s.label, s.count);
            r = line(sheet, r, "Số món tồn kho kỳ 3 (hiện tại)", d.getTerm3StockCount(), s.label, s.count);
            for (Map.Entry<String, Long> entry : d.getStockByStatus().entrySet()) {
                r = line(sheet, r, "Tồn kho trạng thái " + entry.getKey() + " (hiện tại)", entry.getValue(), s.label, s.count);
            }
        }

        sheet.setColumnWidth(0, 52 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.createFreezePane(0, 3);
    }

    private void writeSoldItemsSheet(Workbook wb, List<SoldItemRow> soldItems, LocalDate from, LocalDate to,
                                     Styles s) {
        Sheet sheet = wb.createSheet("Món đã bán");

        titleRow(sheet, 0, 6, "MÓN ĐÃ BÁN (ĐƠN HOÀN THÀNH) — " + fmt(from) + " đến " + fmt(to), s.title);

        Row head = sheet.createRow(2);
        String[] columns = {"Sản phẩm", "Mã đơn hàng", "Giá bán", "Kỳ áp dụng", "Tỷ lệ hoa hồng",
                "Hoa hồng", "Ngày hoàn thành"};
        for (int i = 0; i < columns.length; i++) {
            cell(head, i, columns[i], s.header);
        }

        int r = 3;
        for (SoldItemRow item : soldItems) {
            Row row = sheet.createRow(r++);
            textCell(row, 0, item.productName(), s.text);
            textCell(row, 1, item.orderNo(), s.text);
            moneyCell(row, 2, item.priceAtOrder().setScale(0, RoundingMode.HALF_UP).doubleValue(), s.money);
            textCell(row, 3, item.termApplied() == null ? "—" : "Kỳ " + item.termApplied(), s.text);
            numberCell(row, 4,
                    item.commissionRateApplied().multiply(java.math.BigDecimal.valueOf(100)).doubleValue(), s.percent);
            moneyCell(row, 5, item.priceAtOrder().multiply(item.commissionRateApplied())
                    .setScale(0, RoundingMode.HALF_UP).doubleValue(), s.money);
            textCell(row, 6,
                    item.completedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(COMPLETED_AT_FORMAT), s.text);
        }

        if (!soldItems.isEmpty()) {
            sheet.setAutoFilter(new CellRangeAddress(2, 2, 0, columns.length - 1));
        }

        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        sheet.setColumnWidth(6, 18 * 256);
        sheet.createFreezePane(0, 3);
    }

    private static String fmt(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    // Tiêu đề lớn, nền xanh, gộp ô từ cột 0 đến lastCol để không bị cắt chữ
    private static void titleRow(Sheet sheet, int rowIdx, int lastCol, String text, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(22);
        for (int c = 0; c <= lastCol; c++) {
            cell(row, c, c == 0 ? text : "", style);
        }
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, lastCol));
    }

    // Dòng tiêu đề nhóm (DOANH THU, NGƯỜI DÙNG, KHO VẬN HÀNH), gộp 2 cột Chỉ số/Giá trị
    private static int sectionRow(Sheet sheet, int rowIdx, String text, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        cell(row, 0, text, style);
        cell(row, 1, "", style);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 1));
        return rowIdx + 1;
    }

    private static void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
    }

    private static void textCell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
    }

    private static void moneyCell(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void numberCell(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static int line(Sheet sheet, int rowIdx, String label, double value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowIdx);
        cell(row, 0, label, labelStyle);
        Cell c = row.createCell(1);
        c.setCellValue(value);
        c.setCellStyle(valueStyle);
        return rowIdx + 1;
    }
}
