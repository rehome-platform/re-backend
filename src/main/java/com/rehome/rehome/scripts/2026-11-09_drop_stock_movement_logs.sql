-- Chạy tay trên PostgreSQL. ddl-auto=update không tự drop bảng khi entity bị xoá khỏi code.
-- stock_movement_logs bị gỡ: chỉ có 2 điểm ghi (STOCK_IN lúc tạo sản phẩm, WITHDRAWAL_OUT lúc
-- bàn giao rút hàng giữa kỳ), không có bảng nào khác FK vào nó, và nghiệp vụ quản lý vị trí
-- kệ/di chuyển kho không còn được theo dõi. Chạy lại nhiều lần vẫn an toàn.
DROP TABLE IF EXISTS stock_movement_logs;
