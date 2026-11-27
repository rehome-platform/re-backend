package com.rehome.rehome.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test void generalErrorLogsRequestAndStackTraceWithoutChangingApiErrorBody() {
        var logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        try {
            var request = new MockHttpServletRequest("GET", "/conversations");
            var exception = new IllegalStateException("Unexpected inbox failure");

            var response = new GlobalExceptionHandler().handleGeneral(exception, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(500);
            assertThat(response.getBody().getError()).isEqualTo("Internal Server Error");
            assertThat(response.getBody().getMessage()).isEqualTo(exception.getMessage());
            assertThat(response.getBody().getPath()).isEqualTo("/conversations");
            assertThat(response.getBody().getTimestamp()).isNotNull();
            assertThat(logs.list).hasSize(1);
            var event = logs.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("GET /conversations", exception.getMessage());
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test void unpaidWithdrawalFeeHas409AndActionableMobilePaymentMessage() {
        var response = new GlobalExceptionHandler().handle(new AppException(ErrorCode.WITHDRAWAL_FEE_NOT_PAID));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("WITHDRAWAL_FEE_NOT_PAID");
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo(
                "Member chưa trả phí rút hàng. Nhắc Member trả trên app ReHome trước khi bàn giao.");
    }
}
