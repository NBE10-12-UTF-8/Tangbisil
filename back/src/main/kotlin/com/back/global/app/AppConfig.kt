package com.back.global.app

import com.back.standard.util.Ut
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import tools.jackson.databind.ObjectMapper

@Configuration
class AppConfig(environment: Environment) {
    companion object {
        private lateinit var environment: Environment

        @JvmStatic
        fun isDev(): Boolean = environment.matchesProfiles("dev")

        @JvmStatic
        fun isTest(): Boolean = !environment.matchesProfiles("test")

        @JvmStatic
        fun isProd(): Boolean = environment.matchesProfiles("prod")

        @JvmStatic
        fun isNotProd(): Boolean = !isProd()

        lateinit var objectMapper: ObjectMapper
            private set
    }

    init {
        AppConfig.environment = environment
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Autowired
    fun setObjectMapper(objectMapper: ObjectMapper) {
        AppConfig.objectMapper = objectMapper
    }

    @PostConstruct
    fun postConstruct() {
        Ut.json.objectMapper = objectMapper
    }
}
