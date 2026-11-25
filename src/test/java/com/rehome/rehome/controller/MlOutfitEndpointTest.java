package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.MlOutfitSuggestionsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.GlobalExceptionHandler;
import com.rehome.rehome.service.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MlOutfitEndpointTest {
    final TryOnService legacy = mock(TryOnService.class);
    final MlOutfitSuggestionService ml = mock(MlOutfitSuggestionService.class);
    final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TryOnController(legacy, mock(MembershipService.class), ml))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void newRouteUsesAuthenticatedIdentityAndKeepsLegacyRouteIndependent() throws Exception {
        Account account = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(account, null, account.getAuthorities()));
        when(ml.suggest(account, "top", null, null)).thenReturn(MlOutfitSuggestionsResponse.builder()
                .model("multinomial-nb-v1").personalized(true).historyItemsUsed(12).candidateItemsScored(30)
                .items(List.of()).build());
        mvc.perform(get("/tryon/outfit-suggestions/ml").param("productId", "top")
                        .param("memberId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.model").value("multinomial-nb-v1"))
                .andExpect(jsonPath("$.result.personalized").value(true))
                .andExpect(jsonPath("$.result.historyItemsUsed").value(12));
        verify(ml).suggest(account, "top", null, null);
        verifyNoInteractions(legacy);
        when(legacy.getOutfitSuggestions("top", null, null)).thenReturn(List.of());
        mvc.perform(get("/tryon/outfit-suggestions").param("productId", "top")).andExpect(status().isOk());
        verify(legacy).getOutfitSuggestions("top", null, null);
        verifyNoMoreInteractions(ml);
    }

    @Test void missingTokenIsRejected() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get("/tryon/outfit-suggestions/ml").param("productId", "top"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ml, legacy);
    }
}
