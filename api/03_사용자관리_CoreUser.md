# 사용자 관리 API (Core User)

> **URL Prefix**: `/api/core/users`
> **컨트롤러**: `CoreUserController`
> **모듈**: core

---

## 1. 사용자 생성

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/core/users` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `UserCreateRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `loginId` | String | O | `@NotBlank`, max 50 | 로그인 ID (공백 자동 trim) |
| `password` | String | O | `@NotBlank`, min 4 | 비밀번호 (공백 자동 trim) |
| `userName` | String | O | `@NotBlank`, max 100 | 사용자 이름 (공백 자동 trim) |
| `email` | String | - | `@Email`, max 200 | 이메일 (공백 자동 trim) |
| `phone` | String | - | max 20 | 전화번호 (공백 자동 trim) |
| `role` | String | - | - | **무시됨** — 서버에서 `ROLE_USER`로 고정 |
| `deptCode` | String | - | - | 부서 코드 |
| `position` | String | - | - | 직위 |
| `jobTitle` | String | - | - | 직책 |
| `employeeNo` | String | - | - | 사번 |
| `joinDate` | String | - | - | 입사일 |
| `officePhone` | String | - | - | 사무실 전화 |
| `internalExt` | String | - | - | 내선번호 |

> **참고**: `role` 필드는 전송해도 서버에서 무시되며, 모든 신규 사용자는 `ROLE_USER`(사용자)로 생성됩니다.

### Request 예시

```json
{
  "loginId": "hong",
  "password": "pass1234",
  "userName": "홍길동",
  "email": "hong@company.com",
  "phone": "010-1234-5678",
  "deptCode": "DEV",
  "position": "대리",
  "jobTitle": "개발자"
}
```

### Response — `ApiResponse<UserResponse>` (201 Created)

`UserResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | Long | 사용자 고유 ID |
| `loginId` | String | 로그인 ID |
| `userName` | String | 사용자 이름 |
| `email` | String | 이메일 |
| `phone` | String | 전화번호 |
| `role` | String | 역할 (항상 `ROLE_USER`) |
| `enabled` | Boolean | 활성화 여부 (기본 `true`) |
| `createdAt` | String | 생성일시 |
| `updatedAt` | String | 수정일시 |
| `deptCode` | String | 부서 코드 |
| `deptName` | String | 부서명 |
| `position` | String | 직위 |
| `jobTitle` | String | 직책 |
| `employeeNo` | String | 사번 |
| `joinDate` | String | 입사일 |
| `officePhone` | String | 사무실 전화 |
| `internalExt` | String | 내선번호 |

### 에러 케이스

| 상황 | HTTP 상태 | 에러코드 |
|------|-----------|----------|
| 로그인 ID 중복 | 409 | USER_LOGIN_ID_DUPLICATED |
| 입력값 누락 | 400 | INVALID_INPUT |

---

## 2. 사용자 단건 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/users/{userId}` |
| **인증** | 필요 (`ADMIN`, `MANAGER` 역할) |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | Long | 사용자 ID |

### Response — `ApiResponse<UserResponse>` (200 OK)

`UserResponse`는 위의 사용자 생성 응답과 동일합니다.

---

## 3. 사용자 목록 조회 (페이징)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/users` |
| **인증** | 필요 (`ADMIN`, `MANAGER` 역할) |

### Query Parameters

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `page` | Integer | 0 | 페이지 번호 (0부터) |
| `size` | Integer | 20 | 페이지 크기 |
| `sort` | String | - | 정렬 (예: `userName,asc`) |

### Response — `ApiResponse<PageResponse<UserResponse>>` (200 OK)

```json
{
  "success": true,
  "code": 200,
  "data": {
    "content": [ { "userId": 1, "loginId": "admin", ... }, ... ],
    "page": 0,
    "size": 20,
    "totalElements": 15,
    "totalPages": 1,
    "first": true,
    "last": true
  }
}
```

---

## 4. 사용자 정보 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/core/users/{userId}` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | Long | 사용자 ID |

### Request Body — `UserUpdateRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `userName` | String | - | max 100 | 사용자 이름 |
| `email` | String | - | `@Email`, max 200 | 이메일 |
| `phone` | String | - | - | 전화번호 |
| `deptCode` | String | - | - | 부서 코드 |
| `position` | String | - | - | 직위 |
| `jobTitle` | String | - | - | 직책 |
| `employeeNo` | String | - | - | 사번 |
| `joinDate` | String | - | - | 입사일 |
| `officePhone` | String | - | - | 사무실 전화 |
| `internalExt` | String | - | - | 내선번호 |

### Response — `ApiResponse<UserResponse>` (200 OK)

---

## 5. 사용자 비활성화

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/core/users/{userId}/disable` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 6. 사용자 활성화

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/core/users/{userId}/enable` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 7. 사용자 역할 변경

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/core/users/{userId}/role` |
| **인증** | 필요 (`isAuthenticated()`) |

### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `role` | String | O | 변경할 역할 (예: `ROLE_ADMIN`) |

### 호출 예시

```
PATCH /api/core/users/5/role?role=ROLE_MANAGER
Authorization: Bearer {accessToken}
```

### Response — `ApiResponse<UserResponse>` (200 OK)
