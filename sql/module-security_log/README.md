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

`ISMS 로그 점검 현황` 메뉴를 누르면 **대시보드**가 먼저 열립니다. 세부 작업은 상단 탭 5개(대시보드 / 월별 점검 현황 / 접속·보안 로그 / 점검 대상 시스템 / **관리**)로 나눴습니다. **관리** 탭을 누르면 관리 페이지 하위 메뉴(탐지 룰셋 편집 · 내부 IP 설정)가 보입니다.

| 탭 | 파일 | 용도 |
|---|---|---|
| **대시보드** | `dashboard.html` | **사용 중인 점검 대상 시스템만 집계**(사용 토글 기준). 아래 '대시보드 구성' 참고. 5분마다 자동 새로고침, **로그 업로드**(ADMIN/MANAGER, 팝업) · **월 점검 결과 내보내기(엑셀)** 버튼 |
| 월별 점검 현황 | `status.html` | 시스템별 접속·행위 로그 등록/검토 상태, 위험 계정, 탐지 건수 (월 마감용) |
| 접속·보안 로그 | `index.html` | 월 1회 업로드한 로그 목록, 탐지 결과 |
| 점검 대상 시스템 | `systems.html` | 시스템 마스터. **사용 열의 토글로 사용/미사용 바로 전환** (ADMIN, 미사용 전환 시 확인창) |
| **관리 › 탐지 룰셋 편집** | `rules.html` | **시스템별 룰셋 사용/미사용**(공통·전용), 공통 룰 / 시스템 전용 룰 편집 (관리 탭의 첫 화면) |
| **관리 › 내부 IP 설정** | `internal-ip.html` | 외부 IP 판정 기준이 되는 내부 IP 대역 등록·수정·사용/미사용·삭제, **IP 확인**(어느 대역에 속하는지) |

### 대시보드 구성 (정보보안 담당자: 매일 확인 + 매달 증적)

| 위치 | 항목 | 매일 확인 | 매달 증적 |
|---|---|---|---|
| 1 | **로그 업로드 현황** — **업로드 완료 칸 1개**(`올린 시스템 / 사용 시스템`, 미업로드 수). 칸을 누르면 **시스템별 업로드 표**가 펼쳐지고 다시 누르면 접힘(기본 접힘, 새로고침해도 펼친 상태 유지). 표: 상태(기간 확인 사유 포함), 파일, 로그 기간, 활동일, 줄 수·일시 인식 안 됨·대상 월 밖, 업로드 일시, 보기/업로드. 시스템 이름을 누르면 오른쪽 상세 창 | 시스템마다 그 달 로그를 **빠짐없이(기간 전체)** 올렸는지. 업로드 기한은 없음(나중에 올려도 됨) | 2.9.4 로그 빠짐없이 수집 |
| 2 | **검토 현황** — 요약(월 마감 = 검토완료 시스템 / 사용 시스템, 탐지 검토율, 이상징후 확인, 조치완료/오탐) + 마감 D-day(남은 시스템 수) + 시스템별 접속·행위 로그 검토(상태, 검토 진행 막대, 미검토) + **다음 할 일** 바로가기 | 무엇을 먼저 할지 | 2.9.5 검토·마감 |
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
| 일자 점검 전 | `08` 적용 전에 올린 파일. ADMIN 에게 **[로그 일자 점검 다시 하기 (N)]** 버튼이 보임 (해당 파일이 있을 때만) |
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

수집률 = (로그 있음 + 활동 없음) ÷ 대상 일수 (시스템 상세 창 달력·엑셀 2번 시트). 로그 일자는 각 줄의 일시(표 형식은 '일시' 열)로 판단하며, UTC 표기는 한국시간으로 바꿉니다. syslog 처럼 연도가 없는 형식은 대상 연월의 연도를 씁니다.

### 관리 › 내부 IP 설정 (`09_internal_ip.sql`)

