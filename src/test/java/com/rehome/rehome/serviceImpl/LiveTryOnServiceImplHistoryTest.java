package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.client.DecartVtonClient;
import com.rehome.rehome.dto.response.LiveTryOnHistoryItemResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.repository.LiveTryOnSessionRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.FileStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class LiveTryOnServiceImplHistoryTest {

    @Mock private LiveTryOnSessionRepository sessionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductPhotoRepository productPhotoRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private DecartVtonClient decartVtonClient;
    @Mock private LiveTryOnQuotaGuard quotaGuard;

    @InjectMocks private LiveTryOnServiceImpl service;

    private Account member() {
        Account a = new Account();
        a.setId(UUID.randomUUID());
        return a;
    }

    private Product product(String id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        return p;
    }

    private LiveTryOnSession session(Product product, String videoUrl) {
        return LiveTryOnSession.builder()
                .id(UUID.randomUUID())
                .product(product)
                .modelId("lucy-vton-3.5")
                .status(LiveTryOnStatus.COMPLETED)
                .videoUrl(videoUrl)
                .actualDurationSeconds(10)
                .createdAt(Instant.parse("2026-11-16T01:00:00Z"))
                .completedAt(Instant.parse("2026-11-16T01:00:12Z"))
                .build();
    }

    @Test
    void getHistory_returnsOnlyCompletedWithVideo_mappedWithNameAndThumbnail() {
        Account account = member();
        Product p = product("P-1", "Áo khoác denim");
        LiveTryOnSession s = session(p, "https://res.cloudinary.com/x/raw/upload/v1/try-on-live/a.mp4");

        when(sessionRepository.findByMember_IdAndStatusAndVideoUrlIsNotNull(
                eq(account.getId()), eq(LiveTryOnStatus.COMPLETED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(s)));
        ProductPhoto photo = new ProductPhoto();
        photo.setProduct(p);
        photo.setImageUrl("https://img/p1.jpg");
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of("P-1")))
                .thenReturn(List.of(photo));

        PageResponse<LiveTryOnHistoryItemResponse> page = service.getHistory(account, 0, 20);

        assertThat(page.getContent()).hasSize(1);
        LiveTryOnHistoryItemResponse item = page.getContent().get(0);
        assertThat(item.getSessionId()).isEqualTo(s.getId());
        assertThat(item.getProductName()).isEqualTo("Áo khoác denim");
        assertThat(item.getProductThumbnailUrl()).isEqualTo("https://img/p1.jpg");
        assertThat(item.getVideoUrl()).endsWith("a.mp4");
    }

    @Test
    void getHistory_clampsPageSizeAndSortsNewestFirst() {
        Account account = member();
        when(sessionRepository.findByMember_IdAndStatusAndVideoUrlIsNotNull(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getHistory(account, -5, 9999);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(sessionRepository).findByMember_IdAndStatusAndVideoUrlIsNotNull(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void getHistory_skipsThumbnailQuery_whenNoSessions() {
        when(sessionRepository.findByMember_IdAndStatusAndVideoUrlIsNotNull(any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        PageResponse<LiveTryOnHistoryItemResponse> page = service.getHistory(member(), 0, 20);

        assertThat(page.getContent()).isEmpty();
        verify(productPhotoRepository, never()).findByProduct_IdInOrderByDisplayOrderAscIdAsc(any());
    }
}
