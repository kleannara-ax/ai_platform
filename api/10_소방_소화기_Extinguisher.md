# 소화기 관리 API (Extinguisher)

> **URL Prefix**: `/fire-api/extinguishers`
> **컨트롤러**: `ExtinguisherController`
> **모듈**: module-fire
> **권한**: 메뉴 접근 권한 `FIRE_EXTINGUISHER` 기반 인가

---

## 1. 소화기 목록 조회 (페이징)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/extinguishers` |
| **인증** | 필요 |

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `buildingId` | Long | - | - | 건물 ID 필터 |
| `floorId` | Long | - | - | 층 ID 필터 |
| `q` | String | - | - | 검색어 |
| `page` | Integer | - | 0 | 페이지 번호 |
| `size` | Integer | - | 50 | 페이지 크기 |

### Response — `ApiResponse<Page<ExtinguisherResponse>>` (200 OK)

---

## 2. 소화기 상세 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/extinguishers/{id}` |
| **인증** | 필요 |

### Response — `ApiResponse<ExtinguisherResponse>` (200 OK)

---

## 3. 소화기 등록/수정

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/extinguishers` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |
| **Content-Type** | `application/json` |

### Request Body — `ExtinguisherSaveRequest`

소화기 등록/수정에 필요한 정보를 포함합니다.

### Response — `ApiResponse<ExtinguisherResponse>` (200 OK)

---

## 4. 소화기 이미지 업로드

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/extinguishers/{id}/image` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |
| **Content-Type** | `multipart/form-data` |

### Request

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `file` | MultipartFile | O | 이미지 파일 (최대 10MB, image/* 타입만) |

### Response — `ApiResponse<Map>` (200 OK)

```json
{
  "success": true,
  "data": {
    "imagePath": "/fire-api/extinguishers/files/abc123.png"
  }
}
```

---

## 5. 소화기 이미지 파일 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/extinguishers/files/{filename}` |
| **인증** | 불필요 |
| **Response** | 이미지 바이너리 (Content-Type: image/*) |

---

## 6. 소화기 일괄 점검

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/extinguishers/inspect` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |
| **Content-Type** | `application/json` |

### Request Body — `ExtinguisherInspectRequest`

점검 대상 소화기 목록과 점검 결과를 포함합니다.

### Response — `ApiResponse<Void>` (200 OK)

---

## 7. 점검 이력 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /fire-api/extinguishers/{id}/inspections/{inspectionId}` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |
| **Content-Type** | `application/json` |

### Request Body — `ExtinguisherInspectionUpdateRequest`

| 필드 | 타입 | 설명 |
|------|------|------|
| `inspectionDate` | LocalDate | 점검일 |
| `isFaulty` | Boolean | 고장 여부 |
| `faultReason` | String | 고장 사유 |

### Response — `ApiResponse<Void>` (200 OK)

---

## 8. 점검 이력 추가

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/extinguishers/{id}/inspections` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |
| **Content-Type** | `application/json` |

### Request Body — `ExtinguisherInspectionUpdateRequest` (수정과 동일)

### Response — `ApiResponse<Void>` (200 OK)

---

## 9. 점검 이력 삭제

| 항목 | 값 |
|------|-----|
| **URL** | `DELETE /fire-api/extinguishers/{id}/inspections/{inspectionId}` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |

### Response — `ApiResponse<Void>` (200 OK)

---

## 10. 소화기 삭제

| 항목 | 값 |
|------|-----|
| **URL** | `DELETE /fire-api/extinguishers/{id}` |
| **인증** | 필요 (메뉴 권한 `FIRE_EXTINGUISHER`) |

### Response — `ApiResponse<Void>` (200 OK)