- 접속 행위 분석에서 **사용 중인 내부 대역에 속하지 않는 IPv4** 를 '외부 IP' 로 표시합니다. 예전에 코드에 고정돼 있던 사설·예약 대역 6개가 기본값으로 등록됩니다.
- 사내망, VPN 대역, 본사 공인 IP, 관리 PC 처럼 **정상 접속지를 등록**하면 외부 IP 오탐이 줄어듭니다.
- 입력: `10.20.0.0/16`, `192.168.10.0/24`, 단일 IP `203.0.113.10`(→ `/32`). 호스트 비트가 섞이면 네트워크 주소로 맞춰 저장합니다(`192.1.2.77/24` → `192.1.2.0/24`). IPv4 만, **/8 보다 넓은 대역은 거부**(모든 IP 가 내부가 되는 것을 막음). 같은 대역 중복 등록 거부, 삭제했던 대역을 다시 등록하면 복구합니다.
- **IP 확인**: IP 를 넣으면 내부/외부 여부와 속한 대역을 보여줍니다(저장 안 함). 전원 사용 가능.
- 적용 시점: 변경은 **이후 업로드·재분석부터** 적용됩니다. 이미 분석한 결과는 상세 화면의 [재분석]으로 다시 계산합니다(검토완료 건은 잠김).
- 권한: 조회·IP 확인은 ADMIN/MANAGER/USER, 등록·수정·사용여부·삭제는 ADMIN.

### 점검 매뉴얼 (대시보드 팝업)

- 대시보드 상단 **[점검 매뉴얼]** 버튼(전원)을 누르면 로그 검토자 점검 순서를 팝업으로 보여줍니다. 서버 변경 없음(화면 안내만).
- 내용: ① 대시보드에서 이번 달 상황 보기(확인이 필요한 항목 → 로그 업로드 현황 → 위험 계정·특권 계정) → ② 볼 시스템 정하기(최고 위험도 순) → ③ 시스템 상세 검토(업로드 정보 → 심각·높음 룰 → 접속 행위 분석 → 보통·낮음 룰 → 최종 검토완료, 룰 하나 처리 방법, 검토 상태 기준) → ④ 월 마감(검토 현황 → 엑셀 내보내기 → 서명), 위험도별 처리 시한, 주의할 점.
- 목차를 누르면 팝업 안에서 해당 항목으로 이동합니다(페이지 주소는 바뀌지 않음). Esc·닫기로 닫힙니다.

### 대시보드에서 로그 업로드 (팝업)

- 대시보드 상단 **[로그 업로드]** 버튼(ADMIN/MANAGER 에게만 보임)을 누르면 업로드 팝업이 열립니다. 대상 연월은 대시보드에서 보고 있는 달로 채워집니다.
- 펼친 업로드 표의 **[업로드]** 버튼이나 시스템 상세 창의 '로그 파일 업로드'로 열면 그 시스템이 미리 선택됩니다. 시스템을 고르면 기본 로그 유형이 자동 선택됩니다.
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

### 룰별 로그 확인 / 룰 일괄 검토 (상세 화면 '룰별 탐지 건수')

- 로그인 실패처럼 **같은 사유로 여러 줄이 탐지되는 룰**은 줄마다 조치 내용을 쓰지 않고 룰 단위로 한 번에 검토합니다.
- 룰별 탐지 건수 표에는 이 업로드에 **지금 적용되는 룰(공통 + 시스템 전용)을 0건이어도 모두** 보여줍니다. 탐지된 룰은 건수 많은 순, 0건 룰은 아래쪽 회색 줄(위험도 높은 순)로 '탐지 없음'이 표시되고 버튼은 없습니다. 룰마다 **공통 / 전용** 구분을 붙이고, 임계 건수가 2 이상인 룰은 '10건↑ 탐지'처럼 기준을 보여줍니다(그보다 적게 매칭된 경우도 0건). 제목 옆에는 '적용 룰 17개 중 3개 탐지'처럼 요약합니다.
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
- **업로드**: [월별 점검 현황]의 `등록` 또는 [접속·보안 로그]의 `로그 업로드`. 시스템을 고르면 로그 유형이 자동 선택됩니다. 텍스트(log/txt/csv/tsv/json/out)와 엑셀(xlsx/xls), 최대 50MB, 같은 내용 재업로드는 차단.
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
| `sec_log_upload` | 업로드 이력 + 분석 요약 | TARGET_YM, SYSTEM_NAME, LOG_TYPE, FILE_HASH, 위험도별 건수, **DATE_UNKNOWN_COUNT, OUT_OF_MONTH_COUNT**(로그 일자 점검, `08`), STATUS, REVIEW_COMMENT | - |
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
| GET | `/security_log-api/uploads?targetYm=202610&systemName=&logType=&status=&maxSeverity=&page=0&size=50` | **목록 화면 (월별 조회)** | ADMIN, MANAGER, USER |
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
| 삭제되었거나 없는 ID | `RESOURCE_NOT_FOUND` (404) |
| 권한 없음 / 비로그인 | 403 / 401 (core 공통 처리) |

