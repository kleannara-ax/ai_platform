# 메뉴 관리 API (Core Menu)

> **URL Prefix**: `/api/core/menus`
> **컨트롤러**: `CoreMenuController`
> **모듈**: core

---

## 1. 전체 메뉴 트리 조회 (활성 메뉴만)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus` |
| **인증** | 필요 |

### Response — `ApiResponse<List<MenuResponse>>` (200 OK)

`MenuResponse` 필드:

| 필드 | 타입 | 설명 |
|------|------|------|
| `menuId` | Long | 메뉴 고유 ID |
| `menuName` | String | 메뉴명 |
| `menuCode` | String | 메뉴 코드 |
| `parentId` | Long | 상위 메뉴 ID (루트이면 null) |
| `menuUrl` | String | 메뉴 URL |
| `icon` | String | 아이콘 |
| `sortOrder` | Integer | 정렬 순서 |
| `menuType` | String | 메뉴 타입 |
| `isVisible` | Boolean | 표시 여부 |
| `isActive` | Boolean | 활성화 여부 |
| `description` | String | 설명 |
| `allowedIps` | String | 허용 IP 대역 |
| `children` | List\<MenuResponse\> | 하위 메뉴 목록 |

---

## 2. 전체 메뉴 트리 조회 (비활성 포함, 관리용)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus/tree` |
| **인증** | 필요 |

### Response — `ApiResponse<List<MenuResponse>>` (200 OK)

---

## 3. 플랫 리스트 조회 (활성 메뉴만)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus/list` |
| **인증** | 필요 |

### Response — `ApiResponse<List<MenuResponse>>` (200 OK)

---

## 4. 플랫 리스트 조회 (비활성 포함, 관리용)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus/list/all` |
| **인증** | 필요 |

### Response — `ApiResponse<List<MenuResponse>>` (200 OK)

---

## 5. 역할별 메뉴 트리 (IP 기반 필터링 포함)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus/role/{role}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `role` | String | 역할 코드 (예: `ROLE_ADMIN`) |

### 설명

- 역할에 매핑된 메뉴만 트리 형태로 반환
- 접속자의 IP 주소를 기반으로 `allowedIps` 필터링 적용

### Response — `ApiResponse<List<MenuResponse>>` (200 OK)

---

## 6. 메뉴 상세 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus/{menuId}` |
| **인증** | 필요 |

### Path Parameter

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `menuId` | Long | 메뉴 ID |

### Response — `ApiResponse<MenuResponse>` (200 OK)

---

## 7. 메뉴 생성

| 항목 | 값 |
|------|-----|
| **URL** | `POST /api/core/menus` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |
| **HTTP 상태** | `201 Created` |

### Request Body — `MenuRequest`

| 필드 | 타입 | 필수 | 검증 | 설명 |
|------|------|------|------|------|
| `menuName` | String | O | `@NotBlank`, max 100 | 메뉴명 |
| `menuCode` | String | O | `@NotBlank`, max 50 | 메뉴 코드 |
| `parentId` | Long | - | - | 상위 메뉴 ID |
| `menuUrl` | String | - | - | 메뉴 URL |
| `icon` | String | - | - | 아이콘 |
| `sortOrder` | Integer | - | - | 정렬 순서 |
| `menuType` | String | - | - | 메뉴 타입 |
| `isVisible` | Boolean | - | - | 표시 여부 |
| `isActive` | Boolean | - | - | 활성화 여부 |
| `description` | String | - | - | 설명 |
| `allowedIps` | String | - | - | 허용 IP 대역 (쉼표 구분) |

### Request 예시

```json
{
  "menuName": "사용자 관리",
  "menuCode": "USER_MGMT",
  "parentId": null,
  "menuUrl": "/users",
  "icon": "fas fa-users",
  "sortOrder": 10,
  "menuType": "MENU",
  "isVisible": true,
  "isActive": true,
  "description": "사용자 관리 메뉴"
}
```

### Response — `ApiResponse<MenuResponse>` (201 Created)

---

## 8. 메뉴 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PUT /api/core/menus/{menuId}` |
| **인증** | 필요 (`isAuthenticated()`) |
| **Content-Type** | `application/json` |

### Request Body — `MenuRequest`

> 메뉴 생성과 동일한 형식

### Response — `ApiResponse<MenuResponse>` (200 OK)

---

## 9. 메뉴 삭제

| 항목 | 값 |
|------|-----|
| **URL** | `DELETE /api/core/menus/{menuId}` |
| **인증** | 필요 (`isAuthenticated()`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 10. 현재 접속자 IP 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /api/core/menus/my-ip` |
| **인증** | 필요 |

### Response — `ApiResponse<Map<String, String>>` (200 OK)

```json
{
  "success": true,
  "code": 200,
  "data": {
    "ip": "192.168.1.100"
  }
}
```
