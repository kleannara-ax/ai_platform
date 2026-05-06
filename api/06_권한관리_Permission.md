# 권한 관리 API (Permission)

> **URL Prefix**: `/api/core/permissions`
> **컨트롤러**: `CorePermissionController`
> **모듈**: core

---

## 1. 전체 권한 목록 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/permissions` |
| **인증** | 필요 |

### Response — `ApiResponse<List<PermissionResponse>>` (200 OK)

`PermissionResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `permId` | Long | 권한 고유 ID |
| `permCode` | String | 권한 코드 |
| `permName` | String | 권한명 |
| `description` | String | 설명 |
| `isActive` | Boolean | 활성화 여부 |

---

## 2. 권한 상세 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/permissions/{permId}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `permId` | Long | 권한 ID |

### Response — `ApiResponse<PermissionResponse>` (200 OK)

---

## 3. 권한 생성

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/core/permissions` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `PermissionRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `permCode` | String | O | `@NotBlank`, max 50 | 권한 코드 |
| `permName` | String | O | `@NotBlank`, max 100 | 권한명 |
| `description` | String | - | - | 설명 |
| `isActive` | Boolean | - | - | 활성화 여부 |

### Request 예시

```json
{
  "permCode": "USER_WRITE",
  "permName": "사용자 쓰기",
  "description": "사용자 생성/수정 권한",
  "isActive": true
}
```

### Response — `ApiResponse<PermissionResponse>` (201 Created)

---

## 4. 권한 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/core/permissions/{permId}` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Request Body — `PermissionRequest`

> 권한 생성과 동일한 형식

### Response — `ApiResponse<PermissionResponse>` (200 OK)

---

## 5. 모든 역할별 매핑 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/permissions/roles` |
| **인증** | 필요 |

### Response — `ApiResponse<List<RoleMappingResponse>>` (200 OK)

`RoleMappingResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `role` | String | 역할 코드 |
| `roleDescription` | String | 역할 설명 |
| `menuIds` | List\<Long\> | 매핑된 메뉴 ID 목록 |
| `permissionIds` | List\<Long\> | 매핑된 권한 ID 목록 |

### Response 예시

```json
{
  "success": true,
  "code": 200,
  "data": [
    {
      "role": "ROLE_ADMIN",
      "roleDescription": "관리자",
      "menuIds": [1, 2, 3, 4, 5],
      "permissionIds": [1, 2, 3]
    },
    {
      "role": "ROLE_USER",
      "roleDescription": "사용자",
      "menuIds": [1, 2],
      "permissionIds": [1]
    }
  ]
}
```

---

## 6. 특정 역할 매핑 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/permissions/roles/{role}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `role` | String | 역할 코드 (예: `ROLE_ADMIN`) |

### Response — `ApiResponse<RoleMappingResponse>` (200 OK)

---

## 7. 역할별 메뉴/권한 매핑 갱신

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/core/permissions/roles` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Request Body — `RoleMappingRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `role` | String | O | `@NotBlank` | 역할 코드 |
| `menuIds` | List\<Long\> | - | - | 매핑할 메뉴 ID 목록 |
| `permissionIds` | List\<Long\> | - | - | 매핑할 권한 ID 목록 |

### Request 예시

```json
{
  "role": "ROLE_USER",
  "menuIds": [1, 2, 5],
  "permissionIds": [1, 3]
}
```

### Response — `ApiResponse<RoleMappingResponse>` (200 OK)
