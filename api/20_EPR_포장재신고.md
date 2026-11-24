# 20. EPR 포장재 중량 신고 (module-epr)

> **URL Prefix**: `/epr-api`
> **인증**: 플랫폼 JWT (`Authorization: Bearer {token}`)
> **권한**: `EPR_REPORT` 메뉴가 열린 역할만 (없으면 403). 처음에는 ROLE_ADMIN 만 열려 있다.
> **응답**: `ApiResponse<T>` — 단, 엑셀 내려받기 4개는 xlsx 파일 본문
> **화면**: `/epr/index.html` (플랫폼 메뉴 `EPR 포장재 신고 > EPR 포장재 중량 신고`)

표 구조 · 계산 규칙 · SQL 실행 순서는 [`sql/module-epr/README.md`](../sql/module-epr/README.md) 를 본다.

## 제품 마스터

| Method | URL | 설명 |
|---|---|---|
| GET | `/epr-api/products?q=&part=` | 목록. `q` = 제품코드·제품명 일부(대소문자 무시), `part` = 파트코드 |
| GET | `/epr-api/products/overview` | `{ productCount }` |
| POST | `/epr-api/products` | 단건 등록 (같은 제품코드가 있으면 수정) |
| PUT | `/epr-api/products/{productCode}` | 단건 수정 |

요청 본문 (`ProductMasterSaveRequest`)

```json
{
  "productCode": "FRT-A0001", "productName": "화장지 30롤",
  "eaPerBag": 12, "bagPerBox": 10, "eaPerBox": 120, "bagPerPlt": 100, "boxPerPlt": 10,
  "bagWeight": 125.5, "boxWeight": 350, "companyType": "자사"
}
```

제품코드는 3~30자, trim · 대문자로 저장한다. 앞 3자리가 파트코드이며 등록되지 않은 파트는 `미분류(코드)` 로 자동 생성된다.
포장중량 단위는 g. `companyType` 은 `자사` / `타사` (생략하면 자사).

## 파트 코드

| Method | URL | 설명 |
|---|---|---|
| GET | `/epr-api/parts` | `[{ partPrefix, partName, skuCount }]` |
| POST | `/epr-api/parts` | `{ partPrefix, partName }` 추가 또는 파트명 수정 |
| DELETE | `/epr-api/parts/{partPrefix}` | 소프트 삭제. 그 파트를 쓰는 제품이 있으면 400 |

## 엑셀

| Method | URL | 설명 |
|---|---|---|
| GET | `/epr-api/excel/templates/master?includeData=false` | 마스터 양식 (`includeData=true` 면 현재 전체 마스터) |
| GET | `/epr-api/excel/templates/weight` | 포장중량 양식 (현재 SKU · 중량 포함) |
| GET | `/epr-api/excel/templates/sales` | 판매수량 양식 |
| POST | `/epr-api/excel/uploads/master` | multipart `file` — 마스터 일괄 등록·갱신 |
| POST | `/epr-api/excel/uploads/weight` | multipart `file` — 포장중량만 갱신 |

업로드 응답 (`UploadSummary`): `{ type, processedRows, insertedRows, updatedRows, failedRows, issues: [{ rowNumber, productCode, message }] }`

## 판매수량 · 연도별 실적

| Method | URL | 설명 |
|---|---|---|
| POST | `/epr-api/annual/calculate` | multipart `file`, `year`, `replaceExisting` — 계산 후 그 연도로 저장. 이미 있는 연도인데 `replaceExisting=false` 면 409 |
| GET | `/epr-api/annual/years` | 저장된 연도 목록 |
| GET | `/epr-api/annual/compare?fromYear=&toYear=` | 연도별 `totalSalesEa`, `totalReportWeightKg`, 전년 대비 `salesChangeEa`, `reportWeightChangeKg` (전년 자료 없으면 null) |
| GET | `/epr-api/annual/{year}` | 저장된 결과 (`SalesCalculationResult`). 없으면 404 |
| GET | `/epr-api/annual/{year}/exists` | `{ year, exists }` |
| GET | `/epr-api/annual/{year}/report` | 결과 리포트 xlsx (파트별 집계 · SKU 상세) |

`SalesCalculationResult` 주요 필드: `reportYear, calculatedAt, acceptedRows, totalSalesEa, uniqueSkuCount, unregisteredSkuCount,
missingWeightSkuCount, grandTotalWeightKg, partSummaries[], skuDetails[], unregisteredSales[], issues[]`.
`skuDetails[].status` 는 `정상` / `미등록코드` / `중량미입력`.
