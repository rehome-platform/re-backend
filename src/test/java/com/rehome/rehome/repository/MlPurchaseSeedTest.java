package com.rehome.rehome.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

/** Execute the generated DML against Hibernate's actual schema and verify rerun safety. */
@DataJpaTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@org.springframework.context.annotation.Import(com.rehome.rehome.service.MlOutfitSuggestionService.class)
class MlPurchaseSeedTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProductRepository productRepository;
    @Autowired com.rehome.rehome.service.MlOutfitSuggestionService mlOutfitService;

    @Test void purchaseFixturesMatchEntitiesAndCanBeAppliedTwice() throws Exception {
        jdbc.execute("SET MODE PostgreSQL");
        String sql = Files.readString(Path.of("scripts/seed_ml_purchase_history.sql"));
        // Keep Spring's rollback-only test transaction; omit PostgreSQL-only session controls.
        sql = sql.replaceAll("(?m)^(BEGIN;|COMMIT;|SET LOCAL[^\\r\\n]*;)\\s*$", "");
        var resource = new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, resource);
            ScriptUtils.executeSqlScript(connection, resource);
            return null;
        });
        assertThat(count("select count(*) from orders where order_no like 'MLR1-OD-%' and status='COMPLETED'" )).isEqualTo(360);
        assertThat(count("select count(*) from products where id like 'MLR1-SP-%' and status='SOLD'" )).isEqualTo(360);
        assertThat(count("select count(*) from products where id like 'MLR1-SP-%' and status='ON_SALE'" )).isEqualTo(90);
        assertThat(count("select count(*) from accounts where email like 'ml.%@example.test'" )).isEqualTo(37);
        assertThat(count("select count(*) from (select buyer_id from orders where order_no like 'MLR1-OD-%' group by buyer_id having count(*) = 12) buyers_with_twelve_orders")).isEqualTo(30);
        assertThat(count("select count(*) from orders o join accounts a on a.id=o.buyer_id where a.email like 'ml.new.%@example.test'" )).isZero();
        assertThat(count("select count(*) from (select product_id from order_items where product_id like 'MLR1-SP-%' group by product_id having count(*) > 1) duplicate_sales")).isZero();
        assertThat(count("select count(*) from orders o join order_items i on i.order_id=o.id join products p on p.id=i.product_id where o.order_no like 'MLR1-OD-%' and (o.buyer_id=p.member_id or o.warehouse_id<>p.warehouse_id or o.subtotal<>i.price_at_order or o.total_amount<>o.subtotal+o.shipping_fee-o.discount_amount or o.payout_at is null or o.completed_at<o.delivered_at or o.delivered_at<o.created_at or o.payout_at<o.return_deadline)")).isZero();
        String hash = jdbc.queryForObject("select password_hash from accounts where email='ml.sport.01@example.test'", String.class);
        assertThat(new BCryptPasswordEncoder().matches("123456", hash)).isTrue();
        String[] exports = Files.readString(Path.of("scripts/export_ml_purchase_history.sql"))
                .replaceAll("(?m)^--.*$", "").split(";");
        assertThat(jdbc.queryForList(exports[0])).hasSize(360);
        assertThat(jdbc.queryForList(exports[1])).hasSize(90);
        var buyer = jdbc.queryForObject("select id from accounts where email='ml.office.01@example.test'", java.util.UUID.class);
        var history = orderItemRepository.findOutfitPreferenceHistory(buyer, org.springframework.data.domain.PageRequest.of(0, 200));
        assertThat(history).hasSize(12);
        assertThat(history).allMatch(item -> item.getOrder().getBuyer().getId().equals(buyer));
        var selected = jdbc.queryForObject("select min(id) from products where id like 'MLR1-SP-%' and status='ON_SALE' and category_id in (select id from categories where tryon_slot='TOP')", String.class);
        var response = mlOutfitService.suggest(com.rehome.rehome.entity.auth.Account.builder().id(buyer).build(), selected, null, null);
        assertThat(response.isPersonalized()).isTrue();
        assertThat(response.getHistoryItemsUsed()).isEqualTo(12);
        assertThat(response.getItems()).hasSize(10);
        assertThat(response.getItems()).allMatch(item -> !item.getId().equals(selected));
        // Changing the history order to CANCELLED must remove its evidence immediately.
        var excludedOrder = history.get(0).getOrder();
        jdbc.update("update orders set status='CANCELLED' where id=?", excludedOrder.getId());
        assertThat(orderItemRepository.findOutfitPreferenceHistory(buyer, org.springframework.data.domain.PageRequest.of(0, 200))).hasSize(11);
        // Even a COMPLETED order is excluded if an accepted return exists.
        var returnedOrder = history.get(1).getOrder();
        jdbc.update("insert into return_requests (id, order_id, reason, status, handover_deadline, counterfeit_suspected, degraded, created_at) values (?, ?, 'test return', 'VALID', CURRENT_TIMESTAMP, false, false, CURRENT_TIMESTAMP)",
                java.util.UUID.randomUUID(), returnedOrder.getId());
        assertThat(orderItemRepository.findOutfitPreferenceHistory(buyer, org.springframework.data.domain.PageRequest.of(0, 200))).hasSize(10);
        var seller = jdbc.queryForObject("select id from accounts where email='ml.seller.sport@example.test'", java.util.UUID.class);
        var pool = productRepository.findMlOutfitCandidates(com.rehome.rehome.entity.enums.TryOnSlot.BOTTOM, selected, seller,
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(pool).isNotEmpty().allMatch(p -> !p.getMember().getId().equals(seller)
                && p.getStatus() == com.rehome.rehome.entity.enums.ProductStatus.ON_SALE
                && p.getCategory().getTryonSlot() == com.rehome.rehome.entity.enums.TryOnSlot.BOTTOM);
    }

    private long count(String query) { return jdbc.queryForObject(query, Long.class); }
}
