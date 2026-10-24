package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.QueueItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.rehome.rehome.serviceImpl.QueueServiceImpl;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QueueServiceImplTest {

    @Mock private QueueItemRepository queueItemRepository;

    @InjectMocks private QueueServiceImpl queueService;

    private static void assertAppError(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // Tạo Warehouse với id xác định theo key; tự nhận biết kiểu id (UUID/Long/Integer/String)
    private static Warehouse warehouse(int key) {
        Warehouse w = new Warehouse();
        Class<?> idType;
        try {
            idType = Warehouse.class.getDeclaredField("id").getType();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Warehouse không có field id", e);
        }
        Object id;
        if (idType == UUID.class) id = new UUID(0L, key);
        else if (idType == Long.class || idType == long.class) id = (long) key;
        else if (idType == Integer.class || idType == int.class) id = key;
        else id = "wh-" + key;
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    private static Account moderator(Warehouse warehouse) {
        return Account.builder().id(UUID.randomUUID()).warehouse(warehouse).build();
    }

    private static QueueItem item(String id, QueueType type, Account claimedBy, Warehouse warehouse) {
        return QueueItem.builder()
                .id(id)
                .type(type)
                .refId("cf-" + id)
                .claimedBy(claimedBy)
                .claimedAt(claimedBy == null ? null : Instant.parse("2026-10-13T08:05:00Z"))
                .warehouse(warehouse)
                .createdAt(Instant.parse("2026-10-13T08:00:00Z"))
                .build();
    }

    @Nested
    @DisplayName("API 28 - list")
    class ListQueue {

        @Test
        @DisplayName("Moderator chưa gán kho: 403 MODERATOR_NO_WAREHOUSE")
        void noWarehouse_throws() {
            Account mod = moderator(null);

            assertAppError(() -> queueService.list(null, mod), ErrorCode.MODERATOR_NO_WAREHOUSE);
        }

        @Test
        @DisplayName("type null: lấy tất cả loại việc trong kho")
        void typeNull_returnsAll() {
            Warehouse wh = warehouse(1);
            Account mod = moderator(wh);
            when(queueItemRepository.findAllVisible(wh))
                    .thenReturn(List.of(item("q-1", QueueType.FORM_REVIEW, null, wh)));

            List<QueueItemResponse> result = queueService.list(null, mod);

            assertThat(result).hasSize(1);
            verify(queueItemRepository, never()).findVisibleByType(any(), any());
        }

        @Test
        @DisplayName("type rỗng hoặc toàn khoảng trắng: lấy tất cả loại việc")
        void typeBlank_returnsAll() {
            Warehouse wh = warehouse(1);
            Account mod = moderator(wh);
            when(queueItemRepository.findAllVisible(wh)).thenReturn(List.of());

            assertThat(queueService.list("   ", mod)).isEmpty();
            verify(queueItemRepository).findAllVisible(wh);
        }

        @Test
        @DisplayName("type hợp lệ (có khoảng trắng thừa): lọc theo type")
        void validType_filters() {
            Warehouse wh = warehouse(1);
            Account mod = moderator(wh);
            when(queueItemRepository.findVisibleByType(QueueType.FORM_REVIEW, wh))
                    .thenReturn(List.of(item("q-1", QueueType.FORM_REVIEW, null, wh)));

            List<QueueItemResponse> result = queueService.list(" FORM_REVIEW ", mod);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(QueueType.FORM_REVIEW);
            verify(queueItemRepository, never()).findAllVisible(any());
        }

        @Test
        @DisplayName("type không hợp lệ: 400 INVALID_QUEUE_TYPE")
        void invalidType_throws() {
            Account mod = moderator(warehouse(1));

            assertAppError(() -> queueService.list("NOT_A_TYPE", mod), ErrorCode.INVALID_QUEUE_TYPE);
            verify(queueItemRepository, never()).findVisibleByType(any(), any());
        }

        @Test
        @DisplayName("type phân biệt hoa thường: chữ thường là không hợp lệ")
        void lowercaseType_throws() {
            Account mod = moderator(warehouse(1));

            assertAppError(() -> queueService.list("form_review", mod), ErrorCode.INVALID_QUEUE_TYPE);
        }

        @Test
        @DisplayName("Map đúng claimedBy: null khi chưa nhận, id Moderator khi đã nhận")
        void mapsClaimedBy() {
            Warehouse wh = warehouse(1);
            Account mod = moderator(wh);
            Account other = moderator(wh);
            when(queueItemRepository.findAllVisible(wh))
                    .thenReturn(List.of(
                            item("q-1", QueueType.FORM_REVIEW, null, wh),
                            item("q-2", QueueType.PACKING, other, wh)));

            List<QueueItemResponse> result = queueService.list(null, mod);

            assertThat(result.get(0).getClaimedBy()).isNull();
            assertThat(result.get(0).getClaimedAt()).isNull();
            assertThat(result.get(1).getClaimedBy()).isEqualTo(other.getId().toString());
            assertThat(result.get(1).getClaimedAt()).isEqualTo(Instant.parse("2026-10-13T08:05:00Z"));
        }
    }

    @Nested
    @DisplayName("API 29 - claim")
    class Claim {

        @Test
        @DisplayName("Moderator chưa gán kho: 403 MODERATOR_NO_WAREHOUSE")
        void noWarehouse_throws() {
            Account mod = moderator(null);

            assertAppError(() -> queueService.claim("q-1", mod), ErrorCode.MODERATOR_NO_WAREHOUSE);
            verify(queueItemRepository, never()).claimIfFree(any(), any(), any());
        }

        @Test
        @DisplayName("Không có việc: 404 QUEUE_ITEM_NOT_FOUND")
        void notFound_throws() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-404")).thenReturn(Optional.empty());

            assertAppError(() -> queueService.claim("q-404", mod), ErrorCode.QUEUE_ITEM_NOT_FOUND);
            verify(queueItemRepository, never()).claimIfFree(any(), any(), any());
        }

        @Test
        @DisplayName("Việc thuộc kho khác: 404, không được claim")
        void otherWarehouse_treatedAsNotFound() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-1"))
                    .thenReturn(Optional.of(item("q-1", QueueType.PACKING, null, warehouse(2))));

            assertAppError(() -> queueService.claim("q-1", mod), ErrorCode.QUEUE_ITEM_NOT_FOUND);
            verify(queueItemRepository, never()).claimIfFree(any(), any(), any());
        }

        @Test
        @DisplayName("Claim thành công việc cùng kho")
        void sameWarehouse_success() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-1"))
                    .thenReturn(Optional.of(item("q-1", QueueType.PACKING, null, warehouse(1))));
            when(queueItemRepository.claimIfFree(eq("q-1"), eq(mod), any(Instant.class))).thenReturn(1);

            QueueClaimResponse result = queueService.claim("q-1", mod);

            ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(queueItemRepository).claimIfFree(eq("q-1"), eq(mod), nowCaptor.capture());
            assertThat(result.getId()).isEqualTo("q-1");
            assertThat(result.getClaimedBy()).isEqualTo(mod.getId().toString());
            assertThat(result.getClaimedAt()).isEqualTo(nowCaptor.getValue());
        }

        @Test
        @DisplayName("Việc không gắn kho (null) thì kho nào cũng claim được")
        void nullWarehouse_success() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-2"))
                    .thenReturn(Optional.of(item("q-2", QueueType.ORG_VERIFICATION, null, null)));
            when(queueItemRepository.claimIfFree(eq("q-2"), eq(mod), any(Instant.class))).thenReturn(1);

            QueueClaimResponse result = queueService.claim("q-2", mod);

            assertThat(result.getId()).isEqualTo("q-2");
        }

        @Test
        @DisplayName("Người khác nhận trước (update trả 0 dòng): 409 ALREADY_CLAIMED")
        void alreadyClaimed_throws() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-1"))
                    .thenReturn(Optional.of(item("q-1", QueueType.PACKING, null, warehouse(1))));
            when(queueItemRepository.claimIfFree(eq("q-1"), eq(mod), any(Instant.class))).thenReturn(0);

            assertAppError(() -> queueService.claim("q-1", mod), ErrorCode.QUEUE_ITEM_ALREADY_CLAIMED);
        }
    }

    @Nested
    @DisplayName("API 30 - release")
    class Release {

        @Test
        @DisplayName("Không có việc: 404 QUEUE_ITEM_NOT_FOUND")
        void notFound_throws() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-404")).thenReturn(Optional.empty());

            assertAppError(() -> queueService.release("q-404", mod), ErrorCode.QUEUE_ITEM_NOT_FOUND);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Việc chưa ai nhận: 403 QUEUE_ITEM_NOT_CLAIMED_BY_YOU")
        void unclaimed_throws() {
            Account mod = moderator(warehouse(1));
            when(queueItemRepository.findById("q-1"))
                    .thenReturn(Optional.of(item("q-1", QueueType.PACKING, null, warehouse(1))));

            assertAppError(() -> queueService.release("q-1", mod), ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Việc do Moderator khác nhận: 403 QUEUE_ITEM_NOT_CLAIMED_BY_YOU")
        void claimedByOther_throws() {
            Warehouse wh = warehouse(1);
            Account mod = moderator(wh);
            Account other = moderator(wh);
            when(queueItemRepository.findById("q-1"))
                    .thenReturn(Optional.of(item("q-1", QueueType.PACKING, other, wh)));

            assertAppError(() -> queueService.release("q-1", mod), ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("Người đang nhận nhả việc: xóa claimedBy, claimedAt và lưu")
        void claimedByMe_success() {
            Warehouse wh = warehouse(1);
            Account mod = moderator(wh);
            QueueItem claimed = item("q-1", QueueType.PACKING, mod, wh);
            when(queueItemRepository.findById("q-1")).thenReturn(Optional.of(claimed));

            queueService.release("q-1", mod);

            ArgumentCaptor<QueueItem> captor = ArgumentCaptor.forClass(QueueItem.class);
            verify(queueItemRepository).save(captor.capture());
            assertThat(captor.getValue().getClaimedBy()).isNull();
            assertThat(captor.getValue().getClaimedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("enqueue / dequeue")
    class EnqueueDequeue {

        @Test
        @DisplayName("enqueue khi đã có (type, refId): trả về dòng cũ, không tạo thêm")
        void enqueue_existing_isIdempotent() {
            QueueItem existing = item("q-1", QueueType.FORM_REVIEW, null, warehouse(1));
            when(queueItemRepository.findByTypeAndRefId(QueueType.FORM_REVIEW, "cf-1"))
                    .thenReturn(Optional.of(existing));

            QueueItem result = queueService.enqueue(QueueType.FORM_REVIEW, "cf-1", warehouse(1));

            assertThat(result).isSameAs(existing);
            verify(queueItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("enqueue khi chưa có: tạo dòng mới chưa ai nhận")
        void enqueue_new_savesItem() {
            Warehouse wh = warehouse(1);
            when(queueItemRepository.findByTypeAndRefId(QueueType.RETURN, "rr-1")).thenReturn(Optional.empty());
            when(queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));

            QueueItem result = queueService.enqueue(QueueType.RETURN, "rr-1", wh);

            ArgumentCaptor<QueueItem> captor = ArgumentCaptor.forClass(QueueItem.class);
            verify(queueItemRepository).save(captor.capture());
            QueueItem saved = captor.getValue();
            assertThat(saved.getType()).isEqualTo(QueueType.RETURN);
            assertThat(saved.getRefId()).isEqualTo("rr-1");
            assertThat(saved.getWarehouse()).isSameAs(wh);
            assertThat(saved.getClaimedBy()).isNull();
            assertThat(result).isSameAs(saved);
        }

        @Test
        @DisplayName("dequeue khi có việc: xóa dòng")
        void dequeue_existing_deletes() {
            QueueItem existing = item("q-1", QueueType.FORM_REVIEW, null, warehouse(1));
            when(queueItemRepository.findByTypeAndRefId(QueueType.FORM_REVIEW, "cf-1"))
                    .thenReturn(Optional.of(existing));

            queueService.dequeue(QueueType.FORM_REVIEW, "cf-1");

            verify(queueItemRepository).delete(existing);
        }

        @Test
        @DisplayName("dequeue khi không có việc: không làm gì, không lỗi")
        void dequeue_missing_doesNothing() {
            when(queueItemRepository.findByTypeAndRefId(QueueType.FORM_REVIEW, "cf-9"))
                    .thenReturn(Optional.empty());

            queueService.dequeue(QueueType.FORM_REVIEW, "cf-9");

            verify(queueItemRepository, never()).delete(any());
        }
    }
}