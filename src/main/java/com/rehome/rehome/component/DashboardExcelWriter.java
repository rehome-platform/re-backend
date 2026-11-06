package com.rehome.rehome.component;

import com.rehome.rehome.dto.response.AdminDashboardResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

/** F-159: dựng file Excel báo cáo dashboard. */
@Component
public class DashboardExcelWriter {

    public byte[] write(AdminDashboardResponse d, LocalDate from, LocalDate to) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Dashboard");

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

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Báo cáo dashboard ReHome: " + from + " đến " + to);
            titleCell.setCellStyle(title);

            Row head = sheet.createRow(2);
            cell(head, 0, "Chỉ số", header);
            cell(head, 1, "Giá trị", header);

            int r = 3;
            r = line(sheet, r, "Doanh thu (đ)", d.getRevenue(), money);
            r = line(sheet, r, "Số đơn", d.getOrdersCount(), null);
            r = line(sheet, r, "Số món tồn kho (hiện tại)", d.getInventoryCount(), null);
            r = line(sheet, r, "Điểm đánh giá dịch vụ trung bình", d.getAvgServiceRating(), null);
            r = line(sheet, r, "Số dư ví đang giữ cho Member (hiện tại, đ)", d.getWalletBalanceHeld(), money);
            r = line(sheet, r, "Doanh thu nền tảng (đ)", d.getPlatformRevenue(), money);
            r = line(sheet, r, "Số dư quỹ nền tảng (hiện tại, đ)", d.getFundBalance(), money);
            r = line(sheet, r, "Số việc quá hạn (hiện tại)", d.getOverdueTaskCount(), null);
            r = line(sheet, r, "Tổng số món đã bán", d.getTotalSoldItemsCount(), null);
            r = line(sheet, r, "Tỷ suất lợi nhuận ròng (%)", d.getNetProfitMarginPercent(), null);
            r = line(sheet, r, "Doanh thu bán kỳ 1 (đ)", d.getTerm1SalesRevenue(), money);
            r = line(sheet, r, "Hoa hồng kỳ 1 (đ)", d.getTerm1Commission(), money);
            r = line(sheet, r, "Doanh thu bán kỳ 2 (đ)", d.getTerm2SalesRevenue(), money);
            r = line(sheet, r, "Hoa hồng kỳ 2 (đ)", d.getTerm2Commission(), money);
            r = line(sheet, r, "Doanh thu bán kỳ 3 (đ)", d.getTerm3SalesRevenue(), money);
            r = line(sheet, r, "Hoa hồng kỳ 3 (đ)", d.getTerm3Commission(), money);
            r = line(sheet, r, "Số món tồn kho kỳ 1 (hiện tại)", d.getTerm1StockCount(), null);
            r = line(sheet, r, "Số món tồn kho kỳ 2 (hiện tại)", d.getTerm2StockCount(), null);
            line(sheet, r, "Số món tồn kho kỳ 3 (hiện tại)", d.getTerm3StockCount(), null);

            sheet.setColumnWidth(0, 52 * 256);
            sheet.setColumnWidth(1, 22 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AppException(ErrorCode.DASHBOARD_EXPORT_FAILED);
        }
    }

    private static void cell(Row row, int col, String text, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(text);
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
