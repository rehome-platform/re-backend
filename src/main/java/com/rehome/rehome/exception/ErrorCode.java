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
    PRODUCT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác với sản phẩm này."),
    PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT, "Chỉ giảm giá được sản phẩm đang bán."),
    BELOW_FLOOR_PRICE(HttpStatus.CONFLICT, "BELOW_FLOOR_PRICE: giá mới không được thấp hơn giá sàn."),
    PRICE_CHANGE_LIMIT_REACHED(HttpStatus.CONFLICT, "PRICE_CHANGE_LIMIT_REACHED: sản phẩm đã đạt số lần giảm giá tối đa trong ngày."),
    // API 24 - quyết định cuối kỳ (F-029)
    END_OF_TERM_DECISION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu quyết định cuối kỳ."),
    END_OF_TERM_DECISION_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền quyết định cho sản phẩm này."),
    END_OF_TERM_DECISION_ALREADY_DECIDED(HttpStatus.CONFLICT, "Yêu cầu này đã được quyết định."),
    INVALID_DECISION(HttpStatus.BAD_REQUEST, "Chỉ được chọn TAKE_BACK hoặc RE_CONSIGN."),
    DECISION_EXPIRED(HttpStatus.CONFLICT, "DECISION_EXPIRED: đã quá hạn quyết định, hàng chuyển sang xử lý vô thừa nhận."),

    // API - giỏ hàng
    // API - hồ sơ Member
    // Thanh toán MoMo
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng."),
    ORDER_FORBIDDEN(HttpStatus.FORBIDDEN, "Đơn hàng này không thuộc về bạn."),
    ORDER_NOT_PENDING_PAYMENT(HttpStatus.CONFLICT, "Đơn hàng không ở trạng thái chờ thanh toán."),
    ORDER_ALREADY_PAID(HttpStatus.CONFLICT, "Đơn hàng đã được thanh toán."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch thanh toán."),
    MOMO_SIGNATURE_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo chữ ký thanh toán."),
    MOMO_CREATE_PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "Tạo giao dịch MoMo thất bại."),
    MOMO_IPN_VERIFY_FAILED(HttpStatus.BAD_REQUEST, "Không thể xác thực IPN."),
    MOMO_RETURN_VERIFY_FAILED(HttpStatus.BAD_REQUEST, "Không thể xác thực kết quả thanh toán."),
    MOMO_INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "Chữ ký MoMo không hợp lệ."),
    MEMBER_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Hồ sơ Member không tồn tại."),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "Số điện thoại không đúng định dạng."),
    // API 71 - webhook GHN


    MODERATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy modeartor nào."),

    GHN_ORDER_CODE_REQUIRED(HttpStatus.BAD_REQUEST, "Thiếu OrderCode trong webhook."),
    GHN_SHIPMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn."),
    PRODUCT_ID_REQUIRED(HttpStatus.BAD_REQUEST, "productId không được để trống."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "PRODUCT_NOT_AVAILABLE: sản phẩm không còn để bán."),
    ALREADY_IN_CART(HttpStatus.CONFLICT, "ALREADY_IN_CART: sản phẩm đã có trong giỏ hàng."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy mục giỏ hàng."),
    // API 25, 27 - quầy
    LOOKUP_INVALID_INPUT(HttpStatus.BAD_REQUEST, "Phải cung cấp memberCode, phone hoặc qrPayload."),
    QR_PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "Mã QR không hợp lệ."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy Member."),
    // API 28, 29, 30 - hàng đợi
    INVALID_QUEUE_TYPE(HttpStatus.BAD_REQUEST, "type không hợp lệ."),
    INVALID_FORM_STAGE(HttpStatus.BAD_REQUEST, "stage không hợp lệ. Giá trị hợp lệ: PENDING, APPROVED, INSPECTING, STOCKED_TODAY."),
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
    // DELETE /brands/{id}, /categories/{id} - đang có sản phẩm / phiếu ký gửi dùng thì không xóa được
    BRAND_IN_USE(HttpStatus.CONFLICT, "Thương hiệu đang được sử dụng, không thể xóa."),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Danh mục đang được sử dụng, không thể xóa."),
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
    // Vi phạm CHECK / NOT NULL ở DB (dữ liệu hoặc schema lệch với code), chi tiết nằm trong log server
    DATA_CONSTRAINT_VIOLATION(HttpStatus.CONFLICT,
            "DATA_CONSTRAINT_VIOLATION: dữ liệu vi phạm ràng buộc của hệ thống, vui lòng thử lại sau hoặc liên hệ hỗ trợ."),
    // Admin khoá / mở khoá tài khoản
    CANNOT_BAN_ADMIN_OR_SELF(HttpStatus.FORBIDDEN, "Không thể khoá tài khoản Admin hoặc chính bạn."),
    ACCOUNT_NOT_BANNED(HttpStatus.CONFLICT, "Tài khoản này đang không bị khoá."),
    ACCOUNT_ALREADY_BANNED(HttpStatus.CONFLICT, "Tài khoản này đã bị khoá."),
    // Phase 7 - Admin: Moderator và kho (API 138, 140, 142, 143, 144)
    NOT_MODERATOR_ACCOUNT(HttpStatus.BAD_REQUEST, "Tài khoản này không phải Moderator."),
    MODERATOR_HAS_CLAIMED_TASK(HttpStatus.CONFLICT, "MODERATOR_HAS_CLAIMED_TASK: Moderator đang giữ việc chưa xong trong hàng đợi."),
    MODERATOR_SAME_WAREHOUSE(HttpStatus.CONFLICT, "Moderator đã thuộc kho này."),
    WAREHOUSE_HIDDEN(HttpStatus.CONFLICT, "WAREHOUSE_HIDDEN: kho đang bị ẩn, không thể gán Moderator."),
    WAREHOUSE_ALREADY_HIDDEN(HttpStatus.CONFLICT, "Kho này đã được ẩn."),
    WAREHOUSE_HAS_STOCK(HttpStatus.CONFLICT, "WAREHOUSE_HAS_STOCK: kho còn hàng hoặc phiếu ký gửi đang xử lý, không thể ẩn."),
    WAREHOUSE_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "Tên kho đã tồn tại."),

    // Nháp ký gửi ("Tủ đồ") và giới hạn yêu cầu chờ duyệt
    CONSIGNMENT_FORM_NOT_DRAFT(HttpStatus.CONFLICT, "Form này không phải bản nháp."),
    TOO_MANY_PENDING_FORMS(HttpStatus.CONFLICT, "Bạn đã đạt giới hạn yêu cầu ký gửi đang chờ duyệt. Vui lòng chờ duyệt xong rồi gửi tiếp."),
    // Validate body POST/PUT /consignment-forms và nháp: thiếu trường bắt buộc trả 400 kèm mã riêng
    CATEGORY_ID_REQUIRED(HttpStatus.BAD_REQUEST, "categoryId không được để trống."),
    BRAND_ID_REQUIRED(HttpStatus.BAD_REQUEST, "brandId không được để trống."),
    WAREHOUSE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "warehouseId không được để trống."),
    PRODUCT_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "productName không được để trống."),
    DESCRIPTION_REQUIRED(HttpStatus.BAD_REQUEST, "description không được để trống."),
    INVALID_DESIRED_PRICE(HttpStatus.BAD_REQUEST, "desiredPrice phải lớn hơn 0."),
    PHOTOS_REQUIRED(HttpStatus.BAD_REQUEST, "Cần ít nhất 1 ảnh."),
    // Ký gửi lại cuối kỳ: Moderator chốt giá và điều khoản
    RE_CONSIGN_NOT_REQUESTED(HttpStatus.CONFLICT, "Member chưa chọn ký gửi lại cho sản phẩm này."),
    RE_CONSIGN_TERMS_ALREADY_SET(HttpStatus.CONFLICT, "Giá và điều khoản ký gửi lại đã được chốt."),
    INVALID_FLOOR_PRICE(HttpStatus.BAD_REQUEST, "Giá sàn phải lớn hơn 0 và không vượt quá giá thỏa thuận."),
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
    FILE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể xóa file."),
    FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể đọc file. Vui lòng thử lại."),

    // F-139: ảnh giấy tờ pháp lý chỉ ra qua endpoint có phân quyền, không qua URL công khai
    LEGAL_FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tệp giấy tờ."),
    LEGAL_FILE_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xem tệp giấy tờ này."),
    // F-097 (OP-05): mỗi lần chỉ 1 yêu cầu đổi thông tin pháp lý đang chờ duyệt
    LEGAL_CHANGE_REQUEST_ALREADY_PENDING(HttpStatus.CONFLICT,
            "Bạn đang có một yêu cầu đổi thông tin pháp lý chờ duyệt."),

    // API 90 - F-032: Member yêu cầu rút lại hàng ký gửi giữa kỳ
    PRODUCT_ALREADY_ORDERED(HttpStatus.CONFLICT, "PRODUCT_ALREADY_ORDERED: sản phẩm đã có đơn mua, không thể rút."),
    ITEM_WITHDRAWAL_ALREADY_PENDING(HttpStatus.CONFLICT, "Sản phẩm đang có yêu cầu rút hàng chờ xử lý."),
    // Dữ liệu cũ/seed thiếu biên nhận ký gửi hoặc mốc bắt đầu kỳ nên không tính được phí rút
    PRODUCT_RECEIPT_INCOMPLETE(HttpStatus.CONFLICT,
            "PRODUCT_RECEIPT_INCOMPLETE: sản phẩm thiếu dữ liệu biên nhận ký gửi (kỳ ký gửi hoặc phí rút), chưa thể rút hàng."),

    // API 91 - F-033: Member trả phí rút hàng
    ITEM_WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu rút hàng."),
    ITEM_WITHDRAWAL_FORBIDDEN(HttpStatus.FORBIDDEN, "Yêu cầu rút hàng này không thuộc về bạn."),
    ITEM_WITHDRAWAL_NOT_PENDING(HttpStatus.CONFLICT,
            "Yêu cầu rút hàng không còn ở trạng thái chờ lấy hàng (đã hết hạn)."),

    // Moderator duyệt/từ chối yêu cầu rút hàng
    ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL(HttpStatus.CONFLICT,
            "Yêu cầu rút hàng không còn ở trạng thái chờ duyệt."),

    // POST /products/{id}/donate: Member quyên góp sản phẩm ký gửi
    CAMPAIGN_NOT_ACCEPTING(HttpStatus.CONFLICT, "Chiến dịch hiện không nhận quyên góp."),
    CAMPAIGN_HIDDEN(HttpStatus.CONFLICT, "Chiến dịch đang bị ẩn."),
    CAMPAIGN_NOT_AT_WAREHOUSE(HttpStatus.CONFLICT, "Chiến dịch không nhận đồ tại kho đang giữ sản phẩm."),
    PRODUCT_NOT_DONATABLE(HttpStatus.CONFLICT,
            "Sản phẩm không ở trạng thái có thể quyên góp."),
    WITHDRAWAL_PENDING_APPROVAL(HttpStatus.CONFLICT,
            "Yêu cầu rút hàng đang chờ Moderator duyệt, chưa thể quyên góp sản phẩm này."),

    // API 95 - F-036: Member quyết định cách xử lý hàng xuống cấp
    DEGRADED_ITEM_CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ca xử lý hàng xuống cấp."),
    DEGRADED_ITEM_CASE_FORBIDDEN(HttpStatus.FORBIDDEN, "Ca xử lý hàng xuống cấp này không thuộc về bạn."),
    DEGRADED_ITEM_CASE_ALREADY_DECIDED(HttpStatus.CONFLICT, "Ca này đã được quyết định."),
    // Moderator mở ca trực tiếp lúc kiểm kho - chỉ mở được khi sản phẩm còn đang bán
    DEGRADED_ITEM_CASE_PRODUCT_NOT_ON_SALE(HttpStatus.CONFLICT,
            "Chỉ mở ca xử lý hàng xuống cấp cho sản phẩm đang bán."),

    // F-080, F-081 - bộ sưu tập ảnh thử đồ
    TRYON_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh thử đồ."),
    // API 96 - gợi ý món thử cùng
    INVALID_SUGGESTION_SLOT(HttpStatus.BAD_REQUEST, "slot phải là TOP hoặc BOTTOM."),
    TRYON_IMAGE_FORBIDDEN(HttpStatus.FORBIDDEN, "Ảnh thử đồ này không thuộc về bạn."),

    // API 97 - F-077, F-078: sinh ảnh thử đồ
    INCOMPLETE_OUTFIT(HttpStatus.CONFLICT, "INCOMPLETE_OUTFIT: cần đúng một bộ áo+quần hoặc đầm."),
    DRESS_NOT_SUPPORTED_FOR_MALE(HttpStatus.BAD_REQUEST, "Người mẫu nam chưa hỗ trợ thử đầm."),
    TRYON_SLOT_MISMATCH(HttpStatus.BAD_REQUEST, "Sản phẩm không đúng vị trí thử đồ (áo, quần hoặc đầm)."),
    TRYON_PRODUCT_NOT_AVAILABLE(HttpStatus.CONFLICT, "Sản phẩm không thể dùng để thử đồ (không còn bán hoặc thiếu ảnh)."),
    TRYON_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "Không thể sinh ảnh thử đồ lúc này, vui lòng thử lại sau."),

    // F-104/F-105 - đăng/sửa bài viết cộng đồng: giới hạn nội dung và số ảnh
    POST_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "content tối đa 2000 ký tự."),
    POST_TOO_MANY_PHOTOS(HttpStatus.BAD_REQUEST, "Tối đa 5 ảnh cho 1 bài viết."),
    // F-087 - bình luận bài viết cộng đồng: giới hạn nội dung
    COMMENT_CONTENT_TOO_LONG(HttpStatus.BAD_REQUEST, "content tối đa 500 ký tự."),

    // API 146 - F-157: Admin chỉnh tham số cấu hình
    CONFIG_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tham số."),
    CONFIG_VALUE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Giá trị không hợp lệ cho tham số này."),

    // API 148 - F-159: xuất báo cáo dashboard
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "Khoảng thời gian không hợp lệ."),
    DASHBOARD_EXPORT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo file Excel báo cáo."),

    // API 149 - F-160: tra cứu audit log
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "page phải >= 0 và size trong khoảng 1 đến 100."),

    // Admin: quản lý từ khóa vi phạm
    VIOLATION_WORD_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy từ khóa vi phạm."),
    VIOLATION_WORD_ALREADY_EXISTS(HttpStatus.CONFLICT, "Từ khóa vi phạm này đã tồn tại."),

    // Chat: giới hạn tốc độ gửi tin nhắn
    MESSAGE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi quá nhanh, thử lại sau ít giây."),

    // Thử đồ AI real-time (Decart Lucy VTON) - hạn mức lượt/ngày và giây/lượt lấy theo gói thành
    // viên của Member (MembershipQuotaResolver), không còn là con số cứng 2 lượt/10 giây
    TRYON_LIVE_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "Bạn đã dùng hết lượt thử đồ AI real-time hôm nay. Nâng cấp gói để thử thêm."),
    TRYON_LIVE_TOKEN_FAILED(HttpStatus.BAD_GATEWAY,
            "Không thể kết nối tới dịch vụ thử đồ AI real-time lúc này, vui lòng thử lại sau."),
    TRYON_LIVE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên thử đồ AI real-time."),
    TRYON_LIVE_SESSION_FORBIDDEN(HttpStatus.FORBIDDEN, "Phiên thử đồ AI real-time này không thuộc về bạn."),
    TRYON_LIVE_SESSION_ALREADY_FINISHED(HttpStatus.CONFLICT, "Phiên thử đồ AI real-time này đã kết thúc."),
    TRYON_LIVE_VIDEO_INVALID(HttpStatus.BAD_REQUEST, "File video không hợp lệ."),

    // Quản lý báo cáo vi phạm (Moderator)
    DUPLICATE_PENDING_REPORT(HttpStatus.CONFLICT,
            "Bạn đã báo cáo đối tượng này và báo cáo đang chờ xử lý."),
    VIOLATION_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo vi phạm."),
    VIOLATION_REPORT_ALREADY_HANDLED(HttpStatus.CONFLICT, "Báo cáo này đã được xử lý."),

    // Gói thành viên VIP
    MEMBERSHIP_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy gói thành viên."),
    MEMBERSHIP_PLAN_NOT_PURCHASABLE(HttpStatus.CONFLICT, "Gói này hiện không bán."),
    MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Gói này không bán theo năm."),
    MEMBERSHIP_ALREADY_ACTIVE_SAME_TIER(HttpStatus.CONFLICT,
            "Bạn đang dùng gói này, hãy dùng chức năng gia hạn."),
    MEMBERSHIP_PAYMENT_PENDING(HttpStatus.CONFLICT, "Bạn đang có một giao dịch mua gói chờ thanh toán."),
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "Bạn chưa có gói thành viên nào."),
    MEMBERSHIP_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy gói đăng ký."),
    MEMBERSHIP_PLAN_INVALID(HttpStatus.BAD_REQUEST, "Cấu hình gói thành viên không hợp lệ."),
    MEMBERSHIP_SUBSCRIPTION_NOT_ACTIVE(HttpStatus.CONFLICT, "Gói đăng ký này không còn hiệu lực."),

    // Voucher
    VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "Mã giảm giá không tồn tại."),
    VOUCHER_FORBIDDEN(HttpStatus.FORBIDDEN, "Mã giảm giá này không thuộc về bạn."),
    VOUCHER_ALREADY_USED(HttpStatus.CONFLICT, "Mã giảm giá đã được sử dụng."),
    VOUCHER_EXPIRED(HttpStatus.CONFLICT, "Mã giảm giá đã hết hạn."),
    VOUCHER_MIN_ORDER_NOT_MET(HttpStatus.BAD_REQUEST, "Đơn hàng chưa đạt giá trị tối thiểu để dùng mã này."),
    VOUCHER_NOT_APPLICABLE(HttpStatus.BAD_REQUEST, "Mã giảm giá không áp dụng được cho đơn này."),
    VOUCHER_TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy mẫu voucher."),
    VOUCHER_TEMPLATE_INVALID(HttpStatus.BAD_REQUEST, "Cấu hình mẫu voucher không hợp lệ."),

    // Phối đồ AI (ảnh tĩnh) - hạn mức theo ngày của gói, reset 00:00 giờ VN
    TRYON_IMAGE_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,
            "Bạn đã dùng hết lượt phối đồ AI hôm nay.");

    private final HttpStatus status;
    private final String defaultMessage;
}