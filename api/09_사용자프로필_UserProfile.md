# 사용자 프로필 API (User Profile)

> **URL Prefix**: `/api/module-common/profiles`
> **컨트롤러**: `UserProfileController`
> **모듈**: module-common

---

## 1. 프로필 생성

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/module-common/profiles` |
| **인증** | 필요 (`ADMIN`, `MANAGER` 역할) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `UserProfileRequest`

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userId` | Long | O | 사용자 ID |
| `deptCode` | String | - | 부서 코드 |
| `position` | String | - | 직위 |
| `jobTitle` | String | - | 직책 |
| `employeeNo` | String | - | 사번 |
| `joinDate` | String | - | 입사일 |
| `officePhone` | String | - | 사무실 전화 |
| `internalExt` | String | - | 내선번호 |

### Request 예시

```json
{
  "userId": 5,
  "deptCode": "DEV",
  "position": "대리",
  "jobTitle": "백엔드 개발자",
  "employeeNo": "EMP-0042",
  "joinDate": "2024-03-01",
  "officePhone": "02-1234-5678",
  "internalExt": "1234"
}
```

### Response — `ApiResponse<UserProfileResponse>` (201 Created)

`UserProfileResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `profileId` | Long | 프로필 고유 ID |
| `userId` | Long | 사용자 ID |
| `deptCode` | String | 부서 코드 |
| `position` | String | 직위 |
| `jobTitle` | String | 직책 |
| `employeeNo` | String | 사번 |
| `joinDate` | String | 입사일 |
| `officePhone` | String | 사무실 전화 |
| `internalExt` | String | 내선번호 |
| `createdAt` | String | 생성일시 |
| `updatedAt` | String | 수정일시 |

---

## 2. 사용자 ID로 프로필 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/module-common/profiles/user/{userId}` |
| **인증** | 필요 (`ADMIN`, `MANAGER`, `USER` 역할) |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | Long | 사용자 ID |

### Response — `ApiResponse<UserProfileResponse>` (200 OK)

---

## 3. 부서별 프로필 목록 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/module-common/profiles/department/{deptCode}` |
| **인증** | 필요 (`ADMIN`, `MANAGER` 역할) |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `deptCode` | String | 부서 코드 (예: `DEV`) |

### Response — `ApiResponse<List<UserProfileResponse>>` (200 OK)

---

## 4. 프로필 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/module-common/profiles/user/{userId}` |
| **인증** | 필요 (`ADMIN`, `MANAGER` 역할) |
| **Content-Type** | `application/json` |

### Request Body — `UserProfileRequest`

> 프로필 생성과 동일한 형식 (`userId` 필드는 path에서 결정)

### Response — `ApiResponse<UserProfileResponse>` (200 OK)
