package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.AiTryOnQuotaGuard;
import com.rehome.rehome.client.GeminiOutfitClient;
import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.entity.tryon.*;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.mockito.quality.Strictness;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedTryOnReuseTest {
    @Mock TryOnImageRepository tryOnImageRepository;
    @Mock TryOnSharedAssetRepository sharedAssetRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @Mock FileStorageService fileStorageService;
    @Mock TryOnImageGenerator tryOnImageGenerator;
    @Mock AiTryOnQuotaGuard aiTryOnQuotaGuard;
    @Mock GeminiOutfitClient geminiOutfitClient;
    @InjectMocks TryOnServiceImpl service;
    final Account a = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
    final Account b = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
    final Map<String, TryOnSharedAsset> assets = new ConcurrentHashMap<>();
    final Map<String, TryOnImage> images = new ConcurrentHashMap<>();
    final CreateTryOnImageRequest request = new CreateTryOnImageRequest();

    @BeforeEach void setup() {
        request.setModelType(TryOnModelType.FEMALE);
        request.setTopProductId("top"); request.setBottomProductId("bottom");
        Category topCategory = mock(Category.class), bottomCategory = mock(Category.class);
        when(topCategory.getTryonSlot()).thenReturn(TryOnSlot.TOP);
        when(bottomCategory.getTryonSlot()).thenReturn(TryOnSlot.BOTTOM);
        Product top = Product.builder().id("top").name("Top").category(topCategory).status(ProductStatus.ON_SALE).build();
        Product bottom = Product.builder().id("bottom").name("Bottom").category(bottomCategory).status(ProductStatus.ON_SALE).build();
        when(productRepository.findWithCategoryByIdIn(anyCollection())).thenReturn(List.of(top, bottom));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(ProductPhoto.builder().product(top).imageUrl("top-photo").build(),
                        ProductPhoto.builder().product(bottom).imageUrl("bottom-photo").build()));
        when(fileStorageService.loadByUrl(anyString())).thenReturn(Optional.of(new byte[]{1}));
        when(tryOnImageGenerator.generateOutfit(any(), any(), any())).thenReturn(new byte[]{2});
        when(fileStorageService.store(anyString(), any(), anyString())).thenReturn("shared-image");
        when(sharedAssetRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(assets.get(inv.getArgument(0))));
        when(sharedAssetRepository.saveAndFlush(any())).thenAnswer(inv -> {
            TryOnSharedAsset asset = inv.getArgument(0); assets.put(asset.getFingerprint(), asset); return asset;
        });
        when(tryOnImageRepository.findFirstByMember_IdAndGenerationFingerprintAndDeletedAtIsNullOrderByCreatedAtDesc(any(), anyString()))
                .thenAnswer(inv -> Optional.ofNullable(images.get(inv.getArgument(0) + ":" + inv.getArgument(1)))
                        .filter(image -> image.getDeletedAt() == null));
        when(tryOnImageRepository.save(any())).thenAnswer(inv -> {
            TryOnImage image = inv.getArgument(0); image.setId(UUID.randomUUID()); image.setCreatedAt(Instant.now());
            images.put(image.getMember().getId() + ":" + image.getGenerationFingerprint(), image); return image;
        });
    }

    @Test void anotherMemberReusesAssetWithoutQuotaOrGpuAndGetsOwnId() {
        when(aiTryOnQuotaGuard.assertCanGenerate(a.getId())).thenReturn(true);
        var first = service.createImage(a, request);
        var reused = service.createImage(b, request);
        assertThat(first.isReused()).isFalse();
        assertThat(reused.isReused()).isTrue();
        assertThat(reused.getId()).isNotEqualTo(first.getId());
        assertThat(reused.getImageUrl()).isEqualTo(first.getImageUrl());
        verify(tryOnImageGenerator, times(1)).generateOutfit(any(), any(), any());
        verify(aiTryOnQuotaGuard, never()).assertCanGenerate(b.getId());
        verify(aiTryOnQuotaGuard, times(1)).consumeBonusCredit(a.getId());
        assertThat(images.values().stream().filter(TryOnImage::isQuotaCharged).count()).isEqualTo(1);
    }

    @Test void repeatedUseDoesNotDuplicatePersonalCollectionEntry() {
        var first = service.createImage(a, request);
        var next = service.createImage(a, request);
        assertThat(next.getId()).isEqualTo(first.getId());
        assertThat(next.isReused()).isTrue();
        assertThat(images).hasSize(1);
        verify(tryOnImageGenerator, times(1)).generateOutfit(any(), any(), any());
    }

    @Test void concurrentMembersGenerateOnce() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(tryOnImageGenerator.generateOutfit(any(), any(), any())).thenAnswer(inv -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("GPU test timed out");
            return new byte[]{2};
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.createImage(a, request));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.createImage(b, request));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).isReused()).isFalse();
            assertThat(second.get(5, TimeUnit.SECONDS).isReused()).isTrue();
            verify(tryOnImageGenerator, times(1)).generateOutfit(any(), any(), any());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test void deletingOneMembersEntryKeepsSharedFileAndOtherMemberCanStillUseIt() {
        var first = service.createImage(a, request);
        service.createImage(b, request);
        TryOnImage original = images.values().stream().filter(i -> i.getMember() == a).findFirst().orElseThrow();
        when(tryOnImageRepository.findById(first.getId())).thenReturn(Optional.of(original));
        service.deleteImage(a, first.getId());
        assertThat(original.getDeletedAt()).isNotNull();
        assertThat(original.isQuotaCharged()).isTrue();
        assertThat(service.createImage(b, request).getImageUrl()).isEqualTo(first.getImageUrl());
        var restored = service.createImage(a, request);
        assertThat(restored.getId()).isNotEqualTo(first.getId());
        assertThat(restored.isReused()).isTrue();
        verify(fileStorageService, never()).deleteByUrl(anyString(), anyString());
    }

    @Test void publicationFailureRemovesUnownedFileAndUnlocksForRetry() {
        doThrow(new IllegalStateException("database unavailable")).when(sharedAssetRepository).saveAndFlush(any());
        assertThatThrownBy(() -> service.createImage(a, request)).hasMessage("database unavailable");
        assertThatThrownBy(() -> service.createImage(a, request)).hasMessage("database unavailable");
        verify(fileStorageService, times(2)).deleteByUrl("shared-image", "image/png");
        verify(tryOnImageRepository, never()).save(any());
    }

    @Test void publicationRaceReusesWinnerAndDoesNotConsumeBonusCredit() {
        when(aiTryOnQuotaGuard.assertCanGenerate(a.getId())).thenReturn(true);
        doAnswer(inv -> {
            TryOnSharedAsset candidate = inv.getArgument(0);
            assets.put(candidate.getFingerprint(), TryOnSharedAsset.builder()
                    .fingerprint(candidate.getFingerprint()).imageUrl("winner-image").build());
            throw new org.springframework.dao.DataIntegrityViolationException("duplicate fingerprint");
        }).when(sharedAssetRepository).saveAndFlush(any());
        var response = service.createImage(a, request);
        assertThat(response.isReused()).isTrue();
        assertThat(response.getImageUrl()).isEqualTo("winner-image");
        verify(fileStorageService).deleteByUrl("shared-image", "image/png");
        verify(aiTryOnQuotaGuard, never()).consumeBonusCredit(any());
        assertThat(images.values()).allMatch(image -> !image.isQuotaCharged());
    }
}
