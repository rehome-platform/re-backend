package com.rehome.rehome.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.internal.SchemaCreatorImpl;
import org.hibernate.tool.schema.internal.exec.GenerationTarget;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * Test chạy trên H2 nên kiểu cột thật ở Postgres không bao giờ được kiểm tra -
 * mà có loại sai lệch chỉ nổ ở production. Test này sinh DDL bằng
 * PostgreSQLDialect (không cần kết nối DB) và khóa lại những cột quan trọng.
 *
 * <p>Lý do cụ thể: {@code byte[]} để mặc định bị Hibernate sinh ra
 * {@code varbinary(255)}, PDF biên nhận ~40KB insert không vào. Và nếu đánh
 * {@code @Lob} thì Postgres lại ra {@code oid} (large object) chứ không phải
 * {@code bytea}, đọc ghi phải qua LargeObject API.
 */
class PostgresSchemaContractTest {

    private static String ddl;

    /** Gom các câu DDL Hibernate sinh ra thay vì ghi ra file. */
    private static final class CollectingTarget implements GenerationTarget {
        private final List<String> commands = new ArrayList<>();

        @Override
        public void prepare() {
        }

        @Override
        public void accept(String command) {
            commands.add(command);
        }

        @Override
        public void release() {
        }
    }

    @BeforeAll
    static void exportPostgresSchema() {
        // Chỉ sinh script, không kết nối DB - nên khai dialect thẳng
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .build();

        MetadataSources sources = new MetadataSources(registry);
        for (Class<?> entity : entityClasses()) {
            sources.addAnnotatedClass(entity);
        }

        CollectingTarget target = new CollectingTarget();
        new SchemaCreatorImpl(registry).doCreation(sources.buildMetadata(), false, target);

        ddl = String.join(";\n", target.commands).toLowerCase() + ";";
    }