## 5. 메뉴 등록 요청 정보

메뉴 seed SQL은 작성하지 않았습니다. 운영자가 플랫폼 **메뉴관리 / 접근 권한** 화면(또는 `MODULE_GUIDE.md` 3장의 `core_menu`, `core_role_menu` 등록 절차)에서 아래 정보로 등록해 주세요.

| 메뉴명 | 메뉴 위치 | 메뉴 URL (프론트) | 사용 API | 접근 역할 |
|---|---|---|---|---|
| 보안로그 관리 (상위) | 정보보안 > 보안로그 관리 | (그룹, URL 없음) | - | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| **ISMS 로그 점검 현황 (권장 대표 메뉴)** | 정보보안 > 보안로그 관리 > 점검 현황 | `/security_log/dashboard.html` | `GET /security_log-api/dashboard` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 로그 업로드 목록 | 정보보안 > 보안로그 관리 > 로그 업로드 목록 | `/security_log/index.html` | `GET /security_log-api/uploads`, `/summary` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 점검 대상 시스템 | (화면 상단 탭, 메뉴 등록 선택) | `/security_log/systems.html` | `/security_log-api/systems/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |
| 로그 업로드 등록 | (목록 화면 버튼, 메뉴 비노출) | `/security_log/upload.html` | `POST /security_log-api/uploads` | ROLE_ADMIN, ROLE_MANAGER |
| 로그 업로드 상세 | (목록 화면 행 클릭, 메뉴 비노출) | `/security_log/detail.html?id={id}` | `GET /uploads/{id}`, `/detections` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| 로그 업로드 수정 | (상세 화면 버튼, 메뉴 비노출) | `/security_log/edit.html?id={id}` | `PUT /security_log-api/uploads/{id}` | ROLE_ADMIN, ROLE_MANAGER |
| **관리** (탐지 룰셋 편집) | 정보보안 > 보안로그 관리 > 관리 | `/security_log/rules.html` | `/security_log-api/rules/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |
| 관리 › 내부 IP 설정 | (관리 페이지 하위 메뉴, 메뉴 등록 선택) | `/security_log/internal-ip.html` | `/security_log-api/internal-ips/**` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER (편집은 ROLE_ADMIN) |

### 5-1. 화면 파일 (모듈에 포함)

`module-security_log/src/main/resources/static/security_log/` 에 있으며, 앱 jar 에 포함되어 `/security_log/**` URL 로 서빙됩니다.
module-safety 와 같은 방식으로 플랫폼 SPA 안에서 iframe 으로 열리며, 플랫폼 로그인 세션(`localStorage.fireweb_user` 의 JWT)을 그대로 사용합니다.

