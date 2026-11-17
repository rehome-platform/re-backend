package com.rehome.rehome.component;

import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AdminReportQueryRepository.SoldItemRow;
import com.rehome.rehome.service.AdminDashboardService.ExportSections;
import com.rehome.rehome.service.AdminDashboardService.UsersExportData;
import org.apache.poi.ss.usermodel.*;
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

    public byte[] write(AdminDashboardResponse d, ExportSections sections, UsersExportData users,
                        List<SoldItemRow> soldItems, LocalDate from, LocalDate to) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle title = wb.createCellStyle();
            title.setFont(bold);
            CellStyle header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            writeDashboardSheet(wb, d, sections, users, from, to, title, header, money);
            writeSoldItemsSheet(wb, soldItems, from, to, title, header, money);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.DASHBOARD_EXPORT_FAILED);
        }
    }

    private void writeDashboardSheet(Workbook wb, AdminDashboardResponse d, ExportSections sections,
                                     UsersExportData users, LocalDate from, LocalDate to,
                                     CellStyle title, CellStyle header, CellStyle money) {
        Sheet sheet = wb.createSheet("Dashboard");

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Báo cáo dashboard ReHome: " + from + " đến " + to);
        titleCell.setCellStyle(title);

        Row head = sheet.createRow(2);
        cell(head, 0, "Chỉ số", header);
        cell(head, 1, "Giá trị", header);

        int r = 3;
        if (sections.revenue()) {
            r = line(sheet, r, "Doanh thu (đ)", d.getRevenue(), money);
            r = line(sheet, r, "Số đơn", d.getOrdersCount(), null);
            r = line(sheet, r, "Tiền đang giữ hộ (đơn đã trả, chưa hoàn tất, đ)", d.getHeldOrderAmount(), money);
            r = line(sheet, r, "Số dư ví đang giữ cho Member (hiện tại, đ)", d.getWalletBalanceHeld(), money);
            r = line(sheet, r, "Doanh thu nền tảng (đ)", d.getPlatformRevenue(), money);
            r = line(sheet, r, "Số dư quỹ nền tảng (hiện tại, đ)", d.getFundBalance(), money);
            r = line(sheet, r, "Tổng số món đã bán", d.getTotalSoldItemsCount(), null);
            r = line(sheet, r, "Tỷ suất lợi nhuận ròng (%)", d.getNetProfitMarginPercent(), null);
            r = line(sheet, r, "Doanh thu bán kỳ 1 (đ)", d.getTerm1SalesRevenue(), money);
            r = line(sheet, r, "Hoa hồng kỳ 1 (đ)", d.getTerm1Commission(), money);
            r = line(sheet, r, "Doanh thu bán kỳ 2 (đ)", d.getTerm2SalesRevenue(), money);
            r = line(sheet, r, "Hoa hồng kỳ 2 (đ)", d.getTerm2Commission(), money);
            r = line(sheet, r, "Doanh thu bán kỳ 3 (đ)", d.getTerm3SalesRevenue(), money);
            r = line(sheet, r, "Hoa hồng kỳ 3 (đ)", d.getTerm3Commission(), money);
            r += 1;
        }

        if (users != null) {
            r = line(sheet, r, "Tổng số Member (hiện tại)", users.memberCount(), null);
            r = line(sheet, r, "Tổng số tổ chức đối tác (hiện tại)", users.organizationCount(), null);
            r = line(sheet, r, "Tổng số nhân viên Moderator (hiện tại)", users.moderatorCount(), null);
            r = line(sheet, r, "Tài khoản mới trong khoảng đã chọn", users.newAccountsCount(), null);
            r += 1;
        }

        if (sections.warehouse()) {
            r = line(sheet, r, "Số món tồn kho (hiện tại)", d.getInventoryCount(), null);
            r = line(sheet, r, "Điểm đánh giá dịch vụ trung bình", d.getAvgServiceRating(), null);
            r = line(sheet, r, "Số việc quá hạn (hiện tại)", d.getOverdueTaskCount(), null);
            r = line(sheet, r, "Số món tồn kho kỳ 1 (hiện tại)", d.getTerm1StockCount(), null);
            r = line(sheet, r, "Số món tồn kho kỳ 2 (hiện tại)", d.getTerm2StockCount(), null);
            r = line(sheet, r, "Số món tồn kho kỳ 3 (hiện tại)", d.getTerm3StockCount(), null);
            for (Map.Entry<String, Long> entry : d.getStockByStatus().entrySet()) {
                r = line(sheet, r, "Tồn kho trạng thái " + entry.getKey() + " (hiện tại)", entry.getValue(), null);
            }
        }

        sheet.setColumnWidth(0, 52 * 256);
        sheet.setColumnWidth(1, 22 * 256);
    }

    private void writeSoldItemsSheet(Workbook wb, List<SoldItemRow> soldItems, LocalDate from, LocalDate to,
                                     CellStyle title, CellStyle header, CellStyle money) {
        Sheet sheet = wb.createSheet("Món đã bán");

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Món đã bán (đơn hoàn thành) từ " + from + " đến " + to);
        titleCell.setCellStyle(title);

        Row head = sheet.createRow(2);
        String[] columns = {"Sản phẩm", "Mã đơn hàng", "Giá bán (đ)", "Kỳ áp dụng", "Tỷ lệ hoa hồng (%)",
                "Hoa hồng (đ)", "Ngày hoàn thành"};
        for (int i = 0; i < columns.length; i++) {
            cell(head, i, columns[i], header);
        }

        int r = 3;
        for (SoldItemRow item : soldItems) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(item.productName());
            row.createCell(1).setCellValue(item.orderNo());
            moneyCell(row, 2, item.priceAtOrder().setScale(0, RoundingMode.HALF_UP).doubleValue(), money);
            row.createCell(3).setCellValue(item.termApplied() == null ? "—" : "Kỳ " + item.termApplied());
            row.createCell(4).setCellValue(
                    item.commissionRateApplied().multiply(java.math.BigDecimal.valueOf(100)).doubleValue());
            moneyCell(row, 5, item.priceAtOrder().multiply(item.commissionRateApplied())
                    .setScale(0, RoundingMode.HALF_UP).doubleValue(), money);
            row.createCell(6).setCellValue(
                    item.completedAt().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(COMPLETED_AT_FORMAT));
        }

        sheet.setColumnWidth(0, 40 * 256);
        sheet.setColumnWidth(1, 18 * 256);
        sheet.setColumnWidth(2, 16 * 256);
        sheet.setColumnWidth(3, 12 * 256);
        sheet.setColumnWidth(4, 16 * 256);
        sheet.setColumnWidth(5, 16 * 256);
        sheet.setColumnWidth(6, 18 * 256);
    }

    private static void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
        c.setCellStyle(style);
    }

    private static void moneyCell(Row row, int col, double value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static int line(Sheet sheet, int rowIdx, String label, double value, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        Cell c = row.createCell(1);
        c.setCellValue(value);
        if (style != null) c.setCellStyle(style);
        return rowIdx + 1;
    }
}
