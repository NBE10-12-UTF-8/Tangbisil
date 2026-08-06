package com.back.global.exception

import com.back.global.rsData.RsData

class ServiceException(
    val resultCode: String,
    val msg: String
) : RuntimeException("$resultCode : $msg") {
    val rsData: RsData<Void>
        get() = RsData(resultCode, msg)
}
