# module-security_log — ISMS 보안 로그 이상징후 탐지/검토 모듈

## 1. 모듈 설명

각 시스템 담당자가 **시스템 로그 파일을 업로드**하면, 정보보안팀이 관리하는 **탐지 룰셋**으로 이상징후를 자동 탐지합니다.
탐지 결과는 **월 단위로 검토하고 모니터링**합니다. ISMS-P 인증 기준 2.9.4(로그 및 접속기록 관리)와 2.9.5(로그 및 접속기록 점검)의 정기 점검 증적으로 활용할 수 있습니다.

| 항목 | 내용 |
|---|---|
| 모듈명 | `security_log` |
| 모듈 디렉토리 | `module-security_log/` |
| 패키지 루트 | `com.company.module.security_log` |
| API Prefix | `/security_log-api` |
| 사용 대상 | 각 시스템 담당자, 정보보안팀 담당자 |
| 실행 형태 | 플랫폼 app 모듈에 포함되는 plain jar (독립 실행 아님) |

### 업무 흐름

```
[시스템 담당자]                              [정보보안팀]
  ① 로그 파일 업로드 (월/시스템/로그유형)       ⓪ 룰셋 편집 (등록/수정/테스트/사용여부)
        │                                            │
        ▼                                            │
  ② 자동 분석 (활성 룰 = 해당 로그유형 + ALL) ◀───────┘
        │   - SHA-256 해시 저장, 원본 GZIP 보관
        ▼
  ③ 탐지 결과 생성 (라인 단위, 위험도별 집계)
        │
        ▼
  ④ 탐지 건 검토/조치 등록 (확인/조치완료/오탐)  ◀── 담당자 또는 보안팀
        │
        ▼
  ⑤ 최종 검토완료 (미검토 0건일 때만 가능)    ◀── 정보보안팀 관리자
        │
        ▼
  ⑥ 월별 모니터링 요약 (업로드 현황, 위험도, TOP 룰, 미검토 건수)
```

### 중점 점검 대상 시스템 (14개, `03_system_access_account.sql` 로 기본 등록)

| 구분 | 시스템 | 기본 로그 유형 | 접속·행위 로그 예 |
|---|---|---|---|
| 서비스 | 홈페이지 / 자사몰(깨끗한나라몰) / 임직원몰 | WEB | 관리자 페이지 로그인 이력 |
| 서비스 | KN-OMS | APP | OMS 로그인 이력 |
| 정보보호시스템 | AWS방화벽 / UTM | NETWORK | 관리자 접속 로그 |
| 정보보호시스템 | SSL VPN / V3 EPP / WAF / DLP | SECURITY | VPN 인증 · 관리 콘솔 접속·감사 로그 |
| 정보보호시스템 | GIT | APP | 로그인 · 감사 로그 |
| 정보보호시스템 | AWS console | CLOUD | CloudTrail `ConsoleLogin` |
| 네트워크 | AP/스위치 | NETWORK | SSH/Console 접속 로그 |
| DBMS | DBMS(OMS, 홈페이지) | DB | DB 접속 감사 로그 |

### 월 점검 흐름 (접속·행위 로그 분석만)

```
[월별 점검 현황] ─ 사용 중인 시스템별 접속·행위 로그 등록/검토 상태를 한 화면에 표시
   │
   └─ 접속·행위 로그 업로드 (월 1회)
        → 계정별 로그인 성공/실패, 업무시간 외(22~06시), 외부 IP, 다중 IP, 특권/공용 계정 자동 집계
        → 시스템별 룰셋(공통 + 전용)으로 이상징후 탐지
        → 탐지 건 검토 → 최종 검토완료(ADMIN) → 월 마감
```

> **장기 미접속 계정 점검(계정 목록 업로드·판정·조치)은 삭제했습니다.** 현재는 행위 로그 분석만 합니다. 관련 테이블(`sec_log_account_check`, `sec_log_account`)과 `sec_log_system.INACTIVE_DAYS` 컬럼은 데이터 보존을 위해 DB 에 남아 있지만 앱에서 쓰지 않습니다.

### 화면 구성 (메뉴 진입 = 대시보드)

`ISMS 로그 점검 현황` 메뉴를 누르면 **대시보드**가 먼저 열립니다. 세부 작업은 상단 탭 5개(대시보드 / 월별 점검 현황 / 분석 결과 (접속 행위·이상징후) / 점검 대상 시스템 / **관리**)로 나눴습니다. ('접속·보안 로그' 탭은 2026-11 요청으로 삭제) **관리** 탭을 누르면 관리 페이지 하위 메뉴(탐지 룰셋 편집 · 내부 IP 설정)가 보입니다.

| 탭 | 파일 | 용도 |
|---|---|---|
| **대시보드** | `dashboard.html` | **사용 중인 점검 대상 시스템만 집계**(사용 토글 기준). 아래 '대시보드 구성' 참고. 5분마다 자동 새로고침, **로그 업로드**(ADMIN/MANAGER, 팝업) · **월 점검 결과 내보내기(엑셀)** 버튼 |
| 월별 점검 현황 | `status.html` | 시스템별 접속·행위 로그 등록/검토 상태, 위험 계정, 탐지 건수 (월 마감용) |
| 분석 결과 (접속 행위·이상징후) | `detail.html` | 처음엔 로그 고르기 카드(위험도 높은 순) → 고른 로그의 접속 행위 분석·이상징후 탐지 |
| ~~접속·보안 로그~~ (삭제) | `index.html` | **메뉴 삭제**. 파일은 예전 주소·즐겨찾기·플랫폼 메뉴용으로 남겨 두었고, 열면 [분석 결과] 로그 고르기 화면(`detail.html`, `?ym=` 유지)으로 바로 넘어감 |
| 점검 대상 시스템 · **시스템 검색창**(시스템명·코드·구분·담당자·설명, 띄어쓰기로 여러 단어 동시 검색) | `systems.html` | 시스템 마스터. **사용 열의 토글로 사용/미사용 바로 전환** (ADMIN, 미사용 전환 시 확인창) |
| **관리 › 탐지 룰셋 편집** | `rules.html` | **시스템별 룰셋 사용/미사용**(공통·전용), 공통 룰 / 시스템 전용 룰 편집 (관리 탭의 첫 화면) |
| **관리 › 내부 IP 설정** | `internal-ip.html` | 외부 IP 판정 기준이 되는 내부 IP 대역 등록·수정·사용/미사용·삭제, **IP 확인**(어느 대역에 속하는지) |

### 대시보드 구성 (정보보안 담당자: 매일 확인 + 매달 증적)

| 위치 | 항목 | 매일 확인 | 매달 증적 |
|---|---|---|---|
| 1·2 | ~~로그 업로드 현황 / 검토 현황~~ — **대시보드에서 삭제**(2026-11 요청). 시스템별 업로드·검토 완료 여부는 상단 **[월별 점검 현황]** 탭(① 로그 올리기 / ② 분석 결과 검토 — 완료·미완료)에서, 로그 기간 등 자세한 내용은 [접속·보안 로그] 목록과 분석 결과 화면에서 봅니다. 마감 D-day 는 대시보드 '지금 먼저 볼 것' 오른쪽에 그대로 표시 | 시스템마다 빠짐없이 올렸는지, 무엇을 먼저 할지 | 2.9.4 로그 빠짐없이 수집 · 2.9.5 검토·마감 |
| 3 | **확인이 필요한 항목** + 미검토 경과 | 7일 넘게 방치된 미검토는 긴급으로 맨 위 | 기한 내 검토 |
| 4 | 위험 접속 계정 TOP 10 · **특권 계정 활동** | 실패 반복·외부 IP·특권 계정 접속 | 특권 계정 점검 |
| 5 | **이번 달 일별 추이** + 많이 탐지된 룰 | 날짜별 실패·업무시간 외·탐지 | 추이 증적 |

**시각 기준**: 모든 시각은 **한국시간(Asia/Seoul)** 입니다. 서버(JVM) 시간대가 UTC 여도 모듈의 현재 시각은 `support/SecLogTime` 으로 한국시간을 씁니다. 화면의 '몇 분 전' 도 브라우저 시간대와 관계없이 한국시간 기준으로 계산합니다.

**일별 수집 현황 판정** (`sec_log_access_daily` 기준, 월 1회 업로드)

| 표시 | 의미 |
|---|---|
| 진한 초록 | 그 날짜 로그가 1줄 이상 있음 |
| 연한 초록 | 그 달 로그 파일은 올렸지만 그 날 활동이 없음 |
| 노랑 | 대상 월이 지났는데 그 달 로그 파일이 없음 (누락 → 로그 파일 업로드) |
| 점선 | 대상 월이 아직 끝나지 않음 (월이 끝난 뒤 업로드) |

**로그 업로드 상태** (`SecLogDashboardService.uploadRows`)

| 상태 | 의미 |
|---|---|
| 업로드 완료 | 그 달 파일을 올렸고 로그 기간·일자에 문제 없음 |
| 기간 확인 | 아래 확인 기준 중 하나 이상 해당 (사유를 표와 '확인이 필요한 항목'에 표시) |
| 일자 점검 전 | `08` 적용 전에 올린 파일. 대시보드의 [로그 일자 점검 다시 하기] 버튼은 업로드 현황 표와 함께 삭제됨 — 필요하면 ADMIN 이 API `POST /dashboard/rebuild-daily?targetYm=` 로 실행 |
| 미업로드 | 대상 월이 끝났는데 파일 없음. **업로드 기한은 두지 않음** (나중에 올려도 됨, D-day 표시 없음) |
| 월말 후 업로드 | 대상 월이 아직 끝나지 않아 올릴 시점이 아님 |

**로그 기간 확인 기준** (상수: `PERIOD_EDGE_DAYS = 3`, `DATE_UNKNOWN_PERCENT = 10`)

| 사유 | 기준 |
|---|---|
| `M/D부터 시작` | 대상 월 안의 첫 로그일이 4일 이후 (1일~4일 시작은 정상) |
| `M/D까지만 있음` | 대상 월이 끝났고, 마지막 로그일이 말일 3일 전보다 이름 (진행 중인 달은 검사하지 않음) |
| `일시 인식 안 된 줄 N` | 일시를 읽지 못한 줄이 전체 줄의 10% 이상 → 일별 통계가 틀릴 수 있음 (형식 확인·전용 룰 검토) |
| `대상 월 밖 줄 N` | 대상 월이 아닌 날짜의 줄이 1줄 이상 → 다른 달 파일이 섞였는지 확인 |
| `대상 월 로그 없음` | 파일은 올렸지만 대상 월 날짜의 줄이 하나도 없음 |

주말 등 활동이 없는 날이 있는 시스템이 많아 **활동일 수는 확인 기준에서 제외**하고 표에 참고로만 표시합니다.

수집률 = (로그 있음 + 활동 없음) ÷ 대상 일수 (엑셀 2번 시트). 로그 일자는 각 줄의 일시(표 형식은 '일시' 열)로 판단하며, UTC 표기는 한국시간으로 바꿉니다. syslog 처럼 연도가 없는 형식은 대상 연월의 연도를 씁니다.

### 관리 › 내부 IP 설정 (`09_internal_ip.sql`)

- 접속 행위 분석에서 **사용 중인 내부 대역에 속하지 않는 IPv4** 를 '외부 IP' 로 표시합니다. 예전에 코드에 고정돼 있던 사설·예약 대역 6개가 기본값으로 등록됩니다.
- 사내망, VPN 대역, 본사 공인 IP, 관리 PC 처럼 **정상 접속지를 등록**하면 외부 IP 오탐이 줄어듭니다.
- 입력: `10.20.0.0/16`, `192.168.10.0/24`, 단일 IP `203.0.113.10`(→ `/32`). 호스트 비트가 섞이면 네트워크 주소로 맞춰 저장합니다(`192.1.2.77/24` → `192.1.2.0/24`). IPv4 만, **/8 보다 넓은 대역은 거부**(모든 IP 가 내부가 되는 것을 막음). 같은 대역 중복 등록 거부, 삭제했던 대역을 다시 등록하면 복구합니다.
- **IP 확인**: IP 를 넣으면 내부/외부 여부와 속한 대역을 보여줍니다(저장 안 함). 전원 사용 가능.
- 적용 시점: 변경은 **이후 업로드·재분석부터** 적용됩니다. 이미 분석한 결과는 상세 화면의 [재분석]으로 다시 계산합니다(검토완료 건은 잠김).
- 권한: 조회·IP 확인은 ADMIN/MANAGER/USER, 등록·수정·사용여부·삭제는 ADMIN.

### 점검 매뉴얼 (대시보드 팝업)

- 대시보드 상단 **[점검 매뉴얼]** 버튼(전원)을 누르면 로그 검토자 점검 순서를 팝업으로 보여줍니다. 서버 변경 없음(화면 안내만).
- 내용: ① 대시보드에서 지난달 점검 상황 보기(점검 대상 월 안내 → 진행 3단계 → 지금 먼저 볼 것 → 위험 계정·특권 계정, 시스템별 업로드 여부는 [월별 점검 현황]) → ② 볼 시스템 정하기(최고 위험도 순) → ③ 시스템 상세 검토(업로드 정보 → 심각·높음 룰 → 접속 행위 분석 → 보통·낮음 룰 → 최종 검토완료, 룰 하나 처리 방법, 검토 상태 기준) → ④ 월 마감([월별 점검 현황]에서 마감 완료 확인 → 엑셀 내보내기 → 서명), 위험도별 처리 시한, 주의할 점.
- 목차를 누르면 팝업 안에서 해당 항목으로 이동합니다(페이지 주소는 바뀌지 않음). Esc·닫기로 닫힙니다.

### 대시보드에서 로그 업로드 (팝업)

- 대시보드 상단 **[로그 업로드]** 버튼(ADMIN/MANAGER 에게만 보임)을 누르면 업로드 팝업이 열립니다. 대상 연월은 대시보드에서 보고 있는 달로 채워집니다.
- '지금 먼저 볼 것'의 [올리기]나 [월별 점검 현황]의 [N월 로그 올리기]로 열면 그 시스템이 미리 선택됩니다. 시스템을 고르면 기본 로그 유형이 자동 선택됩니다.
- 입력: 대상 연월, 점검 대상 시스템, 로그 유형, 파일 인코딩, 비고, 로그 파일(끌어다 놓기 가능). 확장자·크기는 업로드 화면과 같은 기준으로 먼저 확인합니다.
- 업로드가 끝나면 팝업에 분석 결과(줄 수, 탐지 건수, 위험도)를 보여주고 **[상세 보기 / 검토]**, **[다른 파일 업로드]** 를 고를 수 있습니다. 대시보드는 바로 새로 고칩니다(다른 달에 올렸으면 그 달로 이동).
- 같은 파일 재업로드 등 오류는 팝업을 닫지 않고 안내합니다. 직접 입력 시스템이 필요하면 팝업 아래 '전체 화면에서 업로드'(`upload.html`)를 씁니다.
- 사용 API 는 기존 `POST /security_log-api/uploads` 그대로입니다 (서버 변경 없음).

### 시스템별 전용 룰셋 (`10_system_rules.sql`)

시스템마다 로그 형식과 남는 정보가 달라서, 14개 시스템 모두에 **그 시스템 로그 형식에 맞춘 전용 룰**을 넣었습니다(신규 87개, DLP 8개는 전용으로 이동 → 전용 룰 95개).

| 시스템 | 기준 | 룰 수 | 룰 (위험도) |
|---|---|---:|---|
| 홈페이지 | 실제 로그 | 7 | 관리자 계정 생성(높음), 관리자 계정 권한·상태 변경(높음), 관리자 계정 삭제(높음), 관리자 비밀번호 본인 변경(보통), 게시물·콘텐츠 삭제(보통), 심야(22~06시) 등록·수정·삭제(보통), 관리자 목록·상세 조회(낮음·미사용) |
| 자사몰(깨끗한나라몰) | 초안 | 6 | 회원 개인정보 엑셀 다운로드(높음), 관리자(운영자) 계정·권한 변경(높음), 회원 정보 관리자 수정·탈퇴 처리(보통), 적립금·쿠폰 수동 지급(보통), 관리자 주문 취소·환불 처리(낮음), 관리자 비밀번호 변경·초기화(보통) |
| 임직원몰 | 초안 | 6 | 임직원(회원) 개인정보 엑셀 다운로드(높음), 관리자(운영자) 계정·권한 변경(높음), 임직원(회원) 정보 관리자 수정·탈퇴 처리(보통), 복지포인트·쿠폰 수동 지급(보통), 관리자 주문 취소·환불 처리(낮음), 관리자 비밀번호 변경·초기화(보통) |
| KN-OMS | 초안 | 5 | 사용자 계정·권한 변경(높음), 주문·고객 데이터 엑셀 반출(보통), 주문 강제·일괄 변경/삭제(보통), 단가·거래처·상품 기준정보 변경(보통), 사용자 비밀번호 초기화(보통) |
| AWS방화벽 | 초안 | 5 | 전체 개방(0.0.0.0/0) 규칙 추가(심각), 방화벽 정책·규칙 삭제(높음), 방화벽 정책·규칙 변경(보통), 외부 IP → 관리 포트 허용 접속(높음·3건↑), 침입 탐지(IPS) 경보(높음) |
| UTM | 초안 | 7 | 장비 관리자 로그인 실패(높음·5건↑), 방화벽 정책 추가·수정·삭제(보통), 전체 허용(any→any) 정책(심각), 장비 관리자 계정 변경(높음), 펌웨어 변경·설정 복원·초기화(높음), 로그 설정 변경·삭제(심각), IPS 심각·높음 공격 탐지(높음) |
| SSL VPN | 초안 | 6 | VPN 인증 실패 다발(높음·10건↑), 없는 계정으로 VPN 접속 시도(높음·3건↑), VPN 계정 잠김(보통), 동일 계정 중복(동시) 접속(보통), VPN 사용자·접근 정책 변경(높음), 심야(00~06시) VPN 접속(낮음) |
| GIT | 초안 | 8 | 저장소(프로젝트) 삭제(높음), 저장소 공개(public) 전환(높음), 저장소 권한·멤버 변경(높음), 보호 브랜치 해제(높음), 강제 푸시(force push)(보통), 접근 토큰·배포 키 생성(보통), 저장소 압축 다운로드·내보내기(보통), 2단계 인증(2FA) 해제(높음) |
| V3 EPP | 초안 | 8 | 악성코드 탐지(높음), 악성코드 치료·격리 실패(심각), 랜섬웨어 탐지·차단(심각), 실시간 검사·자기 보호 해제(높음), 에이전트(V3) 삭제(높음), 엔진 업데이트 실패 반복(낮음·5건↑), 보안 정책 변경(보통), 검사 예외 추가(높음) |
| AWS console | 초안 | 9 | 루트 계정 콘솔 로그인(높음), MFA 없이 콘솔 로그인(보통), 콘솔 로그인 실패 다발(높음·5건↑), IAM 사용자·권한·액세스 키 변경(높음), 보안그룹 전체 개방(0.0.0.0/0)(심각), 감사 로그(CloudTrail 등) 중지·삭제(심각), S3 버킷 정책·공개 설정 변경(높음), 암호화 키(KMS) 삭제 예약·비활성화(높음), 권한 없는 API 호출 다발(보통·20건↑) |
| WAF | 초안 | 7 | WAF 관리자 로그인 실패(높음·5건↑), 차단 해제·탐지 모드 전환(높음), 탐지 정책·시그니처 변경(보통), 예외(허용 IP·URL) 추가(높음), SQL 인젝션 공격 다발(높음·10건↑), 웹셸·악성 파일 업로드 시도(심각), WAF 관리자 계정 변경(높음) |
| AP/스위치 | 초안 | 7 | 장비 로그인 실패 다발(높음·5건↑), 장비 설정 변경·저장(보통), 접근 제어 목록(ACL) 변경(높음), 장비 계정·비밀번호·SNMP 변경(높음), 장비 재부팅(높음), 장비 로그 삭제·전송 중지(심각), 비인가 AP 탐지(높음) |
| DBMS(OMS, 홈페이지) | 초안 | 6 | DB 계정 생성·삭제·비밀번호 변경(높음), DB 고위험 권한 부여(높음), DB 접속 실패 다발(높음·10건↑), 주민·카드·계좌번호 컬럼 조회(보통), 조건 없는 DELETE(전체 삭제)(높음), DB 감사 로그 중지(심각) |
| DLP | 실제 로그 | 8 | `04` 의 DLP 룰 8개를 DLP 전용으로 이동 (내용은 그대로) |

