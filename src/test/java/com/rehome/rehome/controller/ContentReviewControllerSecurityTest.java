package com.rehome.rehome.controller;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.service.ContentReviewService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentReviewControllerSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean ContentReviewService reviews() { return mock(ContentReviewService.class); }
        @Bean ContentReviewController controller(ContentReviewService reviews) {
            return new ContentReviewController(reviews);
        }
    }

    AnnotationConfigApplicationContext context;
    ContentReviewController controller;
    ContentReviewService reviews;
    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(ContentReviewController.class);
        reviews = context.getBean(ContentReviewService.class);
    }
    @AfterEach void cleanup() {
        SecurityContextHolder.clearContext();
        context.close();
    }
    private Account authenticate(Role role) {
        var actor = Account.builder().id(UUID.randomUUID()).role(role).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        return actor;
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "MODERATOR"})
    void adminAndModeratorCanListAndDecide(Role role) {
        Account actor = authenticate(role);
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> controller.pending(ContentReviewService.Kind.COMMENT, 0, 20));
        assertDoesNotThrow(() -> controller.resolve(actor, ContentReviewService.Kind.COMMENT, id,
                new ContentReviewController.Decision(true, 3L, null)));
        verify(reviews).pending(ContentReviewService.Kind.COMMENT, 0, 20);
        verify(reviews).resolve(actor, ContentReviewService.Kind.COMMENT, id, 3L, true, null);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"MEMBER", "ORGANIZATION"})
    void otherRolesCannotListOrDecide(Role role) {
        Account actor = authenticate(role);
        assertThrows(AccessDeniedException.class,
                () -> controller.pending(ContentReviewService.Kind.POST, 0, 20));
        assertThrows(AccessDeniedException.class, () -> controller.resolve(actor, ContentReviewService.Kind.POST,
                UUID.randomUUID(), new ContentReviewController.Decision(true, 3L, null)));
        verifyNoInteractions(reviews);
    }

    @Test void unauthenticatedCallerCannotReadReviewQueue() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.pending(ContentReviewService.Kind.POST, 0, 20));
        verifyNoInteractions(reviews);
    }
}
