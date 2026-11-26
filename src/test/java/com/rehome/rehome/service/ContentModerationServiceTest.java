package com.rehome.rehome.service;

import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ViolationWordRepository;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentModerationServiceTest {
    @ParameterizedTest
    @EnumSource(ViolationWordAction.class)
    void reviewEvaluationReturnsEveryActionWithoutBlocking(ViolationWordAction action) {
        var service = serviceFor("đồ ngu", action);
        assertEquals(action, service.evaluateForReview(null, "D.O   NGU!"));
    }

    @Test
    void reviewEvaluationSelectsHighestSeverityAndIgnoresPartialWords() {
        var words = mock(ViolationWordRepository.class);
        when(words.findAllByIsActiveTrue()).thenReturn(List.of(
                ViolationWord.builder().word("abc").action(ViolationWordAction.AUTO_REJECT).build(),
                ViolationWord.builder().word("đồ ngu").action(ViolationWordAction.AUTO_FLAG).build(),
                ViolationWord.builder().word("xin otp").action(ViolationWordAction.NOTIFY_ADMIN).build()));
        var service = new ContentModerationService(words, null, null, null);
        assertEquals(ViolationWordAction.AUTO_REJECT, service.evaluateForReview("a*b_c", "do ngu xin otp"));
        assertEquals(ViolationWordAction.AUTO_FLAG, service.evaluateForReview("đồ ngu", "xin otp"));
        assertEquals(ViolationWordAction.NOTIFY_ADMIN, service.evaluateForReview("xin OTP"));
        assertNull(service.evaluateForReview("xabcd", "do", "ngu"));
        assertEquals(ErrorCode.CONTENT_VIOLATION,
                assertThrows(AppException.class, () -> service.evaluate("abc")).getErrorCode());
    }

    @Test
    void reviewEvaluationDoesNotLoadRulesForMissingText() {
        var words = mock(ViolationWordRepository.class);
        var service = new ContentModerationService(words, null, null, null);
        assertNull(service.evaluateForReview());
        assertNull(service.evaluateForReview((String[]) null));
        assertNull(service.evaluateForReview(null, "", "  "));
        verifyNoInteractions(words);
    }

    @ParameterizedTest
    @CsvSource({"POST,AUTO_REJECT", "POST,AUTO_FLAG", "POST,NOTIFY_ADMIN",
            "POST_COMMENT,AUTO_REJECT", "POST_COMMENT,AUTO_FLAG", "POST_COMMENT,NOTIFY_ADMIN"})
    void communityMatchesFlagAuditAndNotifyEveryAdmin(String context, ViolationWordAction action) {
        var accounts = mock(AccountRepository.class);
        var audits = mock(AuditLogRepository.class);
        var notifications = mock(NotificationService.class);
        UUID actorId = UUID.randomUUID();
        UUID contentId = UUID.randomUUID();
        var actor = Account.builder().id(actorId).build();
        var firstAdmin = Account.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        var secondAdmin = Account.builder().id(UUID.randomUUID()).role(Role.ADMIN).build();
        when(accounts.getReferenceById(actorId)).thenReturn(actor);
        when(accounts.findByRole(Role.ADMIN)).thenReturn(List.of(firstAdmin, secondAdmin));
        when(audits.save(any())).thenAnswer(inv -> inv.getArgument(0));

        new ContentModerationService(mock(ViolationWordRepository.class), accounts, audits, notifications)
                .recordMatch(actorId, context, contentId, action);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(captor.capture());
        var audit = captor.getValue();
        assertEquals("CONTENT_AUTO_FLAG", audit.getActionType());
        assertEquals("CONTENT_REVIEW", audit.getTargetType());
        assertEquals(actor, audit.getActorAccount());
        assertEquals(context, audit.getDetail().get("context").asText());
        assertEquals(contentId.toString(), audit.getDetail().get("contentId").asText());
        assertEquals(action.name(), audit.getDetail().get("moderationAction").asText());
        for (var admin : List.of(firstAdmin, secondAdmin)) {
            verify(notifications).notify(eq(admin), eq(NotificationType.CONTENT_REVIEW), anyString(),
                    contains(audit.getTargetId()), isNull(), isNull(), isNull());
        }
        verifyNoMoreInteractions(notifications);
    }

    @ParameterizedTest
    @EnumSource(ViolationWordAction.class)
    void chatOnlyRecordsMaskedAuditWithoutAdminNotifications(ViolationWordAction action) {
        var accounts = mock(AccountRepository.class);
        var audits = mock(AuditLogRepository.class);
        var notifications = mock(NotificationService.class);
        UUID actorId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(accounts.getReferenceById(actorId)).thenReturn(Account.builder().id(actorId).build());

        new ContentModerationService(mock(ViolationWordRepository.class), accounts, audits, notifications)
                .recordMatch(actorId, "CHAT", messageId, action);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(audits).save(captor.capture());
        assertEquals("CONTENT_CHAT_MASKED", captor.getValue().getActionType());
        assertEquals("CONTENT_REVIEW", captor.getValue().getTargetType());
        assertEquals(messageId.toString(), captor.getValue().getDetail().get("contentId").asText());
        assertFalse(captor.getValue().getDetail().has("content"));
        verify(accounts, never()).findByRole(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void absentMatchDoesNotWriteAuditOrNotify() {
        var accounts = mock(AccountRepository.class);
        var audits = mock(AuditLogRepository.class);
        var notifications = mock(NotificationService.class);
        new ContentModerationService(mock(ViolationWordRepository.class), accounts, audits, notifications)
                .recordMatch(UUID.randomUUID(), "POST", UUID.randomUUID(), null);
        verifyNoInteractions(accounts, audits, notifications);
    }

    private ContentModerationService serviceFor(String term, ViolationWordAction action) {
        var repository = mock(ViolationWordRepository.class);
        when(repository.findAllByIsActiveTrue()).thenReturn(List.of(
                ViolationWord.builder().word(term).action(action).build()));
        return new ContentModerationService(repository, null, null, null);
    }

    @Test void maskCoversAllActionsAndReturnsTheHighestWithoutRejecting() {
        var repository = mock(ViolationWordRepository.class);
        when(repository.findAllByIsActiveTrue()).thenReturn(List.of(
                ViolationWord.builder().word("đồ ngu").action(ViolationWordAction.AUTO_REJECT).build(),
                ViolationWord.builder().word("abc").action(ViolationWordAction.AUTO_FLAG).build(),
                ViolationWord.builder().word("xin otp").action(ViolationWordAction.NOTIFY_ADMIN).build()));
        var service = new ContentModerationService(repository, null, null, null);
        String text = "Mình cần đồ tốt. ĐỒ NGU, a*b_c và xin OTP!";
        var masked = service.mask(text);
        assertEquals("Mình cần đồ tốt. ** ***, ***_* và *** ***!", masked.text());
        assertEquals(text.length(), masked.text().length());
        assertEquals(ViolationWordAction.AUTO_REJECT, masked.action());
        assertEquals(ViolationWordAction.AUTO_FLAG, service.mask("abc và xin otp").action());
        assertEquals(ViolationWordAction.NOTIFY_ADMIN, service.mask("xin otp").action());
        assertEquals(ErrorCode.CONTENT_VIOLATION,
                assertThrows(AppException.class, () -> service.evaluate("đồ ngu")).getErrorCode());
    }

    @Test void maskPreservesPositionsAroundAccentsEmojiAndDecomposedVietnamese() {
        var service = serviceFor("đồ ngu", ViolationWordAction.AUTO_REJECT);
        String text = "e\u0301 😀 ĐO\u0302\u0300 NGU!";
        var masked = service.mask(text);
        assertEquals("e\u0301 😀 **** ***!", masked.text());
        assertEquals(text.length(), masked.text().length());
        assertEquals("İ 😀 tốt, ** *** rồi.", service.mask("İ 😀 tốt, ĐỒ NGU rồi.").text());
    }

    @Test void maskRespectsWholeWordsAndLeavesPunctuationUntouched() {
        var service = serviceFor("ngu", ViolationWordAction.AUTO_FLAG);
        assertEquals("nguyen nguoi (***), ***!", service.mask("nguyen nguoi (NGU), ngu!").text());
        assertEquals("nguyen nguoi", service.mask("nguyen nguoi").text());
        assertNull(service.mask("nguyen nguoi").action());
    }

    @Test void maskIncludesOverlappingPhrases() {
        var service = serviceFor("ngu ngu", ViolationWordAction.AUTO_FLAG);
        assertEquals("*** *** ***", service.mask("ngu ngu ngu").text());
    }

    @Test void maskDoesNotLoadRulesForAbsentOrBlankText() {
        var repository = mock(ViolationWordRepository.class);
        var service = new ContentModerationService(repository, null, null, null);
        assertEquals(new ContentModerationService.MaskResult(null, null), service.mask(null));
        assertEquals(new ContentModerationService.MaskResult("  ", null), service.mask("  "));
        verifyNoInteractions(repository);
    }

    @Test
    void checksVietnameseVariantsAndAppliesStrongestMatchingAction() {
        var repository = mock(ViolationWordRepository.class);
        when(repository.findAllByIsActiveTrue()).thenReturn(List.of(
                ViolationWord.builder().word("đồ ngu").action(ViolationWordAction.AUTO_FLAG).build(),
                ViolationWord.builder().word("xin otp").action(ViolationWordAction.NOTIFY_ADMIN).build(),
                ViolationWord.builder().word("abc").action(ViolationWordAction.AUTO_REJECT).build()));
        var service = new ContentModerationService(repository, null, null, null);

        assertEquals(ViolationWordAction.AUTO_FLAG, service.evaluate("D.O   NGU", "xin otp"));
        assertEquals(ViolationWordAction.NOTIFY_ADMIN, service.evaluate("Xin OTP"));
        assertNull(service.evaluate("xabcd", "do", "ngu"));
        assertEquals(ErrorCode.CONTENT_VIOLATION,
                assertThrows(AppException.class, () -> service.evaluate("đồ ngu", "a*b_c!")).getErrorCode());
        verify(repository).findAllByIsActiveTrue();
        service.invalidate(new ContentModerationService.WordsChanged());
        service.evaluate("sạch");
        verify(repository, times(2)).findAllByIsActiveTrue();
    }
}