- **실제 로그 기준**: 홈페이지(관리자 감사 엑셀), DLP(관리자 감사 엑셀). 받은 파일에서 확인한 형식(엑셀 열 순서, 메뉴명, 유형 코드)으로 만들었습니다.
- **초안**: 나머지 12개 시스템은 실제 샘플이 없어 장비·제품의 일반 로그 형식(CloudTrail JSON, Cisco syslog, FortiGate 계열 `key="value"`, MariaDB `server_audit`, GitLab/GitHub 감사 JSON, 한글 콘솔 로그 등)으로 만들었습니다. 설명 앞에 **`[샘플 확인 전 초안]`** 이 붙어 있으며, 샘플을 받으면 패턴을 맞추고 표시를 지웁니다.
- **DLP 룰 이동 이유**: `04` 의 DLP 룰 6개가 SECURITY 유형 공통 룰로 남아 있어 SSL VPN·V3 EPP·WAF 로그에도 적용되고 있었습니다. 이제 DLP 에만 적용됩니다(DLP 결과 258건 그대로).
- 위험도는 [위험도 기준](#위험도-기준-심각--높음--보통--낮음)과 같은 원칙입니다: 로그·감사 설정 훼손, 전체 개방, 랜섬웨어·치료 실패, 웹셸 = 심각 / 계정·권한 변경, 인증 실패 다발, 공개 전환 = 높음 / 정책 변경, 데이터 반출 = 보통 / 참고 이벤트 = 낮음.
- 건수가 많을 룰은 임계 건수(예: 인증 실패 5~10건 이상)를 두었고, 홈페이지 '관리자 목록·상세 조회'는 건수가 많아 **기본 미사용**입니다.
- 모든 룰은 화면 **[관리 › 탐지 룰셋 편집]** 에서 수정·사용여부 변경이 가능하며, SQL 을 다시 실행해도 화면에서 바꾼 내용은 덮어쓰지 않습니다.

### 자사몰 로그인 기록 분석 룰 (`11_mall_login_rules.sql`)

자사몰(깨끗한나라몰, `SVC_MALL`) 관리자 **로그인 기록**을 분석하는 전용 룰 9개를 추가했습니다. `10` 의 자사몰 룰 6개(엑셀 다운로드·계정 변경 등 업무 행위)는 그대로 두고, 로그인·인증 단계만 따로 봅니다. 자사몰 전용 룰은 15개가 됩니다.

| 룰 코드 | 룰 | 위험도 | 탐지 기준 | 확인할 것 |
|---|---|---|---|---|
| `MALL_LOGIN_FAIL_BURST` | 관리자 로그인 실패 다발 | 높음 | 5건↑ | 특정 계정·IP 에 몰렸는지. 무차별 대입이면 IP 차단·비밀번호 변경 |
| `MALL_LOGIN_UNKNOWN_ID` | 없는 아이디로 로그인 시도 | 보통 | 3건↑ | 시도한 아이디 목록(root·oracle 등)과 접속 IP — 계정 추측 공격 |
| `MALL_ACCOUNT_LOCK` | 관리자 계정 잠금·잠금 해제 | 보통 | 1건↑ | 잠금은 공격 대상 계정, 해제는 본인 요청·승인 여부 |
| `MALL_LOGIN_IP_DENIED` | 허용되지 않은 IP에서 관리자 접속 시도 | 높음 | 1건↑ | 해외·처음 보는 IP 면 계정 탈취 시도로 보고 비밀번호 변경 여부 |
| `MALL_LOGIN_OTP_FAIL` | 2차 인증(OTP·인증번호) 실패 | 높음 | 3건↑ | 비밀번호는 맞은 상태 → 유출 가능성, 비밀번호 즉시 변경 검토 |
| `MALL_SHARED_ACCOUNT_LOGIN` | 공용·기본 계정(admin·master 등)으로 관리자 로그인 | 보통 | 1건↑ | admin·administrator·master·root·guest 로그인(인증 완료). 1인 1계정 원칙. **test1·test3 처럼 개인에게 발급된 계정은 대상 아님** |
| `MALL_OFF_HOUR_LOGIN` | 심야(22~06시) 관리자 로그인 | 보통 | 1건↑ | 수행업무 **'인증'(= 로그인 완료)·'로그인'** 줄만. 실패·로그아웃 제외. 야간 근무·긴급 작업 기록과 대조. **실제 로그 기준** |
| `MALL_DUPLICATE_LOGIN` | 동일 계정 중복(동시) 로그인 | 보통 | 1건↑ | 두 접속 IP 가 모두 본인 것인지 — 계정 공유·탈취 |
| `MALL_PW_FIND` | 관리자 비밀번호 찾기·임시 비밀번호 발급 | 보통 | 1건↑ | 본인 요청인지, 발급 직후 다른 IP 로그인이 있는지 |

- **실제 자사몰 접속 기록 형식 (2026-10 테스트 파일로 확인)**: `접속일시 | 접속IP | 운영자 아이디 | 수행업무` 4열 엑셀. 수행업무는 `인증`(로그인 완료) · `로그인` · `로그아웃`. **결과(성공/실패) 열이 없어** 실패·잠금·OTP·IP 차단·없는 아이디·중복 로그인·비밀번호 찾기 룰 7개는 이 파일에서는 탐지할 내용이 없습니다(실패 기록이 따로 남으면 그 파일로 탐지). 이 형식에 맞춰 '심야 로그인' 룰은 초안 표시를 지웠습니다.
- **나머지 형식 (샘플 확인 전 초안)**: 실제 자사몰 로그 샘플이 없어 아래 3가지 일반 형식을 모두 잡도록 만들었습니다. 설명 앞에 `[샘플 확인 전 초안]` 이 붙어 있으며, 샘플을 받으면 패턴을 맞추고 표시를 지웁니다.
  - 관리자 접속 기록 엑셀/CSV: `번호 | 아이디 | 이름 | 구분(로그인·로그아웃·OTP 인증…) | 결과 | 접속 IP | 일시`
  - key=value 텍스트: `2026-09-03 10:02:11 [ADMIN_LOGIN] result=FAIL reason=PASSWORD_MISMATCH admin_id=… ip=…`
  - 한글 문장형: `관리자 로그인 실패 - 아이디: …, 접속IP: …`
- **오탐 방지**: 로그인 성공·로그아웃·일반 업무 행 줄은 잡지 않습니다. '공용 계정'·'심야 로그인' 은 실패·차단·로그아웃 줄을 빼고 성공한 로그인만 봅니다. '공용 계정' 은 아이디 칸이 정확히 admin·master·root·guest(+숫자)인 경우만 잡습니다. 자사몰은 1인 1계정이라 test·tester·demo·temp 같은 이름도 개인 계정으로 보고 제외했습니다.
- **접속 행위 분석**: 엑셀 머리글 `수행업무` 를 구분 칸으로 인식하도록 했습니다(`AccessLogAnalyzer`). '인증'·'로그인' 은 로그인 성공으로, '로그아웃' 은 제외로 집계됩니다. 테스트 파일의 `1.2.3.x` 는 공인 대역이라 '외부 IP' 로 표시됩니다 — 실제 사내 대역이면 [관리 › 내부 IP 설정]에 등록하세요.
- **공통 룰과의 관계**: 공통 룰은 그대로 함께 적용됩니다. 자사몰은 '반복 인증 실패'(10건↑)보다 이른 5건↑, '심야 시간대 접근'(00~05시, 낮음)보다 넓은 22~06시로 봅니다.
- 계정별 성공·실패·업무시간 외·외부 IP 집계는 기존 **① 접속 행위 분석**이 그대로 보여 줍니다(룰과 별개).
- 적용은 이후 업로드·재분석부터입니다. 이미 분석한 자사몰 업로드는 상세 화면 **[재분석]**.

### 룰별 로그 확인 / 룰 일괄 검토 (상세 화면 '룰별 탐지 건수')

- 로그인 실패처럼 **같은 사유로 여러 줄이 탐지되는 룰**은 줄마다 조치 내용을 쓰지 않고 룰 단위로 한 번에 검토합니다.
- 룰별 탐지 건수 표는 **탐지된 룰만** 보여줍니다(건수 많은 순). 탐지 없는 룰은 기본으로 숨기고, 표 아래 **[탐지 없는 룰 N개 보기]** 버튼으로 펼치면 회색 줄(위험도 높은 순, '탐지 없음', 버튼 없음)로 나옵니다. 다시 누르면 숨깁니다. 탐지된 룰이 하나도 없으면 '이 로그에서 탐지된 룰이 없습니다' 한 줄이 나옵니다. 룰마다 **공통 / 전용** 구분을 붙이고, 임계 건수가 2 이상인 룰은 '10건↑ 탐지'처럼 기준을 보여줍니다(그보다 적게 매칭된 경우도 0건). 제목 옆에는 '적용 룰 17개 중 3개 탐지'처럼 요약합니다.
- 룰별 탐지 건수 표에 **검토 진행**(조치완료·오탐·이상징후 확인·미검토 막대, 미검토 n건 / 검토 끝)을 보여줍니다.
- **[로그 보기]**: 아래 탐지 결과 목록을 그 룰로 거르고 목록으로 이동합니다(전원 가능). 탐지 결과 목록에도 **룰** 필터가 생겼고, 룰을 고르면 [이 룰 일괄 검토]·[룰 필터 해제] 버튼이 보입니다.
- **[룰 일괄 검토]** (ADMIN/MANAGER, 검토완료 전): 팝업에서 룰 정보·검토 건수, 로그 예시 5줄을 확인하고 검토 상태(기본 '오탐 (정상 업무/단순 실수)')와 조치 내용을 입력합니다.
  - 적용 대상: **미검토 건만**(기본, 이미 줄 단위로 검토한 건은 그대로) / **전체**(이미 검토한 건도 덮어씀, 한 번 더 확인).
  - 건수 제한 없음(줄 단위 선택 검토는 최대 1,000건). 같은 룰 안에서 일부만 다르게 처리할 건은 먼저 줄 단위로 검토한 뒤 '미검토 건만' 으로 나머지를 처리합니다.
- 검토자·검토 일시는 줄마다 똑같이 기록되어 엑셀 내보내기·증적에 그대로 나옵니다.

### 월 점검 결과 내보내기 (엑셀, `GET /dashboard/export`)

대시보드의 [월 점검 결과 내보내기] 버튼으로 `ISMS_로그점검결과_YYYY년MM월.xlsx` 를 받습니다. 사용 중인 시스템만 포함하며, 글꼴은 맑은 고딕입니다. 인쇄 시 시트마다 A4 너비 1장에 맞춥니다.

| 시트 | 내용 |
|---|---|
| 1.요약 | 점검 요약(마감률, 마감 기준일, 수집률, 탐지·미검토·위험 계정·특권 계정), 시스템별 점검 결과, **검토자/검토일/승인자 서명란** |
| 2.일별 수집 현황 | 시스템 × 날짜 로그 줄 수 (0=활동 없음, —=누락), 수집일·누락·수집률 |
| 3.접속 로그 검토 | 업로드 건별 분석 줄 수, 위험도별 탐지, 검토 상태·의견·일시 |
| 4.탐지 내역 | 탐지 건별 룰·위험도·로그 원문·검토 상태·조치 내용 (최대 5만 건) |
| 5.특권 계정 활동 | 특권/공용 계정별 성공·실패·업무시간 외·외부 IP |

### 월 1회 로그 업로드 + 시스템별 룰셋 (`07_system_ruleset.sql`)

```
[매월] 시스템별 지난달 로그 파일 업로드 (시스템마다 형식이 달라도 됨)
   → 시스템별 룰셋으로 분석 = 공통 룰셋(로그 유형 + ALL) + 시스템 전용 룰셋
   → 탐지 검토 → 최종 검토완료 → 월 마감 (분석 이후 흐름은 기존과 동일)
```

- **로그 수집은 월 1회 파일 업로드만 사용합니다.** API 수집(연결 설정·커넥터·매일 자동 수집)은 삭제했고, S3 가져오기는 방식이 정해지면 추가합니다. `05` 의 `sec_log_collector`, `sec_log_collect_history` 테이블은 데이터 보존을 위해 남겨 두고 더 이상 쓰지 않습니다.
- **업로드**: 대시보드 [로그 올리기], [월별 점검 현황]의 [N월 로그 올리기], [분석 결과] 로그 고르기 화면의 [로그 올리기]. 시스템을 고르면 로그 유형이 자동 선택됩니다. 텍스트(log/txt/csv/tsv/json/out)와 엑셀(xlsx/xls), 최대 50MB, 같은 내용 재업로드는 차단.
- **로그 형식이 시스템마다 다름**: 탐지는 줄 단위 키워드/정규식이라 형식과 관계없이 동작합니다. 시스템 고유 형식은 그 시스템 **전용 룰**로 맞춥니다(예: AWS방화벽 `action=CONFIG ... cidr=0.0.0.0/0`).
- **시스템별 룰셋**: 룰에 `SYSTEM_ID` 가 있으면 그 시스템 전용, 없으면 공통. [탐지 룰셋] 화면 상단 '시스템별 룰셋' 표에서 시스템마다 **공통 룰셋 / 시스템 전용 룰셋을 각각 스위치로 사용·미사용**(ADMIN). 분석에 적용되는 룰 수가 0이면 경고를 표시합니다.
- **적용 시점**: 룰셋·룰 변경은 이후 업로드부터 적용됩니다. 이미 올린 로그는 상세 화면의 `재분석`으로 다시 분석합니다(검토완료 건은 잠김).

## 2. 주요 기능

| 구분 | 기능 | 설명 |
|---|---|---|
| 룰셋 편집 | 룰 목록/상세/등록/수정/삭제 | 키워드(쉼표 구분) 또는 정규식, 위험도 4단계, 임계 건수, 로그 유형별 적용 |
| | 사용여부 토글 | 룰을 삭제하지 않고 일시 중지 |
| | 패턴 사전 테스트 | 샘플 로그를 붙여 넣어 매칭 결과를 미리 확인 (저장 안 함) |
| 로그 업로드 | 파일 업로드 + 즉시 분석 | `.log/.txt/.csv/.json/.out`, 최대 50MB, UTF-8/EUC-KR/MS949 |
| | 중복 업로드 차단 | 같은 월·시스템에 같은 해시의 파일을 다시 올리면 거부 |
| | 원본 보관 | GZIP 압축으로 DB에 보관하여 증적 보존과 재분석에 사용 |
| | 재분석 | 룰셋을 변경한 뒤 현재 룰로 다시 분석 (기존 결과는 소프트 삭제) |
| 탐지 검토 | 탐지 목록/상세 | 위험도·검토상태 필터, 라인 번호, 원문 |
| | 단건/일괄 검토 | 미검토 → 이상징후 확인 / 조치완료 / 오탐, 조치 내용 기록 |
| | 룰 일괄 검토 | 룰별 로그 확인([로그 보기]) 후 같은 룰 탐지 건을 한 번에 검토·조치 내용 기록 (미검토만 / 전체) |
| | 최종 검토완료/취소 | 정보보안팀 관리자 전용, 검토완료 후에는 수정·재분석 잠금 |
| 모니터링 | 월별 조회 | `targetYm=YYYYMM` 기준 목록·요약·TOP 10 룰 |
| 월별 점검 현황 | 시스템별 상태판 | 사용 중인 시스템마다 접속·행위 로그 등록/검토 여부, 접속 계정·성공/실패, 위험 계정 수, 탐지 건수 |
| 점검 대상 시스템 | 시스템 마스터 CRUD | 구분, 기본 로그 유형, 담당자, 정렬, 사용여부 (편집은 ADMIN) |
| 접속 행위 분석 | 업로드 시 자동 집계 | 계정별 성공/실패/업무시간 외/외부 IP/IP 목록/최초·최종 시각 + 위험 표시 5종 |

### 접속 행위 분석 기준 (`AccessLogAnalyzer`)

| 인식 형식 | 예 |
|---|---|
| **관리 콘솔 엑셀/CSV 감사로그** | 첫 데이터 행 머리글로 아이디·IP·일시·유형(결과/내용) 열을 찾음. 예: DLP `발생 일시 / 사용자(관리자) 아이디 / IP / 유형 / 내용 / 상세 정보`. 로그인 여부는 **유형 열**로만 판단(메뉴명 오인식 방지), 로그아웃·세션 만료는 제외 |
| Linux sshd | `Accepted password for kim from 10.1.1.1`, `Failed password for ...`, `Invalid user test from ...` |
| Windows 보안 이벤트 | 4624(성공) / 4625(실패) + `Account Name:` / `Source Network Address:` |
| AWS CloudTrail | `"eventName":"ConsoleLogin"`, `userName`, `sourceIPAddress`, `"ConsoleLogin":"Failure"` |
| key=value | `user=kim src=1.2.3.4 ... login success/fail` |
| 일반 텍스트 | login/logon/로그인/인증 + 성공/실패 키워드가 함께 있는 줄 |

| 위험 표시 | 기준 |
|---|---|
| `FAIL_MANY` | 로그인 실패 5회 이상 |
| `OFF_HOUR` | 22시~06시 로그인 성공 (UTC 표기 `...Z`/`+00:00` 는 한국시간 +9 로 환산) |
| `EXTERNAL_IP` | **[관리 › 내부 IP 설정]의 사용 중인 대역**에 속하지 않는 IPv4 (기본값: 10/8, 172.16/12, 192.168/16, 127/8, 169.254/16, 0/8). IPv6 는 판정하지 않음 |
| `MULTI_IP` | 서로 다른 접속 IP 3개 이상 |
| `PRIV_ACCOUNT` | root, admin, administrator, sa, sys, system, oracle, postgres, dba, superuser, guest |
| `IP_DENIED` | 장비의 **접속 IP 제한**에 걸린 로그인 시도 (`접속 IP 제한`, `IP 차단`, `허용되지 않은 IP` 등) |

**엑셀 업로드**: `.xlsx/.xls`(최대 20MB)는 첫 번째 데이터 시트를 탭 구분 텍스트로 변환해 분석합니다. 앞쪽 빈 행·열(제목 여백)은 건너뛰고, 셀 안 줄바꿈은 ` / ` 로 바꿔 1행 = 1줄을 유지합니다. 원본 엑셀은 그대로 보관(증적)하며, 재분석 시에도 원본에서 다시 변환합니다. 암호가 걸린 엑셀은 거부합니다.

### 시스템별 로그 인식 확인 결과

| 시스템 | 샘플 | 결과 |
|---|---|---|
| DLP | 관리 콘솔 > 관리자 감사 엑셀 (2026년 4월, 9,764행, 사용자가 값을 수정한 테스트본) | 로그인 성공 131 / 실패 57 / IP 제한 19건, 계정 5개 **원본과 100% 일치**. 전용 룰 8개(`04`) 적용 |

### 위험도 기준 (심각 / 높음 / 보통 / 낮음)

위험도는 **탐지 룰마다 관리자가 지정**합니다([관리 › 탐지 룰셋 편집]). 탐지 건은 그 룰의 위험도를 그대로 받고, 탐지 시점의 값을 보관합니다(룰 위험도를 나중에 바꿔도 이미 탐지된 건은 그대로). 업로드의 **최고 위험도**는 그 파일 탐지 건 중 가장 높은 위험도입니다. 접속 행위의 **위험 표시**(로그인 실패 5회↑, 외부 IP 등)는 위험도와 별개입니다.

| 위험도 | 의미 (룰 위험도를 정하는 기준) | 예 (기본 룰) | 권장 대응 |
|---|---|---|---|
| 심각 | 침해가 이미 일어났거나 로그·증적이 훼손될 수 있는 행위 | 로그 삭제·감사 설정 변경, 해킹도구·악성코드 흔적, SQL Injection, 보안그룹 전체 개방 | 당일 확인, 침해사고 대응 절차 검토 |
| 높음 | 공격 시도이거나 계정·권한·데이터에 영향을 주는 중요 변경 | 반복 인증 실패(무차별 대입), 관리자 계정·권한 변경, 허용 외 IP 접속 시도, DB 구조 변경·대량 반출 | 3일 이내 확인, 정상 업무인지 담당자 소명 |
| 보통 | 정상 업무일 수 있지만 기록과 확인이 필요한 행위 | 특권 계정 로그인, 보안 정책 변경, 데이터 다운로드·반출, 취약점 스캐너 접근 | 월 점검 때 검토, 승인·작업 내역과 대조 |
| 낮음 | 참고용 이벤트 (단독으로는 위험이 낮음) | 심야 시간대 접근, 사용자 정보 변경, 웹 서버 오류(5xx) 다발 | 추이 확인, 반복되면 위험도 상향 검토 |

**화면에서 기준 보기**: 위험도 배지·위험도 카드(심각/높음/보통/낮음)·위험도 분포 막대·대시보드 범례·표 머리글의 ⓘ 아이콘에 **마우스를 올리면(또는 키보드로 포커스하면) 위험도 기준 창**이 뜹니다. 창에는 4단계 기준, 지금 가리킨 위험도에 지정된 사용 중 룰 목록(임계 건수·시스템 전용 표시), 위험도 분포 막대는 단계별 건수가 나옵니다. 창 안으로 마우스를 옮겨도 유지되고, 벗어나거나 Esc 를 누르면 닫힙니다. 룰 등록/수정 팝업에서는 고른 위험도의 기준이 선택 상자 바로 아래에 표시됩니다. 기준 문구는 `js/seclog.js` 의 `SEVERITY` 한 곳에서 관리합니다.

### 탐지 엔진 동작 기준

- **KEYWORD**: 쉼표로 구분한 키워드 중 하나라도 포함되면 매칭합니다. 대소문자는 구분하지 않습니다.
- **REGEX**: Java 정규식 `find()`로 매칭합니다. 대소문자는 구분하지 않습니다.
- **임계 건수(THRESHOLD_COUNT)**: 파일 안의 매칭 건수가 임계값 이상일 때만 탐지로 확정합니다. 예: 로그인 실패 10회 이상.
- **안전장치**
  - ReDoS 방지: 정규식 매칭을 라인당 200ms로 제한하고, 매칭에는 라인 앞 4,000자만 사용합니다.
  - 저장 상한: 룰당 최대 1,000건, 업로드당 최대 20,000건까지 저장합니다. 집계 건수는 저장된 건수 기준입니다.
  - 바이너리 파일 차단: 파일 앞부분에 NUL 바이트가 있으면 거부합니다.
  - 경로 조작 방지: 업로드 파일명에서 디렉토리 경로를 제거합니다.
- **스냅샷**: 탐지 결과에는 탐지 시점의 룰 코드, 룰명, 위험도를 저장합니다. 이후 룰을 수정해도 탐지 이력은 바뀌지 않습니다.

## 3. 생성 테이블 목록

| 테이블 | 설명 | 주요 컬럼 | FK (업무 테이블 간) |
|---|---|---|---|
| `sec_log_rule` | 이상징후 탐지 룰셋 (공통 / 시스템 전용) | RULE_CODE(UK), **SYSTEM_ID**(NULL=공통), LOG_TYPE, MATCH_TYPE, PATTERN, SEVERITY, THRESHOLD_COUNT, USE_YN | - |
| `sec_log_upload` | 업로드 이력 + 분석 요약 | TARGET_YM, SYSTEM_NAME, LOG_TYPE, FILE_HASH, 위험도별 건수, **DATE_UNKNOWN_COUNT, OUT_OF_MONTH_COUNT**(로그 일자 점검, `08`), **PII_SHEET_STATUS, PII_SHEET_NAME**(개인정보 다운로드 내역 탭 읽기 결과, `15`), STATUS, REVIEW_COMMENT | - |
| `sec_log_upload_file` | 원본 로그 (GZIP, LONGBLOB) | UPLOAD_ID, FILE_DATA | `FK_SEC_LOG_UPLOAD_FILE_UPLOAD` → sec_log_upload |
| `sec_log_detection` | 탐지 결과 (라인 단위) | UPLOAD_ID, RULE_ID, SEVERITY, LINE_NUMBER, LOG_CONTENT, REVIEW_STATUS, ACTION_CONTENT | `FK_SEC_LOG_DETECTION_UPLOAD`, `FK_SEC_LOG_DETECTION_RULE` |
| `sec_log_system` | 점검 대상 시스템 마스터 | SYSTEM_CODE(UK), SYSTEM_NAME, CATEGORY, DEFAULT_LOG_TYPE, **COMMON_RULE_YN, SYSTEM_RULE_YN**, USE_YN, SORT_ORDER (`INACTIVE_DAYS` 는 사용 안 함, 데이터 보존) | - |
| `sec_log_access_summary` | 접속 행위 분석 (업로드별 계정 집계) | UPLOAD_ID, ACCOUNT_ID, SUCCESS/FAIL/OFF_HOUR/EXTERNAL_IP/IP_COUNT, IP_LIST, FIRST/LAST_SEEN, RISK_FLAGS | `FK_SEC_LOG_ACCESS_UPLOAD` → sec_log_upload |
| `sec_log_account_check` | (사용 안 함) 이전 장기 미접속 계정 점검. `03` 으로 생성, 데이터 보존 | - | `FK_SEC_LOG_ACCOUNT_CHECK_SYSTEM` → sec_log_system |
| `sec_log_account` | (사용 안 함) 이전 장기 미접속 계정 상세. `03` 으로 생성, 데이터 보존 | - | `FK_SEC_LOG_ACCOUNT_CHECK` → sec_log_account_check |
| `sec_log_collector` | (사용 안 함) 이전 API 수집 연결 설정. `05` 로 생성, 데이터 보존 | - | `FK_SEC_LOG_COLLECTOR_SYSTEM` → sec_log_system |
| `sec_log_access_daily` | 업로드별 로그 일자 단위 집계 (대시보드 달력·일별 추이·내보내기) | UPLOAD_ID, SYSTEM_ID, LOG_DATE, LINE_COUNT, LOGIN_SUCCESS/FAIL_COUNT, OFF_HOUR_COUNT, PRIV_LOGIN_COUNT, DETECTION_COUNT | `FK_SEC_LOG_ACCESS_DAILY_UPLOAD` → sec_log_upload |
| `sec_log_collect_history` | (사용 안 함) 이전 수집·수동 업로드 이력. `05` 로 생성, 데이터 보존 | - | `FK_SEC_LOG_COLLECT_HISTORY_SYSTEM` → sec_log_system |
| `sec_log_internal_ip` | 내부 IP 대역 (관리 › 내부 IP 설정, 외부 IP 판정 기준) | IP_RANGE(UK, CIDR), START_IP_NUM, END_IP_NUM, IP_NAME, DESCRIPTION, USE_YN, SORT_ORDER | - |
| `sec_log_rule_run` | **룰별 분석 실행 기록** (룰셋별 분석 결과의 분석 완료·실패·미분석 판정, `14`) — 분석(업로드·재분석) 때 적용한 룰마다 1행(탐지 0건 룰 포함). 행이 없으면 미분석, 재분석·업로드 삭제 시 소프트 삭제 | UPLOAD_ID, RULE_ID, RULE_CODE·RULE_NAME·SEVERITY(분석 시점 사본), RUN_STATUS(DONE/FAILED), MATCH_COUNT, STORED_COUNT, TIMEOUT_LINES, FAIL_REASON | `FK_SEC_LOG_RULE_RUN_UPLOAD` → sec_log_upload |
| `sec_log_pii_download` | **개인정보 다운로드 내역 + 사유 검토** (자사몰 `SVC_MALL`·임직원몰 `SVC_EMP_MALL` 만, `15`) — 올린 엑셀의 '개인정보 다운로드' 탭 1행 = 1건. 재분석·업로드 삭제 시 소프트 삭제 | UPLOAD_ID, SHEET_NAME, ROW_NO, DOWNLOAD_AT(+원문), ADMIN_ID, ACCESS_IP, ACCESS_PAGE, TASK_NAME, FILE_TARGET, FORM_NAME, DOWNLOAD_REASON, RAW_CONTENT, REVIEW_STATUS(PENDING/NORMAL/CONFIRMED/RESOLVED), REVIEW_COMMENT, REVIEWED_AT/BY | `FK_SEC_LOG_PII_DL_UPLOAD` → sec_log_upload |
| `sec_log_account_review` | **접속 계정 검토** (접속 행위 분석의 계정별 소명·조치, `12`) — 업로드 1건 x 계정 1개당 사용 중인 행 1개. 행이 없으면 미검토, 미검토로 되돌리면 소프트 삭제 | UPLOAD_ID, ACCOUNT_ID, REVIEW_STATUS(NORMAL/CONFIRMED/RESOLVED), REVIEW_COMMENT, RISK_FLAGS(검토 당시 사본), REVIEWED_AT, REVIEWED_BY | `FK_SEC_LOG_ACC_REVIEW_UPLOAD` → sec_log_upload |
| `sec_log_dlp_rule` | (사용 안 함) 이전 DLP 모니터링 룰셋. 룰셋 삭제로 앱에서 쓰지 않음, 이전에 `16` 으로 만든 환경은 데이터 보존(DROP 안 함). 새로 설치하면 만들어지지 않음 | - | - |
| `sec_log_dlp_leak_upload` | **DLP 유출 로그 업로드 + 분석 요약** (DLP 모니터링, `16`) — 월 1회 DLP 관리시스템에서 받은 유출 로그 파일 1개 = 1행. 같은 달은 가장 최근 파일만 분석·보고서 | TARGET_YM, ORIGINAL_FILE_NAME, FILE_HASH, SHEET_NAME, HEADER_ROW, EVENT_COUNT, MATCHED_EVENT_COUNT, USER_COUNT, PERIOD_FROM/TO, DATE_UNKNOWN_COUNT, OUT_OF_MONTH_COUNT, APPLIED_RULE_COUNT, ANALYSIS_NOTE, ANALYZED_AT, REMARK | - |
| `sec_log_dlp_leak_file` | DLP 유출 로그 원본 (GZIP, LONGBLOB, 재분석용) | LEAK_UPLOAD_ID, COMPRESS_TYPE, COMPRESSED_SIZE, FILE_DATA | `FK_SEC_LOG_DLP_LEAK_FILE_UPLOAD` → sec_log_dlp_leak_upload |
| `sec_log_dlp_leak_event` | DLP 유출 로그 1건 + DLP 룰 적용 결과 + **AI_SERVICE**(생성형 AI 서비스, `17`) — '… 샘플 데이터' 열(실제 개인정보 값)은 저장 안 함. 재분석·삭제 시 소프트 삭제 | LEAK_UPLOAD_ID, ROW_NO, OCCURRED_AT(+원문), ACTION_NAME, DEPT_NAME, USER_NAME, USER_ID, AGENT_IP, CHANNEL, SUB_CHANNEL, FILE_SIZE_TEXT, FILE_LIST, CONTENT, DETAIL, POLICY_NAME, EVENT_STATUS, MEDIA_REGISTERED, COMPUTER_NAME, PII_TOTAL, PII_DETAIL(개수만), MATCHED_RULES, MAX_SEVERITY, ANALYSIS_LINE | `FK_SEC_LOG_DLP_LEAK_EVENT_UPLOAD` → sec_log_dlp_leak_upload |
| `sec_log_dlp_report` | **DLP 보고서 표지 정보** (`17`, 월별 1행) — 다음 달에 값이 없으면 이전 달 값 이어서 표시 | TARGET_YM(UK), REPORT_TYPE, TARGET_SYSTEM, WRITER_DEPT, COMPANY_KEYWORDS(보안 위험도 '상' 제안용) | - |
| `sec_log_dlp_report_item` | **DLP 보고서 항목별 입력값** (`17`) — 승인여부·검토 결과(USB_UNREG) / 사전승인·사유(KAKAO_USER) / 부서 인원(DEPT_HEADCOUNT, 다음 달에도 이어서 사용) / 검토내용(PI_REINSTALL) / 조치 구분·내용(PI_OFFLINE) / 보안 위험도(AI_USER) | TARGET_YM, SECTION, ITEM_KEY (UK 3개), VALUE1, VALUE2 | - |
| `sec_log_dlp_privacyi_row` | **Privacy-I 재설치·장기 미접속 목록** (`17`, 올린 파일 1행 = 1명, 다시 올리면 이전 행 소프트 삭제) | TARGET_YM, KIND(REINSTALL/OFFLINE), FILE_NAME, ROW_NO, DEPT_NAME, USER_NAME, USER_ID, EVENT_AT(+원문), OFFLINE_DAYS | - |
| `sec_log_dlp_kakao_approval` | **PC 카카오톡 사용 승인 대장** (`18`, 전자결재 승인 건 · 기존 엑셀 대장 대체). 신청 상태는 '신청중' 만. 상태(사용 중·7일 내 만료·만료·시작 전)·최신여부는 저장하지 않고 날짜로 계산. 보고서 02장·카카오톡 사용자 현황의 사전승인을 이 대장으로 자동 판정(부서 + 이름) | DIVISION_NAME, DEPT_NAME, USER_NAME, START_DATE, END_DATE, APPLY_STATUS, APPROVAL_DOC_NO, REASON | - |
| `sec_log_dlp_mail_rule` | **메일 알림 규칙** (`19`, DLP 관리 > 메일 알림 관리, 여러 개). 카카오톡 승인 대장 신청 종료일 + N일(음수 = 종료일 전)에 매일 정한 시각에 받는 사람에게 메일. 제목·본문은 자리표시({부서} {이름} 등)로 작성. 기본 규칙 1개(`RULE_KEY`=KAKAO_EXPIRE) | RULE_KEY(UK, 기본 규칙만), RULE_NAME, OFFSET_DAYS, SEND_TIME, TO_ADDRS, CC_ADDRS, SUBJECT_TPL, BODY_TPL, USE_YN, START_DATE, LAST_RUN_AT, LAST_RUN_RESULT | - |
| `sec_log_dlp_mail_send_log` | **메일 알림 보낸 기록** (`19`, 규칙 1개 x 승인 건 1개당 1행, 신청 종료일이 같아 메일 1통에 묶인 사람은 같은 제목·본문). SENT = 보냄(다시 안 보냄), FAIL = 실패(다음 실행 때 같은 행으로 다시 시도) | MAIL_RULE_ID + KAKAO_APPROVAL_ID(UK), DEPT_NAME, USER_NAME, END_DATE, TO_ADDRS, CC_ADDRS, SUBJECT, BODY, SEND_STATUS, TRY_COUNT, SENT_AT, LAST_TRY_AT, ERROR_MESSAGE | `FK_SEC_LOG_DLP_MAIL_SEND_RULE` → sec_log_dlp_mail_rule, `FK_SEC_LOG_DLP_MAIL_SEND_APPR` → sec_log_dlp_kakao_approval |
| `sec_log_dlp_org` | **사업부·부서 목록** (`20`, 카카오톡 사용 승인 대장 등록·수정 드롭다운). 사업부 = PARENT_ORG_ID 0, 부서 = 그 사업부 ID. 삭제 대신 USE_YN = 'N'(드롭다운에서만 빠짐, 기존 대장 값 그대로). 처음 목록 = 적용 시점 승인 대장의 사업부·부서 | ORG_ID, PARENT_ORG_ID, ORG_NAME, USE_YN, UK(PARENT_ORG_ID, ORG_NAME) | 없음 |

- `sec_log_upload` 에는 `SYSTEM_ID`(NULL 허용) 컬럼이 추가됩니다. `03` 스크립트의 `ADD COLUMN IF NOT EXISTS` 로 추가하며, 기존 데이터는 NULL(직접 입력 시스템)로 남습니다.

- 모든 테이블에 공통 컬럼(CREATED_AT/BY, UPDATED_AT/BY, DELETED_YN/AT/BY)이 있습니다.
- core 테이블(core_user 등)에는 FK가 없고, 사용자 ID 값만 저장합니다.
- 원본 파일(LONGBLOB)은 목록 조회 성능을 위해 별도 테이블로 분리했습니다.

### 코드값

| 구분 | 값 |
|---|---|
| LOG_TYPE | `ALL`(룰 전용), `LINUX`, `WINDOWS`, `WEB`, `WAS`, `DB`, `NETWORK`, `APP`, `SECURITY`(정보보호솔루션), `CLOUD`(클라우드 콘솔), `ETC` |
| MATCH_TYPE | `KEYWORD`, `REGEX` |
| SEVERITY | `LOW`(낮음), `MEDIUM`(보통), `HIGH`(높음), `CRITICAL`(심각) |
| 업로드 STATUS | `ANALYZED`(분석완료/검토대기), `REVIEWED`(검토완료) |
| REVIEW_STATUS | `PENDING`(미검토), `CONFIRMED`(이상징후 확인), `RESOLVED`(조치완료), `FALSE_POSITIVE`(오탐) |

## 4. API 목록

모든 응답은 `ApiResponse<T>`로 감싸서 반환합니다. 페이징 응답은 `Page<T>`입니다.

### 4-1. 룰셋 편집 (`/security_log-api/rules`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/rules?q=&logType=&useYn=&scope=&systemId=&page=0&size=50` | 룰 목록. `scope`: COMMON(공통)/SYSTEM(시스템 전용), `systemId`: 그 시스템 전용 룰 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/rules/{ruleId}` | 룰 상세 | ADMIN, MANAGER, USER |
| POST | `/security_log-api/rules` | 룰 등록. `systemId` 를 넣으면 시스템 전용 룰, 비우면 공통 룰 | ADMIN |
| PUT | `/security_log-api/rules/{ruleId}` | 룰 수정 (RULE_CODE 변경 불가) | ADMIN |
| PATCH | `/security_log-api/rules/{ruleId}/use-yn` | 사용여부 변경 `{ "useYn": "N" }` | ADMIN |
| POST | `/security_log-api/rules/test` | 패턴 사전 테스트 (저장 안 함) | ADMIN |
| DELETE | `/security_log-api/rules/{ruleId}` | 룰 삭제 (소프트) | ADMIN |

### 4-2. 로그 업로드 / 모니터링 (`/security_log-api/uploads`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/uploads?targetYm=202610&systemName=&logType=&status=&maxSeverity=&sort=LATEST&activeOnly=false&latestOnly=false&page=0&size=50` | **목록 화면 (월별 조회)** — `sort` = `LATEST`(최신 업로드 순, 기본) / `SEVERITY`(최고 위험도 순) / `PENDING`(미검토 많은 순). `activeOnly=true` 면 [점검 대상 시스템]에서 **사용**으로 표시한 시스템의 업로드만 (사용 안 함 시스템·직접 입력 업로드 제외, 기본 false). `latestOnly=true` 면 **시스템마다 가장 최근에 올린 파일 1개만**(분석 대상). 각 행에 `olderFileCount`(같은 달·같은 시스템의 이전 파일 수). 업로드 상세(`GET /uploads/{id}`)에는 `latest`(이 파일이 분석 대상인지)와 `latestUploadId` 가 붙음 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/uploads/summary?targetYm=202610` | 월별 모니터링 요약 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/uploads/{uploadId}` | **상세 화면** (요약 + 룰별 집계 + 검토현황) | ADMIN, MANAGER, USER |
| POST | `/security_log-api/uploads` (multipart) | **등록 화면** — 업로드 + 즉시 분석 | ADMIN, MANAGER |
| PUT | `/security_log-api/uploads/{uploadId}` | **수정 화면** — 기본정보 수정 (옵션: 재분석) | ADMIN, MANAGER |
| POST | `/security_log-api/uploads/{uploadId}/reanalyze` | 현재 룰셋으로 재분석 (접속 행위 분석도 다시 집계) | ADMIN, MANAGER |
| GET | `/security_log-api/uploads/{uploadId}/access` | **접속 행위 분석** — 계정별 성공/실패/업무시간 외/외부 IP/위험 표시 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/uploads/{uploadId}/file` | 원본 로그 (Base64) | ADMIN, MANAGER, USER |
| POST | `/security_log-api/uploads/{uploadId}/review` | 최종 검토완료 `{ "reviewComment": "..." }` | ADMIN |
| POST | `/security_log-api/uploads/{uploadId}/review-cancel` | 최종 검토완료 취소 | ADMIN |
| DELETE | `/security_log-api/uploads/{uploadId}` | 삭제 (업로드 + 원본 + 탐지결과 소프트 삭제) | ADMIN |

- 목록 응답의 각 업로드에는 검토 진행(`pendingCount / confirmedCount / resolvedCount / falsePositiveCount`)과 로그 기간 확인(`firstLogDate`, `lastLogDate`, `periodStatus` = `OK` / `CHECK` / `UNCHECKED`, `periodReasons`)이 함께 옵니다. 기간 확인 기준은 대시보드 '로그 업로드 현황'과 같습니다(`LogPeriodCheck` 공용): 월초 3일 넘게 지나서 시작 · 월말 3일 넘게 남기고 끝남(지난 달만) · 일시 인식 안 된 줄 10% 이상 · 대상 월 밖 줄 1줄 이상.
- 정렬 동점 처리: `SEVERITY` 는 위험도 → 미검토 많은 순 → 최신 순, `PENDING` 은 미검토 → 위험도 → 최신 순. 그 외 `sort` 값은 400 "정렬은 LATEST, SEVERITY, PENDING 중 하나입니다."

**업로드 요청 예시 (multipart/form-data)**

| 파트 | 필수 | 예시 |
|---|---|---|
| `file` | O | `web01_secure_202610.log` |
| `targetYm` | O | `202610` |
| `systemId` | △ | `7` (점검 대상 시스템. `systemId` 와 `systemName` 중 하나 필수, `systemId` 우선) |
| `systemName` | △ | `그룹웨어` (시스템 목록에 없는 경우 직접 입력) |
| `logType` | O | `LINUX` (`ALL`은 사용 불가) |
| `fileCharset` | - | `UTF-8` (기본값) / `EUC-KR` / `MS949` |
| `remark` | - | `10월 정기 점검분` |

### 4-2-1. 접속 계정 검토 (접속 행위 분석)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/uploads/{uploadId}/access` | 접속 행위 분석. 계정마다 `reviewStatus`(NORMAL/CONFIRMED/RESOLVED, 미검토면 null)·`reviewStatusLabel`·`reviewComment`·`reviewedAt`·`reviewedBy`, 전체에 `riskReviewedCount`·`riskPendingCount`(검토 남은 위험 계정)·`otherReviewedCount` 가 붙음 | ADMIN, MANAGER, USER |
| PUT | `/security_log-api/uploads/{uploadId}/access/review` | **선택한 계정 일괄 검토** (최대 1,000개). 본문 `{ "accountIds": ["hana1"], "reviewStatus": "NORMAL", "reviewComment": "..." }`. `reviewStatus` = `NORMAL`(정상 확인) / `CONFIRMED`(이상 확인, 조치 필요) / `RESOLVED`(조치완료) / `PENDING`(미검토로 되돌리기). CONFIRMED·RESOLVED 는 `reviewComment` 필수. 그 로그의 접속 계정이 아니면 400, 검토완료된 업로드는 400. 응답 `{ "uploadId", "updatedCount", "riskPendingCount" }` | ADMIN, MANAGER (모든 시스템, 매핑안 A) |
| GET | `/security_log-api/uploads/{uploadId}/pii-downloads` | **개인정보 다운로드 내역 + 사유 검토 현황** (자사몰·임직원몰만 `target=true`, 그 외 `target=false`·빈 목록). 응답 `target`, `ruleCode`(연동 룰: 자사몰 MALL_MEMBER_EXPORT · 임직원몰 EMALL_MEMBER_EXPORT), `sheetStatus`(FOUND 탭 있음 / NOT_FOUND 탭 없음 / null 기능 전 업로드), `sheetName`, `locked`(최종 검토완료), `totalCount/pendingCount/normalCount/confirmedCount/resolvedCount`, `items`(행 번호·다운로드 일시(+원문)·운영자 ID·접속 IP·접속 페이지·수행 업무·파일 대상·다운로드 양식명·원본 사유·검토 상태·사유·검토 일시·검토자 ID) | ADMIN, MANAGER, USER |
| PUT | `/security_log-api/uploads/{uploadId}/pii-downloads/review` | **선택한 다운로드 건 일괄 사유 검토** (최대 1,000건). 본문 `{ "piiDownloadIds": [2], "reviewStatus": "NORMAL", "reviewComment": "10월 정산 업무" }`. `reviewStatus` = `NORMAL`(정상 업무, 사유 확인) / `CONFIRMED`(사유 불명확·조치 필요) / `RESOLVED`(조치완료) / `PENDING`(미검토로 되돌리기). PENDING 외 `reviewComment` 필수. 대상 시스템이 아니면 400, 그 업로드의 다운로드 건이 아니면 400, 검토완료된 업로드는 400. 응답 `{ "uploadId", "updatedCount", "pendingCount" }` | ADMIN, MANAGER |
| GET | `/security_log-api/uploads/{uploadId}/dlp-report` | **DLP 관리자 감사 로그 점검 보고서** (탐지 룰셋 기반, DB 저장 없음 — 저장된 탐지 결과 + 보관된 원본 파일로 매번 계산). DLP 시스템(시스템 코드·이름에 DLP) + 머리글 '발생 일시·아이디·유형·내용' 형식일 때만 `applicable=true`, 아니면 `applicable=false` + `reason`. 판정 기준은 [관리 › 탐지 룰셋 편집]의 적용 룰뿐(별도 판정 기준 없음). 탐지 결과를 **룰 × 계정**으로 묶어 항목 1개로 만들고, 위험도 = 룰 위험도(심각·높음 → `HIGH`, 보통 → `MEDIUM`, 낮음 → `LOW`). 응답: `highCount/mediumCount/lowCount`(항목 수), `detectionTotal/detectionPending`, `totalRows`, `accountCount`, 로그 기간, `summaryLines` · `items`(번호·`itemKey`=룰 코드\|계정·`severity`·`ruleCode/ruleName/ruleDescription`·계정·이름·`title/content/what`·횟수·근거 `evidenceRows` 최대 200개 + `evidenceTotal`·**`detectionIds`**·`pendingCount`·검토 요약 `reviewStatus`(null/PARTIAL/CONFIRMED/RESOLVED/FALSE_POSITIVE/MIXED)·`reviewStatusLabel/reviewComment/reviewedAt/reviewedBy`) · `rules`(적용 룰 전체 0건 포함: 위험도·공통/전용·임계·탐지·계정·미검토) · `accounts`(계정별 활동 건수·🔴🟡🟢·한 줄 요약) · `limits`(분석 한계, 마지막 분석 이후 바뀐 룰 수 포함) · `suspiciousNote` · `highReviewed/mediumReviewed/lowReviewed`(미검토 탐지 0건인 항목 수)·`reviewLocked` | ADMIN, MANAGER, USER |
| GET | `/security_log-api/uploads/{uploadId}/dlp-report/rows?rows=5807,5689` | 점검 보고서 **근거 로그 원문** (원본 엑셀 행 번호 / 텍스트 줄 번호, 한 번에 최대 200개). 응답 `[{ row, time, account, name, ip, type, content, detail }]`. 없는 번호는 빠짐. DLP 로그가 아니면 400 | ADMIN, MANAGER, USER |

- **위험 표시 계정은 모두 검토해야** 월별 점검 현황 '② 분석 결과 검토'가 완료로 바뀌고, 최종 검토완료(`POST /uploads/{id}/review`)도 됩니다(남아 있으면 400 "검토하지 않은 위험 접속 계정이 N개 남아 있어 …"). 위험 표시 없는 계정은 선택 기록.
- 월별 점검 현황 `GET /monthly-status` 의 시스템 행에 `accessRiskPending`, 대시보드 `GET /dashboard` 에 `riskPendingCount` 와 할 일 `PENDING_ACCOUNT`("위험 접속 계정 N개 검토 필요")가 붙습니다(최신 파일 기준). `READY_TO_CLOSE` 는 미검토 탐지 0 + 미검토 위험 계정 0 일 때만 나옵니다.
- 재분석하면 그 업로드의 계정 검토도 초기화됩니다(탐지 검토와 같은 방식). 업로드 삭제 시 함께 소프트 삭제.
- **개인정보 다운로드 (자사몰·임직원몰)**: 미검토 다운로드가 남아 있으면 최종 검토완료 400 "검토하지 않은 개인정보 다운로드가 N건 남아 있어 …". 월별 점검 현황 시스템 행에 `piiTarget`·`piiSheetStatus`·`piiDownloadCount`·`piiPending`(최신 파일 기준), 대시보드 할 일 `PENDING_PII`("개인정보 다운로드 N건 사유 확인 필요")·`NO_PII_SHEET`(올린 파일에 탭 없음, 참고), `READY_TO_CLOSE` 는 `piiPending` 0 일 때만. 재분석하면 탭을 다시 읽고 검토는 초기화.

### 4-3. 탐지 결과 검토

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/uploads/{uploadId}/detections?severity=&reviewStatus=&ruleCode=&page=0&size=50` | 탐지 목록 (`ruleCode` 로 룰별 로그 확인) | ADMIN, MANAGER, USER |
| GET | `/security_log-api/detections/{detectionId}` | 탐지 상세 | ADMIN, MANAGER, USER |
| PUT | `/security_log-api/uploads/{uploadId}/detections/review` | 단건/일괄 검토 (최대 1,000건) | ADMIN, MANAGER |
| PUT | `/security_log-api/uploads/{uploadId}/detections/review-by-rule` | **룰 일괄 검토** — 그 업로드에서 같은 룰로 탐지된 건을 한 번에 검토 (건수 제한 없음). 응답 `{ "ruleCode", "updatedCount" }` | ADMIN, MANAGER |

```json
{ "detectionIds": [101, 102, 103], "reviewStatus": "FALSE_POSITIVE", "actionContent": "정기 배치 계정의 정상 접속" }
```

```json
{ "ruleCode": "COM_AUTH_FAIL_BRUTE", "reviewStatus": "FALSE_POSITIVE", "actionContent": "사용자 비밀번호 오입력으로 확인, 조치 불필요", "onlyPending": true }
```

- `onlyPending` 생략 시 `true`(미검토 건만). `false` 면 이미 검토한 건도 덮어씁니다.
- 업로드 상세(`GET /uploads/{id}`)의 `ruleHits` 에 룰별 `pendingCount / confirmedCount / resolvedCount / falsePositiveCount` 가 함께 옵니다.
- `ruleHits` 에는 지금 적용되는 활성 룰 중 탐지 0건인 룰도 `hitCount: 0` 으로 포함되며, `scope`(COMMON/SYSTEM), `thresholdCount` 가 함께 옵니다. 룰셋을 바꾼 뒤 재분석하지 않은 업로드는 '지금 룰셋' 기준으로 0건 룰이 표시됩니다.

### 4-3-1. 점검 대상 시스템 / 월별 점검 현황 (`/security_log-api`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/systems?activeOnly=true` | 시스템 목록 (`activeOnly=false` 면 미사용 포함) | ADMIN, MANAGER, USER |
| POST | `/security_log-api/systems` | 시스템 등록 | ADMIN |
| PUT | `/security_log-api/systems/{systemId}` | 시스템 수정 (코드 변경 불가) | ADMIN |
| PATCH | `/security_log-api/systems/{systemId}/use-yn` | 사용/미사용 토글 `{ "useYn": "N" }`. 미사용 시스템은 월별 점검 현황·대시보드·룰셋 현황에서 빠짐 (이력 유지) | ADMIN |
| DELETE | `/security_log-api/systems/{systemId}` | 시스템 소프트 삭제 (기존 점검 이력 유지) | ADMIN |
| GET | `/security_log-api/monthly-status?targetYm=202610` | **월별 점검 현황** — 시스템별 접속·행위 로그 등록/검토 상태 | ADMIN, MANAGER, USER |

### 4-3-1-1. 대시보드 (`/security_log-api/dashboard`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/security_log-api/dashboard?targetYm=202610` | 대시보드 한 번에 조회 (마감 진행률·D-day, 확인 필요 항목, 미검토 경과, 일별 수집 현황, 위험 계정 TOP 10, 특권 계정, 일별 추이, 상위 룰, **로그 업로드 현황 `uploadSummary` / 시스템별 `uploadRows`**). `targetYm` 생략 시 이번 달 | ADMIN, MANAGER, USER |
| GET | `/security_log-api/dashboard/systems/{systemId}?targetYm=202610` | 시스템 상세: 수집 달력, 그 달 업로드 파일, 계정별 접속 행위(합산) | ADMIN, MANAGER, USER |
| GET | `/security_log-api/dashboard/export?targetYm=202610` | 월 점검 결과 엑셀 (Base64, 원본 파일 다운로드와 같은 방식) | ADMIN, MANAGER, USER |
| POST | `/security_log-api/dashboard/rebuild-daily?targetYm=202610` | **로그 일자 점검 다시 하기** — 일자 점검 전(`DATE_UNKNOWN_COUNT` NULL) 업로드의 일별 집계와 일자 점검(일시 인식 안 된 줄, 대상 월 밖 줄)을 원본에서 다시 함. 탐지·검토 결과는 바꾸지 않음. 응답 `{ "rebuilt": N }` | ADMIN |

### 4-3-1-2. 시스템별 룰셋 (`/security_log-api/rules`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/rules?scope=COMMON\|SYSTEM&systemId=` | 룰 목록 (공통/시스템 전용 필터) | ADMIN, MANAGER, USER |
| GET | `/rules/rulesets` | 시스템별 공통·전용 룰셋 사용 여부, 룰 수 | ADMIN, MANAGER, USER |
| PATCH | `/rules/rulesets/{systemId}` | `commonRuleYn` / `systemRuleYn` 변경 | ADMIN |
| POST/PUT | `/rules` | `systemId` 를 넣으면 시스템 전용 룰, 비우면 공통 룰 | ADMIN |

### 4-3-1-3. 관리 › 내부 IP 설정 (`/security_log-api/internal-ips`)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/internal-ips` | 내부 IP 대역 목록 (시작·끝 IP, IP 수 포함) | ADMIN, MANAGER, USER |
| POST | `/internal-ips/test` | IP 확인 `{ "ip": "192.1.2.88" }` → 내부 여부, 속한 대역 (저장 안 함) | ADMIN, MANAGER, USER |
| POST | `/internal-ips` | 등록 `{ "ipRange": "192.1.2.0/24", "ipName": "본사 공인 대역", "description": "", "useYn": "Y", "sortOrder": 70 }` | ADMIN |
| PUT | `/internal-ips/{id}` | 수정 | ADMIN |
| PATCH | `/internal-ips/{id}/use-yn` | 사용/미사용 `{ "useYn": "N" }` | ADMIN |
| DELETE | `/internal-ips/{id}` | 소프트 삭제 | ADMIN |

### 4-3-1-4. DLP 모니터링 — 유출 로그 · 보고서 (`/security_log-api/dlp-leak`, ISMS 로그 점검과 별도)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/dlp-leak/monthly?targetYm=202609` | 그 달 올린 유출 로그 파일 목록 (`upload` = 보고서 기준 = 가장 최근 파일, `files` = 최근 순) | ADMIN, MANAGER, USER |
| POST | `/dlp-leak/uploads` | ① 유출 로그 올리기 (multipart: `file`, `targetYm`, `remark`) → 바로 읽어서 저장(생성형 AI 판정 포함). 보고서 작성·카카오톡 사용자 현황이 이 로그로 계산 | ADMIN, MANAGER |
| GET | `/dlp-leak/kakao-approvals` | **카카오톡 승인 대장** 목록 (오늘 기준 상태 `state`·`stateLabel`·남은 날 `daysLeft`·최신여부 `latestYn` 계산 + 상태별 건수) | ADMIN, MANAGER, USER |
| POST | `/dlp-leak/kakao-approvals` | 대장 등록 `{ "divisionName": "PS 사업부", "deptName": "SCM팀(PS)", "userName": "이성이", "startDate": "2026-11-05", "endDate": "2027-02-02", "approvalDocNo": "EA-…", "reason": "…" }` (신청 상태 = 신청중) | ADMIN, MANAGER |
| PUT | `/dlp-leak/kakao-approvals/{id}` | 대장 수정 | ADMIN, MANAGER |
| DELETE | `/dlp-leak/kakao-approvals/{id}` | 대장 소프트 삭제 | ADMIN |
| GET | `/dlp-leak/kakao-approvals/export?state=&latestOnly=false&q=` | 승인 대장 **엑셀 내려받기** (Base64 `{ fileName, fileSize, contentBase64 }`, 화면과 같은 거르기: state = ACTIVE/EXPIRING/EXPIRED/BEFORE, latestOnly = 최신 건만, q = 사업부·부서·이름·문서번호 검색, 정렬 = 신청 종료일 늦은 순). 시트 1개: 제목 · 조건 한 줄(오늘 기준·거르기·건수) · 머리글 사업부/부서/이름/신청 시작일/신청 종료일/신청 상태/최신여부/상태/남은 일수/전자결재 문서번호/사유 (머리글 고정·자동 거르기). ~~엑셀 대장 올리기 `POST /kakao-approvals/upload`~~ 는 삭제 (시스템 화면으로만 관리) | ADMIN, MANAGER, USER |
| GET | `/dlp-leak/mail-rules` | **메일 알림 규칙** 목록 (메일 서버 설정 여부 `smtpReady`·보내는 주소, 자리표시 안내 `placeholders`, 규칙별 보내는 날 `offsetText`·보내는 시각·받는 사람·참조·제목·본문·사용 여부·마지막 실행·보낼 대기·보냄·실패 수) | ADMIN, MANAGER, USER |
| POST | `/dlp-leak/mail-rules` | 규칙 추가 `{ "ruleName": "…", "offsetDays": 1, "sendTime": "11:00", "toAddrs": "a@x.com, b@x.com", "ccAddrs": "", "subjectTpl": "[DLP] … {부서} {이름}", "bodyTpl": "…", "useYn": "Y" }` (일수 -90~90, 주소 최대 20개, 규칙 최대 20개, 보내기 시작일 = 만든 날, **받는 사람·참조 = 회사 메일(`SECLOG_MAIL_ALLOWED_DOMAINS`)만 — 회사 밖 주소 400**, 받는 사람이 비어 있으면 `useYn: "N"` 으로만 저장 가능) | ADMIN |
| PUT | `/dlp-leak/mail-rules/{id}` | 규칙 수정 (보내는 시각을 바꾸면 서버 재시작 없이 바로 적용) | ADMIN |
| DELETE | `/dlp-leak/mail-rules/{id}` | 규칙 소프트 삭제 (보낸 기록은 남김) | ADMIN |
| POST | `/dlp-leak/mail-rules/{id}/run` | [지금 보내기] — 보내는 날이 된 사람 중 아직 안 보낸 사람에게 바로 보냄(사용 안 함이어도 실행). **신청 종료일이 같은 사람은 메일 1통**. 응답 `{ result }` (예: '보냄 2통 (4명)') | ADMIN |
| GET | `/dlp-leak/mail-logs?ruleId=` | 보낸 기록 (최근 300건, 규칙으로 거르기) | ADMIN, MANAGER, USER |
| GET | `/dlp-leak/orgs` | 사업부·부서 목록 `[ { orgId, orgName, useYn, usedCount(승인 대장 건수), depts: [ { orgId, orgName, useYn, usedCount } ] } ]` (사용 안 함 포함, 이름순) | ADMIN, MANAGER, USER |
| POST | `/dlp-leak/orgs` | 사업부 추가 `{ "orgName": "영업사업부" }` / 부서 추가 `{ "parentOrgId": 1, "orgName": "영업팀" }` (같은 사업부 안 이름 중복 = 409) | ADMIN |
| PUT | `/dlp-leak/orgs/{id}` | 이름·사용 여부 수정 `{ "orgName": "…", "useYn": "Y\|N" }` (이미 등록된 대장 값은 바꾸지 않음) | ADMIN |
| GET | `/dlp-leak/report?targetYm=202610` | ③ **DLP 모니터링 보고서 PPT** (Base64, 16:9, 흰 바탕 디자인, **8장**: 표지 / 목차(쪽 번호 자동) / 01 Removable Drive / 02 PC 카카오톡 / 03 Privacy-I 재설치 / 04 Privacy-I 장기 미접속 / 05 생성형 AI 사용현황 / 06 생성형 AI 업로드 파일 내역). 목록이 넘치면 '(계속)' 장 추가. 저장하지 않고 내려받을 때마다 생성. 글꼴 맑은 고딕(동아시아 글꼴 지정) | ADMIN, MANAGER, USER |
| GET | `/dlp-leak/uploads/{id}/file` | 원본 파일 내려받기 | ADMIN, MANAGER, USER |
| DELETE | `/dlp-leak/uploads/{id}` | 파일·읽은 로그 소프트 삭제 | ADMIN |

### 4-3-1-4-1. DLP 보고서 작성 (`/security_log-api/dlp-leak`, 회사 보고서 양식 8장)

| Method | URL | 설명 | 권한 |
|---|---|---|---|
| GET | `/dlp-leak/report-data?targetYm=202610` | 보고서 자료: 표지(보고 기간 자동) / 01 외장하드·USB(차단·등록·미등록 건수, 미등록 인원) / 02 카카오톡(부서별 사용 인원·부서 인원, 사용자·사전승인) / 03·04 Privacy-I 재설치·장기 미접속(목록 + 7일 이상·조치완료·휴직 및 퇴사·미조치 및 보류) / 05 생성형 AI 서비스별·부서별(상위 10) / 06 업로드 상위 10명(보안 위험도: 입력값 또는 자동 제안 + 이유) | ADMIN, MANAGER, USER |
| PUT | `/dlp-leak/report-data?targetYm=202610` | 입력값 저장 `{ "cover": { "reportType", "targetSystem", "writerDept", "companyKeywords" }, "items": [{ "section": "USB_UNREG", "itemKey": "cspark", "value1": "미승인", "value2": "검토 결과" }] }` → 저장 후 자료 반환 | ADMIN, MANAGER |
| POST | `/dlp-leak/privacyi?targetYm=202610&kind=AGENT\|REINSTALL\|OFFLINE` | **kind=AGENT (DLP 모니터링 ② Privacy-I Agent 현황 올리기)**: Agent List 파일 1개로 재설치·장기 미접속을 함께 채움 — **재설치 = '설치 일시'가 모니터링 월인 사람**(다른 달·설치 일시 없음 제외), 장기 미접속 = 미접속일 7일 이상. 파일 정보는 `sec_log_dlp_report_item`(SECTION `PI_AGENT`, ITEM_KEY `FILE`, VALUE1 = 읽은 인원, VALUE2 = 파일 이름)에 저장. 응답 `{ targetYm, kind, fileName, totalCount, reinstallCount, reinstallOtherMonth, reinstallNoDate, offlineCount, offlineShort, offlineNoDate }`. kind=REINSTALL/OFFLINE: Privacy-I 목록 파일 올리기 (multipart `file`, xlsx·xls·csv, 20MB·2만 행). Privacy-I 'Agent List' 엑셀 그대로 올림 — 앞 300행에서 '사용자 이름'(이름·성명) 머리글을 찾음(위쪽 부서별 요약 표 건너뜀), 부서·아이디·설치 일시/최종 오프라인 일시·미접속일 열 인식(미접속일 없으면 월말 기준 계산). **장기 미접속(OFFLINE)은 미접속일 7일 이상만 저장**. 같은 달·같은 종류는 교체. 응답 `{ rowCount(저장), totalCount(읽은 인원), shortCount(7일 미만 제외), unknownCount(미접속일 없음 제외) }` | ADMIN, MANAGER |

- 값 검사: 승인여부·사전승인 = 승인/미승인, 조치 구분 = 조치완료/휴직 및 퇴사/미조치 및 보류, 보안 위험도 = 상/중/하, 부서 인원 = 숫자 (그 외 400)
- 생성형 AI 판정(`GenAiCatalog`, 79종): 상세 내용·내용·서브 채널의 접속 주소(도메인, 하위 도메인 포함)를 주요 생성형 AI 목록과 대조(ChatGPT·Gemini·Claude·Microsoft Copilot·GitHub Copilot·Perplexity·DeepSeek·Grok·뤼튼·CLOVA X·Papago·DeepL·Midjourney 등) → 없으면 원본 'AI' 열 값(빈 값·기타·없음 제외)
- 보안 위험도 자동 제안(사용자 기준): **상** = 파일 이름에 회사 식별 단어(표지 '회사 식별 단어') / **중** = 업무 문서 확장자(xlsx·docx·pptx·pdf·hwp·zip·sql 등) 또는 업무 단어(보고·매출·견적·계약·회의 등) / **하** = 그 외(사진 등). 담당자가 파일 내용을 보고 직접 고쳐 저장하면 그 값 사용

### 4-3-1-5. ~~DLP 룰셋~~ (삭제)

DLP 룰셋(`/security_log-api/dlp-rules` 6개)은 삭제했습니다. 보고서는 고정 기준(외장하드/USB · 카카오톡 · Privacy-I 목록 · 생성형 AI)으로만 만들고, 룰 기반 점검은 ISMS 로그 점검의 [관리 › 탐지 룰셋 편집]에서 진행합니다.

### 4-4. 업무 규칙 (BusinessException 발생 조건)

| 조건 | 결과 |
|---|---|
| 같은 월·시스템에 같은 해시의 파일을 다시 업로드 | `DUPLICATE_RESOURCE` (409) "동일 월/시스템에 같은 내용의 로그 파일이 이미 업로드되어 있습니다." |
| 허용되지 않은 확장자, 50MB 초과, 바이너리 파일 | `INVALID_INPUT_VALUE` (400) + 사유 메시지 |
| 정규식 문법 오류 / 빈 키워드 | `INVALID_INPUT_VALUE` (400) "정규식 문법 오류: ..." |
| RULE_CODE 중복 (삭제된 코드 재사용 포함) | `DUPLICATE_RESOURCE` (409) |
| 검토완료 건의 수정·재분석·탐지 검토 | `INVALID_INPUT_VALUE` (400) "검토완료된 건은 변경할 수 없습니다. 검토취소 후 진행하세요." |
| 룰 일괄 검토: 대상 건 없음 (미검토만 선택 시 미검토 0건 / 없는 룰) | `INVALID_INPUT_VALUE` (400) "해당 룰에 미검토 탐지 건이 없습니다." / "해당 룰의 탐지 건이 없습니다." |
| 미검토(PENDING) 탐지가 남아 있는 상태에서 최종 검토완료 | `INVALID_INPUT_VALUE` (400) "미검토 탐지 건이 N건 남아 있어 검토완료할 수 없습니다." |
| 접속 로그 업로드에 시스템 선택·시스템명 모두 없음 | `INVALID_INPUT_VALUE` (400) |
| 시스템 코드 중복 / 시스템 기본 로그 유형에 `ALL` | `DUPLICATE_RESOURCE` (409) / `INVALID_INPUT_VALUE` (400) |
| 내부 IP: IPv4 형식 아님, /33 이상, /8 보다 넓은 대역 | `INVALID_INPUT_VALUE` (400) + 입력 예시 안내 |
| 내부 IP: 이미 등록된 대역 | `DUPLICATE_RESOURCE` (409) |
| DLP 유출 로그: 같은 달에 같은 내용(해시) 파일 다시 올림 | `DUPLICATE_RESOURCE` (409) \"같은 달에 같은 내용의 DLP 유출 로그 파일이 이미 올라와 있습니다.\" |
| DLP 유출 로그: '발생 일시' + '사용자 아이디/이름' 머리글을 찾지 못함 (다른 형식 파일) / 확장자·50MB·20만 건 초과 | `INVALID_INPUT_VALUE` (400) + 사유 |
| DLP 보고서 입력값: 알 수 없는 항목, 선택 값이 정해진 값이 아님, 부서 인원이 숫자 아님 / Privacy-I 목록: 종류가 REINSTALL·OFFLINE 아님, 확장자·20MB·2만 행 초과, '사용자 이름' 머리글 없음 | `INVALID_INPUT_VALUE` (400) + 사유 |
| 카카오톡 승인 대장: 부서·이름·시작일·종료일 없음 / 종료일이 시작일보다 앞 / 엑셀 머리글에 부서·이름·신청 시작일·신청 종료일 없음 | `INVALID_INPUT_VALUE` (400) + 사유 메시지 (엑셀은 못 읽은 행만 `errors` 로 알려 주고 나머지는 등록) |
| 메일 알림 규칙: 이름·제목·본문·받는 사람 없음 / 메일 주소 형식 아님 / 주소 20개 초과 / 일수가 -90~90 밖 / 보내는 시각이 HH:MM 아님 / 규칙 20개 초과 | `INVALID_INPUT_VALUE` (400) "받는 사람 메일 주소 형식이 올바르지 않습니다: …" 등 |
| 메일 알림 보내기: 메일 서버(SMTP) 미설정 / 보내기 실패 | 오류 아님 — 규칙의 마지막 실행 결과에 '메일 서버 미설정 - N명 대기' / '실패 N명 (사유)' 기록, 실패 건은 다음 실행 때 다시 시도 |
| DLP 보고서: 그 달 유출 로그 없음 | `INVALID_INPUT_VALUE` (400) "YYYY년 M월 유출 로그가 없습니다. 먼저 유출 로그를 올려 주세요." |
| 삭제되었거나 없는 ID | `RESOURCE_NOT_FOUND` (404) |
| 권한 없음 / 비로그인 | 403 / 401 (core 공통 처리) |

## 5. 메뉴 등록 요청 정보

메뉴 seed SQL은 작성하지 않았습니다. 운영자가 플랫폼 **메뉴관리 / 접근 권한** 화면(또는 `MODULE_GUIDE.md` 3장의 `core_menu`, `core_role_menu` 등록 절차)에서 아래 정보로 등록해 주세요.

| 메뉴명 | 메뉴 위치 | 메뉴 URL (프론트) | 사용 API | 접근 역할 |
|---|---|---|---|---|
| 보안로그 관리 (상위) | 정보보안 > 보안로그 관리 | (그룹, URL 없음) | - | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| **ISMS 로그 점검 현황 (권장 대표 메뉴)** | 정보보안 > 보안로그 관리 > 점검 현황 | `/security_log/dashboard.html` | `GET /security_log-api/dashboard` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| ~~로그 업로드 목록~~ (**삭제 — 메뉴 등록하지 마세요**) | - | `/security_log/index.html` (열면 분석 결과로 이동) | `GET /security_log-api/uploads`, `/summary` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 점검 대상 시스템 | (화면 상단 탭, 메뉴 등록 선택) | `/security_log/systems.html` | `/security_log-api/systems/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |
| 로그 업로드 등록 | (목록 화면 버튼, 메뉴 비노출) | `/security_log/upload.html` | `POST /security_log-api/uploads` | ROLE_ADMIN, ROLE_MANAGER |
| 분석 결과 (접속 행위·이상징후) | 모듈 상단 메뉴 **[분석 결과]** (목록 [결과 보기]·행 클릭으로도 이동) | `/security_log/detail.html` (id 없으면 로그 고르기 화면) / `detail.html?id={id}` | `GET /uploads`, `/uploads/{id}`, `/detections`, `/access` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 로그 업로드 수정 | (상세 화면 버튼, 메뉴 비노출) | `/security_log/edit.html?id={id}` | `PUT /security_log-api/uploads/{id}` | ROLE_ADMIN, ROLE_MANAGER |
| **관리** (탐지 룰셋 편집) | 정보보안 > 보안로그 관리 > 관리 | `/security_log/rules.html` | `/security_log-api/rules/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |
| 관리 › 내부 IP 설정 | (관리 페이지 하위 메뉴, 메뉴 등록 선택) | `/security_log/internal-ip.html` | `/security_log-api/internal-ips/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |
| **DLP 모니터링 (상위, ISMS 로그 점검과 별도 그룹)** | 최상위 그룹 (ISMS 로그 점검 아래 순서) | (그룹, URL 없음) | - | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| └ DLP 모니터링 | DLP 모니터링 > DLP 모니터링 | `/security_log/dlp.html` | `/security_log-api/dlp-leak/monthly`, `/uploads/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER ([올리기]는 ROLE_ADMIN·ROLE_MANAGER, 파일 삭제는 ROLE_ADMIN) |
| ~~└ DLP 보고서 작성~~ (**메뉴 삭제** — DLP 모니터링 메뉴와 겹침. 화면은 DLP 모니터링 위쪽 탭 '보고서 작성'으로 열림) | - | `/security_log/dlp-report.html` (02장 사용 부서 현황 = 카카오톡 사용자 현황 최신 기준, 기준일 표시) | `/security_log-api/dlp-leak/report-data`, `/privacyi`, `/report` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (입력·목록 올리기는 ROLE_ADMIN·ROLE_MANAGER) |
| └ **DLP 관리** (관리 기능 모음 — 화면 안 탭: **메일 알림 관리** / **사업부·부서 관리**) | DLP 모니터링 > DLP 관리 | `/security_log/dlp-admin.html` | `/security_log-api/dlp-leak/mail-rules/**`, `/mail-logs`, `/orgs/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (규칙 추가·수정·삭제·[지금 보내기], 사업부·부서 추가·이름 바꾸기·사용 켜기/끄기는 ROLE_ADMIN, 나머지는 보기만) |
| └ 카카오톡 사용자 현황 (**메뉴 등록 안 함** — DLP 모니터링·보고서 작성 화면 위쪽 탭 '카카오톡 사용자 현황'으로 열림. 승인 대장 화면만 표시) | - | `/security_log/dlp-kakao.html` | `/security_log-api/dlp-leak/kakao-approvals/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (대장 등록·수정·엑셀 올리기는 ROLE_ADMIN·ROLE_MANAGER, 삭제는 ROLE_ADMIN) |
| ~~└ DLP 룰셋~~ (**삭제** — 화면·API 없음) | - | - | - | - |

### 5-1. 화면 파일 (모듈에 포함)

`module-security_log/src/main/resources/static/security_log/` 에 있으며, 앱 jar 에 포함되어 `/security_log/**` URL 로 서빙됩니다.
module-safety 와 같은 방식으로 플랫폼 SPA 안에서 iframe 으로 열리며, 플랫폼 로그인 세션(`localStorage.fireweb_user` 의 JWT)을 그대로 사용합니다.

| 화면 | 파일 | 역할별 동작 |
|---|---|---|
| **대시보드 (메뉴 첫 화면)** — 화면 안내, **진행 3단계**(로그 올리기 → 분석 결과 검토 → 검토 완료, 칸을 누르면 남은 시스템으로 이동), **지금 먼저 볼 것**(쉬운 제목 + [검토하기]/[올리기]/[마감하기] 버튼, 5건 넘으면 더 보기), 주의할 계정 TOP 10, 관리자·공용 계정 접속, 날짜별 추이, 로그 올리기 팝업 (시스템별 업로드·검토 현황 표와 시스템 상세 옆 창은 삭제 → [월별 점검 현황]에서 확인) | `dashboard.html` | 전원 조회. 로그 올리기 버튼은 ADMIN/MANAGER |
| 월별 점검 현황 — 화면 안내, 진행 3단계 칸, **시스템별 ① 로그 올리기 / ② 분석 결과 검토 = 완료·미완료만 표시하는** 표(① 완료 = 그 달 로그 올림, ② 완료 = 이상징후 탐지 모두 검토, 탐지 없으면 완료), 상태 거르기(전체·할 일 남음·로그 미등록·검토 중·마감 가능·마감 완료), 줄마다 **[다음 할 일]** 버튼 | `status.html` | 전원 조회. [로그 올리기]는 ADMIN/MANAGER, [마감하기]는 ADMIN (그 외에는 '대기' 안내) |
| ~~접속·보안 로그 (올린 로그 목록)~~ — **메뉴 삭제**(2026-11). 열면 분석 결과 로그 고르기 화면으로 이동만 함 | `index.html` | - |
| 점검 대상 시스템 관리 | `systems.html` | 전원 조회, 편집은 ADMIN |
| 등록 (파일 업로드 + 즉시 분석) | `upload.html` | ADMIN/MANAGER. USER 는 권한 없음 안내 |
| 분석 결과 (① 접속 행위 분석, ② 이상징후 탐지, 룰별 집계, 검토 현황, 탐지 목록, 일괄 검토) — 상단 메뉴 고정, **위에 붙는 막대**(같은 달 다른 로그로 바로 이동 · 다른 달에서 고르기 · ①/②/탐지 목록 바로가기). id 없이 열면 **로그 고르기 화면**(위험도 높은 순 카드, 달 이동, '마지막으로 본 결과 이어서 보기'). 고르기 카드·'다른 로그 보기'·'마지막으로 본 결과'는 **사용 중 시스템만** 표시. **① 접속 행위 분석에 계정별 검토**(체크 선택 → 정상 확인 / 이상 확인 / 조치완료 / 미검토로 되돌리기 + 소명·조치 내용, '위험 계정 검토 N / M' 칸, 처음엔 '검토 안 한 위험 계정'만 표시) | `detail.html` | 검토 저장은 ADMIN/MANAGER. 최종 검토완료·취소·삭제는 ADMIN |
| **DLP 점검 보고서** (분석 결과 화면 맨 위, DLP 로그만) — 1.한눈에 보기 · 2.🔴 카드(무슨 일 / 왜 확인이 필요한가 / 확인할 것 / 소명 질문 / 근거 로그) · 3.🟡 표 · 4.🟢 표 · 5.항목별 점검 결과 · 6.계정별 요약 · 7.분석 한계. 행 번호를 누르면 근거 로그 원문 창. 기존 업로드 정보·① 접속 행위 분석(계정 검토)·② 이상징후 탐지·탐지 목록은 아래 **[상세 근거 보기]** 에 접혀 있음(바로가기 누르면 자동으로 펼침). DLP 가 아닌 시스템은 예전 화면 그대로 | `detail.html` | 보기는 ADMIN/MANAGER/USER 모두 |
| 수정 (기본정보, 재분석) | `edit.html` | ADMIN/MANAGER. 검토완료 건은 잠김 |
| 관리 › 탐지 룰셋 편집 (시스템별 룰셋 스위치, 공통/전용 룰 목록, 등록·수정, 사용여부, 패턴 테스트) | `rules.html` | 전원 조회. 편집·룰셋 스위치는 ADMIN |
| 관리 › 내부 IP 설정 (대역 목록, 등록·수정·사용여부·삭제, IP 확인) | `internal-ip.html` | 전원 조회·IP 확인. 편집은 ADMIN |
| 룰셋 분석 근거 (룰셋별 분석 결과에서 검토 대상 있는 룰 클릭: 탐지 조건·이유·걸린 로그) | `rule-evidence.html` | 전원 조회 |
| **DLP 모니터링** (자체 탭 DLP 모니터링 / 보고서 작성 / 카카오톡 사용자 현황. ① 유출 로그 올리기 → ② Privacy-I Agent 현황 올리기(재설치·장기 미접속) → ③ 보고서 작성 · PPT 이동 + 이 달에 올린 파일(원본 내려받기·삭제)) | `dlp.html` | 전원 조회. 올리기는 ADMIN/MANAGER, 파일 삭제는 ADMIN |
| **DLP 보고서 작성** (회사 양식 8장 항목별 화면: 회색 = 자동 계산, 흰 칸 = 입력, 03·04 는 DLP 모니터링 ② 에서 올린 파일로 채움(보고서 화면 개별 올리기 버튼 없음), [입력 저장] · [PPT 내려받기]) | `dlp-report.html` | 전원 조회·PPT. 입력은 ADMIN/MANAGER |
| **카카오톡 사용자 현황** (승인 대장 화면만: 상태별 건수 · **부서별 사용 중 현황**(처음엔 접힘, 눌러서 펼치기) · 대장 목록(사업부·부서·이름·신청 시작일·종료일·신청 상태·최신여부·상태·전자결재 문서번호·사유) · 상태 거르기·최신 건만·검색 · [등록]·[수정]은 목록 안 입력 줄(사업부·부서 드롭다운) · [삭제] · **[엑셀 내려받기]**(화면 거르기 그대로), 정렬 = 신청 종료일 늦은 순. 엑셀 올리기 없음) | `dlp-kakao.html` | 전원 조회·엑셀 내려받기. 대장 등록·수정은 ADMIN/MANAGER, 삭제는 ADMIN |
| **DLP 관리** (관리 기능 모음, 탭 **메일 알림 관리**: 메일 서버 상태 · 메일 알림 규칙 목록(**활성화 토글**·규칙 이름·보내는 날·보내는 시각·받는 사람/참조·보낼 대기·보냄·실패·마지막 실행) · [규칙 추가]/[수정] 입력 칸(규칙 이름, 신청 종료일 +/- N일, 매일 보내는 시각, 받는 사람, 참조, 제목, 본문, 자리표시 버튼) · [지금 보내기]·[삭제] · 보낸 기록(규칙 거르기, 제목 누르면 본문); 탭 **사업부·부서 관리**(주소 `#org`, 폭 820px): 위쪽 한 상자에 [사업부 추가](이름) · [부서 추가](사업부 선택 + 이름) · 아래 표(사업부 / 부서 / 사용 토글 / [이름 바꾸기])) | `dlp-admin.html` | 전원 조회. 규칙 추가·수정·삭제·활성화 토글·지금 보내기, 사업부·부서 추가·이름 바꾸기·사용 토글은 ADMIN |
| 공통 스크립트 / 스타일 | `js/seclog.js`, `js/upload-modal.js`(로그 올리기 창, 대시보드·월별 점검 현황 공통), `css/seclog.css` | - |

> **점검 대상 월 (지난달 기준)**: N월에는 N−1월 로그를 올리고 검토합니다(마감 N월 10일). 대시보드·월별 점검 현황·분석 결과(로그 고르기)·로그 업로드 화면을 **처음 열면 지난달**(예: 11월에 열면 10월)이 먼저 보입니다. 같은 창에서 다른 달로 옮기면 그 달이 유지됩니다(sessionStorage `seclog_ym`, 주소의 `?ym=` 가 가장 우선).
> 위 5개 화면 맨 위에 **"2026년 10월 로그를 올려 주세요"** 안내(현재 연월·검토 마감일 함께 표시, 마감이 지나면 빨간색 '지남')가 나옵니다. 조회 전용(USER)에게는 "2026년 10월 로그 점검 결과를 확인해 주세요"로 보입니다. 다른 달을 보고 있으면 주황색으로 "지금 보고 있는 달: 2026년 09월 (점검 대상 월이 아닙니다)"와 **[10월로 가기]** 버튼이 나옵니다. 업로드 화면·대시보드 업로드 창도 대상 연월에 맞춰 "N월 로그를 올려 주세요" / "N월 로그를 올립니다 (이번 점검 대상은 …)"로 바뀝니다. 월별 점검 현황의 안 올린 시스템, 대시보드 '지금 먼저 볼 것'의 미등록 항목, 목록·분석 결과의 빈 화면도 "N월 로그를 올려 주세요"로 안내합니다(서버 할 일 메시지 NO_DATA 도 같은 문구).

> **분석 대상 = 시스템별 가장 최근 파일 1개**: 같은 달·같은 시스템에 파일을 여러 번 올리면 **가장 최근에 올린 파일(UPLOAD_ID 가 가장 큰 것) 1개만** 분석·집계합니다. 대시보드(진행 3단계·지금 먼저 볼 것·주의할 계정·관리자 계정·날짜별 추이·많이 탐지된 룰), 월별 점검 현황, 월 점검 결과 엑셀(3·4·5번 시트), 월 요약 API 모두 같은 기준입니다. 이전 파일은 지우지 않고 보관만 하며(원본·탐지 결과 그대로), 분석 결과의 로그 고르기·다른 로그 보기에는 나오지 않습니다. 이전 파일을 주소로 직접 열면 "이전에 올린 파일입니다 … 월 점검 집계에서 빠져 있습니다 [최신 파일 결과 보기]" 안내가 나옵니다. 최신 파일을 삭제하면 그 전 파일이 다시 분석 대상이 됩니다. 시스템을 고르지 않은(직접 입력) 업로드는 월 집계에서 빠집니다.

> **접속 계정 검토**: [분석 결과] ① 접속 행위 분석에서 계정을 골라 **정상 확인 / 이상 확인(조치 필요) / 조치완료**와 소명·조치 내용을 저장합니다(일반 사용자·관리자, 모든 시스템). 위험 표시가 있는 계정은 모두 검토해야 '② 분석 결과 검토 완료'와 최종 검토완료(마감)가 됩니다. 결과는 월 점검 결과 엑셀 '6.위험 계정 검토' 시트에 증적으로 남습니다.

> 화면의 버튼 숨김은 사용성용입니다. 실제 권한은 API 의 `@PreAuthorize` 로 서버에서 막습니다.

### 5-2. 화면을 플랫폼에 띄우려면 필요한 작업 (**develop 에 반영 완료**)

아래 3가지가 모두 있어야 플랫폼 메뉴에서 화면이 열립니다. ①·② 는 사용자 허락을 받아 develop 브랜치에 반영했습니다 (core `SecurityConfig` 는 수정하지 않음).

**① 모듈 등록** — `settings.gradle` 에 `include 'module-security_log'`, `app/build.gradle` 에 `implementation project(':module-security_log')` (develop 커밋 e0003d6).

**② app `static/index.html` iframe 라우팅** — `/security_log/` 로 시작하는 메뉴 URL 을 CCKB 와 같은 방식으로 SPA 안 iframe 으로 엽니다 (`seclogMenuNames` / `isSeclogPage` / `seclog::` / `navigateToSeclogPage`, develop 커밋 3adb7cd). 제목은 메뉴명, 경로 표시는 '모듈 > 보안로그 관리 > 메뉴명'.

**③ 모듈 안 화면 파일 공개 설정** — `config/SecLogSecurityConfig.java` (module-kims `KimsSecurityConfig`·module-cckb `CckbSecurityConfig` 와 같은 방식, `@Order(-2)`). iframe 이 화면 파일을 불러올 때는 Authorization 헤더가 없어 401 이 나므로 **화면 파일만** 공개합니다.
```java
.securityMatcher("/security_log/*.html", "/security_log/css/**", "/security_log/js/**")
```
- 공개: 화면 모양(HTML·CSS·JS)만. 데이터는 들어 있지 않습니다.
- 공개 안 함: 데이터 API `/security_log-api/**` 는 이 체인에 들어가지 않아 core 메인 체인(JWT 인증 + `@PreAuthorize` 역할 검사)을 그대로 탑니다.

**③ 메뉴 등록** — 아래 값으로 `core_menu` / `core_role_menu` 에 등록합니다 (`MODULE_GUIDE.md` 3장 절차).

| MENU_CODE | 메뉴명 | 상위 | MENU_URL | 접근 역할 |
|---|---|---|---|---|
| `SECLOG_MGMT` | 보안로그 관리 | (최상위 그룹) | (없음) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_STATUS` | ISMS 로그 점검 현황 | SECLOG_MGMT | **`/security_log/dashboard.html`** (대시보드가 첫 화면) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| ~~`SECLOG_UPLOAD`~~ | ~~로그 업로드 목록~~ — **등록 불필요(화면 삭제)**. 이미 등록했다면 메뉴를 숨기거나 URL 을 `/security_log/detail.html` 로 바꿔 주세요 | SECLOG_MGMT | (`/security_log/index.html` → 분석 결과로 이동) | - |
| `SECLOG_ADMIN` | 관리 (탐지 룰셋 편집 · 내부 IP 설정) | SECLOG_MGMT | `/security_log/rules.html` (관리 페이지 첫 화면) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `DLP_MON_MGMT` | **DLP 모니터링** (최상위 그룹, ISMS 로그 점검과 별도) | (최상위 그룹) | (없음) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_DLP` | DLP 모니터링 (SORT_ORDER 1) | **DLP_MON_MGMT** (이미 SECLOG_MGMT 아래 등록했다면 상위를 DLP_MON_MGMT 로 변경) | `/security_log/dlp.html` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| ~~`SECLOG_DLP_REPORT`~~ | ~~DLP 보고서 작성~~ — **등록 불필요 (메뉴 삭제, DLP 모니터링 화면 탭으로 열림)**. 이미 등록했다면 메뉴관리에서 숨김(비표시) 처리 | - | - | - |
| `SECLOG_DLP_ADMIN` | **DLP 관리** (SORT_ORDER 2) | DLP_MON_MGMT | `/security_log/dlp-admin.html` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| ~~`SECLOG_DLP_RULE`~~ | ~~DLP 룰셋~~ — **등록 불필요 (화면 삭제)**. 이미 등록했다면 메뉴관리에서 숨김(비표시) 처리 | - | - | - |

등록·상세·수정 화면은 목록 화면 안에서 이동하므로 메뉴로 등록하지 않습니다. 모든 목록 화면 상단에 탭(대시보드 / 월별 점검 현황 / 분석 결과 / 점검 대상 시스템 / 관리)과 관리 페이지 하위 메뉴(탐지 룰셋 편집 / 내부 IP 설정)가 있어, 메뉴는 `SECLOG_STATUS` 하나만 등록해도 전 기능을 쓸 수 있습니다.

## 6. 권한 (core 역할 매핑 — 매핑안 A 확정)

별도 권한 코드를 만들지 않고, core의 기존 역할(`core_user_role`)을 그대로 사용합니다. Controller에서는 `@PreAuthorize("hasRole(...)")` / `hasAnyRole(...)`로 제어합니다.

| 요구사항 역할 | core 역할 | 사용 가능 기능 |
|---|---|---|
| 관리자 | `ROLE_ADMIN` | 전체 기능 (룰셋 편집, 최종 검토완료/취소, 삭제 포함) |
| 일반 사용자 | `ROLE_MANAGER` | 조회 + 로그 업로드(등록), 업로드 정보 수정, 재분석, 탐지 건 검토/조치 등록 |
| 조회 사용자 | `ROLE_USER` | 조회만 (목록, 요약, 상세, 탐지 목록, 룰 목록, 원본 파일) |

| 기능 | ADMIN | MANAGER | USER | `@PreAuthorize` |
|---|:---:|:---:|:---:|---|
| 조회 (업로드/탐지/룰/요약/원본) | O | O | O | `hasAnyRole('ADMIN', 'MANAGER', 'USER')` |
| 업로드 등록 / 수정 / 재분석 / 탐지 검토 / **접속 계정 검토** | O | O | - | `hasAnyRole('ADMIN', 'MANAGER')` |
| 룰셋 등록·수정·사용여부·테스트·삭제 | O | - | - | `hasRole('ADMIN')` |
| 최종 검토완료 / 검토취소 | O | - | - | `hasRole('ADMIN')` |
| 업로드 삭제 | O | - | - | `hasRole('ADMIN')` |
| 월별 점검 현황 / 시스템 목록 / 접속 행위 분석 조회 | O | O | O | `hasAnyRole('ADMIN', 'MANAGER', 'USER')` |
| 시스템 등록·수정·사용여부·삭제 | O | - | - | `hasRole('ADMIN')` |
| 대시보드 / 월 점검 결과 내보내기 / 시스템별 룰셋 현황 조회 | O | O | O | `hasAnyRole('ADMIN', 'MANAGER', 'USER')` |
| 시스템별 룰셋 사용/미사용 (공통·전용) | O | - | - | `hasRole('ADMIN')` |
| 내부 IP 대역 조회 / IP 확인 | O | O | O | `hasAnyRole('ADMIN', 'MANAGER', 'USER')` |
| 내부 IP 대역 등록·수정·사용여부·삭제 | O | - | - | `hasRole('ADMIN')` |

> 사용자 ID는 다른 업무 모듈과 같은 방식으로 Controller에서 `@AuthenticationPrincipal(expression = "userId") Long userId`로 받아 Service에 전달합니다. core의 `CustomUserDetails.userId`를 사용합니다.

## 7. SQL 실행 순서

| 순서 | 파일 | 내용 | 재실행 |
|---|---|---|---|
| 1 | `01_schema.sql` | 테이블 4개 생성 (rule → upload → upload_file → detection) | 가능 (`CREATE TABLE IF NOT EXISTS`) |
| 2 | `02_seed_data.sql` | 기본 탐지 룰 19개 | 가능 (`INSERT IGNORE` + UK) |
| 3 | `03_system_access_account.sql` | 테이블 4개 추가(system → access_summary → account_check → account) + `sec_log_upload.SYSTEM_ID` 추가 + 점검 대상 시스템 14개. **account_check / account 테이블은 현재 사용하지 않음**(장기 미접속 기능 삭제, 스크립트는 기존 환경 호환을 위해 그대로 둠) | 가능 (`IF NOT EXISTS`, `INSERT IGNORE`) |
| 4 | `04_seed_dlp_rules.sql` | DLP 관리자 감사로그 전용 탐지 룰 8개 (LOG_TYPE=SECURITY) | 가능 (`INSERT IGNORE` + UK) |
| 5 | `05_collector.sql` | 테이블 2개(collector → collect_history). **현재 사용하지 않음** — 이미 적용한 환경은 그대로 두고, 새로 설치할 때도 실행해도 무방(이후 스크립트가 참조하지 않음) | 가능 (`IF NOT EXISTS`, `INSERT IGNORE`) |
| 6 | `06_access_daily.sql` | 테이블 1개(access_daily). **적용 후 대시보드에서 ADMIN 이 [일별 집계 다시 만들기]를 월별로 한 번 눌러 기존 업로드분을 채움** | 가능 (`IF NOT EXISTS`) |
| 7 | `07_system_ruleset.sql` | `sec_log_rule.SYSTEM_ID`(시스템 전용 룰), `sec_log_system.COMMON_RULE_YN/SYSTEM_RULE_YN`(룰셋 사용 여부) 컬럼 추가. 기존 룰은 모두 공통, 기존 시스템은 두 룰셋 모두 사용으로 시작. **앱 배포 전에 먼저 적용** | 가능 (`ADD COLUMN IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`) |
| 8 | `08_upload_date_check.sql` | `sec_log_upload.DATE_UNKNOWN_COUNT/OUT_OF_MONTH_COUNT`(로그 일자 점검 결과) 컬럼 추가. 기존 업로드는 NULL(점검 전) → **적용 후 ADMIN 이 `POST /security_log-api/dashboard/rebuild-daily?targetYm=` 를 월별로 한 번 호출**(대시보드 버튼은 삭제됨). **앱 배포 전에 먼저 적용** | 가능 (`ADD COLUMN IF NOT EXISTS`) |
| 9 | `09_internal_ip.sql` | 테이블 1개(internal_ip) + 기본 내부 대역 6개(사설 A/B/C, 루프백, 링크 로컬, 0/8 예약). **앱 배포 전에 먼저 적용** (테이블이 없으면 업로드 분석이 실패) | 가능 (`IF NOT EXISTS`, `INSERT IGNORE` + UK) |
| 10 | `10_system_rules.sql` | 시스템별 전용 탐지 룰 87개(14개 시스템) 추가 + `04` 의 DLP 룰 8개를 DLP 전용으로 이동. SYSTEM_ID 는 SYSTEM_CODE 로 찾아 넣음(시스템이 없으면 건너뜀). **적용 후 이미 분석한 업로드는 상세 화면 [재분석]** | 가능 (`INSERT IGNORE` + UK, UPDATE 는 공통으로 남은 DLP 룰에만) |
| 11 | `11_mall_login_rules.sql` | 자사몰(`SVC_MALL`) 관리자 로그인 기록 분석 룰 9개 추가. SYSTEM_ID 는 SYSTEM_CODE 로 찾아 넣음. **적용 후 이미 분석한 자사몰 업로드는 상세 화면 [재분석]** | 가능 (`INSERT IGNORE` + UK) |
| 12 | `12_account_review.sql` | 접속 계정 검토 테이블 1개(`sec_log_account_review`) 추가. **앱 배포 전에 먼저 적용** (테이블이 없으면 접속 행위 분석·월별 현황·대시보드 조회가 실패) | 가능 (`CREATE TABLE IF NOT EXISTS`) |
| 13 | `13_dlp_external_ip_rule.sql` | 탐지 룰 매칭 방식 `EXTERNAL_IP`(내부 IP 외 접속) 설명 추가(`MATCH_TYPE` COMMENT 만 변경) + DLP 전용 룰 1개(`DLP_EXTERNAL_IP_ACCESS`, 높음) 등록. SYSTEM_ID 는 SYSTEM_CODE 로 찾아 넣음. **앱 배포와 같이 적용**, 적용 후 이미 분석한 DLP 업로드는 상세 화면 [재분석] | 가능 (`INSERT IGNORE` + UK, ALTER 는 COMMENT 만) |
| 14 | `14_rule_run.sql` | 룰별 분석 실행 기록 테이블 1개(`sec_log_rule_run`) 추가. **앱 배포 전에 먼저 적용** (테이블이 없으면 분석·재분석·룰셋별 분석 결과 조회가 실패). 적용 전에 분석한 업로드는 기록이 없어 '분석 이후 바뀌지 않은 룰 = 분석 완료'로 추정 표시, 정확한 상태는 [재분석] 후 표시 | 가능 (`CREATE TABLE IF NOT EXISTS`) |
| 15 | `15_pii_download.sql` | 개인정보 다운로드 검토 테이블 1개(`sec_log_pii_download`) 추가 + `sec_log_upload` 에 컬럼 2개(`PII_SHEET_STATUS`, `PII_SHEET_NAME`) 추가. **앱 배포 전에 먼저 적용** (없으면 업로드·조회가 실패). 적용 전에 올린 자사몰·임직원몰 로그는 상세 화면 [재분석] 시 다운로드 내역 탭을 읽음 | 가능 (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`) |
| 16 | `16_dlp_leak.sql` | DLP 모니터링(ISMS 로그 점검과 별도) 테이블 3개(`sec_log_dlp_leak_upload` → `sec_log_dlp_leak_file` → `sec_log_dlp_leak_event`). 룰셋 없음(이전 버전의 `sec_log_dlp_rule` 생성·기본 룰 6개는 뺐음 — 이미 만든 환경은 그대로 둠). 다른 sec_log 테이블을 참조·변경하지 않음. **앱 배포 전에 먼저 적용** | 가능 (`CREATE TABLE IF NOT EXISTS`) |
| 17 | `17_dlp_report.sql` | DLP 보고서(회사 양식 8장): 테이블 3개(`sec_log_dlp_report`, `sec_log_dlp_report_item`, `sec_log_dlp_privacyi_row`) + `sec_log_dlp_leak_event.AI_SERVICE` 컬럼 추가. **앱 배포 전에 먼저 적용**. 적용 전에 올린 유출 로그는 DLP 모니터링 화면 [재분석] 시 생성형 AI 서비스를 채움 | 가능 (`CREATE TABLE IF NOT EXISTS`, `ADD COLUMN IF NOT EXISTS`) |
| 18 | `18_dlp_kakao_approval.sql` | PC 카카오톡 사용 승인 대장 테이블 1개(`sec_log_dlp_kakao_approval`). 다른 테이블 변경 없음, FK 없음. **앱 배포 전에 먼저 적용** (테이블이 없으면 보고서 작성·카카오톡 사용자 현황 조회가 실패) | 가능 (`CREATE TABLE IF NOT EXISTS`) |
| 19 | `19_dlp_mail_rule.sql` | DLP 관리 > 메일 알림 관리: 테이블 2개(`sec_log_dlp_mail_rule` → `sec_log_dlp_mail_send_log`, FK = 규칙·승인 대장만) + 기본 규칙 1개(카카오톡 승인 기한 경과 차단 요청: 신청 종료일 + 1일, 매일 11:00, 받는 사람 비움, **꺼짐** — 화면에서 회사 메일을 넣은 뒤 켬, 보내기 시작일 = 적용한 날. 이미 적용한 DB는 덮어쓰지 않으므로 화면에서 직접 고침). 보내기 시작일 이전에 보내는 날이 지난 건은 보내지 않음. **앱 배포 전에 먼저 적용** (없으면 DLP 관리 화면 조회가 실패) | 가능 (`CREATE TABLE IF NOT EXISTS`, `INSERT IGNORE` + UK) |
| 20 | `20_dlp_org.sql` | DLP 관리 > 사업부·부서 관리: 테이블 1개(`sec_log_dlp_org`, FK 없음) + 처음 목록(적용 시점 승인 대장의 사업부·부서 이름 그대로, 사업부가 비어 있는 대장 건은 넣지 않음). **앱 배포 전에 먼저 적용** (없으면 승인 대장 [등록] 드롭다운·DLP 관리 화면 조회가 실패) | 가능 (`CREATE TABLE IF NOT EXISTS`, `INSERT IGNORE` + UK) |

```bash
mysql -u <user> -p <database> < sql/module-security_log/01_schema.sql
mysql -u <user> -p <database> < sql/module-security_log/02_seed_data.sql
mysql -u <user> -p <database> < sql/module-security_log/03_system_access_account.sql
mysql -u <user> -p <database> < sql/module-security_log/04_seed_dlp_rules.sql
mysql -u <user> -p <database> < sql/module-security_log/05_collector.sql
mysql -u <user> -p <database> < sql/module-security_log/06_access_daily.sql
mysql -u <user> -p <database> < sql/module-security_log/07_system_ruleset.sql
mysql -u <user> -p <database> < sql/module-security_log/08_upload_date_check.sql
mysql -u <user> -p <database> < sql/module-security_log/09_internal_ip.sql
mysql -u <user> -p <database> < sql/module-security_log/10_system_rules.sql
mysql -u <user> -p <database> < sql/module-security_log/11_mall_login_rules.sql
mysql -u <user> -p <database> < sql/module-security_log/12_account_review.sql
mysql -u <user> -p <database> < sql/module-security_log/13_dlp_external_ip_rule.sql
mysql -u <user> -p <database> < sql/module-security_log/14_rule_run.sql
mysql -u <user> -p <database> < sql/module-security_log/15_pii_download.sql
mysql -u <user> -p <database> < sql/module-security_log/16_dlp_leak.sql
mysql -u <user> -p <database> < sql/module-security_log/17_dlp_report.sql
mysql -u <user> -p <database> < sql/module-security_log/18_dlp_kakao_approval.sql
mysql -u <user> -p <database> < sql/module-security_log/19_dlp_mail_rule.sql
mysql -u <user> -p <database> < sql/module-security_log/20_dlp_org.sql
```

**DLP 전용 룰 (`04`)**

| 룰 코드 | 내용 | 위험도 | 샘플(4월) 탐지 |
|---|---|---|---:|
| `DLP_IP_RESTRICT_DENY` | 허용되지 않은 IP에서 관리 콘솔 접속 시도 | 높음 | 19 |
| `DLP_ADMIN_ACCOUNT_CHANGE` | 관리자 계정/권한 변경 | 높음 | 3 |
| `DLP_LOG_DELETE` | 로그/이력 삭제 | 높음 | 1 |
| `DLP_AUDIT_SETTING_CHANGE` | 로그·백업·메일 보고 설정 변경 | 높음 | 1 |
| `DLP_ADMIN_PW_CHANGE` | 관리자 비밀번호 변경 | 보통 | 1 |
| `DLP_POLICY_CHANGE` | 보안 정책 추가·수정·삭제·실행 | 보통 | 67 |
| `DLP_DATA_EXPORT` | 다운로드/내보내기/프린트 (데이터 반출) | 보통 | 63 |
| `DLP_USER_INFO_CHANGE` | DLP 사용자(대상자) 정보 변경 | 낮음 | 46 |

조회(9,217건)는 탐지하지 않습니다. 건수가 많은 정책 변경·반출·사용자 변경은 운영해 보며 위험도나 사용 여부를 룰셋 화면에서 조정하세요.

- **반드시 `03` 을 먼저 적용한 뒤 새 버전 앱을 배포하세요.** `sec_log_upload.SYSTEM_ID` 컬럼이 없으면 업로드 조회가 실패합니다.
- `ADD COLUMN IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` 는 MariaDB 문법입니다 (10.0.2+). MySQL 8 에서는 해당 2줄을 컬럼 존재 여부 확인 후 수동 실행하세요.

- 두 스크립트 모두 맨 앞에서 `SET NAMES utf8mb4` 를 실행하므로, 접속 클라이언트 문자셋과 관계없이 한글 COMMENT/데이터가 깨지지 않습니다.
- `02_seed_data.sql`의 정규식은 SQL 문자열 안에서 역슬래시를 `\\`로 이스케이프했습니다. DB 세션에 `NO_BACKSLASH_ESCAPES` SQL 모드가 **설정되어 있지 않아야** 정상적으로 저장됩니다.
- 운영자가 이미 수정한 룰은 seed를 다시 실행해도 덮어쓰지 않습니다.
- 기본 룰 19개: 공통 6, Linux 3, Windows 2, Web 5, DB 2, Network 1. 운영 환경의 로그 형식에 맞게 룰셋 편집 화면에서 조정하세요.

## 8. 운영 반영 시 필요한 설정

### 8-0. 모듈 의존성

- `org.apache.poi:poi-ooxml:5.2.5` (엑셀 업로드). module-kims, module-safety 와 **같은 버전**이라 플랫폼에 새 라이브러리가 추가되지 않습니다.

### 8-1. Gradle 등록 (플랫폼이 수동 등록 방식인 경우, 운영자 작업)

```groovy
// settings.gradle
include 'module-security_log'

// app/build.gradle
implementation project(':module-security_log')
```

### 8-2. 플랫폼 app 설정에 추가 필요

| 설정 | 현재 저장소 상태 | 조치 |
|---|---|---|
| `spring.servlet.multipart.max-file-size` / `max-request-size` | 100MB / 110MB | 추가 작업 없음 (50MB 이상 충족) |
| `@EnableMethodSecurity` | core `SecurityConfig`에 이미 적용 | 추가 작업 없음 |
| 자동 스캔 (`com.company.module`) | `PlatformApplication`에 이미 적용 | 추가 작업 없음 |
| `/security_log-api/**` 인증 | `anyRequest().authenticated()`로 인증 필요 | 추가 작업 없음 |
| Reverse Proxy (Apache/Nginx) 업로드 크기·타임아웃 | 서버 설정 확인 필요 | 업로드 55MB 이상, 타임아웃 120초 이상 권장 |
| MariaDB `max_allowed_packet` | 서버 설정 확인 필요 | 64M 이상 권장 (원본 LONGBLOB 저장) |
| **메일 서버 (SMTP) — DLP 관리 > 메일 알림 관리** | 미설정 (인프라팀 정보 받을 예정) | 앱 실행 환경 변수로 설정 (application.yml 수정 없음): `SPRING_MAIL_HOST`(메일 서버 주소), `SPRING_MAIL_PORT`(보통 25·465·587), `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`(계정이 필요한 경우), STARTTLS 가 필요하면 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`·`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`, 보내는 주소 `SECLOG_MAIL_FROM`(없으면 SMTP 계정), **받을 수 있는 회사 메일 도메인 `SECLOG_MAIL_ALLOWED_DOMAINS`(예: `company.co.kr`, 여러 개면 쉼표 구분 — 없으면 아무 데도 보내지 않고 '회사 도메인 미설정'으로 대기)**. 설정 전에는 메일을 보내지 않고 화면에 '메일 서버 미설정 - N명 대기' 표시, 설정 후 다음 실행 때 대기 건을 모두 보냄. 메일 서버 정보는 화면에서 설정하지 않음(인프라팀 정보로 서버에 설정) |
| 메일 보내는 시각 | 규칙마다 화면에서 설정 (기본 규칙 매일 11:00, 한국시간) | 모듈 안 스케줄러가 1분마다 확인해, 사용 중이고 오늘 보내는 시각이 지났는데 그 시각 이후 실행 기록이 없는 규칙을 실행 → 화면에서 시각을 바꾸면 바로 적용, 서버가 그 시각에 꺼져 있었으면 켜진 뒤 1분 안에 실행. 플랫폼 전체 `@EnableScheduling` 추가 없음 |
| 서버 여러 대 운영 시 | 단일 서버 기준 | 여러 대에 배포하면 대마다 실행되어 같은 사람에게 중복 발송될 수 있음 → 현재는 1대 운영 기준 (여러 대 운영이 정해지면 별도 대응 필요) |

### 8-3. 운영 고려사항

- **월 마감 기준일**은 대상 월 다음 달 10일입니다 (`SecLogDashboardService.CLOSE_DUE_DAY`). 사내 기준이 다르면 이 상수만 바꾸면 됩니다.
- 특권/공용 계정 기준은 root, admin, administrator, sa, sys, system, oracle, postgres, dba, superuser, guest 입니다 (`AccessLogAnalyzer`). 시스템 고유 관리자 계정명은 이 목록에 넣거나 그 시스템 전용 룰로 탐지합니다.

- 분석은 업로드 요청 안에서 **동기로 처리**합니다. 50MB, 약 50만 라인 기준으로 수 초에서 수십 초가 걸릴 수 있습니다. 더 큰 파일이 필요하면 비동기 처리 도입을 검토하세요.
- 원본 로그는 DB(LONGBLOB)에 보관하므로 DB 용량을 모니터링해야 합니다. 보관 기간 정책이 정해지면 소프트 삭제 후 별도 아카이빙 절차를 마련하세요.
- 로그에 개인정보가 포함될 수 있으므로, 원본 조회 API(`/file`) 접근 권한을 운영 정책에 맞게 다시 검토하세요.

## 9. core 연동 확인 결과 및 추가 필요 사항

`kleannara-ax/ai_platform` 저장소의 실제 core와 대조해 확인한 결과입니다.

| 항목 | 사용 방식 | 상태 |
|---|---|---|
| 사용자 ID | `@AuthenticationPrincipal(expression = "userId")` (core `CustomUserDetails`) | 확인 완료 |
| `ApiResponse.success(T)`, `success()`, `created(T)` | 그대로 사용 | 확인 완료 |
| `ErrorCode.INVALID_INPUT_VALUE`, `RESOURCE_NOT_FOUND`, `DUPLICATE_RESOURCE`, `INTERNAL_SERVER_ERROR` | 그대로 사용 | 확인 완료 |
| `BusinessException(ErrorCode, String message)` | 상세 오류 메시지에 사용 | 확인 완료 |
| 사용자명 표시 | 응답에는 CREATED_BY/REVIEWED_BY 사용자 ID만 들어 있습니다. 이름을 표시하려면 **core 사용자 조회 Provider 또는 API 필요** | 필요 시 |
| 파일 다운로드 응답 | 규칙상 `ApiResponse`로 감싸야 해서 원본을 Base64로 반환합니다 | 협의 필요 |
| 데이터 범위 권한 | 일반 사용자가 "자기 시스템"만 보게 하려면 사용자-시스템 매핑 정보가 **core에 추가 필요**. 현재는 조회 권한자 전체 조회 | 필요 시 |

## 10. API URL 표기 관련 참고

표준 규칙 `/{모듈명}-api`를 그대로 적용해 `/security_log-api`로 만들었습니다. URL에 밑줄(`_`)이 들어갑니다.
플랫폼 URL 규칙이 kebab-case를 요구하면 Controller 3개의 `@RequestMapping` prefix만 `/security-log-api`로 바꾸면 됩니다. 다른 코드는 수정할 필요가 없습니다.

## 11. 생성 파일 목록

```
module-security_log/
├── build.gradle
└── src/main/java/com/company/module/security_log/
    ├── support/     SecLogTime (한국시간 현재 시각), IpRange (IPv4 CIDR 파싱)
    ├── controller/  SecLogRuleController, SecLogUploadController, SecLogDetectionController,
    │                SecLogSystemController, SecLogInternalIpController, SecLogDlpLeakController
    ├── dto/         RuleSaveRequest, RuleUseYnRequest, RuleTestRequest, RulesetUseRequest, LogUploadRequest,
    │                LogUploadUpdateRequest, UploadReviewRequest, DetectionReviewRequest,
    │                RuleReviewRequest, RuleReviewResponse,
    │                RuleResponse, RuleTestResponse, LogUploadResponse, LogUploadDetailResponse,
    │                DetectionResponse, MonthlySummaryResponse, RuleHitCount, OriginalFileResponse,
    │                SystemSaveRequest, SystemResponse, MonthlyStatusResponse, AccessSummaryResponse,
    │                AccessReportResponse, AccountReviewRequest, AccountReviewResponse,
    │                DlpReportResponse, DlpLogRowResponse,
    │                PiiDownloadResponse, PiiDownloadReviewRequest, PiiDownloadReviewResponse,
    │                DashboardResponse, SystemUseYnRequest, SystemLogResponse,
    │                DlpLeakUploadRequest, DlpLeakUploadResponse, DlpLeakMonthlyResponse, DlpKakaoApprovalSaveRequest, DlpKakaoApprovalResponse,
    │                DlpMailRuleSaveRequest, DlpMailRuleResponse, DlpMailSendLogResponse, DlpOrgSaveRequest, DlpOrgResponse,
    │                DlpReportDataResponse, DlpReportSaveRequest
    ├── entity/      SecLogRule, SecLogUpload, SecLogUploadFile, SecLogDetection,
    │                SecLogSystem, SecLogAccessSummary, SecLogAccessDaily, SecLogInternalIp, SecLogAccountReview,
    │                SecLogPiiDownload, SecLogDlpLeakUpload, SecLogDlpLeakFile, SecLogDlpLeakEvent,
    │                SecLogDlpReport, SecLogDlpReportItem, SecLogDlpPrivacyiRow, SecLogDlpKakaoApproval,
    │                SecLogDlpMailRule, SecLogDlpMailSendLog, SecLogDlpOrg,
    │                LogType, MatchType, Severity, UploadStatus, ReviewStatus, AccountReviewStatus, PiiReviewStatus (enum)
    ├── repository/  SecLogRuleRepository, SecLogUploadRepository,
    │                SecLogUploadFileRepository, SecLogDetectionRepository, SecLogSystemRepository,
    │                SecLogAccessSummaryRepository,
    │                SecLogAccessDailyRepository, SecLogInternalIpRepository, SecLogAccountReviewRepository,
    │                SecLogPiiDownloadRepository, SecLogDlpLeakUploadRepository, SecLogDlpLeakFileRepository,
    │                SecLogDlpLeakEventRepository,
    │                SecLogDlpReportRepository, SecLogDlpReportItemRepository, SecLogDlpPrivacyiRowRepository, SecLogDlpKakaoApprovalRepository,
    │                SecLogDlpMailRuleRepository, SecLogDlpMailSendLogRepository, SecLogDlpOrgRepository
    └── service/     SecLogRuleService, SecLogUploadService, SecLogDetectionService,
                     SecLogSystemService, SecLogMonthlyStatusService,
                     SecLogDashboardService, SecLogMonthlyReportService, SecLogAccountReviewService, SecLogDlpReportService, SecLogPiiDownloadService, SecLogDlpLeakService, SecLogDlpLeakReportService,
                     SecLogDlpMonitorReportService, SecLogDlpKakaoApprovalService, SecLogDlpMailRuleService, SecLogDlpMailRuleScheduler, SecLogDlpOrgService, GenAiCatalog, LogDateParser,
                     LogAnalysisEngine, AccessLogAnalyzer, CsvSplit, ExcelTextConverter, SecLogConstants
module-security_log/src/main/resources/static/security_log/
├── dashboard.html
├── status.html  index.html  upload.html  detail.html  edit.html  rules.html  internal-ip.html  systems.html  rule-evidence.html  dlp.html  dlp-report.html  dlp-kakao.html  dlp-admin.html
├── js/seclog.js  js/upload-modal.js
└── css/seclog.css
sql/module-security_log/
├── 01_schema.sql
├── 02_seed_data.sql
├── 03_system_access_account.sql
├── 04_seed_dlp_rules.sql
├── 05_collector.sql        (현재 사용하지 않음, 이전 적용 환경 호환용)
├── 06_access_daily.sql
├── 07_system_ruleset.sql
├── 08_upload_date_check.sql
├── 09_internal_ip.sql
├── 10_system_rules.sql
├── 11_mall_login_rules.sql
├── 12_account_review.sql
├── 13_dlp_external_ip_rule.sql
├── 14_rule_run.sql
├── 15_pii_download.sql
├── 16_dlp_leak.sql
├── 17_dlp_report.sql
├── 18_dlp_kakao_approval.sql
├── 19_dlp_mail_rule.sql
├── 20_dlp_org.sql
└── README.md
```

## 12. 자체 검수 체크리스트 결과

**검증 환경**
- 실제 저장소 빌드: `kleannara-ax/ai_platform` main(`de60b2f`)에 임시로 모듈을 포함해 `./gradlew :module-security_log:compileJava :app:bootJar`를 실행했고 성공했습니다. 이 임시 등록은 커밋하지 않았습니다.
- 화면 E2E: 실제 브라우저(Chromium)로 플랫폼에 로그인해 메뉴에서 iframe 으로 열고, 업로드 → 일괄 검토 → 수정 → 최종 검토완료 → 룰 테스트·등록까지 클릭으로 확인했습니다. ADMIN/MANAGER/USER 별 버튼 노출이 의도대로였고 화면 스크립트 오류는 없었습니다.
- E2E: 로컬 MariaDB에 core 스키마와 이 모듈 SQL을 적용한 뒤 앱을 기동하고, ADMIN/MANAGER/USER 계정으로 로그인해 28개 시나리오를 확인했습니다. 권한 허용·403, 업로드·분석, 중복 409, 검토 흐름, 재분석, 원본 일치, 룰 CRUD, 소프트 삭제, 비로그인 401 모두 기대대로 동작했고, Hibernate 매핑 오류는 없었습니다.
- SQL: MariaDB 11.8에서 `01`, `02`를 2회 연속 실행해 정상 동작을 확인했습니다. 컬럼 COMMENT 누락 0건이고, Entity `@Column`과 DDL 컬럼이 4개 테이블 모두 100% 일치합니다.
- **접속 행위 / 장기 미접속 (추가분, 이전 버전 기록 — 장기 미접속 기능은 현재 삭제됨)**: `03` 을 2회 연속 실행해 정상 동작을 확인했습니다. 8개 테이블 모두 Entity `@Column` 수와 DB 컬럼 수가 일치하고(17/19/25/22 + 기존 4개), COMMENT 누락은 0건입니다.
  - API: sshd 로그 12줄에서 계정 5개를 집계했습니다(parkse 실패 5회+외부 IP, root 업무시간 외+외부 IP+특권, kimhs 다중 IP 3개, leejy 23:41 업무시간 외). CloudTrail 3건은 UTC→KST 환산으로 root 00:20 접속을 업무시간 외로 판정했습니다.
  - 계정 CSV 8행(기준일 2026-10-08, 90일): 정상 3, 장기 미접속 3(141/207/280일), 이력 없음 1, 이미 비활성 1로 판정했습니다. 생성 18일 된 신규 계정(접속 이력 없음)은 정상 처리됐고, IAM 자격증명 보고서(영문 머리글, `N/A`)도 인식했습니다.
  - 브라우저 E2E (Chromium, 플랫폼 로그인 → 메뉴 iframe): 월별 현황 14개 시스템·4개 구분 표시 → 현황의 `등록`으로 WAF 접속 로그 업로드(로그 유형 SECURITY 자동 선택) → 상세 접속 분석 위험 계정 5개 → 홈페이지 계정 목록 업로드 → 조치 필요 4건 상태에서 검토완료 버튼 비활성 → 3건 잠금, 예외 승인 사유 누락 오류 확인 후 사유 입력 → 검토완료 → 현황 반영. ADMIN 화면 스크립트 오류 0건입니다. USER 계정은 조치 패널·검토완료·등록 버튼이 모두 숨겨졌습니다.
- **대시보드 2차 개편 / 시각 보정 (추가분)**: 서버 시간대가 UTC 라 저장·표시 시각이 9시간 늦던 문제를 고쳤습니다(모듈 전체 `now()` 18개 파일 → `SecLogTime`). 대시보드 기준 시각이 서버 한국시간과 1초 이내로 일치했고, 브라우저 시간대를 UTC 로 바꿔도 '몇 분 전' 이 맞았습니다. 로그 수집 표의 시스템 클릭 → 상세 창(DLP 10월: 파일 2건, 계정 9개, 관리 계정 5개) 확인, 검토 현황 표 → 상세 창 확인. 화면 스크립트 오류 0건.
- **대시보드 개편 / 월 점검 결과 내보내기 (추가분)**: `06` 을 2회 연속 실행해 정상 동작을 확인했습니다. 11개 테이블, Entity `@Column` 229개 = DB 컬럼 229개, COMMENT 누락 0건입니다.
  - 일별 집계: 기존 업로드 11건을 [일별 집계 다시 만들기]로 채웠고, 업로드마다 일별 탐지 합계가 업로드 탐지 건수와 같았습니다. DLP 4월 엑셀은 22일 로그 있음 + 주말 8일 활동 없음 → 수집률 100%, 일별 합계 성공 131 / 실패 57 / 탐지 258 로 원본과 일치합니다.
  - 대시보드(10월, DLP 만 사용): 마감 D-21(기준일 11-10), 미검토 42건 중 8~14일 2건 → "12일째 방치" 긴급 표시, 수집 달력 18일 수집·1일 누락(95%), 특권 계정 root 1건, 장기 미접속 조치 필요 2건(조치율 0%).
  - 엑셀: 시트 7개, 맑은 고딕, LibreOffice 로 PDF 변환해 한글·레이아웃 확인(시트당 A4 1장). 브라우저에서 ADMIN·USER 모두 내려받기 확인, [일별 집계 다시 만들기]는 ADMIN 에만 표시. 화면 스크립트 오류 0건.
- **대시보드 / 수집 연결 / 수동 업로드 (추가분, 이전 버전 기록 — 수집 연결 관리·API 수집은 현재 삭제됨)**: `05` 를 2회 연속 실행해 정상 동작을 확인했습니다. 10개 테이블 모두 Entity `@Column` 수와 DB 컬럼 수가 일치하고(collector 30, collect_history 21), COMMENT 누락은 0건입니다.
  - API: DLP 를 가상 연결로 설정 → 연결 테스트 성공 → 지금 수집 200건·탐지 40건·계정 5개 → 다시 수집 시 "새로 수집할 구간 없음"(증분 확인). 인증 환경변수가 없는 UTM 은 연결 오류, 가상 오류 주소의 SSL VPN 은 수집 오류로 표시되고 "수동 업로드로 대체" 안내가 나왔습니다. 같은 파일 수동 재업로드는 409 + 실패 이력 기록. MANAGER 설정 변경 403, USER 수집 403.
  - 브라우저 E2E (Chromium, 플랫폼 로그인 → 메뉴 클릭): iframe 에 `dashboard.html` 이 열림 → 월 마감률·확인 필요 항목 목록·시스템 14개 연결 상태 표시 → 수집 연결 관리에서 SSL VPN(오류) 수동 업로드 모달 바로 열기(`?systemId=7&upload=1`, 실패 사유 안내 표시) → 접속 로그 업로드·분석 성공 → GIT 연결 설정(가상) 저장 → 연결 테스트 → 지금 수집 → 이력 표시. 화면 스크립트 오류 0건.
  - 역할별 버튼: ADMIN 전체, MANAGER 는 수동 업로드·지금 수집만(설정·연결 테스트 숨김), USER 는 조회·이력만 (수동 업로드 바로가기 링크로 들어와도 모달이 열리지 않음). 기존 화면 5개 모두 상단 탭 7개 정상 표시.
- **월 1회 업로드 / 시스템별 룰셋 (추가분)**: API 수집·S3 가져오기 코드를 모두 빼고 월 1회 파일 업로드만 남겼습니다. `07` 을 2회 연속 실행해 정상 동작 확인. 9개 Entity `@Column` 181개 = 해당 DB 컬럼 181개, COMMENT 누락 0건.
  - AWS방화벽 9월 로그(7줄, 고유 형식 `action=CONFIG ... policy=DELETE_ALL`)에 시스템 전용 룰 `AWSFW_POLICY_DELETE_ALL` 을 추가해 업로드 → 탐지 3건(전용 룰 2종). 전용 룰셋 끄고 재분석 → 0건, 공통만 끄고 전용만 켜고 재분석 → 3건, 둘 다 켜기 → 3건.
  - 같은 파일을 DLP(다른 시스템)로 올리면 AWS방화벽 전용 룰이 적용되지 않음(탐지 0건). 같은 파일 재업로드 → 중복 거부. MANAGER 의 룰셋 변경 403, 없는 시스템 ID 로 전용 룰 등록 400.
  - 화면: 탐지 룰셋(시스템별 룰셋 스위치·적용 룰 수, 공통/전용 탭, 시스템 전용 룰 등록 시 로그 유형 잠금), 대시보드(9월 수집률 100%, 미등록 할 일 → 업로드 화면 링크), 업로드 화면 시스템·로그 유형 자동 선택. 상단 탭 6개. 브라우저 스크립트 오류 0건. 검증용 룰·업로드는 삭제(소프트)했습니다.
- **장기 미접속 기능 삭제 (추가분)**: 계정 목록 업로드·판정·조치 기능(Entity 2·enum 2·Repository 2·Service 2·Controller 1·DTO 4, 화면 3개)을 삭제하고 행위 로그 분석만 남겼습니다. CSV 분리 함수는 `CsvSplit` 으로 옮겨 접속 행위 분석에서 계속 사용합니다. 사용 중인 7개 Entity `@Column` 133개, 모두 DB 컬럼에 존재(`sec_log_system.INACTIVE_DAYS` 는 DB 에만 남고 기본값 90으로 저장됨).
  - API: 대시보드·월별 현황·시스템·룰셋 응답에 계정 점검 필드 0건, 이전 `/accounts/**` 404, 시스템 수정(미접속 기준 없이) 성공. 엑셀 시트 5개(요약·일별 수집·접속 로그 검토·탐지 내역·특권 계정 활동).
  - 화면: 상단 탭 5개, 대시보드 로그 수집 3칸·검토 현황 4칸·검토 표(접속·행위 로그만), 상세 창 계정 탭 없음, 월별 현황·시스템 화면 미접속 열 없음. 브라우저 스크립트 오류 0건(삭제된 `accounts.html` 404 확인용 요청 제외).
- **로그 업로드 현황 (추가분)**: 대시보드 '로그 수집'을 '로그 업로드 현황'(요약 4칸 + 시스템별 업로드 표)으로 바꿨습니다. `08` 을 2회 연속 실행해 정상 동작 확인, COMMENT 누락 0건. `sec_log_upload` Entity `@Column` 31개 = DB 컬럼 31개.
  - 기존 업로드: [로그 일자 점검 다시 하기]로 9월 4개·10월 10개 파일 점검 → DLP 9월 '업로드 완료'(9/1~9/30, 22/30일), 10월(진행 중) '업로드 완료'(10/1~10/18, 끝 검사 안 함). 점검할 파일이 없으면 버튼 숨김. MANAGER 호출 403.
  - 테스트 파일(8월, 31줄: 8/10~8/20 22줄 + 7월 3줄 + 일시 없는 줄 6): '기간 확인' + 사유 4개(`8/10부터 시작`, `8/20까지만 있음`, `일시 인식 안 된 줄 6`, `대상 월 밖 줄 3`) 표시, '확인이 필요한 항목'에도 추가. 테스트 업로드는 삭제(소프트)했습니다.
  - 요약을 '업로드 완료' 1칸으로 줄이고(미업로드·기간 확인·분석한 로그 칸 삭제), 칸을 누르면 시스템별 표가 펼쳐지도록 변경. 기본 접힘 → 클릭 시 펼침(aria-expanded=true) → 새로고침 후에도 유지 → 다시 클릭 시 접힘, 표의 시스템 클릭 시 상세 창 확인. 기간 확인 사유는 표의 상태 열과 '확인이 필요한 항목'에서 계속 표시.
  - 화면: ADMIN 8·9월, USER 11월(미업로드 → '월말 후 업로드', 업로드 버튼 숨김) 확인, 시스템 이름 클릭 시 상세 창 열림. 이 모듈 화면 스크립트 오류 0건 (미리보기 환경의 다른 모듈 `dailyreport-api` 권한 조회 500 은 무관).
- **대시보드 로그 업로드 (추가분)**: 상단 [로그 업로드] → 팝업(대상 연월 = 보고 있는 달) → 시스템 미선택 시 안내 → DLP 선택 시 로그 유형 SECURITY 자동 → 파일 선택·업로드 → 결과(31줄, 탐지 11건, 높음) · 상세 링크 표시 → 대시보드 자동 갱신. 같은 파일 재업로드는 409 안내 후 팝업 유지. 업로드 표 [업로드]·시스템 상세 창 링크로 열면 시스템 미리 선택(상세 창은 닫힘). USER 는 버튼 0개. 스크립트 오류 0건. 테스트 업로드는 삭제(소프트)했습니다.
- **관리 페이지 / 내부 IP 설정 (추가분)**: 상단 '탐지 룰셋' 탭을 **'관리'** 로 바꾸고 관리 하위 메뉴(탐지 룰셋 편집 · 내부 IP 설정)를 만들었습니다. `09` 를 2회 연속 실행해 정상 동작 확인(기본 대역 6개, 중복 없음), `sec_log_internal_ip` Entity `@Column` 15개 = DB 컬럼 15개, COMMENT 누락 0건.
  - API: 목록(USER 가능), IP 확인 10.1.2.3 내부·192.1.2.88 외부·`abc`/`::1` 판정 불가, MANAGER 등록 403, 형식 오류(`999.1.1.1`, `/33`, `abc`)·너무 넓은 대역(`0.0.0.0/0`) 400, `192.1.2.77/24` → `192.1.2.0/24` 로 저장, 같은 대역 409, 단일 IP → `/32`.
  - 분석 반영: DLP 9월 업로드를 `192.1.2.0/24` 등록 후 재분석 → hana3·hana4 의 외부 IP 표시 사라짐(외부 접속 55·11 → 0), 대역 미사용 후 재분석 → 다시 표시. 탐지 건수(258)는 그대로.
  - 화면(ADMIN): 관리 탭 → 탐지 룰셋 편집(하위 메뉴 활성) → 내부 IP 설정 이동, IP 확인(외부 → 등록 후 내부 → 미사용 후 외부), 등록·중복·넓은 대역 오류 안내(팝업 유지)·사용 토글·삭제. USER: 등록·토글·수정 버튼 숨김, 조회 전용 안내, IP 확인 가능. 화면 스크립트 오류 0건. 검증용 대역은 삭제(소프트)했습니다.
- **위험도 기준 표시 (추가분)**: 위험도 배지·카드·분포 막대·범례·머리글 ⓘ 에 마우스를 올리면 기준 창 표시. 확인: 목록 최고 위험도(높음 → 높음 행 강조, 사용 중 룰 13개 표시), 분포 막대(단계별 0·81·131·46건), KPI 카드, 창 안으로 이동 시 유지·벗어나면 닫힘, 배지를 눌러도 행 클릭(상세 이동) 정상, 상세 화면 머리글 ⓘ·키보드 포커스(aria-describedby)·Esc, 대시보드 범례(화면 안에 위치), 룰 목록 배지, 룰 등록 팝업 위험도 안내(보통 → 심각 변경 시 갱신)와 팝업 위 기준 창. 스크립트 오류 0건.
- **분석 결과 — 사용 중 시스템만 표시 (추가분)**: DB 변경 없음, 엔드포인트 수 변경 없음(목록에 `activeOnly` 파라미터만 추가). 사용 중 시스템 3개(홈페이지·자사몰·DLP). API: 9월 전체 8건 → `activeOnly=true` 5건(AWS방화벽 3건 제외), 10월 11건 → 3건(SSL VPN·AWS console·GIT·직접 입력 그룹웨어/홈페이지/ERP 제외), 4월 1건(DLP). 화면: 로그 고르기 카드 9월 5개·10월 3개·4월 1개, 결과 화면 '다른 로그 보기' 5개, 마지막으로 본 결과가 사용 안 함 시스템이면 표시 안 함, 사용 안 함 시스템 결과를 주소로 직접 열면 '다른 로그 보기' 맨 위에 '- 사용 안 함 시스템' 표시(나머지는 사용 중만). 파라미터를 안 주면 예전과 같이 전체(접속·보안 로그 목록은 그대로). ADMIN·USER 스크립트 오류 0건.
- **화면 개편 — 대시보드·월별 점검 현황·접속·보안 로그 + 상단 [분석 결과] 메뉴 (추가분)**: 서버·DB·API 변경 없음(화면 HTML·CSS·JS만, 기존 API 재사용). 상단 메뉴 6개(대시보드 · 월별 점검 현황 · 접속·보안 로그 · **분석 결과 (접속 행위·이상징후)** · 점검 대상 시스템 · 관리)가 모든 화면(상세 포함)에 같은 순서로 표시. ① 대시보드(9월): 3단계 칸 '로그 올리기 3/3 완료 · 분석 결과 검토 8%(805건 중 미검토 738) · 검토 완료 0/3(23일 지남)', 지금 먼저 볼 것 3건, 업로드·검토 현황 기본 접힘 → 펼침 3행, ①칸 클릭 → 월별 점검 현황(전체). ② 월별 점검 현황(10월): 홈페이지 '아직 안 올림 → [로그 올리기]'(USER는 '기다리는 중'), 자사몰 '올림 · 미검토 3건 → [검토하기]', DLP '올림 · 기간 확인(10/18까지만 있음) · 파일 2개 → [검토하기 (미검토 42건)]', 거르기 '검토 중' → 2행. ③ 접속·보안 로그: 기본 정렬 위험도 높은 순(15·14·16·27·22·26·24·13), 상세 조건 기본 접힘 → '높음' 선택 시 배지 1·4행, 초기화 → 위험도 순·배지 숨김, `?sort=PENDING` 반영, [결과 보기] → detail.html?id=27. ④ 분석 결과: 상단 메뉴 활성, 다른 로그 고르기 8개(현재 27 선택) → 24로 이동, [② 이상징후 탐지] 바로가기 시 제목이 위에 붙는 막대 아래(가림 없음). 메뉴에서 바로 열면 로그 고르기 카드 8개(첫 카드 AWS방화벽·심각), 다음 달 11개·11월 빈 안내, 마지막으로 본 결과 표시, 카드 클릭 → 상세. ADMIN·USER 스크립트 오류·HTTP 오류 0건.
- **룰별 탐지 건수 — 탐지 없는 룰 숨김 (추가분)**: 서버·DB 변경 없음(화면만). 업로드 25: 탐지 룰 1줄만 보이고 0건 룰 25개 숨김 → [탐지 없는 룰 25개 보기] → 25줄 펼침(aria-expanded=true) → [숨기기] → 다시 0줄. 펼친 상태에서 [로그 보기]를 눌러도 펼침 유지. 업로드 26: 8줄 표시 / 18개 숨김. 탐지 0건 업로드 13: '탐지된 룰이 없습니다' 1줄 + 12개 보기 버튼. 제목 옆 '적용 룰 N개 중 M개 탐지' 요약은 그대로. ADMIN·USER 스크립트 오류 0건.
- **자사몰 실제 접속 기록 형식 반영 (추가분)**: '인증' = 로그인 완료, test 계열 아이디 = 개인 계정(1인 1계정)으로 확인받아 반영. '심야 로그인'·'공용 계정' 2개 룰 수정, 접속 행위 분석이 `수행업무` 열을 인식하도록 1줄 수정. 룰 테스트: 양성 49줄 전부 탐지, 음성 134줄(실제 형식 정상 인증·로그인·로그아웃·test 계정 포함) 오탐 0건. `11` 2회 실행 후 9개 룰 DB 값 설계와 일치, 자사몰 전용 룰 15개 유지. 업로드 25(10월 실제 형식 54줄) 재분석 → 탐지 0건 → **3건**(심야 로그인: 10/1 05:00·10/5 23:00 test1, 10/26 22:38 test5), 접속 행위 분석 0개 → **4개 계정**(성공 52, 업무시간 외 3). 업로드 26 재분석 → 27건(test01 '공용 계정' 1건 제외, 나머지 동일).
- **자사몰 로그인 기록 분석 룰 (추가분)**: `11` 을 2회 연속 실행해 정상 동작 확인(9개만 추가, 중복 없음, 자사몰 전용 룰 15개). 9개 룰 모두 DB 에 저장된 이름·설명·패턴·위험도·임계 건수가 설계값과 일치. 룰 테스트 API 로 양성 예시 38줄 전부 탐지, 음성 예시 84줄(로그인 성공·로그아웃·일반 업무 행·다른 실패 유형 등) 오탐 0건. 엑셀/CSV·key=value·한글 문장형 3가지 형식 모두 확인. 자사몰 9월 샘플(52줄) 업로드 → 9개 룰 모두 탐지(로그인 실패 14·OTP 실패 3·없는 아이디 3·계정 잠금 2·심야 로그인 2·IP 차단 1·중복 로그인 1·비밀번호 찾기 1·공용 계정 1, 최고 위험도 높음). 처음에 CSV(쉼표 구분)의 '공용 계정 로그인' 이 0건이라 아이디 칸 구분자에 쉼표를 추가했습니다. 상세 화면 스크립트 오류 0건.
- **접속·보안 로그 목록 검토 진행 / 정렬 / 기간 확인 (추가분)**: DB 변경 없음(SQL 추가 없음), 엔드포인트 수 변경 없음(목록에 `sort` 파라미터만 추가). 2026-09 6건 기준 — 최신 순 24·22·16·15·14·13, 최고 위험도 순 15·14·16·22·24·13, 미검토 많은 순 16(258건)·22(191건)·15·14·24·13. 기간 확인: 14 '9/15까지만 있음', 15 '9/15부터 시작', 13 '9/30부터 시작', 나머지 9/1 ~ 9/30 정상. 잘못된 정렬 400, 정렬 후 페이지 나눔(size 2, 2쪽 → 16·22), 시스템 필터 + 정렬 함께 동작, 업로드 없는 달 0건, 대시보드 업로드 표 결과 변경 없음. 화면: 정렬 바꾸면 바로 다시 조회, 초기화 시 최신 순, 행 클릭 → 상세 이동. ADMIN·USER 스크립트 오류 0건.
- **룰별 탐지 건수 0건 룰 표시 (추가분)**: DB 변경 없음. 홈페이지 업로드(49줄): 적용 룰 17개(공통 11 + 전용 6) 중 3개 탐지 → 3줄 + 0건 14줄(심각 → 낮음 순), 0건 줄에는 버튼 없음, '반복 인증 실패' 10건↑·'5xx 다발' 50건↑ 기준 표시, 룰 필터 목록은 탐지된 룰 3개만, [로그 보기] 정상. DLP 업로드: 14개 중 9개 탐지, ERP(직접 입력 시스템): 공통 8개 중 2개 탐지. ADMIN·USER 화면 스크립트 오류 0건.
- **점검 대상 시스템 검색 (추가분)**: 서버 변경 없음(화면에서 바로 거름). 14개 중 'dlp' → DLP 1개, '몰' → 자사몰·임직원몰, '정보보호' → 8개, 'SEC_' → 8개, '네트워크 로그' → AP/스위치(두 단어 모두 포함), 없는 검색어 → '검색어와 맞는 시스템이 없습니다', 건수 표시(2 / 14개), 지우기 버튼·Esc 로 초기화, 검색 중에도 수정 팝업·사용 토글 정상. 처음에 '기본 로그 유형' 설명(예: 'VPN/WAF/DLP/EPP 등')까지 검색해 'dlp' 에 4개가 나와서 검색 대상에서 뺐습니다. ADMIN·USER 스크립트 오류 0건.
- **검토 현황 표 숨김 (추가분)**: 서버 변경 없음. 기본 접힘(aria-expanded=false) → '월 마감' 칸 클릭 시 시스템별 표 펼침(2개 시스템) → 새로고침 후에도 유지 → 표의 시스템 이름 클릭 시 상세 창 열림 → 다시 클릭 시 접힘. 로그 업로드 현황 표와 따로 동작. ADMIN·USER 스크립트 오류 0건.
- **점검 매뉴얼 (추가분)**: ADMIN·USER 모두 버튼 표시 → 팝업(항목 6개, 표 3개) → 목차 '위험도별 처리 시한'·'시스템 상세 검토' 이동(주소 변경 없음) → Esc·닫기로 닫힘. 스크립트 오류 0건.
- **시스템별 전용 룰셋 (추가분)**: `10` 을 3회 연속 실행해 정상 동작 확인(전용 룰 95개 유지, 중복 없음). 87개 룰 모두 DB 에 저장된 패턴·이름·설명·위험도·임계·사용여부가 설계값과 100% 일치(역슬래시 이스케이프 포함).
  - 패턴 검증: 87개 룰 모두 실제 Java 엔진(룰 테스트 API)으로 '걸려야 할 줄'(양성 예시 117줄)은 모두 걸리고 '걸리면 안 되는 줄'(음성 예시 107줄)은 하나도 걸리지 않음을 확인. 처음 3개 룰이 어순이 다른 예시를 놓쳐 패턴을 보완했습니다.
  - 재분석 결과: **홈페이지 0건 → 18건**(비밀번호 변경 6, 관리자 권한·상태 변경 5, 관리자 생성 4, 관리자 삭제·게시물 삭제·심야 변경 각 1 — 원본 엑셀과 건수 일치), DLP 258건 그대로(전용 이동 후에도 동일), AWS console 0 → 1건(루트 로그인), AWS방화벽 테스트 로그는 새 전용 룰로 탐지(외부 IP → 관리 포트 119·140건, 전체 개방 1건, 정책 변경 1건). 예전에 미리보기에서 시험용으로 만든 AWS방화벽 룰(`AWSFW_*`) 3개는 새 룰과 겹쳐 모두 삭제(소프트) 상태입니다(이번에 2개 삭제, 1개는 이미 삭제돼 있었음).
  - 화면: 상세 화면 룰별 탐지 건수(홈페이지 6개 룰), 탐지 룰셋 편집의 시스템별 룰셋(홈페이지 공통 11 + 전용 6) 정상, 스크립트 오류 0건.
- **룰별 로그 확인 / 룰 일괄 검토 (추가분)**: DB 변경 없음(SQL 추가 없음).
  - API: `ruleCode` 필터(COM_AUTH_FAIL_BRUTE → 57건, 다른 룰 섞이지 않음), 룰별 검토 건수, USER 일괄 검토 403, `ruleCode` 누락 400, MANAGER 미검토 57건 일괄 오탐 처리 → 다시 요청 시 "미검토 탐지 건이 없습니다" 400, 없는 룰 400, 검토완료 업로드 400(잠김), 전체 덮어쓰기(`onlyPending=false`)로 미검토 되돌리기.
  - 화면(ADMIN): 룰 9개 표·검토 진행 막대, [로그 보기] → 목록이 그 룰 57건으로 바뀌고 이동·행 강조, [이 룰 일괄 검토] 팝업(로그 예시 5줄) → 저장 후 '검토 끝'·목록 조치 내용 반영, 룰 필터 해제 → 258건. USER: [로그 보기]만 보이고 일괄 검토 버튼 0개. 스크립트 오류 0건. 검증 후 테스트 데이터는 미검토로 되돌림(258건 모두 미검토, 조치 내용 없음).
- **점검 대상 월 = 지난달 / 'N월 로그를 올려 주세요' 안내 (추가분)**: DB 변경 없음(SQL 추가 없음). 2026-11-05 기준, 저장된 달(sessionStorage)을 지우고 처음 열었을 때 대시보드·월별 점검 현황·접속·보안 로그·분석 결과 고르기·업로드 5개 화면 모두 **2026년 10월**로 열림, 안내 문구 ADMIN '2026년 10월 로그를 올려 주세요' / USER '2026년 10월 로그 점검 결과를 확인해 주세요'. 대시보드에서 9월로 이동 → 주황 안내 '지금 보고 있는 달: 2026년 09월' + [10월로 가기] → 누르면 10월로 돌아오고 주황 안내 사라짐. 월별 점검 현황 11월(미등록)에서 '11월 로그를 올려 주세요' 표시, 버튼 '11월 로그 올리기'(USER 는 버튼 없이 '11월 로그 올라오길 기다리는 중'). 대시보드 업로드 창 '2026년 10월 로그를 올려 주세요' → 대상 연월을 9월로 바꾸면 '2026년 09월 로그를 올립니다 (이번 점검 대상은 2026년 10월입니다)'. USER 업로드 화면은 권한 없음 안내 그대로. ADMIN·USER 스크립트 오류·4xx 0건.
- **접속 행위 분석 계정별 검토 (추가분, 검토자 매핑안 A)**: `12` 를 2회 연속 실행해 정상 동작 확인, `sec_log_account_review` 컬럼 15개 COMMENT 누락 0건, Entity `@Column` 15개 = DB 컬럼 15개, FK 는 모듈 테이블(sec_log_upload)만. 엔드포인트 1개 추가(`PUT /uploads/{id}/access/review`). 검증(10월 DLP 업로드 9, 위험 계정 4개·탐지 40건):
  - API: USER 저장 403, 소명 없이 '이상 확인' 400, 없는 계정 400, 잘못된 상태 400, 빈 목록 400. MANAGER hana1 정상 확인 → 접속 분석 `riskPendingCount` 3, 월별 현황 `accessRiskPending` 3, 대시보드 할 일 '위험 접속 계정 3개 검토 필요 (전체 위험 계정 4개)'. 같은 계정 다시 검토 시 행 1개 그대로 덮어씀(조치완료). 탐지 40건을 모두 처리해도 위험 계정 3개가 남으면 최종 검토완료 400 → 나머지 3개 정상 확인 후 할 일 '탐지·위험 계정 검토 끝 · 최종 검토완료 가능' → 최종 검토완료 200 → 검토완료 후 계정 검토 변경 400. 엑셀 6번 시트 '6.위험 계정 검토' 4행(시스템·계정·위험 표시·성공·실패·업무시간 외·외부 IP·검토 상태·소명·검토자·일시), 1.요약 '4개 (검토 4 · 미검토 0, 6번 시트)'.
  - 화면(MANAGER): 처음 거르기 '검토 안 한 위험 계정' 4행·주황 안내·KPI '위험 계정 검토 0 / 4', 소명 없이 '이상 확인' → 안내 문구, hana1 이상 확인 저장 → 3행·1 / 4, 전체 선택 3개 정상 확인 → '모두 검토했습니다'·4 / 4, 검토 칸에 상태·소명·일시·검토자 ID. USER: 체크박스·검토 패널 숨김, 결과만 보임. ADMIN: 월별 현황 '② 미완료 · 검토하기 (미검토 40건)', 대시보드 ②칸 '탐지 미검토 40건 · 위험 계정 미검토 0개', 한 계정 미검토로 되돌리면 현황 [위험 계정 검토하기 (1개)] → detail.html?id=9#access, 대시보드 할 일 '위험 접속 계정을 검토하세요'. 스크립트·HTTP 오류 0건.
  - 검증 후 테스트 데이터 원상복구(계정 검토 0건, 탐지 40건 미검토, 업로드 검토대기).
- **분석한 로그 시스템별 1개 / 가장 최근 업로드만 분석 (추가분)**: DB·SQL 변경 없음. 월 집계 쿼리 13개(탐지 8·접속 계정 3·일별 2)에 '그 달·그 시스템의 MAX(UPLOAD_ID)' 조건을 넣고, 월 업로드 목록을 쓰는 곳(대시보드 4곳·월별 점검 현황·엑셀 3번 시트·월 요약)을 `findLatestByTargetYm` 로 바꿈. 목록 API `latestOnly`·`olderFileCount`, 상세 `latest`·`latestUploadId` 추가(엔드포인트 수 그대로). 일자 점검 재실행(`rebuild-daily`)은 관리용이라 이전 파일 포함 그대로. 검증(미리보기 데이터): 9월 DLP 파일 3개(16·22·27) → 분석한 로그 카드 1개(27, '이전 파일 2개는 분석에서 뺐습니다'), 월별 점검 현황 탐지 255건·[검토하기 (미검토 255건)] → detail.html?id=27, 대시보드 '탐지 255건 중 미검토 255건'(예전 3개 합산 771건), 엑셀 3번 시트 1행·4번 시트 255행. 10월 DLP 파일 2개(7·9) → 9 하나(탐지 40건). 4월(파일 1개) 변화 없음(258건). 이전 파일 22 를 직접 열면 안내 + [최신 파일 결과 보기] → 27, 다른 로그 보기에 '이전 파일 (분석 제외)' 표시. 업로드 화면·대시보드 업로드 창에 '가장 최근 파일 1개만 분석' 안내. ADMIN·USER 스크립트·HTTP 오류 0건.
- **'접속·보안 로그' 메뉴 삭제 (추가분)**: 서버·DB·API 변경 없음(화면만). 상단 탭에서 빼고(탭 5개: 대시보드 · 월별 점검 현황 · 분석 결과 · 점검 대상 시스템 · 관리), `index.html` 은 예전 주소용으로 남겨 열면 `detail.html`(로그 고르기)로 이동(`?ym=` 유지, 잘못된 값은 무시). 이 화면으로 가던 링크 정리: 대시보드 진행 3단계 ②칸 → `status.html?f=REVIEW`, 월별 점검 현황 [검토하기](파일 여러 개) → `detail.html?ym=`, 결과 화면 [목록]·[로그 목록] → [로그 고르기], 결과 삭제 후 → 로그 고르기, 업로드 화면 [목록] 삭제·[취소] → 월별 점검 현황, 업로드 권한 없음 화면 → [대시보드로], 수정 화면 id 없음 → 로그 고르기, 점검 매뉴얼 문구. 검증(ADMIN·USER): 5개 화면 탭에 '접속·보안 로그' 없음, `index.html` / `?ym=202609` / `?ym=bad` → 로그 고르기(10월·9월·저장된 달), 10월 DLP [검토하기] → 로그 고르기 카드 2개, 결과 화면 [로그 고르기] 버튼(목록 버튼 0개). 스크립트·HTTP 오류 0건. 모듈 안 화면에서 `index.html` 을 가리키는 링크 0건.
- **월별 점검 현황 표 단순화 (추가분)**: 서버·DB·API 변경 없음(`status.html` 만). 표를 '시스템 · ① 로그 올리기 · ② 분석 결과 검토 · 다음 할 일' 4열로 줄이고 ①·②는 **완료 / 미완료**만 표시(로그 기간·파일 수·기간 확인 사유·접속 계정·위험 계정·탐지 건수 문구와 '③ 검토 완료' 열 삭제). [다음 할 일] 버튼과 위쪽 진행 3단계 칸·상태 거르기는 그대로. 검증(ADMIN·USER): 10월 DLP '완료 · 미완료 · 검토하기 (미검토 42건)', 9월 '완료 · 미완료 · 검토하기 (미검토 704건)', 11월 '미완료 · 미완료 · 11월 로그 올리기'(USER '11월 로그 올라오길 기다리는 중'). 스크립트·HTTP 오류 0건.
- **대시보드 '시스템별 로그 업로드 현황'·'시스템별 검토 현황' 삭제 (추가분)**: 서버·DB·API 변경 없음(`dashboard.html` 만). 두 접힌 표와 함께 그 표에서만 열리던 시스템 상세 옆 창, [로그 일자 점검 다시 하기] 버튼도 화면에서 뺐습니다(API `rebuild-daily`·`/dashboard/systems/{id}` 는 그대로). 같은 정보는 [월별 점검 현황] 탭에 있습니다. 검증: ADMIN·USER 로 10월·9월·4월 대시보드 → 두 표 문구 0건, 진행 3단계 3칸·마감 D-day('마감 1일 지남 · 기준일 11/10 · 남은 시스템 1개')·지금 먼저 볼 것(10월 4건)·주의할 계정·날짜별 추이 정상, [로그 올리기] 창 열림('2026년 10월 로그를 올려 주세요'), 점검 매뉴얼에 옛 표 이름 없음. 보안로그 화면 스크립트·HTTP 오류 0건(로그인 화면의 다른 모듈 `dailyreport-api` 500 은 이 모듈과 무관).
- **DLP 관리자 로그 점검 보고서 (추가분, 규칙 기반 A안)**: DB·SQL 변경 없음(테이블 추가 없음, 보고서는 저장하지 않고 보관된 원본 파일을 열 때마다 다시 계산). 엔드포인트 2개 추가(`GET /uploads/{id}/dlp-report`, `GET /uploads/{id}/dlp-report/rows`). `ExcelTextConverter.readRows` 로 엑셀 원래 행 번호를 함께 읽어(기존 TSV 변환 결과는 그대로) 근거 로그를 엑셀 행 번호로 보여 줌. 점검 기준 7개(접속 / 정책 변경 / 에이전트 / 반출 승인 / 증적 열람 / 로그·설정 / 계정·권한), 위험도 🔴 우회·은폐·근거 없는 완화·로그 삭제·에이전트 해제·변경→반출→원복, 🟡 업무시간 외·주말·허용 IP 외·원본 열람 10회 이상·정책 변경·관리자 정보 수정·평일 로그 공백, 🟢 단순 패턴 이탈. 같은 계정+같은 행위는 한 건으로 묶어 'N회/N일'. 정상/위반 판정 없음, 추측은 '(추정)', 로그로 볼 수 없는 항목은 '로그로 확인 불가' + 분석 한계에 이유. 로그 안 지시문처럼 보이는 문장은 엄격한 패턴으로만 찾아 '로그 내 의심 문구'로 보고(따르지 않음). 검증: 9월 DLP 엑셀(업로드 27, 9,577줄·5계정)을 별도 파이썬 시제품과 비교 → 🔴3 · 🟡13 · 🟢9, **25건 모두 위험도·계정·횟수·내용·근거 행 번호 일치**(예: 🔴 hana2 9/15 13:51 정책 변경 → 반출 14회 → 17:06 다시 변경(근거 16줄, 5807행~5509행), 🔴 hana4 9/21 12:33 내보내기 2회 직후 내보내기 이력 삭제(3212·3198·3192행), 🔴 hana2 9/28 IP 제한 실패 직후 관리자 정보 수정 2회). 근거 원문 API 3212·3198·3192행 → 엑셀 원문과 일치, 없는 행 번호(99999)는 빠짐. 항목별 결과: 반출 승인 '로그로 확인 불가', 에이전트 '검토 필요 2건 · 일부 로그로 확인 불가'. 분석 한계에 '변경 기록 86건 상세 정보 비어 있음', '금·토요일 기록 없음(날짜 밀림 추정)' 표시. 10월 DLP TSV(업로드 9, 상세 정보 열 없음) → 🔴0 · 🟡9 · 🟢3, 줄 번호 기준, '높음 이상 없음' 표시. DLP 가 아닌 업로드(24 홈페이지·5 SSL VPN) → `applicable=false`, 화면은 예전 그대로, rows API 400. 로그인 없이 401. 화면(ADMIN·MANAGER·USER): 보고서 1~7 표시, 🔴 카드 3개, 상세 근거 기본 접힘, [전체 16줄 보기] → 근거 창 16줄, 행 칩 클릭 → 그 줄 노란색 강조, 바로가기 [① 접속 행위 분석] → 상세 근거 자동 펼침. 스크립트·HTTP 오류 0건.
- **DLP 점검 보고서 🔴 카드 칸 나누기 (추가분)**: 서버·DB·API 변경 없음(`detail.html`·`seclog.css` 만). 카드를 머리(번호·제목·점검 항목 이름표) / 본문 / 바닥으로 나누고, 본문은 칸 4개로 구분: **무슨 일**(빨강) · **왜 확인이 필요한가**(주황) · **확인할 것**(초록, 쉼표로 나눠 체크 목록 — 괄호 안 쉼표는 나누지 않음) · **소명 질문**(남색, '담당자에게 물어볼 것'). 넓은 화면은 '왜 확인'·'확인할 것'을 나란히, 900px 이하는 세로로 쌓음. 바닥에 '근거 로그 N줄' + 행 번호. 검증: 10월 DLP(카드 5개)·9월 DLP(카드 3개), 첫 카드 칸 4개·확인할 것 3개 항목('허용 IP 변경 이력(변경자·시각)' 등), [전체 5줄 보기] → 근거 창 5줄, ADMIN·MANAGER(800px)·USER 스크립트·HTTP 오류 0건.
- **DLP 점검 보고서 — 🔴 카드 접기 / 항목별 검토 현황 (추가분)**: `13` 을 2회 연속 실행해 정상 동작 확인, `sec_log_dlp_item_review` 컬럼 17개 COMMENT 누락 0건, Entity `@Column` 17개 = DB 컬럼 17개, FK 는 모듈 테이블(sec_log_upload)만. 엔드포인트 1개 추가(`PUT /uploads/{id}/dlp-report/review`). 보고서는 저장하지 않으므로 항목은 `itemKey`(점검 항목 번호·행위 이름·계정·첫 근거 행 번호)로 찾고, 검토 당시 위험도·계정·내용을 함께 저장(증적용). 재분석·업로드 삭제 시 항목 검토도 소프트 삭제. 화면: 🔴 카드는 처음에 **제목만** 보이고(제목·점검 항목·검토 상태 배지) 누르면 펼침, [모두 펼치기]/[모두 접기], 펼친 카드 맨 아래 '검토 결과' 칸([검토하기]/[검토 수정]). '1. 한눈에 보기' 아래 **검토 현황**(🔴·🟡·🟢 각각 검토 수 / 전체 + 막대 + 미검토 수). 🟡·🟢 표에 '검토' 열(상태·내용·일시·검토자 ID)과 선택 칸 — 여러 항목을 골라 [선택 항목 검토]로 한 번에 저장. 검토 창: 검토 결과(정상 확인 / 이상 확인(조치 필요) / 조치완료) + 검토 내용(어떻게 확인했는지, 필수) + [미검토로 되돌리기]. 검토한 카드는 초록색으로 바뀜. USER 는 버튼·선택 칸 없이 결과만 봄, 최종 검토완료된 로그는 잠김 안내. 검증(10월 DLP 업로드 9, 🔴5·🟡9·🟢5): USER 저장 403, 내용 없이 400, 잘못된 상태 400, 보고서에 없는 항목 400, MANAGER 🔴1건 이상 확인 → 남은 🔴4, 🟡 3건 한 번에 정상 확인 → 남은 🟡6, ADMIN 같은 🔴 항목 조치완료로 수정(행 1개 유지), 1건 미검토로 되돌리기(소프트 삭제), USER 조회 시 상태·내용·일시·검토자 ID 표시. 업로드 27 에 검토 1건 저장 후 재분석 → 검토 0건(소프트 삭제). 화면(MANAGER): 처음 카드 5개 모두 접힘(높이 52px), 3번만 펼침(547px), 내용 없이 저장 → 안내, 이상 확인 저장 → 배지·검토 현황 2 / 5, 🟢 표 전체 선택 5개 → 한 번에 저장 → 5 / 5·선택 막대 사라짐, 모두 펼치기 5·모두 접기 0. VIEWER: 검토 버튼·선택 칸 0개, 결과만 보임. ADMIN(800px) 세로 배치. 스크립트·HTTP 오류 0건. 항목 검토는 최종 검토완료(마감) 조건에 넣지 않았습니다(요청 범위 밖).
- **DLP 점검 보고서 문장 개조식 정리 (추가분)**: DB·SQL·API 변경 없음(보고서 문구와 화면 표시만). 보고서 문장의 서술형(~습니다 / ~나요)을 모두 개조식(명사형 종결)으로 변경: 🔴 카드 '무슨 일'은 한 문장 대신 **'항목: 값' 여러 줄**(예: `차단 IP: 10.40.15.83 (허용 IP 외 접속 차단 4회, 최초 10/2 16:45)` / `이후 로그인 성공: 1회 (최초 10/14 13:20)`, 정책 변경→반출→재변경은 `정책 변경 / 반출 / 정책 재변경 (최초 변경 후 2시간 38분)`), '왜 확인이 필요한가'는 '… 가능성 (추정) · … 확인 필요', 소명 질문은 '… 사유 및 요청서 유무는?' 형태, 확인할 것·이번 달 핵심·항목별 비고·분석 한계·화면 안내 문구도 같은 말투로 정리. 표현 통일: '막혔던 IP' → '차단 IP', '다시 변경' → '재변경'. 항목 키(itemKey)는 그대로라 기존 검토 기록 유지(10월 DLP 검토 2·2·5건 그대로). 검증: 10월·9월 보고서 API 전체에서 서술형 종결 0건, 화면(MANAGER·USER 1500px, ADMIN 600px) 모든 카드 펼친 상태 서술형 0건, '무슨 일' 항목 칸 표시, 스크립트·HTTP 오류 0건.
- **DLP 화면 '상세 근거 보기' 영역 삭제 (추가분)**: 서버·DB·SQL·API 변경 없음(`detail.html`·`seclog.css` 만). DLP 업로드 상세 화면에서 예전 '상세 근거 보기' 안에 있던 업로드 정보 · ① 접속 행위 분석(계정 검토) · ② 이상징후 탐지 · 탐지 결과 목록을 모두 화면에서 뺌(불러오지도 않음). DLP 화면은 점검 보고서만 표시, 바로가기는 [점검 보고서]·[검토 현황]. 대시보드·월별 점검 현황의 `#access`·`#detect` 링크로 들어오면 DLP 는 '검토 현황'으로 이동. DLP 가 아닌 시스템(홈페이지·SSL VPN 등)은 이 영역이 화면 전체라 그대로 유지. 검증: ADMIN·MANAGER·USER 로 업로드 9·27(DLP) → 보고서만 표시, 예전 영역 문구 0건, 호출 API 는 `/uploads/{id}`·`/dlp-report` 만, `#access` 링크 → 검토 현황 도착. 업로드 24(홈페이지) → 접속 행위 2행·탐지 7건 예전 그대로. 스크립트·HTTP 오류 0건. **남은 문제**: 최종 검토완료(마감) 조건은 그대로(미검토 탐지 0건 + 위험 계정 미검토 0건)라 DLP 는 화면에서 탐지를 검토할 수 없어 '미검토 탐지 건이 50건 남아 있어 검토완료할 수 없습니다'로 마감 불가 — 마감 기준 결정 필요(요청 범위 밖이라 변경하지 않음).
- **월별 점검 현황 버튼 이름 변경 (추가분)**: 서버·DB·SQL·API 변경 없음(`status.html` 만). '다음 할 일' 버튼 '위험 계정 검토하기 (N개)' → **'분석 결과 검토하기 (N개)'**(위험 계정만이 아니라 로그 분석 결과 전체를 검토하는 단계이므로). 링크·개수(위험 계정 미검토 수)·표시 조건은 그대로. 검증: 화면 파일에 새 이름 1건, 옛 이름 0건.
- **탐지 룰셋 하나로 통일 + DLP 점검 보고서를 룰셋 결과로 작성 + 룰셋 화면 정리 (추가분, 1안)**: 판정 기준을 [관리 › 탐지 룰셋 편집] 하나로 통일. ① 바로 앞 '보고서 판정 룰(REPORT)' 연결을 되돌림(`14_dlp_report_rules.sql`·`MatchType.REPORT` 삭제 — 운영 미반영분). ② DLP 점검 보고서의 고정 판정 20개(순서·시간 흐름 판정 포함)를 없애고, **이 시스템에 적용된 룰로 탐지한 결과를 '룰 × 계정' 항목으로 묶어** 보고서 작성(위험도 = 룰 위험도: 심각·높음 🔴 / 보통 🟡 / 낮음 🟢, 근거 로그 = 원본 엑셀 행 번호). 룰을 추가·수정·사용 해제하면 [재분석] 후 보고서에 그대로 반영(바뀐 룰이 있으면 '7. 분석 한계'에 안내). '5. 항목별 점검 결과' → **'5. 룰별 결과'**(적용 룰 전체, 0건 포함). ③ 보고서 항목 검토 = **그 항목에 묶인 탐지 건 검토**(기존 `PUT /uploads/{id}/detections/review` 사용, 1,000건씩) → 최종 검토완료 조건 '미검토 탐지 0건'과 같은 기록이라 **DLP 도 보고서 항목을 모두 검토하면 마감 가능**. DLP 는 계정 검토 화면이 없으므로 위험 계정 검토 조건에서 제외(마감·월별 현황·대시보드 공통). 검토 결과 선택지: 정상 확인(오탐) / 이상 확인(조치 필요) / 조치완료. ④ 따로 두었던 항목 검토 저장소 삭제: `13_dlp_item_review.sql`·`SecLogDlpItemReview` Entity/Repository/Service·DTO 2개·`PUT /uploads/{id}/dlp-report/review` API 삭제(운영 미반영분 — 미리보기 DB 의 빈 테이블은 DROP 금지 규칙에 따라 남겨 둠, 앱은 사용 안 함). ⑤ **룰셋 화면**: 목록은 **시스템 · 룰 이름 · 위험도 · 사용** 4칸만, 룰을 누르면 오른쪽 상세 패널(설명·점검 포인트 / 찾는 방식·탐지 기준(임계)·패턴 / 위험도 의미·권장 대응 / 룰 코드·적용 범위·로그 유형·정렬 순서·최근 수정, ADMIN 은 [삭제]·[수정 · 패턴 테스트]). 도움말과 '시스템별 룰셋 설정'은 접어 둠(눌러서 펼치기). 키보드 Enter 로도 상세 열림, 사용 스위치는 눌러도 상세가 열리지 않음. 검증: 9월 DLP(업로드 27) → 🔴11·🟡7·🟢3, 탐지 255건, 룰별 결과 14개(공통 6 + 전용 8, 0건 6개 포함), 첫 항목 'op03, 반복 인증 실패 19회' 근거 19줄. 10월 DLP(업로드 9) → 🔴7·🟡5·🟢0, 탐지 50건, 근거 25행 원문 = 'op01 로그인 실패'. 미검토 남은 상태 마감 → '미검토 탐지 건이 255건' 안내, MANAGER 1번 항목 저장 → 미검토 236·검토 1, USER 저장 403, 모두 검토 → **최종 검토완료 성공**(월별 현황 DLP REVIEWED, 위험 계정 미검토 0) → 검토취소 200. '반복 인증 실패' 룰 끄고 재분석 → 탐지 199·🔴6·해당 항목·룰별 결과에서 빠짐, 다시 켜고 재분석 → 255·🔴11. 홈페이지(업로드 24) → 보고서 미적용, 위험 계정 미검토 2(기존 조건 유지). 삭제된 API 404. 화면: ADMIN·MANAGER·USER 룰셋 목록 4칸·123개·패턴 미표시·도움말/설정 접힘, 상세 패널 4구역, ADMIN 만 스위치·[삭제]·[수정 · 패턴 테스트](수정 창 열림), 보고서 1~7 + 검토 현황, 🔴 카드 11개, MANAGER 카드 검토 저장 '1개 항목(탐지 19건)' → 배지 '정상 확인'·1 / 21, 🟡 표 전체 선택 일괄 저장 '7개 항목 · 탐지 129건' → 8 / 21, USER 검토 버튼 0개. 스크립트·HTTP 오류 0건. 테스트 후 업로드 27 재분석으로 검토 기록 초기화(10월 DLP 의 예전 항목 검토 테스트 기록도 함께 없어짐).
- **탐지 룰 '내부 IP 외 접속' 추가 + DLP 보고서 시각 읽기 보완 (추가분, 1안)**: `13_dlp_external_ip_rule.sql` 을 2회 연속 실행해 정상 동작 확인(룰 1개, MATCH_TYPE COMMENT 반영). 테이블·API 추가 없음. ① 매칭 방식 **`EXTERNAL_IP`(내부 IP 외 접속)** 추가: 로그 한 줄에서 처음 나오는 IPv4 가 [관리 › 내부 IP 설정]의 사용 중 대역에 없고 PATTERN(대상 줄 조건 정규식, `.*` = IP 가 있는 모든 줄)에도 맞으면 탐지. 분석(`LogAnalysisEngine.analyze`)과 룰셋 화면 패턴 테스트에 내부 IP 판정기 전달. ② DLP 전용 룰 `DLP_EXTERNAL_IP_ACCESS` '내부 IP 외 접속 (모든 행위)'(높음, 사용) 등록 → 로그인뿐 아니라 조회·추가 등 **모든 행위**가 대상, 탐지 결과는 DLP 점검 보고서 항목 + 검토 대상(미검토 남으면 최종 검토완료 불가). ③ DLP 보고서 '7. 분석 한계'에 내부 IP 기준(사용 중 대역 이름·범위)과 '마지막 분석 이후 내부 IP 설정 변경 → [재분석] 시 반영' 안내. ④ DLP 보고서가 시·분·초 한 자리 시각(예: `2026-10-12 14:10:9`)도 읽도록 보완. ⑤ 룰셋 화면: 등록 창 매칭 방식에 '내부 IP 외 접속' 추가(선택 시 패턴 칸 기본값 `.*`·안내 문구), 상세 패널에 찾는 방식 설명·'대상 줄 조건' 표시·내부 IP 설정 링크, 도움말 1줄. 내부 IP 설정 화면 안내 1줄. 검증: 10월 DLP(업로드 29, DLPTEST10월.xlsx) 재분석 → 탐지 0 → **3건**(엑셀 9행 SMSScheduler 10.0.14.90 추가 · 10행 jypark 192.0.0.64 조회 · 49행 yjpark 192.1.7.232 조회), 보고서 🔴3, 분석한 줄 21 → 40(초 한 자리 시각 읽음, 48행 '2026-10-0214:10:25' 는 날짜·시각 사이 띄어쓰기가 없어 제외), 근거 원문 3행 일치. 패턴 테스트 4줄 중 2줄 일치(내부 192.0.0.1·IP 없는 줄 제외). 미검토 상태 마감 → '미검토 탐지 건이 3건' 안내, USER 저장 403, MANAGER 저장 200 → 최종 검토완료 성공 → 검토취소·재분석으로 초기화. 9월 DLP(27)·10월 DLP(9) 재분석 → 내부 IP 외 탐지 1,000건(룰당 저장 상한)·216건(내부 IP 설정에 10.40.x.x 대역이 없어서), 홈페이지(24) → DLP 전용 룰 영향 없음. 화면: ADMIN·MANAGER·USER 룰 상세(찾는 방식·대상 줄 조건), ADMIN 수정 창 매칭 방식 '내부 IP 외 접속', 업로드 29 보고서 🔴 카드 3개·내부 IP 기준 안내. 스크립트·HTTP 오류 0건.
- 탐지 엔진: seed 룰 19개 정규식 컴파일 성공. 샘플 로그 11라인에서 9개 룰이 기대대로 탐지되었고, 임계 건수(5건 이상)도 확인했습니다. ReDoS 패턴 `(a+)+$`는 203ms에서 안전하게 중단됩니다.
- **분석 결과 상세 화면 '로그 분석 및 검토' 요약 카드 (추가분)**: 서버·DB·SQL·API 변경 없음(`detail.html`·`seclog.css` 만, 기존 API `/uploads/{id}`·`/uploads/{id}/dlp-report`·`/uploads/{id}/access`·`/uploads?targetYm=…&activeOnly=true&latestOnly=true`·`/systems?activeOnly=true` 사용). [분석 결과 (접속 행위·이상징후)]에서 시스템을 눌러 들어가는 상세 화면(`detail.html?id=`) 제목 → **'로그 분석 및 검토'**, 제목 오른쪽에 **분석 기간(연월)·시스템** 필터(기존 '다른 로그 보기' 선택 칸은 이 필터로 대체해 숨김). 제목 아래 **요약 카드 4개 가로 배치**: 분석 대상(시스템명 20px, 줄바꿈 허용) / 분석 완료(분석한 로그 건수, DLP = 보고서가 읽은 행 수, 그 외 = 분석 라인 수) / 검토 대상(주황, 담당자 확인이 필요한 전체 항목 수 — 검토 완료 포함) / 검토 완료(파랑, 'N건 / 전체건' + 아래 작은 '미검토 N건' 링크). 검토 대상 항목 = DLP: 점검 보고서 항목(룰 × 계정, 묶인 탐지가 모두 검토되면 완료) · 그 외 시스템: 탐지된 룰(미검토 탐지 0건이면 완료) + 위험 표시 계정(검토 기록 있으면 완료). 디자인: 화면 배경 옅은 회색(#f3f4f6), 흰색 카드 + 1px 회색 테두리 + 모서리 12px, 카드 제목 14px, 주요 값 30px 굵게, 카드 높이·간격(16px) 통일, 그래프·장식 없음, 폭 1100px 이하 2열 · 560px 이하 1열. 동작: 분석 기간·시스템 변경 → 그 달·그 시스템의 최신 로그로 바뀌며 카드 값 갱신(로그가 없으면 카드 '-' + '분석 로그 없음 → 로그 올리기' 안내, 아래 결과 영역 숨김), 시스템 목록은 사용 중 시스템(해당 달 로그 없으면 '(로그 없음)', 지금 보는 로그가 사용 안 함 시스템이면 '(사용 안 함)'). [검토 대상] 카드 → 전체 목록, [검토 완료] 카드 → 완료 항목만, [미검토 N건] → 미검토 항목만(목록 안 전체·검토 완료·미검토 전환, 닫기). 목록 행 버튼: DLP [보기](보고서 해당 항목으로 이동·강조) · [검토하기/검토 수정](기존 항목 검토 창), 그 외 [로그 보기]·[룰 일괄 검토]·[계정 보기 / 검토]. 검토 버튼은 MANAGER·ADMIN 만, 최종 검토완료된 로그는 표시 안 함. 항목 검토 저장 시 카드 값 바로 갱신. 카드 숫자는 미리보기 DB 의 테스트 데이터 기준(요청서 예시 'DLP / 12,450건 / 8건 / 5건 / 8건 / 미검토 3건'과 같은 형식으로 표시, 고정 값이 아니라 필터 조건의 실제 집계). 검증: ADMIN·MANAGER·USER 업로드 29(10월 DLP) → 'DLP / 40건 / 3건 / 0건 / 3건 / 미검토 3건', 카드 제목 14px·값 30px·시스템명 20px·주황 rgb(234,88,12)·파랑 rgb(37,99,235)·카드 높이 4개 모두 150px·배경 rgb(243,244,246). 검토 대상 클릭 3행 / 검토 완료 0행('검토 완료 항목 없음') / 미검토 3행, USER 는 검토 버튼 0개, ADMIN [검토하기] → 검토 창 열림. 기간 2026-09 → 업로드 27로 이동 'DLP / 9,577건 / 25건 / 0건 / 25건', 2026-11 → 'DLP (로그 없음)'·카드 '-'·안내 표시, 2026-10 → 최신 업로드 30 '40건 / 2건 / 2건 / 2건 / 미검토 0건', 검토 완료 클릭 2행('정상 확인'). 업로드 24(홈페이지) → '49건 / 10건 / 0건 / 10건'(탐지 룰 4 + 위험 계정 6), 시스템 칸 '홈페이지 (사용 안 함)'. 폭 900px 2열 · 420px 1열. 스크립트·HTTP 오류 0건.
- **'로그 분석 및 검토' 화면에 룰셋별 분석 결과 표 추가 + DLP 관리자 로그 점검 보고서 화면 삭제 (추가분)**: `14_rule_run.sql` 을 2회 연속 실행해 정상 동작 확인(`sec_log_rule_run` 컬럼 18개 COMMENT 누락 0건, Entity `@Column` 18개 = DB 컬럼 18개, FK 는 sec_log_upload 만). 엔드포인트 1개 추가(`GET /uploads/{id}/rule-results`, ADMIN·MANAGER·USER). ① **분석 엔진**: 룰마다 실행 결과(패턴 일치 줄 수·저장 건수·시간 초과 줄 수·실패 사유) 기록. **분석 실패** = 패턴 검사 시간 초과 줄 발생(그 줄은 판정 불가) 또는 업로드당 탐지 저장 상한(20,000건) 초과로 결과 미저장. 분석·재분석 때 `sec_log_rule_run` 에 룰마다 1행 저장(탐지 0건 룰 포함), 재분석·업로드 삭제 시 소프트 삭제. 탐지 결과·검토·마감 방식은 그대로. ② **룰셋별 분석 결과 API**: 적용 룰마다 상태(DONE 분석 완료 / FAILED 분석 실패 / NOT_RUN 미분석) + 검토 대상(저장된 탐지 건수) + 검토 완료(검토 기록 있는 탐지 건수). 기록이 없는 룰 중 지금 적용 중인 룰은 **미분석**(분석 이후 추가·수정·사용 전환한 룰). `14` 적용 전에 분석한 업로드는 기록이 없으므로 탐지가 있는 룰 + 분석 이후 룰·시스템 룰셋 설정이 바뀌지 않은 룰 = 분석 완료로 판정, 나머지는 미분석. ③ **화면** (`detail.html`·`seclog.css`): 상단 요약 카드 4개·메뉴·필터는 그대로, 카드 바로 아래 **'룰셋별 분석 결과' 표** — 열: 분석 룰셋 / 검토 대상 / 검토 완료, 현재 선택한 시스템·분석 기간(= 보고 있는 업로드) 기준. 검토 대상 0건인 분석 완료 룰도 표시('0건' · 검토 완료 '—'), 미분석·분석 실패 룰은 0건으로 표시하지 않고 검토 대상 칸에 '미분석'·'분석 실패' 표시(검토 완료 '—', 표 아래에 개수와 사유·'[재분석] 시 반영' 안내). 건수 기준: DLP = 룰 × 계정 묶음(요약 카드 '검토 대상'과 합계 일치), 그 외 시스템 = 탐지 건수(표 아래 한 줄 안내). 순서: 검토 대상 있는 룰(많은 순) → 분석 실패 → 0건 → 미분석. 디자인: 흰색 배경·1px 옅은 회색 테두리·모서리 12px, 룰셋명 왼쪽·건수 가운데 정렬, 검토 완료 건수 파랑, 0건·'—' 회색, 행 위아래 여백 16px(행 높이 약 56px). 클릭 동작·상세 화면 없음(요청 범위). 로그 없는 달을 고르면 표도 숨김. 항목 검토 저장 시 표도 바로 갱신. ④ **DLP 관리자 로그 점검 보고서 화면 삭제**: 보고서(한눈에 보기·검토 현황·🔴 카드·🟡🟢 표·룰별 결과·계정별 요약·분석 한계)와 근거 로그 창, 바로가기 [점검 보고서]·[검토 현황], 카드 목록의 [보기] 버튼 삭제. DLP 화면은 요약 카드 + 룰셋별 분석 결과 + 카드 클릭 목록(검토하기)만 표시하고, 다른 로그 고르기 줄은 숨김(분석 기간·시스템 필터로 대체). 서버의 보고서 API(`/dlp-report`)는 요약 카드·검토 항목 데이터로 계속 사용(화면에서만 삭제). 예전 `#access`·`#detect` 링크로 들어오면 DLP 는 룰셋별 분석 결과로 이동. 검증 (미리보기 테스트 데이터): 업로드 29(10월 DLP) 재분석 → 룰 기록 15행(DONE 15, 저장 3건). DLP 전용 테스트 룰 추가 → 29 표에 '테스트 미분석 룰 / 미분석 / —', 9월(27, 기록 없음)도 미분석. 시간 초과 패턴 `(.*.*)*@@@@` 테스트 룰 + 재분석 → '분석 실패 · 패턴 검사 시간 초과 18줄'. 테스트 룰 2개 삭제(소프트) + 29 재분석으로 원래대로(탐지 3건). 화면: ADMIN·MANAGER·USER 업로드 30(10월 DLP 최신) → 카드 'DLP / 40건 / 2건 / 2건 / 2건 / 미검토 0건', 표 15행(첫 행 '내부 IP 외 접속 (모든 행위) / 2건 / 2건', 나머지 '0건 / —'), 보고서 영역·문구 0건, 메뉴 유지. 표 배경 rgb(255,255,255)·테두리 rgb(229,231,235) 1px·모서리 12px·룰셋명 왼쪽·건수 가운데·검토 완료 rgb(37,99,235)·0건 rgb(156,163,175). 9월 DLP → 카드 25건, 표 첫 행 '반복 인증 실패 / 5건 / 0건'(룰별 합 25 = 카드). 11월(로그 없음) → 표 숨김. 업로드 29 → '내부 IP 외 접속 / 3건 / 0건'. 업로드 24(홈페이지) → 표 18행(첫 행 '관리자 비밀번호 본인 변경 / 5건 / 0건'), 기존 접속 행위 분석 영역 유지. 검토 완료 카드 클릭 목록 2행(기존 동작 유지). 폭 420px 표 정상 표시. USER 조회 200. 스크립트·HTTP 오류 0건.
- **룰셋 분석 근거 화면 — 룰셋을 눌러 왜 검토 대상인지 확인 (추가분)**: DB·SQL 변경 없음(조회 전용). 엔드포인트 1개 추가(`GET /uploads/{id}/rule-results/evidence?ruleCode=`, ADMIN·MANAGER·USER). 새 화면 `rule-evidence.html?id={업로드}&rule={룰 코드}`. ① **이동**: '로그 분석 및 검토 > 룰셋별 분석 결과' 표에서 **검토 대상이 있는 룰셋 행만** 클릭 가능(룰셋명 링크 + 행 아무 곳 클릭, 오른쪽 화살표 표시). 0건·미분석·분석 실패 행은 클릭 안 됨. 표 아래 안내 1줄 추가. ② **화면 구성** (기존 화면과 같은 흰 카드·회색 테두리·둥근 모서리): 룰 요약(위험도·공통/전용·분석 상태, 룰 이름, 시스템·기간·파일·분석 일시 + 검토 대상 / 검토 완료 / 일치한 로그 카드 3개) → **왜 검토 대상인가**(개조식: 탐지 조건 → 일치한 로그 N줄 → 탐지 기준 'N줄 이상' 충족 → 검토 대상 등록 → 검토 대상 N건 계산 방식, 외부 IP 룰은 내부 IP 기준 대역 표시) + 조건 표(룰 설명·점검 포인트 / 찾는 방식 / 키워드·정규식·대상 줄 조건 / 내부 IP 기준 / 탐지 기준 / 룰 코드 + 룰셋 편집 링크) → **근거 로그** 표(DLP: 원본 엑셀 행 · 발생 일시 · 계정 · IP · 내용·상세 · 걸린 부분 · 검토 / 그 외: 로그 줄 · 원문 · 걸린 부분 · 검토). 룰에 걸린 부분은 **노란색 표시**(키워드·정규식 일치 부분, 외부 IP 룰은 IP), [원문]을 누르면 분석 엔진이 본 원래 줄 표시. 거르기: 전체 / 미검토 / 검토 완료, 계정(DLP·계정 2개 이상), 내용 검색, 50줄씩 쪽 나눔. [로그 분석 및 검토로] 버튼은 보던 업로드의 룰셋별 분석 결과 위치로 돌아감. ③ **판정 기준**: 검토 대상·검토 완료 숫자는 룰셋별 분석 결과 표와 같은 기준(DLP = 계정별 묶음, 그 외 = 탐지 줄). 걸린 부분은 **현재 룰·내부 IP 설정**으로 다시 확인해 표시(`LogAnalysisEngine.explain`, 저장된 탐지 원문 기준) → 분석 이후 룰 수정·삭제 또는 내부 IP 설정 변경이 있으면 노란 안내 상자에 '현재 기준 표시, [재분석] 시 반영', 다시 확인해 일치하지 않는 줄은 '현재 기준 불일치'로 표시(검토 대상은 분석 당시 기준 그대로). 룰당 저장 상한(1,000건)에 걸리면 '앞 N줄만 검토 대상' 안내. 검토 저장·클릭 동작 없음(조회 전용, 검토는 기존 화면에서). 검증 (미리보기 테스트 데이터): ADMIN·MANAGER·USER 9월 DLP(업로드 27) 표 링크 10 / 15행(0건 행 링크 0개), '반복 인증 실패' 행 숫자 칸 클릭 → 근거 화면 이동, 카드 '검토 대상 5건(계정별 묶음 · 탐지 56줄) / 검토 완료 0건 / 일치한 로그 56줄(탐지 기준 10줄 이상)', 이유 3줄, 근거 로그 56줄(첫 행 엑셀 48행 op01 '로그인 실패' 노란 표시), 미검토/검토 완료 거르기(검토 완료 0줄), 계정 거르기 13줄, 검색 'IP 제한' 19줄, 돌아가기 → `detail.html?id=27#lrRules`. 같은 업로드 '내부 IP 외 접속' → 1,000줄·11개 쪽 버튼·내부 IP 기준 '본사 서울 공인 IP 대역 192.0.0.0/26'. 10월 DLP(업로드 30) '내부 IP 외 접속' → 검토 대상 2건 / 검토 완료 2건 / 일치 3줄, 엑셀 9행 SMSScheduler 19.1.1.2 · 10행 jypark 192.0.0.64 · 11행 jypark 192.0.0.66 IP 노란 표시 + '내부 대역 밖', 검토 '정상 확인'·소명 표시. 홈페이지(업로드 24) '관리자 비밀번호 본인 변경' → 검토 대상 5건(탐지된 로그 줄), 원문 5줄 'ajaxChangeAdminPassword' 표시. 일치 0줄 룰 API → '일치한 로그 0줄 → 검토 대상 없음', 없는 룰 → '요청한 리소스를 찾을 수 없습니다' 안내. 폭 420px 정상 표시. 스크립트·HTTP 오류 0건.
- **'로그 올리기' 카드 클릭 시 로그 올리기 창 표시 (추가분)**: 서버·DB·SQL·API 변경 없음(화면만, 기존 `POST /uploads` 사용). ① 대시보드에만 들어 있던 업로드 창을 공통 파일 `js/upload-modal.js`(`SECLOG_UPLOAD.open({ ym, systemId, onDone })`)로 옮겨 **대시보드와 월별 점검 현황에서 같은 창**을 띄움(창 내용·검사 규칙은 그대로: 대상 연월·시스템·로그 유형·인코딩·비고·파일 끌어 놓기, 확장자 .xlsx .xls .csv .tsv .log .txt .json .out, 텍스트 50MB·엑셀 20MB). 창 제목 '로그 업로드' → '로그 올리기', 결과 화면 버튼 '분석 결과 보기 / 검토'(상세 화면으로 이동), 업로드 중에는 버튼에 '업로드 및 분석 중...' 표시·닫기 잠금. 점검 대상 시스템이 1개뿐이면 자동 선택, 대상 연월은 보고 있는 달. ② **진행 3단계의 '① 로그 올리기' 카드**: 관리자·담당자(ADMIN·MANAGER)는 카드를 누르면 로그 올리기 창이 뜸(대시보드·월별 점검 현황 모두). 카드 아래 안내도 '로그 올리기'로 표시(모두 올린 달은 '완료 · 로그 올리기' — 같은 달 파일 교체 가능). 조회 사용자(USER)는 올릴 권한이 없으므로 기존처럼 대시보드는 월별 점검 현황으로 이동, 월별 점검 현황은 표 거르기. 대시보드 상단 [로그 올리기] 버튼과 시스템별 [로그 올리기] 버튼도 같은 공통 창 사용. 올리기가 끝나면 화면 숫자 다시 불러옴(다른 달로 올렸으면 그 달로 이동). 검증: ADMIN·MANAGER 대시보드(2026-10) 카드 클릭 → 창 열림(대상 연월 2026-10, 시스템 DLP 자동 선택, 로그 유형 '정보보호솔루션 로그', '2026년 10월 로그를 올려 주세요'), 파일 없이 제출 → '로그 파일을 선택하세요' 안내, Esc 로 닫힘, 상단 버튼도 같은 창(창 1개만 생성). ADMIN 테스트 업로드(2026-11, DLP, u29.xlsx) → '분석 줄 수 42 · 탐지 3 · 높음 3', 대시보드가 2026년 11월로 이동 → 테스트 업로드 31은 화면의 삭제 API 로 소프트 삭제(탐지·룰 분석 기록도 함께 소프트 삭제 확인). 월별 점검 현황 카드 클릭 → 같은 창(대상 연월 2026-10). USER → 창 안 뜸, 대시보드 카드 = 월별 점검 현황으로 이동, 월별 점검 현황 카드 = 표 거르기. 스크립트·HTTP 오류 0건.
- **'② 분석 결과 검토' 진행률을 사용 중 시스템 기준으로 변경 + 카드 클릭 시 분석 결과 메뉴로 이동 (추가분)**: 서버·DB·SQL·API 변경 없음(`dashboard.html`·`status.html` 만, 기존 대시보드·월별 현황 API 값 사용). ① **진행률 기준 변경**: 예전에는 '올라온 로그의 탐지·위험 계정 중 검토한 비율'이라 로그를 안 올린 시스템이 있어도 올라온 로그만 다 검토하면 100% 로 표시됨 → **검토 완료 시스템 수 / 사용 중(점검 대상) 시스템 수**로 변경. 검토 완료 시스템 = 그 달 로그를 올렸고 (최종 검토완료(마감) 했거나 미검토 탐지 0건 + 미검토 위험 계정 0개) — 월별 점검 현황 표의 '② 분석 결과 검토 완료'와 같은 기준. **로그를 안 올린 시스템은 미완료**로 세므로 사용 중 시스템이 모두 검토를 마쳐야 100%. 카드 설명: '검토 남은 시스템 N개 (로그 미등록 N개 포함) · 미검토 탐지 N건 · 위험 계정 N개'(해당 값이 있을 때만), 모두 끝나면 '모든 시스템 분석 결과 검토 완료'. 대시보드·월별 점검 현황 같은 계산. ② **카드 클릭 → 분석 결과 (접속 행위·이상징후) 메뉴 화면**(`detail.html?ym={보고 있는 달}`, 시스템별 로그 고르기 화면)으로 이동(예전: 월별 점검 현황 '검토 중' 거르기). 카드 아래 안내 '분석 결과 보기 ›'(완료 달은 '완료 · 분석 결과 보기 ›'). 권한 구분 없이 모두 같은 동작(분석 결과 화면은 전원 조회 가능). 검증 (미리보기: 사용 중 시스템 = 임직원몰 + DLP): ADMIN 대시보드·월별 점검 현황 2026-10 → 예전 100% → **50%**·'검토 남은 시스템 1개 (로그 미등록 1개 포함)'(DLP 검토 끝, 임직원몰 로그 미등록), 2026-11 → 0%·'로그 미등록 2개 포함', 2026-09 → 0%·'미검토 탐지 1,255건 · 위험 계정 0개'. 카드 클릭 → `detail.html?ym=202609`·화면 제목 '분석 결과 (접속 행위·이상징후)'·메뉴 강조·로그 고르기 화면. MANAGER·USER 2026-10 → 50%, 클릭 → `detail.html?ym=202610`. 스크립트·HTTP 오류 0건.
- **개인정보 다운로드 검토 — 자사몰·임직원몰 (추가분, A안)**: `15_pii_download.sql` 을 3회 연속 실행해 정상 동작 확인(`sec_log_pii_download` 컬럼 25개 COMMENT 누락 0건, Entity `@Column` 25개 = DB 컬럼 25개, FK 는 sec_log_upload 만 / `sec_log_upload` 컬럼 33개 = Entity `@Column` 33개). 엔드포인트 2개 추가(`GET /uploads/{id}/pii-downloads` ADMIN·MANAGER·USER, `PUT /uploads/{id}/pii-downloads/review` ADMIN·MANAGER). ① **대상**: 시스템 코드 `SVC_MALL`(자사몰)·`SVC_EMP_MALL`(임직원몰)만. 다른 시스템은 화면에 표시 안 함, 검토 저장 400. ② **읽는 방법**: 로그 분석(첫 번째 탭, 기존 그대로)과 별도로 올린 엑셀의 모든 탭 중 **탭 이름 또는 1~3행 제목에 '개인정보'와 '다운로드'가 모두 있는 탭**을 찾아, 앞 10행 중 '일시' 열 + '운영자ID(아이디·계정)' 열이 있는 행을 머리글로 보고 그 아래 1행 = 다운로드 1건 저장(일시·운영자 ID 둘 다 빈 행 제외, 최대 20,000건). 열 이름 인식: 접속일시 / 접속IP / 운영자ID / 접속페이지(메뉴) / 수행업무 / 파일대상 / 다운로드 양식명(파일명) / 사유·목적(있을 때만). 일시 `2026/10/30 (15:34)`·`2026-10-30 15:34:00`·`2026.10.30` 형식 읽음(못 읽으면 원문만 표시). 탭이 없으면 '탭 없음'으로 기록(분석은 막지 않음), 텍스트 파일은 '탭 없음'. 업로드·재분석 때 읽고, 재분석·업로드 삭제 시 이전 행 소프트 삭제(검토 초기화). ③ **검토**: 다운로드 1건마다 정상 업무(사유 확인) / 사유 불명확·조치 필요 / 조치완료 + 다운로드 사유·확인 내용(모두 필수), 여러 건 선택 일괄 저장, 미검토로 되돌리기. **미검토가 남으면 최종 검토완료(마감) 불가**. ④ **화면**: 상세 화면(자사몰·임직원몰만) '① 접속 행위 분석' 아래 **'개인정보 다운로드 검토'** 영역(요약 칸 5개 · 안내 · 선택 검토 막대 · 표: 행 / 다운로드 일시 / 운영자 ID / 접속 IP / 접속 페이지 / 수행 업무 / 파일 대상 / 다운로드 양식명 / 검토), 바로가기 [개인정보 다운로드]. 상단 요약 카드 '검토 대상·검토 완료'와 카드 목록에 다운로드 건 포함(목록 [보기 / 검토] → 그 건 선택된 상태로 이동). 대시보드 '② 분석 결과 검토' 진행률·할 일('개인정보 다운로드 사유를 확인하세요', 탭 없음 참고)·검토 현황 다음 할 일, 월별 점검 현황 ② 완료 판정·[다운로드 사유 확인 (N건)] 버튼에 반영. 검증 (사용자 제공 `테스트파일.xlsx`, 탭 2개: 'Sheet1' 임직원몰 admin계정 접속기록 / 'Sheet2' 개인정보다운로드내역 — 탭 이름은 Sheet2 지만 1행 제목으로 찾음): 임직원몰 2026-10 업로드 32 → `PII_SHEET_STATUS=FOUND`·`Sheet2`, 다운로드 1건(3행 · 2026-10-30 10:09 · test1 · 19.0.0.8 · '111_임직원몰_26.10 정산', 빈 칸 3개는 '-'). 미검토 상태 마감 → 400 '검토하지 않은 개인정보 다운로드가 1건 …', USER 저장 403, 사유 없이 저장 400, MANAGER 정상 업무 저장 → 미검토 0 → 대시보드 할 일 'READY_TO_CLOSE 탐지·위험 계정·개인정보 다운로드 검토 끝' · ② 100%(임직원몰·DLP) → ADMIN 마감 성공 → 마감 후 검토 변경 400(잠김) → 검토취소 → 재분석 → 행 새로 읽음(이전 행 소프트 삭제, 미검토 1). DLP 업로드 30 → `target=false`, 검토 저장 400. 화면(ADMIN·MANAGER·USER): USER 영역 표시·검토 막대·선택 칸 없음, MANAGER 카드 목록 '개인정보 다운로드 / 111_임직원몰_26.10 정산 / test1 · 2026-10-30 10:09 · 3행' → [보기 / 검토] → 1건 선택, 사유 없이 저장 → 안내, 저장 → '정상 업무 · 사유 · 일시 · 검토자 ID 2'·카드 '검토 완료 1건 / 1건'. 스크립트·HTTP 오류 0건. **참고**: 같은 파일의 'Sheet1'(admin계정 접속기록, 1행 제목 + `2026/10/30 (15:34)` 형식)은 기존 접속 행위 분석이 로그인 이벤트로 인식하지 못해 접속 계정 0개(요청 범위 밖이라 변경 안 함). 자사몰은 현재 [점검 대상 시스템]에서 '사용 안 함'이라 대시보드·월별 현황에 표시 안 됨(사용으로 바꾸면 같은 방식으로 동작).
- **개인정보 다운로드 이력 → '개인정보 엑셀 다운로드' 룰셋 연동 + 룰셋별 분석 결과 위험도 열 + 상세 화면 예전 영역 삭제 (추가분)**: DB·SQL 변경 없음, 엔드포인트 수 변경 없음(47개 그대로, 기존 응답에 필드만 추가). ① **룰셋 연동**: 자사몰 `MALL_MEMBER_EXPORT`(회원 개인정보 엑셀 다운로드)·임직원몰 `EMALL_MEMBER_EXPORT`(개인정보 엑셀 다운로드)를 개인정보 다운로드 이력과 연결(`SecLogPiiDownloadService.LINKED_RULE`). `GET /uploads/{id}/rule-results` 의 이 룰 행에 다운로드 건수를 더함(검토 대상 = 탐지 건수 + 다운로드 건수, 검토 완료 = 검토한 탐지 + 사유 검토한 다운로드), 행에 `piiLinked`·`piiCount` 추가. 다운로드 내역 탭을 읽은 업로드는 이 룰을 '분석 완료'로 표시. `GET /uploads/{id}/rule-results/evidence` 에 `pii`(다운로드 이력 + 검토 상태, 연동 룰일 때만) 추가, '왜 검토 대상인가'에 '개인정보 다운로드 이력 연동: 'Sheet2' 탭 다운로드 N건 → 건마다 사유 검토 대상' 표시. `GET /uploads/{id}/pii-downloads` 에 `ruleCode`(연동 룰) 추가. 마감 조건·대시보드·월별 현황 계산은 그대로. ② **룰셋 분석 근거 화면**: 연동 룰셋을 누르면 **'개인정보 다운로드 이력'** 칸 — 행 / 다운로드 일시 / 운영자 ID / 접속 IP / **다운로드한 개인정보 파일**(다운로드 양식명) / **검토 여부**(상태·사유·검토 일시·검토자 ID), 전체·미검토·검토 완료 거르기, 담당자·관리자는 선택 후 정상 업무 / 사유 불명확·조치 필요 / 조치완료 + 사유 저장(조회 사용자는 보기만, 마감한 로그는 잠김). 요청대로 파일 대상 상세 열(접속 페이지·수행 업무·파일 대상)은 표시 안 함. 상단 카드에 '개인정보 다운로드 N건 · 미검토 N건' 추가, 탐지 로그가 없으면 근거 로그 칸 숨김. ③ **룰셋별 분석 결과 표에 '위험도' 열** 추가(분석 룰셋 / 위험도 / 검토 대상 / 검토 완료, 룰셋에 설정한 위험도 배지 심각·높음·보통·낮음). 같은 그룹 안에서는 위험도 높은 순. 연동 룰셋 이름 아래 '개인정보 다운로드 이력 N건 연동' 표시, 표 아래 안내 2줄. ④ **상세 화면 예전 영역 삭제**: 업로드 정보 · ① 접속 행위 분석(계정 표) · 개인정보 다운로드 검토 · ② 이상징후 탐지(요약 칸·룰별 탐지 건수·검토 현황) · 탐지 결과 목록 · '다른 로그 보기' 줄 삭제(모든 시스템). 화면은 요약 카드 4개 + 룰셋별 분석 결과 + 카드 클릭 목록만 표시. 검토는 그대로 가능: 탐지 → 룰셋 클릭(근거 화면) 또는 목록 [룰 일괄 검토], 위험 계정 → 목록 [계정 보기 / 검토] → **위험 계정 검토 창**(계정 정보 + 검토 결과·소명), 개인정보 다운로드 → 목록 [보기 / 검토] → 연동 룰셋 근거 화면(그 건 강조·선택). 예전 링크: `#pii` → 연동 룰셋 근거 화면, `#access`·`#detect` → 미검토 목록 열림. 검증: ADMIN·MANAGER·USER 업로드 32(임직원몰)·30(DLP)·24(홈페이지) → 예전 영역 문구 0건, 표 머리글 4칸, 32 첫 행 '개인정보 엑셀 다운로드 · 개인정보 다운로드 이력 1건 연동 / 높음 / 1건 / 0건', 30 '내부 IP 외 접속 / 높음 / 2건 / 2건', 24 '관리자 비밀번호 본인 변경 / 보통 / 5건 / 0건'. 연동 룰셋 클릭 → 다운로드 이력 1행(3행 · 2026-10-30 10:09 · test1 · 19.0.0.8 · 111_임직원몰_26.10 정산 · 미검토), USER 검토 막대·선택 칸 없음, MANAGER 사유 없이 저장 → 안내, 정상 업무 저장 → '정상 업무 · 10월 정산 업무 (담당자 확인) · 검토자 ID 2'·카드 검토 완료 1 / 1 → 상세 표 '1건 / 1건'·요약 카드 '1건 / 1건'. 카드 목록 [보기 / 검토] → `rule-evidence.html?…#pii-3`, `detail.html?id=32#pii` → 근거 화면, 홈페이지 위험 계정 창 열림(user4). DLP 30 은 연동 없음. 스크립트·HTTP 오류 0건.
- **ISMS 로그 점검 하위 메뉴 'DLP 모니터링' 추가 (추가분, 이전 버전 기록 — 아래 'DLP 모니터링 독립 메뉴'로 대체)**: 서버·DB·SQL·API 변경 없음(엔드포인트 47개 그대로, 화면 `dlp.html`·`seclog.css` 만 추가 — 기존 API `/systems?activeOnly=true`·`/uploads?latestOnly=true`·`/uploads/{id}/dlp-report`·`/uploads/{id}/rule-results` 사용). 메뉴 seed SQL 은 규칙(core 테이블 미수정)에 따라 작성하지 않음 → 운영자가 5장 표의 `SECLOG_DLP` 값으로 등록(상위 ISMS 로그 점검, **탐지 룰셋 편집 바로 아래**, URL `/security_log/dlp.html`, ROLE_ADMIN·MANAGER·USER). 미리보기 DB 에만 같은 값으로 임시 등록해 확인(MENU_ID 35, SORT_ORDER 3). ① **화면 구성**: 제목 'DLP 모니터링' + 분석 기간(연월) 선택(DLP 시스템이 2개 이상이면 시스템 선택도 표시), 요약 카드 4개(분석 대상 = 시스템·파일명 / 분석 로그 = 행 수·로그 기간 / 관리 계정 = 계정 수·룰셋에 걸린 계정 수 / 검토 대상·검토 완료 = 룰 × 계정 묶음 + 🔴🟡🟢 수·미검토 수), **룰셋별 분석 결과**(분석 룰셋 / 위험도 / 검토 대상 / 검토 완료 — 분석 결과 화면과 같은 기준, 검토 대상 있는 룰셋 클릭 → 룰셋 분석 근거 화면), **계정별 활동**(계정·이름 / 활동 행 수 / 🔴 / 🟡 / 🟢 / 요약, 🔴 많은 순). 버튼: [DLP 로그 올리기](ADMIN·MANAGER, 공통 업로드 창 · DLP 미리 선택) · [분석 결과에서 검토하기](그 달 DLP 로그 상세 화면). 대상 = [점검 대상 시스템]에서 사용 중인 시스템 중 코드·이름에 'DLP' 가 들어간 시스템. 로그 없는 달은 카드 '-' + '분석 로그 없음' 안내, DLP 시스템이 없으면 등록 안내, DLP 감사 로그 형식이 아니면 사유 표시. 검토 저장 기능은 두지 않음(기존 분석 결과 화면과 중복 방지). ② 검증(미리보기 테스트 데이터): ADMIN·MANAGER·USER 로그인 → 사이드바 ISMS 로그 점검 하위 메뉴 순서 'ISMS 로그 점검 현황 · 탐지 룰셋 편집 · **DLP 모니터링**' → 클릭 시 iframe 으로 `dlp.html` 열림. 10월: 'DLP · DLPTEST10월.xlsx / 40행 · 10/1 ~ 10/30 / 3개 · 룰셋에 걸린 계정 2개 / 2건 · 완료 2건 · 🔴 2 · 모두 검토', 룰셋 첫 행 '내부 IP 외 접속 (모든 행위) / 높음 / 2건 / 2건', 계정 jypark(박진영) 25행 🔴1 · SMSScheduler 1행 🔴1 · yjpark(김유정) 14행 '탐지 없음'. 9월: '9,577행 · 5개 · 25건 · 완료 0건 · 🔴 15 · 🟡 7 · 🟢 3 · 미검토 25건'. 11월: '2026년 11월 · DLP 분석 로그 없음' + 본문 숨김. 룰셋 행 클릭 → `rule-evidence.html?id=30&rule=DLP_EXTERNAL_IP_ACCESS`. USER 는 [DLP 로그 올리기] 없음. 스크립트·HTTP 오류 0건.
- **DLP 모니터링 — 월 1회 유출 로그 업로드 · 기준 적용 (추가분, 이전 버전 기록 — 기준을 ISMS 탐지 룰셋 `DLP_LEAK` 에 두던 방식은 아래 개편으로 대체·되돌림)**: `16_dlp_leak.sql` 을 2회 연속 실행해 정상 동작 확인(테이블 3개 컬럼 26 / 12 / 32개 COMMENT 누락 0건, Entity `@Column` 26 / 12 / 32개 = DB 컬럼, FK 는 sec_log_dlp_leak_upload 만). 엔드포인트 6개 추가(47 → 53개, 4-3-1-4). 보고서 양식은 받은 뒤 작성(이번 범위 = 보고서 직전까지). ① **업로드**: DLP 모니터링 화면 [유출 로그 올리기](ADMIN·MANAGER) → 모니터링 월 + 파일(xlsx·xls·csv, 50MB·20만 건 이하) + 메모. 모든 탭의 앞 15행에서 '발생 일시' + '사용자 아이디/이름' 머리글을 찾아 그 아래 1행 = 유출 로그 1건 저장. 열 이름 인식(공백 무시): 발생 일시·대응 행동·부서 이름·사용자 이름·사용자 아이디·에이전트 IP·채널·서브 채널·파일 크기·파일 목록·내용·상세 내용·정책 이름·상태·이동식 저장 매체 등록 여부·컴퓨터 이름, '… 개수' 열 15개 → 개인정보 합계·내역(개수만). **'… 샘플 데이터' 열 15개(실제 개인정보 값)는 저장하지 않음**. 배열 수식 셀(AI 열)은 계산 실패 시 저장된 값 사용. 원본은 GZIP 으로 보관(재분석용), 같은 달·같은 내용 파일 409, 다른 형식 400. 같은 달에 여러 번 올리면 **가장 최근 파일 1개만 집계**. ② **기준 적용**: 기준 = [관리 › 탐지 룰셋 편집]의 로그 유형 **'DLP 유출 로그 (DLP 모니터링 기준)'**(`DLP_LEAK`, 공통 룰만 — 시스템 전용 선택 시 400, 화면에서 이 유형을 고르면 '공통 룰'로 고정). 유출 로그 1건을 `열 이름=값 | … | 개인정보 합계=N` 한 줄로 만들어 기존 탐지 엔진(키워드·정규식, 시간 초과 보호)으로 검사, 탐지 기준(임계치)은 파일 단위. 기본 기준 6개(예시): 개인정보 포함 파일 반출(높음) · 미등록 이동식 저장매체로 반출(높음) · 이동식 저장매체(USB) 반출(보통) · 업무시간 외 반출 22시~06시(보통) · 차단된 반출 시도(보통) · 대용량 파일 반출 100MB 이상(보통). 기준을 바꾸면 화면에 '기준이 바뀜 → [재분석]' 안내, 재분석은 보관 원본을 다시 읽음. **ISMS 대시보드·월별 점검 현황 진행률에는 넣지 않음**(DLP 모니터링 메뉴에서만 관리), DLP_LEAK 은 일반 로그 업로드·시스템 기본 유형으로 선택 불가. ③ **화면 (`dlp.html` 전면 교체 — 이전 DLP 관리자 감사 로그 요약 대신 유출 로그 전용)**: 모니터링 월 선택, 요약 카드 4개(유출 로그 건수·기간 / 기준 해당 건 / 사용자·부서 수 / 개인정보 포함·차단 건), **기준별 결과**(기준 / 위험도 / 해당 건 / 사용자, 클릭 → 목록이 그 기준으로 걸러짐, [기준 편집] → 룰셋 화면 DLP 유출 로그 필터), **사용자별 현황**(유출 로그·기준 해당·개인정보 포함·최고 위험도·해당 기준, 클릭 → 그 사용자 목록), 채널별·대응 행동별 건수, **유출 로그 목록**(기준 해당만·기준·채널·사용자·내용 검색, 50건씩, 기준 해당 행 노란색), **이 달에 올린 파일**(집계 기준/이전 파일, 원본 내려받기, ADMIN 삭제). 검증(사용자 제공 `DLP 모니터링 예시.xlsx`, Sheet1 · 52열 · 1건): MANAGER 업로드 201 → 2026-09 · 유출 로그 1건 · 기준 해당 1건(업무시간 외 반출 — 23:58:19) · 사용자 1명(홍길동 gildong87 · 개발팀) · 기준 6개 적용 · 인식 못 한 열 0개, USER 업로드 403, 같은 파일 다시 409, 형식이 다른 `테스트파일.xlsx` 400, DLP_LEAK 시스템 전용 룰 등록 400. 화면(ADMIN·MANAGER·USER): 카드 '1건 · 9/30 ~ 9/30 / 1건 / 1건 / 1명 / 1개 부서 / 0건 / 차단 0건', 기준별 6행(첫 행 '업무시간 외 반출 / 보통 / 1건 / 1명'), 기준 클릭 → 목록 1건, '개인정보 포함' 기준 → 0건, 채널 Upload 1 · 대응 허용(정책) 1, USER 는 올리기·재분석·삭제 버튼 없음·'기준 보기'. 룰셋 화면 DLP 유출 로그 필터 6개, 새 룰에서 DLP_LEAK 선택 시 공통 고정. 스크립트·HTTP 오류 0건.
- **DLP 모니터링 독립 메뉴 + DLP 전용 룰셋 + 보고서 PPT (추가분, 정보보안 담당자 1명 사용 · 단순화 — DLP 룰셋 부분은 아래 'DLP 룰셋 삭제'로 대체)**: 요청 프로세스 = ① 유출 로그 올리기 → ② DLP 모니터링 룰셋(ISMS 룰셋 아님)으로 분석 → ③ DLP 모니터링 보고서 PPT. ① **ISMS 와 완전 분리**: 바로 앞에서 ISMS 쪽에 넣었던 연결을 모두 되돌림(`LogType.DLP_LEAK`·`LogAnalysisEngine.matchAll`·업로드/시스템/룰 서비스의 DLP_LEAK 검사·`rules.html` DLP 유형 선택지 삭제 → 7fd298c 상태). `16_dlp_leak.sql` 에서 `sec_log_rule` ALTER·DLP_LEAK 룰 6개를 빼고 **DLP 전용 룰 테이블 `sec_log_dlp_rule`** + 기본 룰 6개로 교체, `sec_log_dlp_leak_upload.SYSTEM_ID`(ISMS 시스템 참조) 삭제 → DLP 모니터링은 다른 sec_log 테이블을 참조하지 않음. 화면 `dlp.html` 에서 ISMS 탭(대시보드·월별 점검 현황·분석 결과·점검 대상 시스템·관리) 삭제, 자체 탭 'DLP 모니터링 / DLP 룰셋' 만 표시. 메뉴: 최상위 그룹 **DLP 모니터링**(`DLP_MON_MGMT`) 아래 DLP 모니터링 · DLP 룰셋 (5장, 미리보기 DB 에만 같은 값으로 등록 — MENU_ID 36 그룹, 35 이동, 37 추가). ② **DLP 룰셋** (`dlp-rules.html`, `SecLogDlpRule`·`DlpRuleMatcher`): 정규식 없이 '항목 / 조건 / 값' 으로 작성, 조건 여러 개 = 모두 만족, 값에 쉼표 = 하나라도. 항목 14개(채널·서브 채널·대응 행동·정책·부서·사용자·파일 목록·내용·매체 등록 여부·상태·발생 시각·발생 요일·파일 크기 MB·개인정보 검출 개수), 조건 6개(포함·포함 안 함·같음·이상·이하·시간대). 파일 크기 원문(196.96KB·1.2GB)은 MB 로 바꿔 비교. 룰 코드 자동 생성, 화면·보고서에 조건 한 줄 설명(예: 채널에 'USB / 이동식' 포함 그리고 …). 룰을 바꾸면 DLP 모니터링 화면에 '룰셋이 바뀜 → [재분석]' 안내. ③ **화면 단순화** (`dlp.html`): 맨 위 3단계 카드 ① 올리기 → ② 분석(재분석) → ③ PPT 내려받기, 아래에 요약 카드 4개 · 룰별 결과 · 사용자별 현황 · 유출 로그 목록 · 올린 파일. 채널·대응 행동 표는 화면에서 빼고 보고서에만 넣음. ④ **보고서 PPT** (`SecLogDlpLeakReportService`, Apache POI, 저장 안 함): 16:9 · 7장(표지 / 1.요약 카드 5개 + 요점 / 2.룰별 결과(위험도·조건·해당 건·사용자) / 3.사용자별 현황(상위 14명) / 4.채널·대응 행동별 건수 / 5.룰 해당 상세(10건씩, 최대 50건) / 6.확인 의견(확인 결과·조치 사항·특이 사항 빈칸)). 모든 글자에 맑은 고딕 + 동아시아 글꼴·ko-KR 지정. 사용자 보고서 양식을 받으면 이 서비스만 바꾸면 됨. 엔드포인트 7개 추가(53 → 60개: 보고서 1 + DLP 룰셋 6). 검증: `16` 2회 연속 실행 정상, `sec_log_dlp_rule` 컬럼 15개 COMMENT 누락 0건 · Entity `@Column` 15개 = DB 컬럼(미리보기 DB 의 `sec_log_dlp_leak_upload.SYSTEM_ID` 는 이전 버전 잔여 — DROP 금지 규칙으로 남겨 둠, 앱은 사용 안 함), ISMS 탐지 룰 목록에 DLP 룰 0개. 샘플(`DLP 모니터링 예시.xlsx`) 재분석 → 룰 6개 적용 · 해당 1건(업무시간 외 반출, 23:58) · 인식 못 한 열 0개. 룰 API: USER·MANAGER 등록 403, '파일 크기 + 포함' 400 안내. 보고서: `DLP_모니터링_보고서_202609.pptx` 7장, LibreOffice 로 변환해 한글 표시 확인, 로그 없는 달 400 안내. 화면(ADMIN·MANAGER·USER): 사이드바 그룹 'ISMS 로그 점검' 과 'DLP 모니터링' 따로 표시, DLP 화면 탭 'DLP 모니터링 | DLP 룰셋'·ISMS 탭 0개, 3단계 '예시.xlsx / 룰 6개 적용 · 룰 해당 1건 / 1건 / 2026년 09월 보고서', [PPT 내려받기] → 파일 받음(3개 권한), USER 는 [올리기]·[재분석] 없음. ADMIN 룰 추가('주말 반출 (테스트)', 발생 요일 포함 토,일) → 7개 → 삭제 → 6개, MANAGER·USER 는 [룰 추가]·[수정] 없음. 스크립트·HTTP 오류 0건.
- **DLP 모니터링 보고서 — 회사 보고서 양식 8장 반영 (추가분)**: 사용자 제공 `DLP_모니터링_보고서_템플릿.pptx`(표지 / 목차 / 01~06) 항목·정보를 모두 넣고 디자인만 다시 그림(남색·금색). `17_dlp_report.sql` 2회 연속 실행 정상, 새 테이블 3개 컬럼 13 / 13 / 18개 + `AI_SERVICE` COMMENT 누락 0건, Entity `@Column` = DB 컬럼, FK 없음. 엔드포인트 3개 추가(60 → 63개). ① **채우는 방법**(사용자 확정): 유출 로그로 자동 계산 — 보고 기간, 외장하드 차단·등록·미등록·총 건수와 미등록 인원(부서·이름·건수), 카카오톡 부서별 사용 인원·사용자 건수, 생성형 AI 서비스별·부서별·상위 10명(파일·서비스) / 직접 입력 — 보고 구분·대상 시스템·작성 부서, 승인여부·검토 결과, 부서 인원(다음 달 이어서 사용)·사전승인·사유, 재설치 검토내용, 장기 미접속 조치 구분·내용, 보안 위험도 / 별도 파일 — Privacy-I 재설치·장기 미접속 목록. ② **외장하드** = 채널·서브 채널 USB·이동식·외장·Removable·Media, 차단 = 대응 행동 '차단', 등록 여부 = '이동식 저장 매체 등록 여부'. **카카오톡** = 채널·서브 채널·내용·상세 내용·파일·정책에 '카카오'/'kakao'. 보고서 '미승인 사용자 현황' = 사전승인이 '승인'이 아닌 사람. ③ **생성형 AI**: 사용자 요청('모든 AI 서비스 기준')에 따라 주요 생성형 AI 79종 도메인 목록(`GenAiCatalog`)으로 접속 주소 판정 → 없으면 'AI' 열 값. 업로드·재분석 때 `AI_SERVICE` 저장. 사용자 예시 줄(URL `https://chatgpt.com/c/…`, AI 열 'ChatGPT') → ChatGPT. ④ **보안 위험도**(사용자 기준 상·중·하): 파일 이름 기준 자동 제안(회사 식별 단어 → 상 / 업무 문서·업무 단어 → 중 / 그 외 → 하) + 이유 표시, 담당자가 고쳐 저장하면 그 값. ⑤ **목차 순서**: 템플릿 목차(05 업로드 파일 → 06 사용현황)가 실제 쪽 순서와 달라 실제 쪽 순서(05 사용현황 P.7 → 06 업로드 파일 내역 P.8)로 맞춤, 쪽 번호 자동. 템플릿에 직접 적힌 '총 1명'은 실제 계산값으로. ⑥ **화면**: DLP 모니터링 그룹에 **'DLP 보고서 작성'**(`dlp-report.html`) 추가 — 탭 DLP 모니터링 / 보고서 작성 / DLP 룰셋, 3단계 ③ 버튼이 보고서 작성으로 이동. 템플릿 순서대로 표지 · 01~06 칸, 회색 = 자동, 흰 칸 = 입력, 재설치·장기 미접속 [목록 올리기], [입력 저장] · [PPT 내려받기](저장 안 한 입력은 자동 저장 후 생성). 메뉴는 미리보기 DB 에만 등록(MENU_ID 38). 검증(테스트 유출 로그 9건 + 목록 2개): 외장하드 차단 1·등록 1·미등록 1·총 3, 미등록 인원 박철수 1건 / 카카오톡 개발팀·영업팀 각 1명, 사용자 2명 / 재설치 2명 / 장기 미접속 3명(59·30·11일, 미접속일 빈 칸은 계산) / 생성형 AI 4건 ChatGPT 2·Claude 1·Gemini 1('AI' 열 '기타'·빈 값이어도 주소로 판정) / 회사 식별 단어 '클린나라' 저장 → 김홍두 '상'(클린나라 3분기 매출 보고.xlsx), 홍길동·이영희 '중'. 입력 저장(미승인·검토 결과, 사전승인 '승인' → 미승인 1명, 부서 인원 12, 조치완료 1·휴직 및 퇴사 1·미조치 1), 잘못된 위험도 400, USER 저장·목록 올리기 403, 11월(로그 없음) 표지는 10월 값 이어서 표시. PPT 8장 LibreOffice 변환 → 한글·항목 표시 확인. 화면(ADMIN·MANAGER·USER): 탭 3개, 입력 칸 23개(USER 0개·버튼 없음), MANAGER 영업팀 인원 8·이영희 위험도 '하' 저장 유지, PPT 내려받기 3개 권한 성공. 스크립트·HTTP 오류 0건.
- **DLP 룰셋 사이드바 메뉴 삭제 (추가분, 이전 버전 기록 — 아래 'DLP 룰셋 삭제'로 화면 자체를 뺌)**: 서버·DB 테이블·SQL·API·화면 파일 변경 없음. DLP 룰셋은 DLP 모니터링·보고서 작성 화면 위쪽 탭(DLP 모니터링 / 보고서 작성 / DLP 룰셋)으로 이미 열 수 있어 사이드바 메뉴 `SECLOG_DLP_RULE` 를 5장 등록 목록에서 뺌(운영 등록 불필요, 이미 등록했다면 숨김). 미리보기 DB 에서는 core 메뉴 행을 지우지 않고 숨김 처리(MENU_ID 37 IS_VISIBLE·IS_ACTIVE = 0). 검증: ADMIN·MANAGER·USER 사이드바 'DLP 모니터링' 아래 'DLP 모니터링 · DLP 보고서 작성' 2개만 표시, DLP 모니터링 화면 [DLP 룰셋] 탭 → `dlp-rules.html` 열림(룰 6개), 스크립트·HTTP 오류 0건.
- **DLP 룰셋 삭제 (추가분)**: 보고서(회사 양식 8장)는 고정 기준(외장하드/USB · 카카오톡 · Privacy-I 목록 · 생성형 AI)만 쓰고 DLP 룰셋 결과를 쓰지 않으며, 룰 기반 점검은 ISMS 로그 점검에서 하므로 DLP 룰셋을 뺌. 순서 = ① 유출 로그 올리기 → ② 보고서 작성(입력) → ③ PPT. 서버: `SecLogDlpRule`·`SecLogDlpRuleRepository`·`SecLogDlpRuleService`·`SecLogDlpRuleController`·`DlpRuleMatcher`·`DlpRuleSaveRequest`·`DlpRuleResponse` 7개 파일 삭제(엔드포인트 63 → 57개). `SecLogDlpLeakService` 에서 룰 판정·룰셋 변경 확인·룰별 집계 삭제, 월 결과에 생성형 AI 사용 건수(전체·사용자별) 추가, 이벤트 목록 거르기에서 `matchedOnly`·`ruleCode` 삭제. 화면: `dlp-rules.html` 삭제, DLP 모니터링·보고서 작성 화면의 [DLP 룰셋] 탭 삭제, DLP 모니터링 화면의 룰별 결과 표·'룰 해당' 카드·룰 필터·'룰셋이 바뀜' 안내 삭제 → '생성형 AI 사용' 카드·사용자별/목록의 생성형 AI 열로 바꿈. 보고서 작성 화면·PPT 는 변경 없음. SQL: `16` 에서 `sec_log_dlp_rule` 생성문·기본 룰 6개 삭제(이미 만든 테이블·데이터는 DROP 하지 않고 그대로 둠, 미리보기 DB 도 그대로), 이벤트·업로드의 룰 결과 칸(`MATCHED_RULES`·`MAX_SEVERITY`·`MATCHED_EVENT_COUNT`·`APPLIED_RULE_COUNT`)은 지우지 않고 '(미사용)' 코멘트로 바꿈(앱은 NULL·0 저장). 검증 결과는 아래 검수표 #11·#12·#20 개수에 반영.
- **DLP 모니터링 — 로그 올리기 → 보고서 작성만 남김 + 카카오톡 사용자 현황 탭 (추가분)**: 요청 = 로그를 올리면 그 로그로 보고서를 작성하는 것 외에는 필요 없음, 보고서 안의 내용만 확인할 수 있으면 됨. ① DLP 모니터링 화면(`dlp.html`)을 2단계(유출 로그 올리기 → 보고서 작성 · PPT) + '이 달에 올린 파일'(원본 내려받기·삭제)만 남기고 요약 카드·사용자별 현황·유출 로그 목록·재분석 버튼을 삭제. ② 서버: 유출 로그 목록 API(`GET /uploads/{id}/events`)·재분석 API(`POST /uploads/{id}/reanalyze`)·`DlpLeakEventResponse`·목록 검색 쿼리 삭제, 월 결과 API(`/monthly`)는 그 달 파일 목록만 돌려주도록 줄임. ③ **카카오톡 사용자 현황** 새 화면 `dlp-kakao.html` + `GET /dlp-leak/kakao` (`DlpKakaoResponse`): 보고서 02장과 같은 판정·같은 숫자(카카오톡 사용 건수·사용자·사용 부서·미승인 수, 부서별 사용 인원·부서 인원·사용 비율, 사용자별 건수·처음/마지막 사용·사전승인·사유), [보기]로 그 사람의 카카오톡 로그(발생 일시·대응 행동·서브 채널·파일·내용), 이름·부서 검색, '미승인·미입력만' 거르기. 조회 전용(입력은 보고서 작성 02장). 위치 = DLP 모니터링 화면 위쪽 탭 '보고서 작성' 오른쪽(사이드바 메뉴 등록 안 함), 보고서 작성 화면에도 같은 탭. DB·SQL 변경 없음. 엔드포인트 57 → 56개(−2, +1). Entity·@Column 수 변경 없음(17개·324개). 검증: API — 카카오톡 현황 3개 권한 200, 결과가 보고서 02장과 같음(2026-10 테스트: 사용자 2명·2건·미승인 1명), 잘못된 연월 400, 로그 없는 달 빈 결과, 삭제한 목록·재분석 API 404, PPT 8장 정상. 화면(ADMIN·MANAGER·USER) — 탭 'DLP 모니터링 / 보고서 작성 / 카카오톡 사용자 현황', DLP 모니터링 화면에 '이 달에 올린 파일'만 표시(USER 는 [올리기] 없음, ADMIN 만 [삭제]), 카카오톡 화면 요약·부서·사용자·[보기]·검색·미승인만 정상, 로그 없는 달 안내, 스크립트·HTTP 오류 0건.
- **카카오톡 사용 승인 대장 (추가분, 사람 맞추기 2안 = 부서 + 이름, 신청 상태 '신청중' 만)**: 전자결재 승인 후 엑셀로 관리하던 카카오톡 신청 대장(사업부·부서·이름·신청 시작일·신청 종료일·신청 상태·최신여부)을 시스템으로 옮김. `18_dlp_kakao_approval.sql` 테이블 1개(`sec_log_dlp_kakao_approval`, 컬럼 16개) — 2회 연속 실행 정상, COMMENT 누락 0건, Entity `@Column` 16개 = DB 컬럼 16개, FK 없음. ① 카카오톡 사용자 현황 화면에 안쪽 탭 **월별 사용 현황 / 승인 대장** 추가. 대장: 엑셀과 같은 칸 + 전자결재 문서번호·사유, 상태(사용 중·30일 안 만료·만료·시작 전)와 최신여부(같은 부서·이름 중 종료일이 가장 늦은 건)는 저장하지 않고 오늘 날짜로 계산, 상태 거르기·최신 건만·검색, [등록]·[수정]·[삭제]·[엑셀 대장 올리기]. ② **사전승인 자동 판정**: 보고서 02장·카카오톡 사용자 현황에서 부서 + 이름(띄어쓰기·대소문자 무시, 괄호 안 표기 무시: SCM팀(PS) = SCM팀)이 같은 '신청중' 건이 있고 카카오톡을 쓴 날이 모두 신청 기간 안이면 '승인', 사유는 대장 사유. 기간 밖 사용 → '미승인'(근거: 기간 밖 사용 N일), 대장에 없음 → '미승인', 같은 이름이 다른 부서에만 있음 → '확인 필요'. 보고서 작성 02장 사전승인 선택 첫 칸 = '대장 자동: …'(저장 안 함), 승인/미승인을 직접 고르면 그 값 우선. PPT 02장 미승인 현황에 '확인 필요'도 표시. ③ 엑셀 대장 올리기: 머리글 자동 인식(사업부·부서·이름·신청 시작일·신청 종료일·신청 상태·문서번호·사유), '신청중' 아닌 행 건너뜀, 최신여부 열 무시, 같은 부서·이름·기간은 다시 올려도 고침만(중복 없음), 날짜를 못 읽은 행은 행 번호로 알려 줌. 엔드포인트 5개 추가(56 → 61개), Entity 17 → 18개, @Column 324 → 340개. 검증: API — 테스트 대장 7행(신청중 6·반려 1, 날짜 오류 1) 올리기 → 새로 5건·건너뜀 1건·못 읽은 행 1건(8행), 다시 올리기 → 새로 0건·고침 5건, 상태 계산(2026-11-24 기준 사용 중 3·30일 안 만료 1·만료 1), 김민수 2건 중 최신 = 2026-12-15 건. 사전승인: 이영희(영업팀) = 대장 자동 승인, 박철수(개발팀, 대장은 기획팀) = 확인 필요, 직접 입력값 있으면 직접 입력 우선. 권한: USER 등록·올리기 403, MANAGER 삭제 403, ADMIN 삭제 200, 삭제한 건 다시 삭제 404, 종료일 < 시작일 400, 부서 없음 400, 형식 아닌 파일 400 안내. 보고서 02장 데이터·PPT 8장 정상. 화면(ADMIN·MANAGER·USER) — 월별 사용 현황 사전승인에 '대장'/'직접 입력' 표시와 판정 근거, 승인 대장 상태별 건수·목록·최신 건만(4건 ↔ 5건)·만료만(김민수 2026-06-30)·모니터링 월 선택 숨김, MANAGER 화면에서 등록 → 수정 반영, ADMIN 삭제, USER 는 [등록]·[엑셀 대장 올리기]·[수정]·[삭제] 없음, 보고서 작성 02장 '대장 자동: 승인 / 확인 필요' + 근거, 스크립트·HTTP 오류 0건. 테스트로 넣은 '화면테스트' 건은 소프트 삭제.
- **카카오톡 승인 대장 — 목록 안에서 바로 등록·수정 + 신청 종료일 정렬 (추가분)**: 서버·DB·SQL·API 변경 없음(`dlp-kakao.html`·`seclog.css` 만, 엔드포인트 61개 그대로). ① [등록] 을 누르면 팝업 창 대신 목록 맨 위에 입력 줄(사업부·부서·이름·신청 시작일·신청 종료일·문서번호·사유)이 생기고 [저장]·[취소] (Enter = 저장, Esc = 취소), [수정] 도 그 줄 자리에서 바로 입력 줄로 바뀜. 등록·수정 팝업 창은 삭제. 부서·이름·시작일·종료일이 비었거나 종료일이 시작일보다 앞이면 저장하지 않고 안내. ② 대장 목록 정렬 = 신청 종료일 늦은 순(같으면 시작일 늦은 순 → 나중 등록 순). 검증(ADMIN·MANAGER·USER): 정렬 박철수 2027-03-31 → 이성이 2027-02-02 → 이영희 2026-12-31 → 김민수 2026-12-15 → 김민수 2026-06-30, 팝업 창 없음, MANAGER [등록] → 맨 위 입력 줄(사업부 칸에 커서) → Enter 저장 → 종료일 2027-06-30 건이 맨 위, 빈 값 저장 시 입력 줄 유지, Esc·[취소] 로 입력 줄 닫힘, [수정] → 기존 값이 채워진 입력 줄 → 저장 반영, USER 는 [등록]·[수정] 없음, 스크립트·HTTP 오류 0건. 테스트로 넣은 '목록등록' 건은 소프트 삭제.
- **카카오톡 승인 대장 — 날짜 칸 키보드 입력 바로 적용 (추가분)**: 서버·DB·SQL·API 변경 없음(`dlp-kakao.html`·`seclog.css` 만). 브라우저 기본 날짜 칸은 키보드로 숫자를 치면 연도 칸에 6자리까지 들어가 '202610-01-일' 처럼 되던 문제 → 신청 시작일·종료일 칸을 글자 칸으로 바꿔 숫자 8자리만 치면 입력하는 대로 바로 YYYY-MM-DD 로 맞춤(20261001 → 2026-10-01, 2026.10.01 → 2026-10-01, 9자리부터는 무시). 오른쪽 달력 버튼으로도 선택 가능(선택 값이 글자 칸에 반영). 없는 날짜(예: 20260231)는 빨간 테두리 + 저장 막음 + 안내. 검증: MANAGER 숫자 입력 20261001·20261102 → 2026-10-01·2026-11-02, 10자리 입력 → 2026-11-02, 20260231 → 빨간 표시·저장 안 됨, 달력 2027-01-31 선택 → 글자 칸 반영, Enter 저장·[수정] 시 기존 날짜 채움·수정 저장 반영, USER 는 입력 줄 없음, 스크립트·HTTP 오류 0건. 테스트 건은 ADMIN 이 소프트 삭제.
- **카카오톡 사용자 현황 = 승인 대장 화면만 + 부서별 사용 중 현황(접힘) (추가분)**: DB·SQL 변경 없음. ① 카카오톡 사용자 현황 탭을 누르면 승인 대장 화면만 보이도록 안쪽 탭(월별 사용 현황 / 승인 대장)과 모니터링 월 선택을 없애고, **월별 사용 현황 화면 삭제**. 그 화면만 쓰던 `GET /dlp-leak/kakao` 와 `DlpKakaoResponse` 도 삭제(엔드포인트 61 → 60개). 보고서 작성 02장의 사전승인 자동 판정·PPT 는 그대로. ② 상태별 건수(사용 중·30일 안 만료·만료·시작 전) 아래, 대장 목록 위에 **부서별 사용 중 현황** 추가 — 처음엔 접혀 있고 제목 줄에 'N개 부서 · N명 사용 중' 만 표시, 누르면 표(사업부·부서·사용 중 인원·30일 안 만료·사용 중 인원 이름과 종료일) 펼침, 다시 누르면 접힘. 사용 중 = 오늘 기준 신청 기간 안(30일 안 만료 포함), 같은 부서·이름은 1명으로 셈, 사용 중 인원이 많은 부서 순. 대장 등록·수정·삭제·엑셀 올리기 후 바로 다시 계산. 검증(ADMIN·MANAGER·USER): 탭 클릭 → 안쪽 탭·월 선택·월별 화면 없음, 화면 순서 = 요약 → 부서별 사용 중 현황 → 대장 목록, 처음엔 접힘('3개 부서 · 4명 사용 중'), 펼치기 → 영업팀 2명(30일 안 만료 1명: 김민수 ~2026-12-15, 이영희 ~2026-12-31)·SCM팀(PS) 1명·기획팀 1명, 다시 누르면 접힘, MANAGER 가 영업팀 1명 등록 → '5명 사용 중'·영업팀 3명으로 바로 반영(테스트 건은 ADMIN 이 삭제), USER 는 [등록] 없음, 삭제한 `/kakao` 404, 보고서 데이터·PPT 200, 스크립트·HTTP 오류 0건.
- **카카오톡 승인 기한 경과 → 차단 요청 메일 자동 발송 (추가분, 받는 주소 테스트 메일 · 매일 오전 11시 · 메일 서버는 인프라팀 정보 받은 뒤 설정)**: `19_dlp_kakao_mail.sql` 테이블 2개(`sec_log_dlp_mail_setting` 컬럼 15개, `sec_log_dlp_kakao_mail_log` 컬럼 21개) + 기본 설정 1행 — 2회 연속 실행 정상, COMMENT 누락 0건, Entity `@Column` 15·21개 = DB 컬럼, FK 는 mail_log → kakao_approval 만. `build.gradle` 에 `spring-boot-starter-mail` 추가(모듈만). 엔드포인트 3개 추가(60 → 63개), Entity 18 → 20개, @Column 340 → 376개. ① **보내는 대상**: 신청 종료일 + 1일이 된 사람(같은 부서·이름 중 최신 '신청중' 건 기준). 기간을 연장해 새로 등록하면(종료일이 더 늦은 건) 안 보냄. 적용 시작일 이전에 이미 기한이 지난 건은 안 보냄(처음 적용할 때 옛날 건이 한꺼번에 나가지 않게). 사람(승인 건)마다 1번 — 보낸 건은 다시 안 보내고, 실패한 건은 다음 실행 때 다시 시도. ② **메일 내용**: 제목 '[DLP] 카카오톡 승인 기한 경과 차단 요청 - 부서 이름', 본문 첫 줄 '영업팀 김철수 사용자 DLP 카카오톡 승인 기한 경과하였으니 차단 요청드립니다.' + 부서·이름·신청 기간(·문서번호), UTF-8. ③ **보내는 시각**: 매일 11:00(한국시간), 모듈 안 스케줄러(플랫폼 설정 변경 없음), 11시에 앱이 꺼져 있었으면 켜진 뒤 1분 안에 그날 몫 실행. ④ **메일 서버**: 환경 변수 `SPRING_MAIL_HOST` 등(8-2). 설정 전에는 보내지 않고 '메일 서버 미설정 - N명 대기' 기록. ⑤ **화면** (`dlp-kakao.html`): 부서별 사용 중 현황 아래 **'차단 요청 메일'** 칸 — 메일 서버·보내는 주소·보낼 대기·보낸 메일·마지막 실행·적용 시작일, 받는 주소·참조·자동 보내기 사용, [설정 저장]·[지금 보내기](ADMIN 만, 다른 권한은 보기만). 대장 목록에 **'차단 메일'** 열(보냄 + 일시 / 실패 + 사유). 검증(미리보기, 테스트 메일 서버 aiosmtpd): 메일 서버 없음 → '메일 서버 미설정 - 1명 대기'. 메일 서버 설정 후 [지금 보내기] → 영업팀 김철수(종료일 2026-11-23) 1통, 받은 메일 보낸 사람 'DLP 모니터링' · 받는 주소 테스트 메일 · 한글 제목·본문 정상. 다시 실행 → '보낼 대상 없음'(중복 0). 메일 서버 끔 → '실패 1명 (Connection refused)' → 켬 → 다시 시도 성공(시도 2회). 같은 사람 연장 등록(2026-12-31) → 메일 안 보냄. 사용 안 함 + 자동 실행 → '자동 발송 사용 안 함 - 실행 안 함'. 사용 + 앱 재시작 → 1분 뒤 자동 실행 '[자동] 보냄 1명'. 잘못된 주소 저장 400. MANAGER·USER 설정 저장·지금 보내기 403, 조회 200. 화면(ADMIN·MANAGER·USER): 상태 6칸·차단 메일 열(보냄 3건) 표시, MANAGER·USER 는 입력 칸 잠김·버튼 없음, 대장 입력 줄 칸 12개, 스크립트·HTTP 오류 0건. 테스트 데이터·메일 기록은 삭제하고 미리보기는 메일 서버 미설정 상태로 되돌림.
- **보고서 02장 사용 부서 현황 = 카카오톡 사용자 현황 기준 + 기준일 표시 (추가분)**: DB·SQL·엔드포인트 변경 없음(63개 유지). **보고서 작성 › 02 PC 카카오톡 사용 현황 — 사용 부서 현황**: 유출 로그 기준 → **카카오톡 사용자 현황(승인 대장) 기준**으로 변경. 보고서를 만든 날(데이터 조회·PPT 내려받기 시점)의 최신 대장으로 신청 기간 안인 사람을 부서별로 셈(메뉴의 '부서별 사용 중 현황'과 같은 기준, 같은 부서·이름 = 1명, 인원 많은 순). 몇 월 보고서든 만드는 날의 최신 정보. 화면 제목 옆에 **'YYYY-MM-DD 기준'** 태그, PPT 02장 표 위에 'YYYY-MM-DD 기준 (카카오톡 사용자 현황)' 표시. 부서 인원 입력은 그대로. 오른쪽 '사용자(사전승인)·미승인 사용자 현황'은 지금처럼 그 달 유출 로그의 카카오톡 사용자 + 대장 자동 판정. `SecLogDlpKakaoApprovalService.activeByDept(날짜)` 추가, `DlpReportDataResponse.Kakao.basisDate` 추가. 검증(2026-11-24): 보고서 2026-09·2026-10 모두 '2026-11-24 기준' 영업팀 2 · SCM팀(PS) 1 · 기획팀 1 = 4명(로그 사용자 수와 무관), 10월 영업팀 부서 인원 8 유지. PPT 02장 '2026-11-24 기준 (카카오톡 사용자 현황)' · 합계 4명 · 8명 확인. 화면 ADMIN·MANAGER·USER 스크립트·HTTP 오류 0건. 카카오톡 사용자 현황 화면은 변경 없음('최신 건만' 체크 상자·전체 건 표시 유지).
- **보고서 02장 사전승인 — '담당자 확인 필요' 태그 (추가분)**: DB·SQL·엔드포인트 변경 없음. 판정 방식은 그대로(대장에 없음 = 미승인). 아래 두 경우 사전승인 칸에 주황색 **'담당자 확인 필요'** 태그와 '→ 확인 후 승인/미승인을 골라 주세요' 문구 표시: ① 대장에 있지만 **승인 기간 밖에 사용**(자동 판정 = 미승인) ② **같은 이름이 다른 부서로만 대장에 있음**(자동 판정 = 확인 필요). 담당자가 승인·미승인을 고르고 [입력 저장]하면 태그가 사라짐. `SecLogDlpKakaoApprovalService.Match.confirm`, `DlpReportDataResponse.PersonRow.needConfirm` 추가. PPT 는 변경 없음. 검증(2026-10 보고서, 테스트로 이영희 신청 시작일을 2026-10-31 로 임시 변경 · 박철수 직접 선택값 임시 비움 → 확인 후 원래 값으로 되돌림): 박철수(개발팀, 대장은 기획팀) · 이영희(기간 밖 사용 1일) 모두 태그 표시, ADMIN·USER 화면 동일, 스크립트·HTTP 오류 0건.
- **DLP 메뉴 정리 — 'DLP 보고서 작성' 메뉴 삭제 + 'DLP 관리' 메뉴 추가 (추가분)**: DB·SQL·API 변경 없음(63개 유지). ① 사이드바에서 'DLP 모니터링' 과 'DLP 보고서 작성' 이 겹쳐 보여 **DLP 보고서 작성 메뉴 삭제**(보고서 작성 화면은 DLP 모니터링 위쪽 탭으로 그대로 열림). ② DLP 모니터링 그룹 아래 **'DLP 관리'** 메뉴 추가(`dlp-admin.html`) — 관리 기능을 따로 모으는 화면, 현재는 **차단 요청 메일** 칸만 넣음(카카오톡 사용자 현황 화면에서 옮김, 기능·권한 그대로). 카카오톡 사용자 현황 대장 목록의 '차단 메일' 열(보냄·실패)은 그대로 둠. ③ 메뉴는 미리보기 DB 에만 등록(`SECLOG_DLP_REPORT` MENU_ID 38 숨김, `SECLOG_DLP_ADMIN` MENU_ID 39 추가, 3개 권한) — 운영은 5장 표대로 메뉴관리에서 등록. 검증(ADMIN·MANAGER·USER): 사이드바 'DLP 보고서 작성' 0개 · 'DLP 관리' 1개, DLP 관리 화면에 메일 상태 6칸·받는 주소 테스트 메일 표시, ADMIN 만 [설정 저장]·[지금 보내기](설정 저장 정상), MANAGER·USER 는 입력 잠김·버튼 없음, 카카오톡 사용자 현황에 메일 칸 없음·'차단 메일' 열 유지·보고서 작성 탭 유지, 스크립트·HTTP 오류 0건.
- **DLP 관리 > 메일 알림 관리 탭 + 메일 알림 규칙 여러 개 (추가분, 메일 서버 설정 화면은 만들지 않음 — 인프라팀 정보로 서버 환경 변수 설정)**: ① **화면** (`dlp-admin.html`): DLP 관리 화면 안 탭 **[메일 알림 관리]** — 메일 서버 상태(연결 설정됨 / 미설정), **메일 알림 규칙** 목록·[규칙 추가]·[수정]·[삭제]·[지금 보내기], 보낸 기록(규칙 거르기, 제목 누르면 본문). 규칙 입력 칸: 규칙 이름 / 보내는 날(신청 종료일 + 또는 - N일, 0 = 당일) / 매일 보내는 시각 / 사용 / 받는 사람 / 참조 / 메일 제목 / 메일 본문 / 자리표시 버튼 8개({사업부} {부서} {이름} {신청시작일} {신청종료일} {문서번호} {사유} {보내는날}, 보낼 때 실제 값으로 바뀜, 값 없으면 '-'). 관리자만 편집, 매니저·일반 사용자는 보기만. ② **규칙 여러 개**(최대 20개): 규칙마다 사람(승인 건)당 1번, 보낸 건은 다시 안 보냄, 실패는 다음 실행 때 다시 시도, 연장 등록한 사람은 최신 건 기준, 규칙을 만든 날 이전에 보내는 날이 지난 건은 안 보냄(기존 규칙 그대로). 보내는 시각은 화면에서 바꾸면 서버 재시작 없이 적용(1분마다 확인). ③ **카카오톡 사용자 현황 대장 목록의 '차단 메일' 열 삭제**(보낸 기록은 메일 알림 관리에서 확인). ④ **SQL·코드 정리**: 아직 운영에 반영하지 않은 `19_dlp_kakao_mail.sql`(설정 1행 방식)을 **`19_dlp_mail_rule.sql`**(규칙 여러 개)로 바꿈 — 테이블 `sec_log_dlp_mail_rule`(컬럼 20개) · `sec_log_dlp_mail_send_log`(컬럼 22개) + 기본 규칙 1개(카카오톡 승인 기한 경과 차단 요청, 종료일 + 1일, 11:00, 테스트 메일). 이전 클래스(SecLogDlpMailSetting·SecLogDlpKakaoMailLog·SecLogDlpKakaoMailService·Scheduler·DlpKakaoMailSetting* DTO·Repository)와 API 3개(`/kakao-mail`, `/kakao-mail/run`) 삭제, 새 API 6개(`/mail-rules` GET·POST, `/mail-rules/{id}` PUT·DELETE, `/mail-rules/{id}/run`, `/mail-logs`) → 엔드포인트 63 → 66개, Entity 20개 유지, @Column 376 → 382개. 미리보기 DB 에 남은 이전 테이블 2개(`sec_log_dlp_mail_setting`, `sec_log_dlp_kakao_mail_log`)는 DROP 금지 규칙으로 그대로 두고 앱은 사용하지 않음(운영은 이전 19 를 적용한 적 없음). 검증: `19` 2회 연속 실행 정상, COMMENT 누락 0건, Entity `@Column` 20·22개 = DB 컬럼. 테스트 메일 서버(aiosmtpd): 기본 규칙 [지금 보내기] → 종료일 2026-11-23 건 1통(받는 사람 테스트 메일, 한글 제목·본문·자리표시 채움 정상), 다시 → '보낼 대상 없음'. 새 규칙(종료일 7일 전, 09:30, 받는 사람 2명 + 참조 1명, {사업부}·{사유} 값 없음 → '-') → 종료일 2026-12-01 건 1통(To 2명·Cc 1명). 메일 서버 끔 → '실패 1명 (Connection refused)' → 켬 → 다시 시도 성공(시도 2회). 보내는 시각을 지금 직전으로 수정 → 1분 안에 '[자동] 보냄 1명'(사용 안 함 규칙은 자동 실행 안 됨). 잘못된 시각 25:00·주소 'abc'·일수 200 → 400. MANAGER·USER 조회 200, 추가·수정·지금 보내기·삭제 403. 화면(ADMIN·MANAGER·USER): 탭 '메일 알림 관리', 규칙 목록·보낸 기록 4건, ADMIN [규칙 추가] → 자리표시 버튼으로 본문에 {사업부} 넣기·'신청 종료일 3일 전' 안내 → 저장 → 목록 3개, MANAGER·USER 는 버튼 없음, 카카오톡 사용자 현황 대장에 '차단 메일' 열 없음, 스크립트·HTTP 오류 0건. 테스트 규칙·대장·보낸 기록은 삭제하고 기본 규칙은 처음 값으로, 미리보기는 메일 서버 미설정 상태로 되돌림.
- **메일 알림 규칙 — '사용' 체크 대신 규칙별 활성화 토글 (추가분)**: DB·SQL·API 변경 없음(66개 유지, 기존 `PUT /mail-rules/{id}` 의 `useYn` 사용). 규칙 입력 칸의 '사용' 체크를 없애고, 메일 알림 규칙 목록 첫 칸을 **활성화 토글**(켜짐 / 꺼짐)로 바꿈 — 켜면 정한 시각에 자동으로 보냄, 끄면 자동으로 보내지 않음(규칙은 남고 [지금 보내기]는 가능). 누르면 바로 저장, 규칙의 다른 값은 그대로. 새 규칙은 켜진 상태로 만들어지고, [수정]으로 저장해도 지금 활성화 값 유지. 관리자만 누를 수 있고 매니저·일반 사용자는 토글이 잠김. 검증: ADMIN 끔 → '꺼짐'·'활성 0개' → 켬 → '켜짐'·'활성 1개', 꺼진 상태에서 [수정] 저장 → 꺼짐 유지, 기본 규칙 값(종료일 + 1일, 11:00, 테스트 메일, 제목·본문) 그대로, 입력 칸에 '사용' 체크 없음. MANAGER·USER 토글 잠김, USER 수정 API 403. 스크립트·HTTP 오류 0건. 마지막 상태 = 켜짐.
- **메일 알림 — 신청 종료일이 같은 사람은 메일 1통으로 묶어 보냄 (추가분)**: DB·SQL·API 변경 없음(66개 유지). 규칙마다 보낼 사람을 **신청 종료일별로 묶어** 받는 사람에게 메일 1통 (예: 오늘 11-24, 기본 규칙 종료일 + 1일 → 종료일 11-23 인 사람 전부 1통). 묶음 안 순서 = 부서·이름 순. 본문: **'사람마다' 자리표시**({번호} {사업부} {부서} {이름} {신청시작일} {문서번호} {사유})가 들어간 줄은 인원 수만큼 반복(이어진 여러 줄은 한 덩어리로 반복, 덩어리 사이 빈 줄), 나머지 줄은 한 번만. 자리표시 2개 추가: {번호}(순번), {인원수}(이 메일에 담긴 인원 수). {신청종료일}·{보내는날} 은 묶음 공통 값. 제목: '사람마다' 자리표시는 첫 사람 값, {이름} 은 여러 명이면 '묶음테스트A 외 2명'. 기본 규칙 제목·본문은 그대로 써도 묶어서 나감(첫 줄 '…차단 요청드립니다.'가 사람 수만큼, 부서·이름·신청 기간·문서번호 덩어리가 사람 수만큼). 보낸 기록은 사람마다 1행(같은 메일에 담긴 사람은 같은 제목·본문), 실패하면 묶음 전체가 실패로 남고 다음 실행 때 같이 다시 보냄. 실행 결과 표기 'N통 (M명)'. 화면 자리표시 안내에 '(사람마다)' 표시·반복 설명 추가. 검증(테스트 메일 서버): 종료일 11-23 3명(영업팀 2·품질팀 1) + 11-22 1명 → '보냄 2통 (4명)', 11-23 메일 제목 '… 영업팀 묶음테스트A 외 2명'·본문 3명 정보(문서번호 없는 사람 '-'), 11-22 메일 1명. 다시 → '보낼 대상 없음'. 같은 날 2명 메일 서버 끔 → '실패 1통 (2명)' → 켬 → '보냄 1통 (2명)'(두 사람 시도 2회). 화면 자리표시 10개·안내 문구 확인, ADMIN·USER 스크립트·HTTP 오류 0건. 테스트 데이터 삭제, 기본 규칙 보내기 시작일 2026-11-24 로 되돌림, 미리보기는 메일 서버 미설정 상태.
- **Privacy-I 장기 미접속 목록 = Agent List 엑셀 그대로 + 미접속일 7일 이상만 / 메일 알림 기본 본문 목록형 (추가분)**: DB 구조·API 개수 변경 없음(66개 유지). ① **보고서 04 Privacy-I 장기 미접속 인원 현황**: 사용자 제공 `privacy-i 현황 테스트.xlsx`(시트 'Agent List', 위쪽 출력 일시·부서별 요약 표(부서 이름·설치 인원·미설치 인원·총인원·설치율), 15행 머리글 부서 이름·사용자 이름·사용자 아이디·…·최종 오프라인 일시·**미접속일**·…·설치 일시) 구성 그대로 올리면 읽음 — 머리글 찾는 범위 앞 15행 → 300행(요약 표가 길어도 찾음). **'미접속일' 7일 이상인 사람만** 장기 미접속으로 저장(미접속일이 비어 있으면 최종 오프라인 일시로 계산, 둘 다 없으면 제외). 올리기 결과에 읽은 인원·7일 미만 제외·미접속일 없음 제외 표시. ② **메일 알림 기본 본문**(사용자 선택 1-나): '아래 {인원수}명의 DLP 카카오톡 승인 기한({신청종료일})이 경과하였으니 차단 요청드립니다.' + '{번호}. {부서} {이름} (신청 기간 {신청시작일} ~ {신청종료일}, 문서번호 {문서번호})' 줄이 사람 수만큼 반복. `19_dlp_mail_rule.sql` 기본 규칙·화면 [규칙 추가] 기본값 모두 변경(미리보기 DB 기본 규칙도 같은 값으로 고침), 화면에서 언제든 수정 가능. ③ 이전 질문 2-가: 규칙 공통 조건(규칙을 만든 날 이전 건 제외 / 최신 건 기준 / 실패 다음 날 재시도) 그대로 유지. 검증: `19` 2회 연속 실행 정상(기본 규칙 1개 유지). 사용자 원본 파일(데이터 행 없음) → 200 · 0명. 같은 구성에 테스트 6명(미접속일 54일 / 14일 / 4일 / 빈 값 + 최종 오프라인 2026-11-17 08:00 / 0일 / 둘 다 빈 값) → 저장 3명(김민수 54일·박철수 14일·최지훈 7일(계산)), 7일 미만 2명·미접속일 없음 1명 제외, 보고서 자료 장기 미접속 3명·미접속일 긴 순. 테스트 행은 삭제.
- **DLP 모니터링 3단계 — ② Privacy-I Agent 현황 올리기 / 재설치 = 설치 일시 기준 (추가분)**: DB·SQL 변경 없음(기존 `sec_log_dlp_privacyi_row`·`sec_log_dlp_report_item` 사용), 엔드포인트 수 변경 없음(`/dlp-leak/privacyi` 에 `kind=AGENT` 추가, `/dlp-leak/monthly` 응답에 `agent` 추가). 보고서에 필요한 파일 = 유출 로그 + Privacy-I Agent 현황 2개. 확인: Agent 테스트 파일(6명) 2026-10 올림 → 재설치 2명(설치 일시 10-05·10-20, 다른 달 2명·설치 일시 없음 2명 제외), 장기 미접속 2명(54일·14일) / 사용자 원본 파일(데이터 0행) → 0명 정상 / MANAGER 올리기 200, USER 403 / 화면: ADMIN 3단계 카드·[올리기] 2개 보임, USER [올리기] 숨김, 오류 0건 / 보고서 03·04 '파일 없음' 문구 = 'DLP 모니터링 화면 ② Privacy-I Agent 현황 올리기'. 테스트 후 미리보기 데이터 원래대로 복구.
- **카카오톡 사용자 현황 — '30일 안 만료' → '7일 내 만료' (추가분)**: DB·SQL·API 변경 없음, 엔드포인트 수 변경 없음. 만료 임박 기준을 30일 → 7일로 변경(`SecLogDlpKakaoApprovalService.EXPIRING_DAYS = 7`, 상태 이름·화면 요약 칸·표 머리글·안내 문구). 메일 알림 규칙(보내는 날 = 종료일 기준 N일)은 별개라 영향 없음. 확인(2026-11-24 기준): 종료일 21일 남은 김민수가 '만료 임박' → '사용 중'으로 바뀜(사용 중 4 · 7일 내 만료 0 · 만료 2 · 시작 전 1).
- **보고서 작성 03·04 개별 올리기 버튼 삭제 (추가분)**: 서버·DB·API 변경 없음(`dlp-report.html` 만, `/dlp-leak/privacyi?kind=REINSTALL|OFFLINE` API 는 그대로 둠). 03 재설치·04 장기 미접속은 DLP 모니터링 ② Privacy-I Agent 현황 올리기 1곳에서만 올림. 확인: ADMIN 화면 올리기 버튼 0개, 파일 이름·목록(03 1줄·04 1줄)·[입력 저장] 정상, 오류 0건.
- **카카오톡 사용 승인 대장 — 사업부·부서 드롭다운 + DLP 관리 > 사업부·부서 관리 탭 (추가분, A안)**: `20` 을 2회 연속 실행해 정상 동작 확인(중복 없음, 11개 컬럼 COMMENT 누락 0). 테이블 21개(사용 중), Entity 21개, `@Column` 393개, 엔드포인트 69개(+ `GET/POST /orgs`, `PUT /orgs/{id}`). 처음 목록 = 승인 대장의 사업부 4개·부서 4개. 대장 [등록]·[수정] 줄의 사업부·부서 = 드롭다운(사업부 고르면 그 사업부의 사용 중 부서만, 수정 중인 기존 값이 목록에 없으면 '(목록에 없음)'으로 함께 표시), 사업부 필수로 바뀜. 엑셀 대장 올리기는 변경 없음. 확인: ADMIN 사업부 추가 201 · 같은 이름 409 · MANAGER/USER 추가 403 / 화면: 사업부·부서 관리 탭 표(사업부 5·부서 4) → [부서 추가] Enter 저장 → 승인 대장 [등록] 사업부 옵션 5개, 사업부 선택 전 '사업부 먼저 선택', 선택 후 그 사업부 부서만 → 저장·[수정] 시 값 유지 / USER 화면 추가·이름 바꾸기 버튼 0개, 토글 잠김 / 오류 0건. 테스트로 만든 사업부·부서·대장 건은 소프트 삭제.
- **카카오톡 사용 승인 대장 — 엑셀 올리기 삭제(버튼·서버 기능 모두) + 엑셀 내려받기 추가 (추가분, 가안)**: DB·SQL 변경 없음, 엔드포인트 수 69개 유지(`POST /kakao-approvals/upload` 삭제, `GET /kakao-approvals/export` 추가). 대장은 시스템 화면 [등록]·[수정]으로만 관리 → 사업부·부서는 항상 드롭다운 목록 값. 확인: 올리기 주소 호출 405(없음) / 내려받기 ADMIN·USER 모두 가능, 파일 이름 `카카오톡_사용_승인_대장_YYYY-MM-DD.xlsx`, 최신 건만 7건 = 화면과 같음, '만료 + 검색 영업' 1건, 화면에서 '7일 내 만료' 고르고 내려받기 → 2건(조건 줄에 반영) / 화면: [엑셀 대장 올리기] 버튼·파일 입력 없음, [엑셀 내려받기] 전원 표시, [등록]은 ADMIN·MANAGER만, 오류 0건.
- **DLP 모니터링 보고서 PPT 디자인 변경 — 흰 바탕 + 포인트 색 1개 (추가분, 가안 · 회사 양식 따르지 않음)**: DB·SQL·API 변경 없음(`SecLogDlpLeakReportService` 그리기 부분, `dlp-report.html` 안내 문구만). 8장 구성 유지(표지 · 목차 · 01~06), 목차·위쪽 머리 글귀·쪽 번호 유지. 표지 = 흰 바탕 + 왼쪽 포인트 색 막대 + 정보 카드 4칸 / 각 장 = 포인트 색 제목 막대 + 얇은 선 / 01·04 숫자 = 카드 4개(등록 안 된 외장하드·미조치 및 보류 = 빨강, 총 건수·장기 미접속 = 포인트 색) / 표 = 머리글 연한 포인트 색, 가로 얇은 선만(세로 선 없음), 합계 행 연회색 굵게. 내용 = 보고서 작성 탭에 있는 것만: 02 는 미승인 사용자만(사용자 요청), '파일을 올리지 않음'·'생성형 AI 판정' 설명 문구 삭제. 02 합계 행을 부서 표 바로 아래로 옮김. 확인: 2026-10 PPT 8장 LibreOffice 그림 변환으로 모양 확인, 삭제 문구 0건, 모든 글자 한글 글꼴(맑은 고딕, 동아시아) 지정.
- **01 Removable Drive 숫자 카드 이름·설명 바꾸기 (추가분)**: DB·SQL·API·계산 방법 변경 없음(화면 `dlp-report.html`·`seclog.css`, PPT `SecLogDlpLeakReportService` 카드 글자만). 카드 4개 = ① 업로드 차단(외장하드·USB로 올리려다 막힌 건수) ② 등록된 외장하드로 업로드(등록된 장치라 허용된 건수) ③ 등록 안 된 외장하드로 업로드(등록 안 된 장치인데 올라간 건수, 빨강) ④ 외장하드·USB 업로드 전체(아래에 '차단 N건 · 허용 N건'). 숫자 뒤에 '건' 단위, 카드마다 설명 한 줄. 확인: 2026-10 화면·PPT 모두 1·1·1·3건, '차단 1건 · 허용 2건', 화면 오류 0건.
- **01 외장하드·USB 숫자 카드 — 보고서 형식 괄호 설명 + 숫자 아래 차단/허용 표시 (추가분)**: DB·SQL·API·계산 변경 없음(화면·PPT 카드 글자만). 카드 = 이름 아래 괄호 설명((외장하드 및 USB로 업로드 시 차단된 건수) / (등록된 외장하드로 업로드 시 허용된 건수) / (등록되지 않은 외장하드로 업로드된 건수) / (외장하드 및 USB 사용 건의 차단·허용 건수 합계)), 숫자 아래 '차단' / '허용' / '허용' / '차단 N건 · 허용 N건'. 확인: 2026-10 화면·PPT 3쪽 그림으로 확인, 화면 오류 0건.
- **생성형 AI 업로드 파일 내역 동점 처리 + 사업부·부서 관리 화면 정리 (추가분)**: DB·SQL·API 변경 없음. ① 06 상위 10명 순위 = 로그 줄 수 많은 순 → **같으면 보안 위험도 높은 순(상 > 중 > 하, 직접 고른 값 우선, 없으면 자동 제안 값)** → 같으면 사람 구분 값 순, 10명에서 자름(10위 동점이면 위험도가 높은 사람이 들어감). 판정 기준(URL ↔ 생성형 AI 79종 목록, 상세 내용·내용·서브 채널)과 이용 빈도(로그 줄 수)는 그대로. ② DLP 관리 > 사업부·부서 관리: 표에서 '승인 대장' 칸·'부서 N개' 글자 삭제, [사업부 추가]·[부서 추가](사업부 선택 + 이름)를 표 위 한 상자에 함께 배치(줄마다 있던 [부서 추가] 버튼 삭제), 화면 폭 820px로 줄임(꽉 차지 않게). 확인: 2026-10 건 수 1건 동점 2명 → 위험도 '중'이 '하'보다 위 / ADMIN 부서 추가(사업부 선택 + Enter) 정상, USER 추가 상자·버튼 없음, 머리글 사업부·부서·사용, 오류 0건. 테스트 부서는 소프트 삭제.
- **05 생성형 AI 판정 문구에서 'AI 열' 설명 삭제 / 06 사용 서비스 여러 개 표시 확인 (추가분)**: DB·SQL·API·서버 변경 없음(`dlp-report.html` 문구만). 화면 05 아래 판정 문구에서 '목록에 없으면 로그의 'AI' 열 값 사용' 삭제(실제 로그에 AI 열 없음, URL로만 판정). 06 '사용 서비스'는 원래 그 사람이 쓴 서비스를 모두 표시(많이 쓴 순, 쉼표 구분) — 코드 변경 없음. 확인: 미리보기 로그 1줄을 잠시 Claude로 바꿔 김홍두 = 'ChatGPT, Claude' 가 화면·PPT 8쪽 모두 표시됨을 확인 후 원래 값으로 복구, 화면 오류 0건.
- **메일 알림 — 받는 사람·참조는 회사 메일만 (추가분, 모든 규칙 · 도메인 = 서버 환경 변수)**: DB 컬럼 변경 없음(`19` 기본 규칙 값·COMMENT만 변경), 엔드포인트 수 69개 유지. ① 저장할 때: 받는 사람·참조의 @ 뒤가 `SECLOG_MAIL_ALLOWED_DOMAINS` 와 똑같지 않으면 400(비슷한 도메인 `evilcompany.co.kr` 도 막음, 대소문자 무시). ② 보낼 때 한 번 더: DB에 회사 밖 주소가 있으면 그 메일은 보내지 않고 보낸 기록에 '실패(회사 밖 주소라 보내지 않음)'. ③ 도메인 미설정이면 규칙은 저장되지만 아무 데도 보내지 않음('회사 도메인 미설정 - N명 대기'). ④ 받는 사람이 비어 있으면 켤 수 없음(꺼진 상태로만 저장, 화면 토글도 막음). ⑤ 기본 규칙 받는 사람 비움 + 꺼짐(미리보기 DB도 같게 고침). ⑥ 화면: 메일 서버 상태 옆에 '받는 사람: 회사 메일만 / 회사 도메인 미설정' 표시, 받는 사람 칸 아래 안내, 목록에 '받는 사람 없음' 빨간 글자. 확인: 미설정 → 외부 주소 규칙 저장됨·지금 보내기 '회사 도메인 미설정 - 1명 대기' / `company.co.kr` 설정(미리보기 임시) → 외부 받는 사람 400 · 외부 참조 400 · 비슷한 도메인 400 · `a@Company.CO.KR` 200 · DB에 외부 참조 직접 넣고 보내기 → 실패 1통·메일 0통 · 회사 주소만 → 보냄 1통(To: a@company.co.kr) / 받는 사람 없음 + 켜짐 400, 꺼짐 201 / 화면 오류 0건. 테스트 규칙·기록 소프트 삭제, 미리보기 임시 환경 변수·테스트 메일 서버 원래대로.
- **개발 서버 메뉴 클릭 시 반응 없음 해결 — 화면 파일 공개 설정 추가 (추가분, 사용자 허락)**: DB·SQL·API 변경 없음(엔드포인트 69개 유지). 원인 ① app `index.html` 에 `/security_log/` iframe 라우팅이 없어 메뉴를 눌러도 반응이 없었음 → develop 에 CCKB 와 같은 방식으로 추가(3adb7cd). 원인 ② iframe 은 Authorization 헤더 없이 화면 파일을 불러와 401 → 모듈 안에 `config/SecLogSecurityConfig.java` 추가(kims·cckb 와 같은 방식, `@Order(-2)`), 공개 범위는 `/security_log/*.html`·`css/**`·`js/**` 만, 데이터 API `/security_log-api/**` 는 계속 JWT + 역할 검사. core `SecurityConfig` 는 수정하지 않음. 5-2 절 갱신.
- **보고서 작성 — ① 유출 로그 또는 ② Privacy-I Agent 현황 중 하나만 올려도 작성 가능 (추가분, 개발서버 테스트 중 요청)**: DB·SQL·API 변경 없음(엔드포인트 69개 유지). ① DLP 모니터링(`dlp.html`) ③ [보고서 작성] 버튼 = ① 또는 ② 중 하나만 있어도 열림(안내 '① 또는 ② 중 하나를 올리면 작성 가능', ① 만 없으면 '(① 을 올리면 01·02·05·06 채움)'). ② 보고서 화면(`dlp-report.html`) = 유출 로그 또는 03·04 Agent 파일 이름이 있으면 내용·[PPT 내려받기] 표시, 둘 다 없으면 '올린 파일 없음 → ① 또는 ② 중 하나를 먼저 올리기'. ③ PPT(`SecLogDlpLeakReportService.build`) = 둘 다 없을 때만 400 '올린 파일이 없습니다. 유출 로그 또는 Privacy-I Agent 현황 중 하나를 먼저 올려 주세요.', 유출 로그 없이 만들면 01·02·05·06 은 0건·'해당 없음'으로 표시.
- **DLP 모니터링 '이 달에 올린 파일' — ① 유출 로그 · ② Privacy-I 현황 두 칸으로 나눠 표시 (추가분, A안)**: DB·SQL·API·서버 변경 없음(`dlp.html`·`seclog.css` 만, 엔드포인트 69개 유지, 기존 `GET /dlp-leak/monthly` 의 `files`·`agent` 사용). ① 유출 로그 칸 = 기존과 같음(보고서 기준/이전 파일 · 파일 이름 클릭 원본 내려받기 · 건수 · 로그 기간 · 올린 일시 · ADMIN 삭제). ② Privacy-I 현황 칸 = 보고서 기준 · 파일 이름 · 재설치 N명 · 장기 미접속 N명 · 올린 일시 (마지막에 올린 파일 1개만, 원본은 보관하지 않아 내려받기 없음). 둘 중 하나만 올려도 표시, 안 올린 칸은 '이 달에 올린 파일 없음', 둘 다 없으면 '올린 파일 없음' 안내.

| 번호 | 검수 항목 | 결과 | 비고 |
|---|---|---|---|
| 1 | User/Auth/Role/Menu/Security 관련 클래스가 생성되지 않았는가? | 통과 | 클래스명 자동 스캔 결과 0건 |
| 2 | SecurityConfig, JwtProvider, AuthController가 생성되지 않았는가? | 통과 | core `SecurityConfig`·JWT·인증 클래스는 만들지 않음. 화면 파일 공개용 `SecLogSecurityConfig`(kims·cckb 와 같은 방식, 사용자 허락)만 모듈 안에 있음 |
| 3 | application.yml, application.properties가 생성되지 않았는가? | 통과 | 설정값은 `SecLogConstants` 상수 + README 8-2. API 인증값은 환경변수 |
| 4 | Dockerfile, docker-compose.yml, Nginx 설정이 생성되지 않았는가? | 통과 | |
| 5 | SpringBootApplication main class가 생성되지 않았는가? | 통과 | bootJar 없음, `jar { enabled = true }` |
| 6 | API URL이 /{모듈명}-api/** 규칙을 지키는가? | 통과 | `/security_log-api/**` (10장 참고) |
| 7 | 금지 URL /api/**, /admin/**, /auth/** 등을 사용하지 않았는가? | 통과 | |
| 8 | Entity에 @Setter가 없는가? | 통과 | |
| 9 | Entity에 @Data가 없는가? | 통과 | |
| 10 | Service에서 entity.setXxx(...)를 사용하지 않았는가? | 통과 | `update()/review()/completeReview()/applyAnalysisResult()/recordTest()/recordCollect()/delete()` 등 비즈니스 메서드만 사용 (자동 수집에서 setXxx 는 요청 DTO 에만 사용) |
| 11 | 모든 Entity 컬럼에 @Column(name = "UPPER_SNAKE_CASE")가 있는가? | 통과 | 사용 중인 21개 Entity, @Column 393개 모두 DB 컬럼과 일치 (사용 안 하는 collector·collect_history·account_check·account·dlp_rule 테이블과 `INACTIVE_DAYS` 컬럼은 Entity 에서 제외) |
| 12 | 모든 업무 테이블에 공통 컬럼이 포함되어 있는가? | 통과 | 사용 중인 21개 테이블 + 미사용 5개 테이블(`sec_log_dlp_rule` 포함) 모두 7개 공통 컬럼 (`sec_log_dlp_org`·`sec_log_dlp_mail_rule`·`sec_log_dlp_mail_send_log`·`sec_log_dlp_kakao_approval`·`sec_log_account_review`·`sec_log_rule_run`·`sec_log_pii_download`·`sec_log_dlp_leak_*` 3개·`sec_log_dlp_report`·`sec_log_dlp_report_item`·`sec_log_dlp_privacyi_row` 포함) |
| 13 | 삭제 기능이 DELETED_YN 기반 소프트 삭제로 구현되어 있는가? | 통과 | 일괄 삭제도 `UPDATE ... SET DELETED_YN='Y' WHERE UPLOAD_ID=?` |
| 14 | 모든 테이블과 컬럼에 COMMENT가 있는가? | 통과 | information_schema로 확인, 누락 0건 |
| 15 | SQL에 DROP TABLE이 없는가? | 통과 | 룰 패턴 문자열 안의 `drop` 키워드는 탐지용 데이터 |
| 16 | SQL에 TRUNCATE TABLE이 없는가? | 통과 | 위와 동일 (탐지 패턴 데이터에만 존재) |
| 17 | SQL에 무조건 DELETE가 없는가? | 통과 | DELETE 문 없음 |
| 18 | SQL에 ALTER TABLE DROP COLUMN이 없는가? | 통과 | `03`·`07`·`08` 의 ALTER 는 모두 `ADD COLUMN IF NOT EXISTS` |
| 19 | core_user, core_menu, core_role 등 core 테이블에 FK를 생성하지 않았는가? | 통과 | SQL 파일의 FK 모두 업무 테이블 간 (information_schema 확인, core 참조 0건, `sec_log_rule_run`·`sec_log_pii_download` 는 sec_log_upload 만, `sec_log_dlp_leak_file`·`sec_log_dlp_leak_event` 는 sec_log_dlp_leak_upload 만 참조, `sec_log_dlp_kakao_approval` 은 FK 없음, `sec_log_dlp_mail_send_log` 는 sec_log_dlp_mail_rule·sec_log_dlp_kakao_approval 만 참조) |
| 20 | Controller 응답이 ApiResponse<T>로 감싸져 있는가? | 통과 | 69개 엔드포인트 전체 (파일·엑셀은 Base64) |
| 21 | Entity를 Controller에서 직접 반환하지 않는가? | 통과 | `from(Entity)` Response DTO 변환 |
| 22 | RuntimeException, IllegalArgumentException을 직접 throw하지 않는가? | 통과 | 엔진 내부 private 정규식 타임아웃 신호 클래스는 외부로 전파되지 않음 |
| 23 | ddl-auto에 의존하지 않고 SQL DDL을 제공했는가? | 통과 | |
| 24 | core 모듈을 수정하지 않았는가? | 통과 | 실제 core API(`CustomUserDetails`, `ErrorCode`)에 맞춤 |
| 25 | app 모듈을 수정하지 않았는가? | 통과 | settings.gradle, app/build.gradle 미수정 |
| 26 | 메뉴/권한 등록 정보가 README.md에 작성되어 있는가? | 통과 | 5장, 6장 |
| 27 | 필요한 core 기능이 부족한 경우 README.md에 "core에 추가 필요"로 명시했는가? | 통과 | 9장 (실제 core 대조 결과 반영) |