| 화면 | 파일 | 역할별 동작 |
|---|---|---|
| **대시보드 (메뉴 첫 화면)** — 로그 업로드 현황, 검토 현황, 확인 필요 항목, 위험 계정, 추이, **로그 업로드 팝업** | `dashboard.html` | 전원 조회. 로그 업로드 버튼은 ADMIN/MANAGER |
| 월별 점검 현황 — 시스템별 접속·행위 로그 상태, 바로 등록 | `status.html` | 전원 조회. `등록` 버튼은 ADMIN/MANAGER |
| 목록 (월별 조회 + 요약 + TOP 10 룰) | `index.html` | 전원 조회. `로그 업로드` 버튼은 ADMIN/MANAGER 만 표시 |
| 점검 대상 시스템 관리 | `systems.html` | 전원 조회, 편집은 ADMIN |
| 등록 (파일 업로드 + 즉시 분석) | `upload.html` | ADMIN/MANAGER. USER 는 권한 없음 안내 |
| 상세 (① 접속 행위 분석, ② 룰 탐지 요약, 룰별 집계, 검토 현황, 탐지 목록, 일괄 검토) | `detail.html` | 검토 저장은 ADMIN/MANAGER. 최종 검토완료·취소·삭제는 ADMIN |
| 수정 (기본정보, 재분석) | `edit.html` | ADMIN/MANAGER. 검토완료 건은 잠김 |
| 관리 › 탐지 룰셋 편집 (시스템별 룰셋 스위치, 공통/전용 룰 목록, 등록·수정, 사용여부, 패턴 테스트) | `rules.html` | 전원 조회. 편집·룰셋 스위치는 ADMIN |
| 관리 › 내부 IP 설정 (대역 목록, 등록·수정·사용여부·삭제, IP 확인) | `internal-ip.html` | 전원 조회·IP 확인. 편집은 ADMIN |
| 공통 스크립트 / 스타일 | `js/seclog.js`, `css/seclog.css` | - |

> 화면의 버튼 숨김은 사용성용입니다. 실제 권한은 API 의 `@PreAuthorize` 로 서버에서 막습니다.

### 5-2. 화면을 플랫폼에 띄우려면 필요한 core/app 작업 (**core·app 에 추가 필요**)

업무 모듈 규칙상 core·app 을 수정하지 않았기 때문에, 아래 2가지가 반영되기 전에는 운영에서 화면이 열리지 않습니다.
(미리보기 환경에서는 같은 내용을 임시로 적용해 동작을 확인했으며, 그 수정은 커밋하지 않았습니다.)

**① core `SecurityConfig` 공개 경로 추가** — iframe 진입은 Authorization 헤더를 보낼 수 없어 지금은 401 이 납니다. 데이터 API(`/security_log-api/**`)는 계속 JWT 로 보호됩니다.
```java
// core/src/main/java/com/company/core/config/SecurityConfig.java 의 기존 permitAll 목록에 추가
.requestMatchers("/security_log/**").permitAll()   // 화면 (플랫폼 SPA 안에서 iframe 로드)
```

**② app `static/index.html` 에 iframe 라우팅 추가** — 플랫폼 SPA 는 `/kims/`, `/safety/` 로 시작하는 메뉴만 iframe 으로 엽니다. `/security_log/` 도 같은 방식으로 열도록 추가해야 합니다. (`isSafetyPage` 처리와 같은 패턴. `menuUrl` 이 `/security_log/` 로 시작하면 `navigateToSafetyPage(menuUrl)` 와 같은 iframe 로더로 열기)

**③ 메뉴 등록** — 아래 값으로 `core_menu` / `core_role_menu` 에 등록합니다 (`MODULE_GUIDE.md` 3장 절차).

| MENU_CODE | 메뉴명 | 상위 | MENU_URL | 접근 역할 |
|---|---|---|---|---|
| `SECLOG_MGMT` | 보안로그 관리 | (최상위 그룹) | (없음) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_STATUS` | ISMS 로그 점검 현황 | SECLOG_MGMT | **`/security_log/dashboard.html`** (대시보드가 첫 화면) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_UPLOAD` | 로그 업로드 목록 | SECLOG_MGMT | `/security_log/index.html` | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |
| `SECLOG_ADMIN` | 관리 (탐지 룰셋 편집 · 내부 IP 설정) | SECLOG_MGMT | `/security_log/rules.html` (관리 페이지 첫 화면) | ROLE_ADMIN, ROLE_MANAGER, ROLE_USER |

