package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.assertj.core.api.Assertions.assertThat;

/** Real JPQL coverage for claim ownership, shared queues and warehouse isolation. */
@DataJpaTest
class QueueMineQueryTest {
    @Autowired EntityManager em;
    @Autowired QueueItemRepository queues;
    Account me;
    Account other;
    Warehouse mine;
    Warehouse elsewhere;
    QueueItem ownPacking;
    QueueItem ownGlobal;
    QueueItem ownReturn;
    QueueItem unclaimed;
    QueueItem othersPacking;

    @BeforeEach void setup() {
        mine = warehouse("Mine");
        elsewhere = warehouse("Elsewhere");
        me = account("mine@example.test");
        other = account("other@example.test");
        ownPacking = queue(QueueType.PACKING, mine, me, 1);
        unclaimed = queue(QueueType.PACKING, mine, null, 2);
        othersPacking = queue(QueueType.PACKING, mine, other, 3);
        ownGlobal = queue(QueueType.PACKING, null, me, 4);
        queue(QueueType.PACKING, null, other, 5);
        queue(QueueType.PACKING, elsewhere, me, 6);
        ownReturn = queue(QueueType.RETURN, mine, me, 7);
        em.flush();
        em.clear();
    }
    private Warehouse warehouse(String name) {
        var result = Warehouse.builder().name(name).address("Test address")
                .latitude(BigDecimal.ZERO).longitude(BigDecimal.ZERO).openingHours("08:00-17:00").build();
        em.persist(result);
        return result;
    }
    private Account account(String email) {
        var result = Account.builder().email(email).passwordHash("test-hash").role(Role.MODERATOR).build();
        em.persist(result);
        return result;
    }
    private QueueItem queue(QueueType type, Warehouse warehouse, Account claimant, int seconds) {
        var result = QueueItem.builder().type(type).refId(UUID.randomUUID().toString()).warehouse(warehouse)
                .claimedBy(claimant).createdAt(Instant.parse("2026-11-25T00:00:00Z").plusSeconds(seconds)).build();
        em.persist(result);
        return result;
    }

    @Test void mineWithTypeIncludesOnlyMyClaimsInVisibleWarehouses() {
        assertThat(queues.findVisibleByTypeClaimedBy(QueueType.PACKING, mine, me))
                .extracting(QueueItem::getId).containsExactly(ownPacking.getId(), ownGlobal.getId());
    }

    @Test void mineWithoutTypeIncludesAllMyVisibleTypesInOrder() {
        assertThat(queues.findAllVisibleClaimedBy(mine, me)).extracting(QueueItem::getId)
                .containsExactly(ownPacking.getId(), ownGlobal.getId(), ownReturn.getId());
    }

    @Test void oldWarehouseQueueStillContainsUnclaimedAndOtherModeratorsWork() {
        assertThat(queues.findVisibleByType(QueueType.PACKING, mine)).extracting(QueueItem::getId)
                .contains(ownPacking.getId(), unclaimed.getId(), othersPacking.getId(), ownGlobal.getId())
                .hasSize(5);
    }
}
