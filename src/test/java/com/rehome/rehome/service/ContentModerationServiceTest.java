package com.rehome.rehome.service;

import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ViolationWordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentModerationServiceTest {
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
