package com.rehome.rehome.controller;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.GlobalExceptionHandler;
import com.rehome.rehome.service.LiveTryOnService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(LiveTryOnControllerDeleteTest.Config.class)
@WebAppConfiguration
class LiveTryOnControllerDeleteTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean LiveTryOnService service() { return mock(LiveTryOnService.class); }
        @Bean LiveTryOnController controller(LiveTryOnService service) { return new LiveTryOnController(service); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .build();
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired LiveTryOnService service;
    MockMvc mvc;
    UUID id = UUID.randomUUID();

    @BeforeEach void setup() {
        reset(service);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private UsernamePasswordAuthenticationToken principal(Role role) {
        Account account = Account.builder().id(UUID.randomUUID()).role(role).build();
        return new UsernamePasswordAuthenticationToken(account, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test void memberCanDeleteAndReceivesMobileSuccessContract() throws Exception {
        var auth = principal(Role.MEMBER);
        mvc.perform(delete("/tryon/live/sessions/{id}", id).with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.message").value("Đã xoá video thử đồ"));
        verify(service).deleteVideo((Account) auth.getPrincipal(), id);
    }

    @Test void missingOrForeignSessionReturns404() throws Exception {
        doThrow(new AppException(ErrorCode.TRYON_LIVE_SESSION_NOT_FOUND)).when(service).deleteVideo(any(), eq(id));
        mvc.perform(delete("/tryon/live/sessions/{id}", id).with(authentication(principal(Role.MEMBER))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRYON_LIVE_SESSION_NOT_FOUND"));
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = "MEMBER", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotDelete(Role role) throws Exception {
        mvc.perform(delete("/tryon/live/sessions/{id}", id).with(authentication(principal(role))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void anonymousCannotDelete() throws Exception {
        mvc.perform(delete("/tryon/live/sessions/{id}", id)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
