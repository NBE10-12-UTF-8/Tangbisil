package com.back.global.email

object PasswordResetEmailTemplate {
    @JvmStatic
    fun render(code: String, expirationMinutes: Int): String =
        """
            <div style="max-width:480px;margin:0 auto;padding:32px 24px;font-family:'Apple SD Gothic Neo','Malgun Gothic',Arial,sans-serif;background-color:#f7f7f8;">
              <div style="background-color:#ffffff;border-radius:12px;padding:40px 32px;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                <p style="margin:0 0 8px;color:#8b8b8b;font-size:13px;letter-spacing:0.5px;">탕비실</p>
                <h1 style="margin:0 0 24px;font-size:20px;color:#1a1a1a;">비밀번호 재설정 코드</h1>
                <p style="margin:0 0 24px;color:#555555;font-size:14px;line-height:1.6;">
                  아래 코드를 입력해 비밀번호를 재설정해주세요.
                </p>
                <div style="background-color:#f2f4ff;border-radius:8px;padding:20px;text-align:center;margin-bottom:24px;">
                  <span style="font-size:32px;font-weight:700;letter-spacing:8px;color:#4f46e5;">$code</span>
                </div>
                <p style="margin:0 0 4px;color:#999999;font-size:13px;">
                  코드는 <b>${expirationMinutes}분</b> 동안 유효합니다.
                </p>
                <p style="margin:0;color:#999999;font-size:13px;">
                  본인이 요청하지 않았다면 이 메일을 무시해주세요.
                </p>
              </div>
              <p style="margin-top:20px;text-align:center;color:#bbbbbb;font-size:12px;">
                © 탕비실
              </p>
            </div>
        """.trimIndent()
}
