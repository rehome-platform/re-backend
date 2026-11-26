package com.rehome.rehome.controller;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.service.QueueService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueControllerTest {
    QueueService service;
    MockMvc mvc;
    Account moderator;
    @BeforeEach void setup() {
        service = mock(QueueService.class);
        moderator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(moderator, null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new QueueController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    void optionalMineQueryParameterIsPassedToService(Boolean mine) throws Exception {
        var request = get("/queues").param("type", "PACKING");
        if (mine != null) request.param("mine", mine.toString());
        mvc.perform(request).andExpect(status().isOk());
        verify(service).list("PACKING", mine, moderator);
    }

    @Test void malformedMineIsRejectedBeforeCallingService() throws Exception {
        mvc.perform(get("/queues").param("mine", "not-a-boolean")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
