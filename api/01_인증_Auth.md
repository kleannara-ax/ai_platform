# 인증 API (Auth)

> **URL Prefix**: `/api/auth`
> **컨트롤러**: `AuthController`
> **모듈**: core

---

## 1. 로그인

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/auth/login` |
| **인증** | 불필요 (permitAll) |
| **Content-Type** | `application/json` |

### Request Body — `LoginRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `loginId` | String | O | `@NotBlank` | 로그인 ID (앞뒤 공백 자동 제거) |
| `password` | String | O | `@NotBlank` | 비밀번호 (앞뒤 공백 자동 제거) |

### Request 예시

```json
{
  "loginId": "admin",
  "password": "password123"
}
```

### Response — `ApiResponse<TokenResponse>` (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| `accessToken` | String | JWT 액세스 토큰 |
| `refreshToken` | String | JWT 리프레시 토큰 |
| `tokenType` | String | 고정값 `"Bearer"` |
| `expiresIn` | Long | 토큰 만료시간 (초) |

### Response 예시

```json
{
  "success": true,
  "code": 200,
  "message": "로그인 성공",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "timestamp": "2026-05-06T10:00:00"
}
```

### 에러 케이스

| 상황 | HTTP 상태 | 에러코드 |
|------|-----------|----------|
| 아이디/비밀번호 불일치 | 401 | INVALID_CREDENTIALS |
| 비활성화된 계정 | 403 | USER_DISABLED |
| 입력값 누락 | 400 | INVALID_INPUT |

---

## 2. 토큰 갱신

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/auth/refresh` |
| **인증** | 불필요 (permitAll) |
| **Content-Type** | 없음 (헤더로 전송) |

### Request Header

| 헤더 | 필수 | 설명 |
|------|------|------|
| `X-Refresh-Token` | O | 리프레시 토큰 |

### Response — `ApiResponse<TokenResponse>` (200 OK)

`TokenResponse`는 로그인과 동일한 형식입니다.

### Response 예시

```json
{
  "success": true,
  "code": 200,
  "message": "토큰 갱신 성공",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...(새 토큰)",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...(새 토큰)",
    "tokenType": "Bearer",
    "expiresIn": 3600
  },
  "timestamp": "2026-05-06T10:30:00"
}
```

### 에러 케이스

| 상황 | HTTP 상태 | 에러코드 |
|------|-----------|----------|
| 만료된 리프레시 토큰 | 401 | TOKEN_EXPIRED |
| 유효하지 않은 토큰 | 401 | INVALID_TOKEN |

---

## 3. 현재 로그인 사용자 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/auth/me` |
| **인증** | 필요 (JWT) |

### Request Header

```
Authorization: Bearer {accessToken}
```

### Response — `ApiResponse<MeResponse>` (200 OK)

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | Long | 사용자 고유 ID |
| `loginId` | String | 로그인 ID |
| `userName` | String | 사용자 이름 |
| `role` | String | 역할 (예: `ROLE_ADMIN`, `ROLE_USER`) |

### Response 예시

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "data": {
    "userId": 1,
    "loginId": "admin",
    "userName": "관리자",
    "role": "ROLE_ADMIN"
  },
  "timestamp": "2026-05-06T10:30:00"
}
```

---

## 4. CSRF 토큰

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/auth/csrf` |
| **인증** | 불필요 |
| **설명** | JWT 기반이므로 CSRF 비활성화 상태. 소방 모듈 프론트엔드(csrf.js)의 404 방지용 빈 응답. |

### Response — `ApiResponse<Void>` (200 OK)

```json
{
  "success": true,
  "code": 200,
  "message": "Success",
  "timestamp": "2026-05-06T10:30:00"
}
```
