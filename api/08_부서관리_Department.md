# 부서 관리 API (Department)

> **URL Prefix**: `/api/module-common/departments`
> **컨트롤러**: `DepartmentController`
> **모듈**: module-common

---

## 1. 부서 생성

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/module-common/departments` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `DepartmentCreateRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `deptName` | String | O | `@NotBlank`, max 100 | 부서명 |
| `deptCode` | String | O | `@NotBlank`, max 20 | 부서 코드 |
| `parentDeptId` | Long | - | - | 상위 부서 ID |
| `sortOrder` | Integer | - | - | 정렬 순서 |

### Request 예시

```json
{
  "deptName": "개발팀",
  "deptCode": "DEV",
  "parentDeptId": 1,
  "sortOrder": 10
}
```

### Response — `ApiResponse<DepartmentResponse>` (201 Created)

`DepartmentResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `deptId` | Long | 부서 고유 ID |
| `deptName` | String | 부서명 |
| `deptCode` | String | 부서 코드 |
| `parentDeptId` | Long | 상위 부서 ID |
| `sortOrder` | Integer | 정렬 순서 |
| `isActive` | Boolean | 활성화 여부 |
| `createdAt` | String | 생성일시 |
| `updatedAt` | String | 수정일시 |

---

## 2. 부서 단건 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/module-common/departments/{deptId}` |
| **인증** | 필요 (`ADMIN`, `MANAGER`, `USER` 역할) |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `deptId` | Long | 부서 ID |

### Response — `ApiResponse<DepartmentResponse>` (200 OK)

---

## 3. 활성화된 전체 부서 목록 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/module-common/departments` |
| **인증** | 필요 (`ADMIN`, `MANAGER`, `USER` 역할) |

### Response — `ApiResponse<List<DepartmentResponse>>` (200 OK)

---

## 4. 하위 부서 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/module-common/departments/{parentDeptId}/children` |
| **인증** | 필요 (`ADMIN`, `MANAGER`, `USER` 역할) |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `parentDeptId` | Long | 상위 부서 ID |

### Response — `ApiResponse<List<DepartmentResponse>>` (200 OK)

---

## 5. 부서 비활성화

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /api/module-common/departments/{deptId}/disable` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)
