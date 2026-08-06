package com.back.global.rsData

import com.fasterxml.jackson.annotation.JsonIgnore

data class RsData<T>(
    val resultCode: String,
    @get:JsonIgnore val statusCode: Int,
    val msg: String,
    val data: T?
) {
    constructor(resultCode: String, msg: String) : this(resultCode, msg, null)

    constructor(resultCode: String, msg: String, data: T?) : this(
        resultCode, resultCode.split("-", limit = 2)[0].toInt(), msg, data
    )
}
