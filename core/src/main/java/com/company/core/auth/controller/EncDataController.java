package com.company.core.auth.controller;

import com.company.core.auth.dto.EncDataRequest;
import com.company.core.auth.dto.EncDataResponse;
import com.company.core.auth.service.EncDataService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * SSO 암호화 데이터 수신 및 로그인 API
 * URL: POST /api/login/sendEncData
 * Content-Type: application/x-www-form-urlencoded
 *
 * 처리 흐름:
 * 1. 타 시스템에서 encData를 form 파라미터로 전송 (브라우저 form submit)
 * 2. SSO 서버(encValidateProduct)에 productId + encData 전달하여 검증
 * 3. 검증 성공 시 sproId로 사용자 조회 → JWT 토큰 발급
 * 4. 응답으로 HTML 페이지를 직접 반환 (text/html)
 *    → JavaScript가 토큰을 sessionStorage에 저장 후 메인페이지(/)로 이동
 *
 * ※ 302 리다이렉트가 아닌 HTML 직접 반환 방식:
 *    타 시스템의 호출 방식(form submit, AJAX, iframe 등)에 관계없이
 *    브라우저가 응답 HTML을 렌더링하면 자동으로 로그인 처리 + 메인페이지 이동
 */
@Slf4j
@RestController
@RequestMapping("/api/login")
@RequiredArgsConstructor
public class EncDataController {

    private final EncDataService encDataService;

