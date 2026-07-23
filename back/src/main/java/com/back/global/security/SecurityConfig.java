package com.back.global.security;

import com.back.global.rsData.RsData;
import com.back.global.security.oauth.CustomOAuth2UserService;
import com.back.global.security.oauth.OAuth2LoginSuccessHandler;
import com.back.standard.util.Ut;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final CustomAuthenticationFilter customAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final PasswordEncoder passwordEncoder;

    @Value("${custom.actuator.username}")
    private String actuatorUsername;

    @Value("${custom.actuator.password}")
    private String actuatorPassword;

    // Prometheus 등 자동 스크래퍼는 앱의 JWT/OAuth2 로그인을 탈 수 없으므로,
    // /actuator/**는 별도의 Basic Auth 계정으로 완전히 독립된 필터체인에서 인증한다.
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        DaoAuthenticationProvider actuatorAuthenticationProvider = new DaoAuthenticationProvider(actuatorUserDetailsService());
        actuatorAuthenticationProvider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher("/actuator/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().hasRole("ACTUATOR")
                )
                // 앱에 이미 CustomUserDetailsService가 존재해 UserDetailsService 빈이 2개가 되므로,
                // 전역 AuthenticationManager 자동 구성에 기대지 않고 이 체인 전용 provider를 명시한다.
                .authenticationProvider(actuatorAuthenticationProvider)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public UserDetailsService actuatorUserDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername(actuatorUsername)
                        .password(passwordEncoder.encode(actuatorPassword))
                        .roles("ACTUATOR")
                        .build()
        );
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers(
                                        "/api/*/members/login",
                                        "/api/*/members/refresh",
                                        "/api/*/members/oauth/exchange",
                                        "/api/*/matches/stats/home"
                                ).permitAll()
                                .requestMatchers(
                                        HttpMethod.POST,
                                        "/api/*/members/signup"
                                ).permitAll()
                                .requestMatchers("/api/*/admin/**").hasRole("ADMIN")
                                .requestMatchers("/api/*/**").authenticated()
                                .anyRequest().permitAll()
                )
                .headers(
                        headers -> headers
                                .frameOptions(
                                        HeadersConfigurer.FrameOptionsConfig::sameOrigin
                                )
                )
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(endpoint -> endpoint.userService(customOAuth2UserService))
                        .successHandler(oauth2LoginSuccessHandler)
                )
                .sessionManagement(AbstractHttpConfigurer::disable)
                .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        exceptionHandling -> exceptionHandling
                                .authenticationEntryPoint(
                                        (request, response, authException) -> {
                                            response.setContentType("application/json;charset=UTF-8");
                                            response.setStatus(401);
                                            response.getWriter().write(
                                                    Ut.json.toString(
                                                            new RsData<Void>(
                                                                    "401-1",
                                                                    "로그인 후 이용해주세요."
                                                            )
                                                    )
                                            );
                                        }
                                )
                                .accessDeniedHandler(
                                        (request, response, accessDeniedException) -> {
                                            response.setContentType("application/json;charset=UTF-8");
                                            response.setStatus(403);
                                            response.getWriter().write(
                                                    Ut.json.toString(
                                                            new RsData<Void>(
                                                                    "403-1",
                                                                    "권한이 없습니다."
                                                            )
                                                    )
                                            );
                                        }
                                )
                );

        return http.build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("https://cdpn.io", "http://localhost:3000", "https://tangbisil-production.up.railway.app"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
