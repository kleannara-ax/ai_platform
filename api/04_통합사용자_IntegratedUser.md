# 통합 사용자 관리 API (Integrated User)

> **URL Prefix**: `/api/integrated/users`
> **컨트롤러**: `IntegratedUserController`
> **모듈**: app
> **설명**: 사용자 기본정보 + 프로필(부서, 직급, 사번 등)을 하나의 API로 처리

---

## 1. 사용자 목록 조회 (프로필 포함)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/integrated/users` |
| **인증** | 필요 |

### Query Parameters

| 파라미터 | 타입 | 기본값 | 설명 |
|----------|------|--------|------|
| `page` | Integer | 0 | 페이지 번호 (0부터) |
| `size` | Integer | 100 | 페이지 크기 |
| `sort` | String | - | 정렬 (예: `userName,asc`) |

### Response — `ApiResponse<PageResponse<IntegratedUserResponse>>` (200 OK)

`IntegratedUserResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `userId` | Long | 사용자 고유 ID |
| `loginId` | String | 로그인 ID |
| `userName` | String | 사용자 이름 |
| `email` | String | 이메일 |
| `phone` | String | 전화번호 |
| `role` | String | 역할 |
| `enabled` | Boolean | 활성화 여부 |
| `createdAt` | String | 생성일시 |
| `updatedAt` | String | 수정일시 |
| `deptCode` | String | 부서 코드 |
| `deptName` | String | 부서명 (공통코드 DEPT에서 조회) |
| `position` | String | 직위 |
| `jobTitle` | String | 직책 |
| `employeeNo` | String | 사번 |
| `joinDate` | String | 입사일 |
| `officePhone` | String | 사무실 전화 |
| `internalExt` | String | 내선번호 |

---

## 2. 사용자 단건 조회 (프로필 포함)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/integrated/users/{userId}` |
| **인증** | 필요 (`ADMIN`, `MANAGER` 역할) |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | Long | 사용자 ID |

### Response — `ApiResponse<IntegratedUserResponse>` (200 OK)

---

## 3. 사용자 생성 (프로필 포함)

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/integrated/users` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `UserCreateRequest`

> `03_사용자관리_CoreUser.md`의 `UserCreateRequest`와 동일합니다.
> `role` 필드는 무시되며, `ROLE_USER`로 고정 생성됩니다.
> 프로필 필드(deptCode, position 등)가 포함되어 있으면 프로필도 함께 생성됩니다.

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
  "jobTitle": "개발자",
  "employeeNo": "EMP-001",
  "joinDate": "2024-01-15"
}
```

### Response — `ApiResponse<IntegratedUserResponse>` (201 Created)

---

## 4. 사용자 수정 (프로필 포함)

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/integrated/users/{userId}` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Request Body — `UserUpdateRequest`

> `03_사용자관리_CoreUser.md`의 `UserUpdateRequest`와 동일합니다.
> 프로필이 없으면 자동 생성, 있으면 업데이트됩니다.

### Response — `ApiResponse<IntegratedUserResponse>` (200 OK)

---

## 5. 사용자 비활성화

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/integrated/users/{userId}/disable` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 6. 사용자 활성화

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/integrated/users/{userId}/enable` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 7. 역할 변경

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/integrated/users/{userId}/role` |
| **인증** | 필요 (`isAuthenticated()`) |

### Query Parameter

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `role` | String | O | 변경할 역할 (예: `ROLE_MANAGER`) |

### 호출 예시

```
PATCH /api/integrated/users/5/role?role=ROLE_MANAGER
Authorization: Bearer {accessToken}
```

### Response — `ApiResponse<IntegratedUserResponse>` (200 OK)
