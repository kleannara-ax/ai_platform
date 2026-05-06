# 수신기 관리 API (Fire Receiver)

> **URL Prefix**: `/fire-api/receivers`
> **컨트롤러**: `FireReceiverController`
> **모듈**: module-fire
> **권한**: 메뉴 접근 권한 `FIRE_RECEIVER` 기반 인가

---

## 1. 수신기 목록 조회 (페이징)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/receivers` |
| **인증** | 필요 |

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `q` | String | - | - | 검색어 |
| `page` | Integer | - | 0 | 페이지 번호 |
| `size` | Integer | - | 50 | 페이지 크기 |

### Response — `ApiResponse<Page<FireReceiverResponse>>` (200 OK)

---

## 2. 수신기 상세 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/receivers/{id}` |
| **인증** | 필요 |

### Response — `ApiResponse<FireReceiverResponse>` (200 OK)

---

## 3. 수신기 등록/수정

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/receivers` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |
| **Content-Type** | `application/json` |

### Request Body — `FireReceiverSaveRequest`

수신기 등록/수정에 필요한 정보를 포함합니다.

### Response — `ApiResponse<FireReceiverResponse>` (200 OK)

---

## 4. 수신기 점검 등록

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/receivers/{id}/inspect` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |
| **Content-Type** | `application/json` |

### Request Body — `EquipmentInspectionRequest`

장비 점검에 필요한 체크리스트 항목을 포함합니다.

### Response — `ApiResponse<FireReceiverResponse>` (200 OK)

---

## 5. 점검 이력 수정

| 항목 | 값 |
|------|-----|
| **URL** | `PATCH /fire-api/receivers/{id}/inspections/{inspectionId}` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |
| **Content-Type** | `application/json` |

### Request Body — `EquipmentInspectionUpdateRequest`

### Response — `ApiResponse<FireReceiverResponse>` (200 OK)

---

## 6. 점검 이력 추가

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/receivers/{id}/inspections` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |
| **Content-Type** | `application/json` |

### Request Body — `EquipmentInspectionUpdateRequest` (수정과 동일)

### Response — `ApiResponse<FireReceiverResponse>` (200 OK)

---

## 7. 점검 이력 CSV 내보내기 (단건)

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/receivers/{id}/inspections/export` |
| **인증** | 필요 |
| **Response Content-Type** | `text/csv` |

### Query Parameters

| 파라미터 | 타입 | 필수 | 형식 | 설명 |
|----------|------|------|------|------|
| `from` | LocalDate | O | `yyyy-MM-dd` | 조회 시작일 |
| `to` | LocalDate | O | `yyyy-MM-dd` | 조회 종료일 |

### Response

CSV 파일 다운로드 (`Content-Disposition: attachment; filename="receiver-inspections-{id}-{from}-{to}.csv"`)

---

## 8. 전체 점검 이력 CSV 내보내기

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/receivers/inspections/export-all` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |
| **Response Content-Type** | `text/csv` |

### Query Parameters

| 파라미터 | 타입 | 필수 | 형식 | 설명 |
|----------|------|------|------|------|
| `from` | LocalDate | O | `yyyy-MM-dd` | 조회 시작일 |
| `to` | LocalDate | O | `yyyy-MM-dd` | 조회 종료일 |

### Response

CSV 파일 다운로드

---

## 9. 점검 이미지 업로드

| 항목 | 값 |
|------|-----|
| **URL** | `POST /fire-api/receivers/{id}/inspections/{inspectionId}/image` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |
| **Content-Type** | `multipart/form-data` |

### Request

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `file` | MultipartFile | O | 이미지 파일 (최대 10MB, image/* 타입만) |

### Response — `ApiResponse<Map>` (200 OK)

```json
{ "success": true, "data": { "imagePath": "/fire-api/receivers/files/abc123.png" } }
```

---

## 10. 점검 이미지 파일 조회

| 항목 | 값 |
|------|-----|
| **URL** | `GET /fire-api/receivers/files/{filename}` |
| **인증** | 불필요 |
| **Response** | 이미지 바이너리 (Content-Type: image/*) |

---

## 11. 수신기 삭제

| 항목 | 값 |
|------|-----|
| **URL** | `DELETE /fire-api/receivers/{id}` |
| **인증** | 필요 (메뉴 권한 `FIRE_RECEIVER`) |

### Response — `ApiResponse<Void>` (200 OK)
