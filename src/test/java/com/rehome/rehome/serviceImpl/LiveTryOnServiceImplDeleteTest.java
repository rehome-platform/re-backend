package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.DecartVtonClient;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.LiveTryOnStatus;
import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.LiveTryOnSessionRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.FileStorageService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveTryOnServiceImplDeleteTest {
    @Mock LiveTryOnSessionRepository sessions;
    @Mock ProductRepository products;
    @Mock ProductPhotoRepository photos;
    @Mock FileStorageService storage;
    @Mock DecartVtonClient decart;
    @Mock LiveTryOnQuotaGuard quota;
    @InjectMocks LiveTryOnServiceImpl service;

    Account member = Account.builder().id(UUID.randomUUID()).build();
    UUID id = UUID.randomUUID();

    private LiveTryOnSession ownSession(String ext) {
        var session = LiveTryOnSession.builder().id(id).member(member)
                .status(LiveTryOnStatus.COMPLETED)
                .videoUrl("https://storage/try-on-live/" + id + "." + ext)
                .firstFrameAt(Instant.parse("2026-11-26T01:00:00Z"))
                .createdAt(Instant.parse("2026-11-26T00:59:50Z"))
                .completedAt(Instant.parse("2026-11-26T01:00:10Z"))
                .actualDurationSeconds(10).build();
        when(sessions.findByIdAndMember_Id(id, member.getId())).thenReturn(Optional.of(session));
        return session;
    }

    @AfterEach void cleanupTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mp4", "webm"})
    void deleteVideoPreservesUsageAndAuditFieldsAndRemovesStoredFile(String ext) {
        var session = ownSession(ext);
        String url = session.getVideoUrl();
        var before = new Object[] {session.getFirstFrameAt(), session.getCreatedAt(), session.getCompletedAt(),
                session.getStatus(), session.getActualDurationSeconds(), session.getMember()};
        when(storage.deleteByUrl(url, "video/" + ext)).thenReturn(true);

        service.deleteVideo(member, id);

        assertThat(session.getVideoUrl()).isNull();
        assertThat(new Object[] {session.getFirstFrameAt(), session.getCreatedAt(), session.getCompletedAt(),
                session.getStatus(), session.getActualDurationSeconds(), session.getMember()}).containsExactly(before);
        verify(sessions).save(session);
        verify(sessions, never()).delete(any());
        verify(sessions, never()).deleteById(any());
        verify(storage).deleteByUrl(url, "video/" + ext);
        verifyNoInteractions(quota, decart);
    }

    @Test void unknownOrOtherMembersSessionIsNotFound() {
        when(sessions.findByIdAndMember_Id(id, member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteVideo(member, id)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND));

        verify(sessions).findByIdAndMember_Id(id, member.getId());
        verifyNoMoreInteractions(sessions);
        verifyNoInteractions(storage, quota);
    }

    @Test void repeatedDeleteIsSuccessfulWithoutDeletingTwice() {
        var session = ownSession("mp4");
        String url = session.getVideoUrl();
        when(storage.deleteByUrl(url, "video/mp4")).thenReturn(true);

        service.deleteVideo(member, id);
        service.deleteVideo(member, id);

        verify(sessions, times(1)).save(session);
        verify(storage, times(1)).deleteByUrl(url, "video/mp4");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void storageFailureOrMissingFileDoesNotUndoHistoryDeletion(boolean throwsError) {
        var session = ownSession("webm");
        if (throwsError) when(storage.deleteByUrl(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Storage offline"));

        assertThatCode(() -> service.deleteVideo(member, id)).doesNotThrowAnyException();
        assertThat(session.getVideoUrl()).isNull();
        verify(sessions).save(session);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fileIsDeletedOnlyAfterSuccessfulCommit(boolean commit) {
        ownSession("mp4");
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        service.deleteVideo(member, id);
        verifyNoInteractions(storage);

        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        if (commit) {
            callbacks.forEach(TransactionSynchronization::afterCommit);
            verify(storage).deleteByUrl(anyString(), eq("video/mp4"));
        } else {
            callbacks.forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(storage);
        }
    }

    @Test void failedDatabaseSaveDoesNotDeleteTheFile() {
        ownSession("mp4");
        when(sessions.save(any())).thenThrow(new IllegalStateException("DB unavailable"));

        assertThatThrownBy(() -> service.deleteVideo(member, id)).hasMessage("DB unavailable");
        verifyNoInteractions(storage);
    }
}
