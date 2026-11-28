package com.rehome.rehome.config;

import com.rehome.rehome.security.JwtAccessDeniedHandler;
import com.rehome.rehome.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/health",
            "/ws/**",
            "/ws-sockjs/**",
            "/api/auth/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**",
            "/api/vnpay/ipn-handler",
            "/api/vnpay/return-info",
            "/webhooks/ghn"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/password/change",
                                "/api/auth/logout").authenticated()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/warehouses").permitAll()
                        .requestMatchers(HttpMethod.GET, "/memberships/plans").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                        .requestMatchers("/files/organization-documents/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/organizations/register/account").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/organizations/me", "/api/organizations/options")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/organizations/following").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/organizations").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/organizations/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/organizations/{id}/campaigns").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/categories", "/brands").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/products",
                                "/products/{id}",
                                "/products/detail/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/campaigns", "/campaigns/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts", "/posts/{id}", "/posts/{id}/comments").permitAll()
                        .requestMatchers(HttpMethod.POST, "/warehouses").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/warehouses/{id}").hasRole("ADMIN")
                        .requestMatchers("/warehouses/moderator").hasRole("MODERATOR")
                        .requestMatchers(HttpMethod.GET, "/warehouses/{id}/campaigns")
                                .hasAnyRole("MODERATOR", "MEMBER")
                        .requestMatchers("/warehouses/{id}/donations",
                                "/warehouses/{id}/donations/**").hasRole("MODERATOR")
                        .requestMatchers("/warehouses/**").hasRole("MEMBER")
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}