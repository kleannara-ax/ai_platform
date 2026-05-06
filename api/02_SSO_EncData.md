# SSO 암호화 데이터 로그인 API (EncData)

> **URL Prefix**: `/api/login`
> **컨트롤러**: `EncDataController`
> **모듈**: core

---

## SSO encData 수신 및 로그인 처리

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/login/sendEncData` |
| **인증** | 불필요 (permitAll) |
| **Content-Type** | `application/x-www-form-urlencoded` |
| **Response Content-Type** | `text/html; charset=UTF-8` |

### 처리 흐름

```
1. 타 시스템에서 encData를 form 파라미터로 전송 (브라우저 form submit)
2. SSO 서버(encValidateProduct)에 productId + encData 전달하여 검증
3. 검증 성공 시 sproId로 사용자 조회 → JWT 토큰 발급
4. 응답으로 HTML 페이지를 직접 반환 (text/html)
   → JavaScript가 토큰을 sessionStorage에 저장 후 메인페이지(/)로 이동
```

### Request — Form Parameters (`EncDataRequest`)

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `encData` | String | O | `@NotBlank` | SSO 암호화 데이터 |

### Request 예시 (Form)

```
POST /api/login/sendEncData
Content-Type: application/x-www-form-urlencoded

encData=AbCdEf123456...
```

### 성공 응답 — HTML (200 OK)

성공 시 다음 동작을 수행하는 HTML 페이지를 반환합니다:

1. JWT 토큰(accessToken, refreshToken)을 `sessionStorage`에 저장
2. `/api/auth/me`로 사용자 정보 조회
3. `fireweb_user` 정보를 `localStorage`에 저장
4. `/index.html`로 자동 리다이렉트

### 실패 응답 — HTML (200 OK)

실패 시 에러 메시지를 표시하는 HTML 페이지를 반환합니다:

- 에러 메시지 표시
- 3초 후 `/index.html`(로그인 페이지)로 자동 리다이렉트
- "로그인 페이지로 바로 이동" 버튼 제공

### 내부 처리 DTO — `EncDataResponse`

| 필드 | 타입 | 설명 |
|------|------|------|
| `accessToken` | String | JWT 액세스 토큰 |
| `refreshToken` | String | JWT 리프레시 토큰 |
| `tokenType` | String | 고정값 `"Bearer"` |
| `expiresIn` | Long | 토큰 만료시간 (초) |
| `sproId` | String | SSO 사용자 ID |
| `resultMessage` | String | 처리 결과 메시지 |

### SSO 검증 흐름 (내부)

```
백엔드 → SSO 서버:
  POST https://sso.kleannara.com/rest/security/encValidateProduct
  Body: { "productId": "PRO_000643", "encData": "..." }

SSO 서버 응답 (SsoValidateResponse):
  {
    "head": {
      "returnCode": "0",        // "0" = 성공
      "returnMessage": "...",
      "returnDesc": "..."
    },
    "body": {
      "sproId": "사용자ID"
    }
  }
```

### 에러 케이스

| 상황 | 처리 |
|------|------|
| SSO returnCode != "0" | 에러 HTML 반환 (returnDesc 메시지 표시) |
| sproId 누락 | 에러 HTML 반환 |
| 해당 sproId 사용자 미존재 | 에러 HTML 반환 ("아이디가 존재하지 않습니다") |
| 비활성화된 계정 | 에러 HTML 반환 (USER_DISABLED) |
| SSO 서버 연결 실패 | 에러 HTML 반환 (SSO_CONNECTION_FAILED) |