등록·상세·수정 화면은 목록 화면 안에서 이동하므로 메뉴로 등록하지 않습니다. 모든 목록 화면 상단에 탭(대시보드 / 월별 점검 현황 / 접속·보안 로그 / 점검 대상 시스템 / 관리)과 관리 페이지 하위 메뉴(탐지 룰셋 편집 / 내부 IP 설정)가 있어, 메뉴는 `SECLOG_STATUS` 하나만 등록해도 전 기능을 쓸 수 있습니다.

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
| 업로드 등록 / 수정 / 재분석 / 탐지 검토 | O | O | - | `hasAnyRole('ADMIN', 'MANAGER')` |
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
| 8 | `08_upload_date_check.sql` | `sec_log_upload.DATE_UNKNOWN_COUNT/OUT_OF_MONTH_COUNT`(로그 일자 점검 결과) 컬럼 추가. 기존 업로드는 NULL(점검 전) → **적용 후 대시보드에서 ADMIN 이 [로그 일자 점검 다시 하기]를 월별로 한 번 누름**. **앱 배포 전에 먼저 적용** | 가능 (`ADD COLUMN IF NOT EXISTS`) |
| 9 | `09_internal_ip.sql` | 테이블 1개(internal_ip) + 기본 내부 대역 6개(사설 A/B/C, 루프백, 링크 로컬, 0/8 예약). **앱 배포 전에 먼저 적용** (테이블이 없으면 업로드 분석이 실패) | 가능 (`IF NOT EXISTS`, `INSERT IGNORE` + UK) |
| 10 | `10_system_rules.sql` | 시스템별 전용 탐지 룰 87개(14개 시스템) 추가 + `04` 의 DLP 룰 8개를 DLP 전용으로 이동. SYSTEM_ID 는 SYSTEM_CODE 로 찾아 넣음(시스템이 없으면 건너뜀). **적용 후 이미 분석한 업로드는 상세 화면 [재분석]** | 가능 (`INSERT IGNORE` + UK, UPDATE 는 공통으로 남은 DLP 룰에만) |

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
    │                SecLogSystemController, SecLogInternalIpController
    ├── dto/         RuleSaveRequest, RuleUseYnRequest, RuleTestRequest, RulesetUseRequest, LogUploadRequest,
    │                LogUploadUpdateRequest, UploadReviewRequest, DetectionReviewRequest,
    │                RuleReviewRequest, RuleReviewResponse,
    │                RuleResponse, RuleTestResponse, LogUploadResponse, LogUploadDetailResponse,
    │                DetectionResponse, MonthlySummaryResponse, RuleHitCount, OriginalFileResponse,
    │                SystemSaveRequest, SystemResponse, MonthlyStatusResponse, AccessSummaryResponse,
    │                AccessReportResponse,
    │                DashboardResponse, SystemUseYnRequest, SystemLogResponse
    ├── entity/      SecLogRule, SecLogUpload, SecLogUploadFile, SecLogDetection,
    │                SecLogSystem, SecLogAccessSummary, SecLogAccessDaily, SecLogInternalIp,
    │                LogType, MatchType, Severity, UploadStatus, ReviewStatus (enum)
    ├── repository/  SecLogRuleRepository, SecLogUploadRepository,
    │                SecLogUploadFileRepository, SecLogDetectionRepository, SecLogSystemRepository,
    │                SecLogAccessSummaryRepository,
    │                SecLogAccessDailyRepository, SecLogInternalIpRepository
    └── service/     SecLogRuleService, SecLogUploadService, SecLogDetectionService,
                     SecLogSystemService, SecLogMonthlyStatusService,
                     SecLogDashboardService, SecLogMonthlyReportService, LogDateParser,
                     LogAnalysisEngine, AccessLogAnalyzer, CsvSplit, ExcelTextConverter, SecLogConstants
