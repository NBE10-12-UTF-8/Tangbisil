package com.back.global.util

object EmailNormalizer {
    @JvmStatic
    fun normalize(email: String): String = email.trim().lowercase()
}
