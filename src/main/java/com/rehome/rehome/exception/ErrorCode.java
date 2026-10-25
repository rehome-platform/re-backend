package com.rehome.rehome.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "Tài khoản đã bị khoá!"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư."),
    WRONG_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu cũ không đúng."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Token không hợp lệ hoặc đã hết hạn."),
    GOOGLE_ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "Tài khoản gmail đã được sử dụng."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp."),
    PASSWORD_NOT_SET(HttpStatus.BAD_REQUEST, "Tài khoản chưa có mật khẩu."),
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu cũ."),
    INVALID_COORDINATES(HttpStatus.BAD_REQUEST, "lat/lng không hợp lệ."),
    // API 3, 4 - hồ sơ tổ chức
    ORGANIZATION_PROFILE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tài khoản này đã có hồ sơ tổ chức."),
    ORGANIZATION_EMAIL_TAKEN(HttpStatus.CONFLICT, "Email liên hệ đã được sử dụng."),
    ORGANIZATION_TAX_CODE_TAKEN(HttpStatus.CONFLICT, "Mã số thuế đã được sử dụng."),
    ORGANIZATION_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ tổ chức."),
    ORGANIZATION_NOT_REJECTED(HttpStatus.FORBIDDEN, "Chỉ nộp lại hồ sơ khi trạng thái là REJECTED."),

    // API 31 - Moderator duyệt form ký gửi
    CONSIGNMENT_FORM_NOT_PENDING(HttpStatus.CONFLICT, "Form không ở trạng thái chờ duyệt."),

    // API 33 - bắt đầu kiểm định
    CONSIGNMENT_FORM_NOT_APPROVED(HttpStatus.CONFLICT, "Form chưa được duyệt để kiểm định."),
    // API 37 - nhập kho
    RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu ký gửi."),
    RECEIPT_NOT_CONFIRMED(HttpStatus.CONFLICT, "Phiếu ký gửi chưa được Member xác nhận."),
    RECEIPT_ALREADY_HAS_PRODUCT(HttpStatus.CONFLICT, "Phiếu ký gửi này đã được nhập kho."),
    // API 34 - hoàn tất kiểm định
    CONSIGNMENT_RECEIPT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Form này đã được tạo phiếu ký gửi."),
    // API 35 - trả hàng tại quầy
    CONSIGNMENT_FORM_NOT_INSPECTING(HttpStatus.CONFLICT, "Form chưa ở trạng thái đang kiểm định."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."),
    CONSIGNMENT_RECEIPT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xem form đã duyệt này."),
    CONSIGNMENT_RECEIPT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy form đã duyệt."),
    CONSIGNMENT_FORM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy form ký gửi."),
    CONSIGNMENT_FORM_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xem form này."),
    NOT_MEMBER_ACCOUNT(HttpStatus.FORBIDDEN, "Chỉ tài khoản member mới coi được danh sách ký gửi."),
    // API 20 - xác nhận phiếu ký gửi, F-022 - sinh PDF
    CONSIGNMENT_RECEIPT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "Phiếu này đã được xác nhận."),
    RECEIPT_PDF_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo file PDF phiếu ký gửi."),
    NOT_ORGANIZATION_ACCOUNT(HttpStatus.FORBIDDEN, "Chỉ tài khoản tổ chức mới được nộp hồ sơ."),
    // ===== Auth =====
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh token không tồn tại."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn. Vui lòng đăng nhập lại."),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "Google token không hợp lệ."),
    // ===== Consignment form - tạo form =====
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục."),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thương hiệu."),
    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kho."),
    NOT_MEMBER_CREATE_FORM(HttpStatus.FORBIDDEN, "Chỉ tài khoản member mới được tạo form ký gửi."),
    // API 23 - giảm giá sản phẩm (F-025, F-026)
    INVALID_NEW_PRICE(HttpStatus.BAD_REQUEST, "Giá mới phải thấp hơn giá hiện tại."),
    PRODUCT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền giảm giá sản phẩm này."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "Chỉ giảm giá được sản phẩm đang bán."),
    BELOW_FLOOR_PRICE(HttpStatus.CONFLICT, "BELOW_FLOOR_PRICE: giá mới không được thấp hơn giá sàn."),
    PRICE_CHANGE_LIMIT_REACHED(HttpStatus.CONFLICT, "PRICE_CHANGE_LIMIT_REACHED: mỗi sản phẩm chỉ giảm giá tối đa 1 lần/ngày."),
    // API 24 - quyết định cuối kỳ (F-029)
    END_OF_TERM_DECISION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu quyết định cuối kỳ."),
    END_OF_TERM_DECISION_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền quyết định cho sản phẩm này."),
    END_OF_TERM_DECISION_ALREADY_DECIDED(HttpStatus.CONFLICT, "Yêu cầu này đã được quyết định."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "Chỉ được chọn TAKE_BACK hoặc RE_CONSIGN."),
    DECISION_EXPIRED(HttpStatus.CONFLICT, "DECISION_EXPIRED: đã quá hạn quyết định, hàng chuyển sang xử lý vô thừa nhận."),

    // API 25, 27 - quầy
    LOOKUP_INVALID_INPUT(HttpStatus.BAD_REQUEST, "Phải cung cấp memberCode, phone hoặc qrPayload."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy Member."),
    // API 28, 29, 30 - hàng đợi
    INVALID_QUEUE_TYPE(HttpStatus.BAD_REQUEST, "type không hợp lệ."),
    QUEUE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy việc."),
    QUEUE_ITEM_ALREADY_CLAIMED(HttpStatus.CONFLICT, "ALREADY_CLAIMED: việc này đã được người khác nhận."),
    QUEUE_ITEM_NOT_CLAIMED_BY_YOU(HttpStatus.FORBIDDEN, "Bạn không phải người đang nhận việc này."),

    // API 34 - hoàn tất kiểm định
    INVALID_INSPECTION_RESULT(HttpStatus.BAD_REQUEST, "result phải là PASS."),
    INVALID_AGREED_PRICE(HttpStatus.BAD_REQUEST, "agreedPrice phải lớn hơn 0."),
    INVALID_TERM_LENGTH_DAYS(HttpStatus.BAD_REQUEST, "termLengthDays phải lớn hơn 0."),
    // API 35 - trả hàng tại quầy
    INVALID_RETURN_ONSITE_RESULT(HttpStatus.BAD_REQUEST, "result phải là FAIL, SUSPECTED_FAKE, SUSPECTED_ILLEGAL hoặc MEMBER_DECLINED."),
    REASON_REQUIRED(HttpStatus.BAD_REQUEST, "reason là bắt buộc."),
    // API 36 - hàng vô thừa nhận
    PARTNER_ORGANIZATION_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "partnerOrganizationName là bắt buộc."),
    PRODUCT_NOT_UNCLAIMED(HttpStatus.CONFLICT, "Sản phẩm chưa ở trạng thái vô thừa nhận."),
    // API 46 - tạo thương hiệu (F-143, F-121)
    BRAND_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên thương hiệu đã tồn tại."),
    // API 45, 47 - danh mục
    INVALID_TRYON_SLOT(HttpStatus.BAD_REQUEST, "tryonSlot phải là TOP, BOTTOM hoặc DRESS."),
    CATEGORY_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên danh mục đã tồn tại."),
    // API 44 - lọc danh mục/thương hiệu
    INVALID_STATUS(HttpStatus.BAD_REQUEST, "status không hợp lệ."),
    // API 42 - đổi vị trí kệ
    PRODUCT_NOT_IN_YOUR_WAREHOUSE(HttpStatus.FORBIDDEN, "Sản phẩm không thuộc kho bạn quản lý."),
    // API 39 - tem SKU
    SKU_LABEL_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo file PDF tem SKU."),
    PHONE_TAKEN(HttpStatus.CONFLICT, "Số điện thoại này đã được sử dụng."),
    // API 40 - danh sách hàng trong kho
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token đã hết hạn."),
    ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Access token không hợp lệ."),
    MODERATOR_NO_WAREHOUSE(HttpStatus.FORBIDDEN, "Tài khoản chưa được gán kho."),
    // API 26, 72 - xác nhận danh tính & bàn giao tại quầy (F-129)
    COUNTER_CONTEXT_INVALID(HttpStatus.BAD_REQUEST, "context không hợp lệ."),
    COUNTER_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu tương ứng."),
    COUNTER_IDENTITY_NOT_CONFIRMED(HttpStatus.CONFLICT, "Phải xác nhận danh tính Member trước khi bàn giao."),

    // F-023 - tải lại PDF biên nhận
    CONSIGNMENT_RECEIPT_PDF_NOT_READY(HttpStatus.CONFLICT, "Biên nhận chưa được xác nhận nên chưa có PDF."),
    CONSIGNMENT_RECEIPT_PDF_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "Không tìm thấy file PDF của biên nhận đã xác nhận."),

    // Upload file (ảnh sản phẩm/form ký gửi/avatar..., PDF giấy tờ tổ chức)
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "File rỗng."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File vượt quá kích thước cho phép."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Định dạng file không được hỗ trợ."),
    FILE_CONTENT_MISMATCH(HttpStatus.BAD_REQUEST, "Nội dung file không khớp với định dạng khai báo."),
    INVALID_UPLOAD_FOLDER(HttpStatus.BAD_REQUEST, "folder không hợp lệ."),
    TOO_MANY_FILES(HttpStatus.BAD_REQUEST, "Vượt quá số file cho phép trong một lần upload."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu file. Vui lòng thử lại."),
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xóa file.");

    private final HttpStatus status;
    private final String defaultMessage;
}