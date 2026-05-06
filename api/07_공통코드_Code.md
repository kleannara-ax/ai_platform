# 공통코드 관리 API (Code)

> **URL Prefix**: `/api/codes`
> **컨트롤러**: `CodeController`
> **모듈**: module-common

---

## 코드 그룹 API

### 1. 전체 코드 그룹 목록 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/codes/groups` |
| **인증** | 필요 |

### Response — `ApiResponse<List<CodeGroupResponse>>` (200 OK)

`CodeGroupResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `groupId` | Long | 그룹 고유 ID |
| `groupCode` | String | 그룹 코드 |
| `groupName` | String | 그룹명 |
| `description` | String | 설명 |
| `isActive` | Boolean | 활성화 여부 |
| `sortOrder` | Integer | 정렬 순서 |
| `details` | List\<CodeDetailResponse\> | 하위 코드 목록 |

---

### 2. 코드 그룹 상세 조회 (하위 코드 포함)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/codes/groups/{groupId}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `groupId` | Long | 그룹 ID |

### Response — `ApiResponse<CodeGroupResponse>` (200 OK)

---

### 3. 그룹 코드(문자열)로 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/codes/groups/code/{groupCode}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `groupCode` | String | 그룹 코드 문자열 (예: `DEPT`) |

### Response — `ApiResponse<CodeGroupResponse>` (200 OK)

---

### 4. 코드 그룹 생성

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/codes/groups` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `CodeGroupRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `groupCode` | String | O | `@NotBlank`, max 50 | 그룹 코드 |
| `groupName` | String | O | `@NotBlank`, max 100 | 그룹명 |
| `description` | String | - | max 200 | 설명 |
| `isActive` | Boolean | - | - | 활성화 여부 |
| `sortOrder` | Integer | - | - | 정렬 순서 |

### Request 예시

```json
{
  "groupCode": "POSITION",
  "groupName": "직위",
  "description": "직위 코드 그룹",
  "isActive": true,
  "sortOrder": 10
}
```

### Response — `ApiResponse<CodeGroupResponse>` (201 Created)

---

### 5. 코드 그룹 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/codes/groups/{groupId}` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Request Body — `CodeGroupRequest` (생성과 동일)

### Response — `ApiResponse<CodeGroupResponse>` (200 OK)

---

### 6. 코드 그룹 삭제

| 항목 | 값 |
|------|-----|
| **URL** | `DELETE /api/codes/groups/{groupId}` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 코드 상세 API

### 7. 그룹 하위 코드 목록 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/codes/groups/{groupId}/details` |
| **인증** | 필요 |

### Response — `ApiResponse<List<CodeDetailResponse>>` (200 OK)

`CodeDetailResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `codeId` | Long | 코드 고유 ID |
| `groupId` | Long | 소속 그룹 ID |
| `groupCode` | String | 소속 그룹 코드 |
| `code` | String | 코드 값 |
| `codeName` | String | 코드명 |
| `description` | String | 설명 |
| `extraValue1` | String | 추가값 1 |
| `extraValue2` | String | 추가값 2 |
| `isActive` | Boolean | 활성화 여부 |
| `sortOrder` | Integer | 정렬 순서 |
| `createdAt` | String | 생성일시 |
| `updatedAt` | String | 수정일시 |

---

### 8. 그룹 코드(문자열)로 활성 코드 목록 조회 (드롭다운용)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/codes/lookup/{groupCode}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `groupCode` | String | 그룹 코드 문자열 (예: `DEPT`) |

### 호출 예시

```
GET /api/codes/lookup/DEPT
Authorization: Bearer {accessToken}
```

### Response — `ApiResponse<List<CodeDetailResponse>>` (200 OK)

---

### 9. 코드 상세 단건 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/codes/details/{codeId}` |
| **인증** | 필요 |

### Response — `ApiResponse<CodeDetailResponse>` (200 OK)

---

### 10. 코드 추가

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/codes/groups/{groupId}/details` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `CodeDetailRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `code` | String | O | `@NotBlank`, max 50 | 코드 값 |
| `codeName` | String | O | `@NotBlank`, max 100 | 코드명 |
| `description` | String | - | max 200 | 설명 |
| `extraValue1` | String | - | max 200 | 추가값 1 |
| `extraValue2` | String | - | max 200 | 추가값 2 |
| `isActive` | Boolean | - | - | 활성화 여부 |
| `sortOrder` | Integer | - | - | 정렬 순서 |

### Request 예시

```json
{
  "code": "DEV",
  "codeName": "개발팀",
  "description": "소프트웨어 개발 부서",
  "isActive": true,
  "sortOrder": 1
}
```

### Response — `ApiResponse<CodeDetailResponse>` (201 Created)

---

### 11. 코드 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/codes/details/{codeId}` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Request Body — `CodeDetailRequest` (추가와 동일)

### Response — `ApiResponse<CodeDetailResponse>` (200 OK)

---

### 12. 코드 삭제

| 항목 | 값 |
|------|-----|
| **URL** | `DELETE /api/codes/details/{codeId}` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)
