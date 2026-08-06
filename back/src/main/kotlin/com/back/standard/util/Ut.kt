package com.back.standard.util

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import tools.jackson.databind.ObjectMapper
import java.util.Date

object Ut {
    object jwt {
        @JvmStatic
        fun toString(secret: String, expireSeconds: Int, body: Map<String, Any?>): String {
            val claimsBuilder = Jwts.claims()

            for ((key, value) in body) {
                claimsBuilder.add(key, value)
            }

            val claims = claimsBuilder.build()

            val issuedAt = Date()
            val expiration = Date(issuedAt.time + 1000L * expireSeconds)

            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

            return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact()
        }

        @JvmStatic
        fun isValid(secret: String, jwtStr: String): Boolean {
            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

            return try {
                Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(jwtStr)
                true
            } catch (e: Exception) {
                false
            }
        }

        @JvmStatic
        fun payload(secret: String, jwtStr: String): Map<String, Any?>? {
            val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())

            return try {
                LinkedHashMap(
                    Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(jwtStr)
                        .payload
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    object json {
        @JvmField
        var objectMapper: ObjectMapper? = null

        @JvmStatic
        @JvmOverloads
        fun toString(obj: Any?, defaultValue: String? = null): String? {
            return try {
                objectMapper!!.writeValueAsString(obj)
            } catch (e: Exception) {
                defaultValue
            }
        }
    }
}