module-security_log/src/main/resources/static/security_log/
├── dashboard.html
├── status.html  index.html  upload.html  detail.html  edit.html  rules.html  internal-ip.html  systems.html
├── js/seclog.js
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
- **룰별 탐지 건수 0건 룰 표시 (추가분)**: DB 변경 없음. 홈페이지 업로드(49줄): 적용 룰 17개(공통 11 + 전용 6) 중 3개 탐지 → 3줄 + 0건 14줄(심각 → 낮음 순), 0건 줄에는 버튼 없음, '반복 인증 실패' 10건↑·'5xx 다발' 50건↑ 기준 표시, 룰 필터 목록은 탐지된 룰 3개만, [로그 보기] 정상. DLP 업로드: 14개 중 9개 탐지, ERP(직접 입력 시스템): 공통 8개 중 2개 탐지. ADMIN·USER 화면 스크립트 오류 0건.
- **점검 매뉴얼 (추가분)**: ADMIN·USER 모두 버튼 표시 → 팝업(항목 6개, 표 3개) → 목차 '위험도별 처리 시한'·'시스템 상세 검토' 이동(주소 변경 없음) → Esc·닫기로 닫힘. 스크립트 오류 0건.
- **시스템별 전용 룰셋 (추가분)**: `10` 을 3회 연속 실행해 정상 동작 확인(전용 룰 95개 유지, 중복 없음). 87개 룰 모두 DB 에 저장된 패턴·이름·설명·위험도·임계·사용여부가 설계값과 100% 일치(역슬래시 이스케이프 포함).
  - 패턴 검증: 87개 룰 모두 실제 Java 엔진(룰 테스트 API)으로 '걸려야 할 줄'(양성 예시 117줄)은 모두 걸리고 '걸리면 안 되는 줄'(음성 예시 107줄)은 하나도 걸리지 않음을 확인. 처음 3개 룰이 어순이 다른 예시를 놓쳐 패턴을 보완했습니다.
  - 재분석 결과: **홈페이지 0건 → 18건**(비밀번호 변경 6, 관리자 권한·상태 변경 5, 관리자 생성 4, 관리자 삭제·게시물 삭제·심야 변경 각 1 — 원본 엑셀과 건수 일치), DLP 258건 그대로(전용 이동 후에도 동일), AWS console 0 → 1건(루트 로그인), AWS방화벽 테스트 로그는 새 전용 룰로 탐지(외부 IP → 관리 포트 119·140건, 전체 개방 1건, 정책 변경 1건). 예전에 미리보기에서 시험용으로 만든 AWS방화벽 룰(`AWSFW_*`) 3개는 새 룰과 겹쳐 모두 삭제(소프트) 상태입니다(이번에 2개 삭제, 1개는 이미 삭제돼 있었음).
  - 화면: 상세 화면 룰별 탐지 건수(홈페이지 6개 룰), 탐지 룰셋 편집의 시스템별 룰셋(홈페이지 공통 11 + 전용 6) 정상, 스크립트 오류 0건.
- **룰별 로그 확인 / 룰 일괄 검토 (추가분)**: DB 변경 없음(SQL 추가 없음).
  - API: `ruleCode` 필터(COM_AUTH_FAIL_BRUTE → 57건, 다른 룰 섞이지 않음), 룰별 검토 건수, USER 일괄 검토 403, `ruleCode` 누락 400, MANAGER 미검토 57건 일괄 오탐 처리 → 다시 요청 시 "미검토 탐지 건이 없습니다" 400, 없는 룰 400, 검토완료 업로드 400(잠김), 전체 덮어쓰기(`onlyPending=false`)로 미검토 되돌리기.
  - 화면(ADMIN): 룰 9개 표·검토 진행 막대, [로그 보기] → 목록이 그 룰 57건으로 바뀌고 이동·행 강조, [이 룰 일괄 검토] 팝업(로그 예시 5줄) → 저장 후 '검토 끝'·목록 조치 내용 반영, 룰 필터 해제 → 258건. USER: [로그 보기]만 보이고 일괄 검토 버튼 0개. 스크립트 오류 0건. 검증 후 테스트 데이터는 미검토로 되돌림(258건 모두 미검토, 조치 내용 없음).
- 탐지 엔진: seed 룰 19개 정규식 컴파일 성공. 샘플 로그 11라인에서 9개 룰이 기대대로 탐지되었고, 임계 건수(5건 이상)도 확인했습니다. ReDoS 패턴 `(a+)+$`는 203ms에서 안전하게 중단됩니다.

