package com.rehome.rehome.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.GlobalExceptionHandler;
import com.rehome.rehome.exception.OtpException;
import com.rehome.rehome.service.*;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** HTTP coverage for Boot's configured forwarded-header filter and API error contract. */
@SpringBootTest(classes = RegistrationAndOtpHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.banner-mode=off")
class RegistrationAndOtpHttpTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class, SecurityAutoConfiguration.class})
    @Import({MemberProfileController.class, OrganizationProfileController.class, AuthController.class,
            GlobalExceptionHandler.class})
    static class TestApplication {}

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired Environment environment;
    @MockBean AuthService auth;
    @MockBean OtpService otp;
    @MockBean OtpRateLimiter limiter;
    @MockBean OrganizationService organizations;
    @MockBean CampaignService campaigns;
    @MockBean CommunityPostService posts;
    @MockBean DonationService donations;
    @MockBean OrganizationLegalFileService legalFiles;

    @BeforeEach void setup() {
        // Inspect the first 429 response without HTTP client's Retry-After retries.
        var client = new SimpleClientHttpRequestFactory();
        client.setConnectTimeout(5_000);
        client.setReadTimeout(5_000);
        http.getRestTemplate().setRequestFactory(client);
        when(auth.registerMember(any(RegisterRequest.class), anyString())).thenReturn(new OtpResponse("Đã đăng ký."));
        when(auth.registerOrganization(any(RegisterRequest.class), anyString())).thenReturn(new OtpResponse("Đã đăng ký."));
    }

    private HttpEntity<Map<String, String>> registration(String forwarded) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (forwarded != null) headers.set("X-Forwarded-For", forwarded);
        return new HttpEntity<>(Map.of("email", "new@example.com", "password", "12345678",
                "fullName", "Nguyen Minh Nguyen", "phone", "0909000000"), headers);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/register", "/api/organizations/register/account"})
    void forwardedClientsReachRegistrationServiceAsSeparateIps(String path) {
        assertThat(environment.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(http.postForEntity(path, registration("198.51.100.10, 10.0.0.2"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity(path, registration("198.51.100.20, 10.0.0.2"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<String> ips = ArgumentCaptor.forClass(String.class);
        if (path.equals("/api/auth/register")) verify(auth, times(2)).registerMember(any(RegisterRequest.class), ips.capture());
        else verify(auth, times(2)).registerOrganization(any(RegisterRequest.class), ips.capture());
        assertThat(ips.getAllValues()).containsExactly("198.51.100.10", "198.51.100.20");
        verifyNoInteractions(limiter);
    }

    @Test void directLocalRequestStillProvidesItsRemoteAddress() {
        assertThat(http.postForEntity("/api/auth/register", registration(null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ArgumentCaptor<String> ip = ArgumentCaptor.forClass(String.class);
        verify(auth).registerMember(any(RegisterRequest.class), ip.capture());
        assertThat(ip.getValue()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
    }

    @ParameterizedTest
    @CsvSource({"/api/auth/register,EMAIL_ALREADY_EXISTS", "/api/organizations/register/account,EMAIL_ALREADY_EXISTS",
            "/api/auth/register,PHONE_TAKEN", "/api/organizations/register/account,PHONE_TAKEN",
            "/api/auth/register,CONTENT_VIOLATION", "/api/organizations/register/account,CONTENT_VIOLATION"})
    void registrationBusinessErrorsKeepTheirStatusAndMobileErrorCode(String path, ErrorCode error) throws Exception {
        if (path.equals("/api/auth/register"))
            doThrow(new AppException(error)).when(auth).registerMember(any(RegisterRequest.class), anyString());
        else doThrow(new AppException(error)).when(auth).registerOrganization(any(RegisterRequest.class), anyString());
        ResponseEntity<String> response = http.postForEntity(path, registration("198.51.100.10"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(error.getStatus());
        assertThat(mapper.readTree(response.getBody()).get("code").asText()).isEqualTo(error.name());
        verifyNoInteractions(limiter, otp);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/resend-otp", "/api/auth/password/forgot"})
    void otpErrorsExposeHumanWaitAndRetryAfterUsingForwardedClientIp(String path) throws Exception {
        String ip = "198.51.100.10";
        doThrow(new OtpException(HttpStatus.TOO_MANY_REQUESTS, "Quá nhiều yêu cầu.", 720L))
                .when(limiter).check("new@example.com", ip);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", ip);
        var request = new HttpEntity<>(Map.of("email", "new@example.com", "purpose", "REGISTER"), headers);
        ResponseEntity<String> response = http.postForEntity(path, request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("720");
        assertThat(mapper.readTree(response.getBody()).get("message").asText())
                .isEqualTo("Quá nhiều yêu cầu. Vui lòng thử lại sau 12 phút.");
        verify(limiter).check("new@example.com", ip);
        verifyNoInteractions(otp);
    }
}
