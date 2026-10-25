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
}