    /**
     * encData 수신 → SSO 검증 → 로그인 처리 → 메인페이지 자동 이동
     *
     * 응답: text/html (브라우저가 직접 렌더링)
     *   - 성공: 토큰을 sessionStorage에 저장 → /api/auth/me로 사용자 조회 → /index.html 이동
     *   - 실패: 에러 메시지 표시 + 로그인 페이지 이동 링크
     */
    @PostMapping(value = "/sendEncData", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void sendEncData(@Valid @ModelAttribute EncDataRequest request,
                            HttpServletResponse response) throws IOException {

        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();

        try {
            EncDataResponse result = encDataService.processEncData(request);

            log.info("SSO 로그인 성공, 인라인 콜백 HTML 반환: sproId={}", result.getSproId());

            // SSO 로그인 성공 → 토큰 정보를 포함한 HTML을 직접 반환
            out.write(buildSuccessHtml(
                    result.getAccessToken(),
                    result.getRefreshToken(),
                    result.getSproId()
            ));

        } catch (Exception e) {
            log.warn("SSO 로그인 실패, 에러 HTML 반환: {}", e.getMessage());
            out.write(buildErrorHtml(e.getMessage()));
        }

        out.flush();
    }

    /**
     * SSO 성공 시 반환할 HTML
     * - 토큰을 sessionStorage에 저장
     * - /api/auth/me로 사용자 정보 조회
     * - /index.html로 자동 이동
     */
    private String buildSuccessHtml(String accessToken, String refreshToken, String sproId) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SSO 로그인 처리 중...</title>
                <style>
                *{margin:0;padding:0;box-sizing:border-box;}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans KR',sans-serif;background:linear-gradient(135deg,#1e3a5f 0%%,#3b82f6 100%%);display:flex;align-items:center;justify-content:center;min-height:100vh;}
                .card{background:#fff;border-radius:16px;padding:48px 40px;width:420px;box-shadow:0 20px 60px rgba(0,0,0,0.2);text-align:center;color:#0f172a;}
                .card h2{font-size:20px;font-weight:700;margin-bottom:8px;}
                .card p{font-size:14px;color:#475569;margin-bottom:24px;}
                .spinner{width:40px;height:40px;border:4px solid #e2e8f0;border-top:4px solid #3b82f6;border-radius:50%%;animation:spin .8s linear infinite;margin:0 auto 20px;}
                @keyframes spin{0%%{transform:rotate(0)}100%%{transform:rotate(360deg)}}
                .hidden{display:none;}
                .error-msg{color:#dc2626;font-size:14px;font-weight:500;margin-bottom:20px;}
                .btn{display:inline-block;padding:10px 24px;border-radius:8px;font-weight:600;font-size:14px;text-decoration:none;background:#3b82f6;color:#fff;}
                .btn:hover{background:#2563eb;}
                </style>
                </head>
                <body>
                <div class="card" id="loadingCard">
                  <div class="spinner"></div>
                  <h2>SSO 로그인 처리 중</h2>
                  <p id="statusMsg">인증 정보를 확인하고 있습니다...</p>
                </div>
                <div class="card hidden" id="errorCard">
                  <h2>SSO 로그인 실패</h2>
                  <p class="error-msg" id="errorMsg"></p>
                  <a class="btn" href="/index.html">로그인 페이지로 이동</a>
                </div>
                <script>
                (async function(){
                  var TOKEN = '%s';
                  var REFRESH = '%s';
                  var SPRO_ID = '%s';
                  try {
                    updateStatus('사용자 정보를 조회하는 중...');
                    var currentUser = null;
                    try {
                      var meRes = await fetch(window.location.origin + '/api/auth/me', {
                        method: 'GET',
                        headers: { 'Authorization': 'Bearer ' + TOKEN }
                      });
                      var meData = await meRes.json();
                      if (meData.success && meData.data) {
                        currentUser = meData.data;
                      }
                    } catch(e) {}
                    if (!currentUser) {
                      currentUser = { loginId: SPRO_ID, userName: SPRO_ID, role: 'ROLE_USER', roles: ['ROLE_USER'] };
                    }
                    sessionStorage.setItem('auth', JSON.stringify({
                      token: TOKEN,
                      refreshTk: REFRESH,
                      currentUser: currentUser,
                      currentPage: 'dashboard'
                    }));
                    // 다중 역할 지원: currentUser.roles(배열) 우선, 없으면 currentUser.role(단일값)로 대체 (하위호환)
                    var userRoles = (Array.isArray(currentUser.roles) && currentUser.roles.length > 0) ? currentUser.roles : (currentUser.role ? [currentUser.role] : ['ROLE_USER']);
                    var fireRole = (currentUser.role||'').replace('ROLE_','');
                    var fireCanManage = ['ROLE_ADMIN','ROLE_FACILITY_MANAGER','ROLE_FIRE_MANAGER','ROLE_EQUIPMENT_MANAGER'].some(function(r){ return userRoles.indexOf(r) >= 0; });
                    localStorage.setItem('fireweb_user', JSON.stringify({
                      loginId: currentUser.loginId,
                      userName: currentUser.userName,
                      role: fireRole,
                      roles: userRoles,
                      token: TOKEN,
                      canManage: fireCanManage
                    }));
                    updateStatus('로그인 완료! 메인 페이지로 이동합니다...');
                    window.location.replace('/index.html');
                  } catch(e) {
                    showError('로그인 처리 중 오류: ' + (e.message||e));
                  }
                })();
                function updateStatus(m){var e=document.getElementById('statusMsg');if(e)e.textContent=m;}
                function showError(m){document.getElementById('loadingCard').classList.add('hidden');document.getElementById('errorCard').classList.remove('hidden');document.getElementById('errorMsg').textContent=m;}
                </script>
                </body>
                </html>
                """.formatted(
                escapeJs(accessToken),
                escapeJs(refreshToken),
                escapeJs(sproId)
        );
    }

    /**
     * SSO 실패 시 반환할 HTML
     */
    private String buildErrorHtml(String errorMessage) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>SSO 로그인 실패</title>
                <style>
                *{margin:0;padding:0;box-sizing:border-box;}
                body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans KR',sans-serif;background:linear-gradient(135deg,#1e3a5f 0%%,#3b82f6 100%%);display:flex;align-items:center;justify-content:center;min-height:100vh;}
                .card{background:#fff;border-radius:16px;padding:48px 40px;width:420px;box-shadow:0 20px 60px rgba(0,0,0,0.2);text-align:center;color:#0f172a;}
                .card h2{font-size:20px;font-weight:700;margin-bottom:12px;color:#dc2626;}
                .error-msg{color:#475569;font-size:15px;font-weight:500;margin-bottom:20px;word-break:break-word;line-height:1.6;}
                .countdown{color:#94a3b8;font-size:13px;margin-bottom:24px;}
                .btn{display:inline-block;padding:10px 24px;border-radius:8px;font-weight:600;font-size:14px;text-decoration:none;background:#3b82f6;color:#fff;}
                .btn:hover{background:#2563eb;}
                </style>
                </head>
                <body>
                <div class="card">
                  <h2>SSO 로그인 실패</h2>
                  <p class="error-msg">%s</p>
                  <p class="countdown" id="countdown">3초 후 로그인 페이지로 이동합니다...</p>
                  <a class="btn" href="/index.html">로그인 페이지로 바로 이동</a>
                </div>
                <script>
                (function(){
                  var sec = 3;
                  var el = document.getElementById('countdown');
                  var timer = setInterval(function(){
                    sec--;
                    if(sec <= 0){
                      clearInterval(timer);
                      window.location.replace('/index.html');
                    } else {
                      el.textContent = sec + '초 후 로그인 페이지로 이동합니다...';
                    }
                  }, 1000);
                })();
                </script>
                </body>
                </html>
                """.formatted(escapeHtml(errorMessage));
    }

    /** JavaScript 문자열 이스케이프 (XSS 방지) */
    private String escapeJs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e");
    }

    /** HTML 이스케이프 (XSS 방지) */
    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