    private static Set<Class<?>> entityClasses() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        return scanner.findCandidateComponents("com.rehome.rehome.entity").stream()
                .map(bean -> {
                    try {
                        return Class.forName(bean.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Lấy phần trong ngoặc của "create table <name> (...)". */
    private static String tableBody(String table) {
        Matcher matcher = Pattern.compile(
                        "create table " + Pattern.quote(table) + " \\((.*?)\\);",
                        Pattern.DOTALL)
                .matcher(ddl);
        assertThat(matcher.find()).as("không tìm thấy create table %s trong DDL", table).isTrue();
        return matcher.group(1);
    }

    @Test
    void receiptPdfContent_isBytea_notVarbinaryNorOid() {
        String body = tableBody("consignment_receipt_pdfs");

        // bytea không có giới hạn độ dài -> chứa được PDF vài chục KB
        assertThat(body).contains("content bytea not null");

        // oid = large object, phải đọc ghi qua LargeObject API, byte[] thường sẽ hỏng
        assertThat(body).doesNotContain("content oid");
        assertThat(body).doesNotContain("varbinary");
    }

    @Test
    void receiptPdfTable_sharesPrimaryKeyWithReceipt() {
        String body = tableBody("consignment_receipt_pdfs");

        assertThat(body).contains("receipt_id uuid not null");
        assertThat(body).contains("primary key (receipt_id)");
    }


    // ================================================================ Gói thành viên VIP + voucher

    /**
     * ⚠️ Bẫy đã gặp một lần trong repo này - xem {@code scripts/drop-payment-gateway-ref-unique.sql}.
     * Hibernate đã từng tạo UNIQUE trên {@code payments.momo_order_id} / {@code vnp_txn_ref}, và
     * {@code ddl-auto: update} KHÔNG drop được index đã tồn tại nên phải chạy script tay trên
     * Postgres sau deploy.
     *
     * <p>Khóa lại để lần này không lặp vết xe cũ trên bảng {@code membership_payments}: nếu ai đó
     * thêm {@code unique = true} vào entity thì test đỏ ngay, chứ không phải lúc deploy.
     */
    @Test
    void membershipPaymentGatewayRefs_areNotUnique() {
        assertThat(isColumnUnique("membership_payments", "momo_order_id"))
                .as("membership_payments.momo_order_id KHÔNG được UNIQUE").isFalse();
        assertThat(isColumnUnique("membership_payments", "vnp_txn_ref"))
                .as("membership_payments.vnp_txn_ref KHÔNG được UNIQUE").isFalse();
    }

    /**
     * Một mã voucher áp cho CẢ lần thanh toán và trải trên nhiều đơn (VIP-MEMBERSHIP-SPEC §12.6),
     * nên nhiều dòng {@code orders} cùng trỏ một {@code voucher_id} là ĐÚNG. Đặt UNIQUE ở đây sẽ
     * làm đơn thứ hai của một giỏ nhiều món fail ngay lúc insert.
     */
    @Test
    void orderVoucherId_isNotUnique() {
        assertThat(isColumnUnique("orders", "voucher_id"))
                .as("orders.voucher_id KHÔNG được UNIQUE - xem VIP-MEMBERSHIP-SPEC §12.6").isFalse();
    }

    /**
     * Hai UNIQUE <b>phải có</b> (ngược với hai test trên): mỗi hạng đúng một dòng cấu hình, và mã
     * voucher duy nhất toàn hệ thống vì người mua nhập mã để dùng.
     */
    @Test
    void membershipUniqueConstraints_arePresent() {
        assertThat(isColumnUnique("membership_plans", "tier"))
                .as("membership_plans.tier phải UNIQUE - chỉ có đúng 4 dòng FREE/SILVER/GOLD/DIAMOND")
                .isTrue();
        assertThat(isColumnUnique("member_vouchers", "code"))
                .as("member_vouchers.code phải UNIQUE").isTrue();
    }

    /**
     * Tiền là {@code numeric(19,2)} - để Hibernate tự chọn kiểu sẽ ra {@code numeric(38,2)} hoặc
     * mất phần thập phân tùy khai báo.
     *
     * <p>{@code orders.discount_amount} có thêm {@code default 0} vì {@code @ColumnDefault("0")}:
     * dòng MỚI được 0, nhưng dòng CŨ vẫn NULL tới khi
     * {@code scripts/backfill-order-discount-amount.sql} chạy tay trên Postgres.
     */
    @Test
    void moneyColumns_areNumeric19Scale2() {
        assertThat(columnDefinition("orders", "discount_amount"))
                .isEqualTo("discount_amount numeric(19,2) default 0 not null");
        assertThat(columnDefinition("membership_plans", "price_per_month"))
                .isEqualTo("price_per_month numeric(19,2) not null");
        assertThat(columnDefinition("member_subscriptions", "price_paid"))
                .isEqualTo("price_paid numeric(19,2) not null");
        assertThat(columnDefinition("member_vouchers", "discount_value"))
                .isEqualTo("discount_value numeric(19,2) not null");
    }

    /**
     * {@code featured_until} phải NULLABLE: NULL = không nổi bật, và mốc quá khứ tự hết tác dụng
     * lúc query. Nhờ vậy cột này không cần script backfill như {@code orders.discount_amount}.
     */
    @Test
    void productFeaturedUntil_isNullable() {
        assertThat(columnDefinition("products", "featured_until"))
                .isEqualTo("featured_until timestamp(6) with time zone");
    }

    /**
     * Định nghĩa của một cột trong {@code create table}, cắt theo dấu phẩy ở mức ngoài cùng.
     * Không tách bằng {@code split(",")} được: phần {@code check (x in ('a','b'))} và
     * {@code numeric(19,2)} đều chứa dấu phẩy bên trong ngoặc.
     */
    private static String columnDefinition(String table, String column) {
        String body = tableBody(table);
        int depth = 0;
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                String fragment = body.substring(start, i).trim();
                if (fragment.startsWith(column + " ")) {
                    return fragment;
                }
                start = i + 1;
            }
        }
        String last = body.substring(start).trim();
        assertThat(last)
                .as("không tìm thấy cột %s trong create table %s", column, table)
                .startsWith(column + " ");
        return last;
    }

    /**
     * Cột có UNIQUE hay không, xét cả hai dạng Hibernate sinh ra: inline trong {@code create table}
     * ({@code tier varchar(255) not null unique}) và {@code alter table ... add constraint ... unique (...)}.
     */
    private static boolean isColumnUnique(String table, String column) {
        // "unique" không nhất thiết ở cuối: Hibernate sinh
        // "tier varchar(255) not null unique check (tier in ('free',...))"
        if (columnDefinition(table, column).matches(".*\\bunique\\b.*")) {
            return true;
        }
        Matcher alter = Pattern.compile(
                        "alter table if exists " + Pattern.quote(table)
                                + "[^;]*?unique \\(([^)]*)\\)")
                .matcher(ddl);
        while (alter.find()) {
            for (String part : alter.group(1).split(",")) {
                if (part.trim().equals(column)) {
                    return true;
                }
            }
        }
        return false;
    }
}
