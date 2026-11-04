package com.rehome.rehome.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetSocketAddress;
import java.net.Socket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * GET /campaigns?status=ACTIVE và GET /api/organizations (không kèm search) từng trả 500
 * trên Postgres: khi keyword = null, Postgres bind tham số thành bytea nên
 * concat('%', :keyword, '%') lỗi "operator does not exist: character varying ~~ bytea".
 * Bộ test còn lại chạy trên H2 (xem src/test/resources/application.properties) nên không
 * bắt được lỗi này - test này phải nối Postgres thật mới tái hiện được.
 *
 * <p>Bỏ qua (không fail) nếu không có Postgres reachable ở DB_HOST:DB_PORT, ví dụ máy dev
 * chưa chạy {@code docker compose up -d postgres}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PublicSearchPostgresIT {

    private static final String DB_HOST = System.getenv().getOrDefault("DB_HOST", "localhost");
    private static final int DB_PORT = Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432"));
    private static final String DB_NAME = System.getenv().getOrDefault("DB_NAME", "jwtdemo");
    private static final String DB_USERNAME = System.getenv().getOrDefault("DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "123456");

    @BeforeAll
    static void requirePostgresReachable() {
        boolean reachable;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(DB_HOST, DB_PORT), 500);
            reachable = true;
        } catch (Exception e) {
            reachable = false;
        }
        assumeTrue(reachable, "Postgres không reachable ở " + DB_HOST + ":" + DB_PORT + " - bỏ qua test này");
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> DB_USERNAME);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void listPublicCampaigns_withoutSearch_doesNotReturn500() {
        ResponseEntity<String> response = restTemplate.getForEntity("/campaigns?status=ACTIVE", String.class);

        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void listPublicOrganizations_withoutSearch_doesNotReturn500() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/organizations", String.class);

        assertThat(response.getStatusCode().is5xxServerError()).isFalse();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
