package com.back.global.security

import com.back.global.rsData.RsData
import com.back.global.security.oauth2.CustomOAuth2UserService
import com.back.global.security.oauth2.OAuth2LoginSuccessHandler
import com.back.standard.util.Ut
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val customAuthenticationFilter: CustomAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oauth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val passwordEncoder: PasswordEncoder
) {
    @Value("\${custom.cors.allowed-origins}")
    private lateinit var allowedOrigins: Array<String>

    @Value("\${custom.actuator.username}")
    private lateinit var actuatorUsername: String

    @Value("\${custom.actuator.password}")
    private lateinit var actuatorPassword: String

    // Prometheus 등 자동 스크래퍼는 앱의 JWT/OAuth2 로그인을 탈 수 없으므로,
    // /actuator/**는 별도의 Basic Auth 계정으로 완전히 독립된 필터체인에서 인증한다.
    @Bean
    @Order(1)
    fun actuatorFilterChain(http: HttpSecurity): SecurityFilterChain {
        val actuatorAuthenticationProvider = DaoAuthenticationProvider(actuatorUserDetailsService())
        actuatorAuthenticationProvider.setPasswordEncoder(passwordEncoder)

        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest().hasRole("ACTUATOR")
            }
            // 앱에 이미 CustomUserDetailsService가 존재해 UserDetailsService 빈이 2개가 되므로,
            // 전역 AuthenticationManager 자동 구성에 기대지 않고 이 체인 전용 provider를 명시한다.
            .authenticationProvider(actuatorAuthenticationProvider)
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic(Customizer.withDefaults())
            .sessionManagement { it.disable() }

        return http.build()
    }

    @Bean
    fun actuatorUserDetailsService(): UserDetailsService =
        InMemoryUserDetailsManager(
            User.withUsername(actuatorUsername)
                .password(passwordEncoder.encode(actuatorPassword))
                .roles("ACTUATOR")
                .build()
        )

    @Bean
    @Order(2)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/prometheus").permitAll()
                    .requestMatchers(
                        "/api/*/members/login",
                        "/api/*/members/refresh",
                        "/api/*/matches/stats/home",
                        "/api/*/members/email-verification/**",
                        "/api/*/members/password-reset/**",
                        "/api/*/trend-keywords"
                    ).permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/*/members/signup"
                    ).permitAll()
                    .requestMatchers("/api/*/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/*/**").authenticated()
                    .requestMatchers("/ws/**").permitAll()
                    .anyRequest().permitAll()
            }
            .headers { headers ->
                headers.frameOptions { it.sameOrigin() }
            }
            .csrf { it.disable() }
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .httpBasic { it.disable() }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { endpoint -> endpoint.userService(customOAuth2UserService) }
                    .successHandler(oauth2LoginSuccessHandler)
            }
            .sessionManagement { it.disable() }
            .addFilterBefore(customAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { _, response, _ ->
                        response.contentType = "application/json;charset=UTF-8"
                        response.status = 401
                        response.writer.write(
                            Ut.json.toString(RsData<Void>("401-1", "로그인 후 이용해주세요."))
                        )
                    }
                    .accessDeniedHandler { _, response, _ ->
                        response.contentType = "application/json;charset=UTF-8"
                        response.status = 403
                        response.writer.write(
                            Ut.json.toString(RsData<Void>("403-1", "권한이 없습니다."))
                        )
                    }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins.toList()
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
        configuration.allowCredentials = true
        configuration.allowedHeaders = listOf("*")

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/**", configuration)
        source.registerCorsConfiguration("/ws/**", configuration)
        return source
    }
}