| 번호 | 검수 항목 | 결과 | 비고 |
|---|---|---|---|
| 1 | User/Auth/Role/Menu/Security 관련 클래스가 생성되지 않았는가? | 통과 | 클래스명 자동 스캔 결과 0건 |
| 2 | SecurityConfig, JwtProvider, AuthController가 생성되지 않았는가? | 통과 | |
| 3 | application.yml, application.properties가 생성되지 않았는가? | 통과 | 설정값은 `SecLogConstants` 상수 + README 8-2. API 인증값은 환경변수 |
| 4 | Dockerfile, docker-compose.yml, Nginx 설정이 생성되지 않았는가? | 통과 | |
| 5 | SpringBootApplication main class가 생성되지 않았는가? | 통과 | bootJar 없음, `jar { enabled = true }` |
| 6 | API URL이 /{모듈명}-api/** 규칙을 지키는가? | 통과 | `/security_log-api/**` (10장 참고) |
| 7 | 금지 URL /api/**, /admin/**, /auth/** 등을 사용하지 않았는가? | 통과 | |
| 8 | Entity에 @Setter가 없는가? | 통과 | |
| 9 | Entity에 @Data가 없는가? | 통과 | |
| 10 | Service에서 entity.setXxx(...)를 사용하지 않았는가? | 통과 | `update()/review()/completeReview()/applyAnalysisResult()/recordTest()/recordCollect()/delete()` 등 비즈니스 메서드만 사용 (자동 수집에서 setXxx 는 요청 DTO 에만 사용) |
| 11 | 모든 Entity 컬럼에 @Column(name = "UPPER_SNAKE_CASE")가 있는가? | 통과 | 사용 중인 8개 Entity, @Column 150개 모두 DB 컬럼과 일치 (사용 안 하는 collector·collect_history·account_check·account 테이블과 `INACTIVE_DAYS` 컬럼은 Entity 에서 제외) |
| 12 | 모든 업무 테이블에 공통 컬럼이 포함되어 있는가? | 통과 | 사용 중인 8개 테이블 + 미사용 4개 테이블 모두 7개 공통 컬럼 |
| 13 | 삭제 기능이 DELETED_YN 기반 소프트 삭제로 구현되어 있는가? | 통과 | 일괄 삭제도 `UPDATE ... SET DELETED_YN='Y' WHERE UPLOAD_ID=?` |
| 14 | 모든 테이블과 컬럼에 COMMENT가 있는가? | 통과 | information_schema로 확인, 누락 0건 |
| 15 | SQL에 DROP TABLE이 없는가? | 통과 | 룰 패턴 문자열 안의 `drop` 키워드는 탐지용 데이터 |
| 16 | SQL에 TRUNCATE TABLE이 없는가? | 통과 | 위와 동일 (탐지 패턴 데이터에만 존재) |
| 17 | SQL에 무조건 DELETE가 없는가? | 통과 | DELETE 문 없음 |
| 18 | SQL에 ALTER TABLE DROP COLUMN이 없는가? | 통과 | `03`·`07`·`08` 의 ALTER 는 모두 `ADD COLUMN IF NOT EXISTS` |
| 19 | core_user, core_menu, core_role 등 core 테이블에 FK를 생성하지 않았는가? | 통과 | FK 9개 모두 업무 테이블 간 (information_schema 확인, core 참조 0건) |
| 20 | Controller 응답이 ApiResponse<T>로 감싸져 있는가? | 통과 | 40개 엔드포인트 전체 (파일·엑셀은 Base64) |
| 21 | Entity를 Controller에서 직접 반환하지 않는가? | 통과 | `from(Entity)` Response DTO 변환 |
| 22 | RuntimeException, IllegalArgumentException을 직접 throw하지 않는가? | 통과 | 엔진 내부 private 정규식 타임아웃 신호 클래스는 외부로 전파되지 않음 |
| 23 | ddl-auto에 의존하지 않고 SQL DDL을 제공했는가? | 통과 | |
| 24 | core 모듈을 수정하지 않았는가? | 통과 | 실제 core API(`CustomUserDetails`, `ErrorCode`)에 맞춤 |
| 25 | app 모듈을 수정하지 않았는가? | 통과 | settings.gradle, app/build.gradle 미수정 |
| 26 | 메뉴/권한 등록 정보가 README.md에 작성되어 있는가? | 통과 | 5장, 6장 |
| 27 | 필요한 core 기능이 부족한 경우 README.md에 "core에 추가 필요"로 명시했는가? | 통과 | 9장 (실제 core 대조 결과 반영) |
