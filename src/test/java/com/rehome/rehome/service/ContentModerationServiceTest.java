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
