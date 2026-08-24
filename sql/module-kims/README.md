# module-kims SQL

KIMS(IT 운영 관리) 모듈의 DB 스크립트. 플랫폼과 **같은 DB** 를 쓴다(로컬: `steam`).
`ddl-auto=none` 이므로 아래 순서로 직접 실행한다.

## 실행 순서

| 순서 | 파일 | 설명 |
|------|------|------|
| 1 | `01_schema.sql` | KIMS 테이블 12개 (구조만). ddl-auto=none 이라 반드시 먼저 실행 |
| 2 | `02_menu.sql` | 플랫폼 메뉴 등록 (KIMS_MGMT 그룹 + 페이지 6개) + 역할별 노출 권한 |
| 3 | `03_perm_code.sql` | 공통코드 `KIMS_PERM` (KIMS 관리자 명단) |

```bash
mysql -u root --default-character-set=utf8mb4 {db} < 01_schema.sql
mysql -u root --default-character-set=utf8mb4 {db} < 02_menu.sql
mysql -u root --default-character-set=utf8mb4 {db} < 03_perm_code.sql
```

세 파일 모두 재실행해도 안전하다(표는 IF NOT EXISTS, 메뉴는 삭제 후 재삽입, 명단은 없을 때만 추가).

### 기존 KIMS 데이터를 함께 옮길 때

`01_schema.sql` 대신 KIMS 덤프를 적재하면 표와 데이터가 같이 들어온다.
덤프는 용량(2MB)·성격상 저장소에 넣지 않으므로 파일을 따로 받아서 쓴다.
원본 덤프는 별도 `kims` 데이터베이스를 만들도록 되어 있으니 아래 두 가지를 고쳐서 적재한다.

1. `DROP DATABASE` / `CREATE DATABASE` 두 줄 삭제
2. `USE \`kims\`;` → 플랫폼 DB 이름으로 변경

## 테이블 (12개, 플랫폼 DB 공용)

| 테이블 | 설명 |
|--------|------|
| `kims_user` | KIMS 사용자·부서 정보 (로그인은 플랫폼 계정으로 통일, 이 표는 담당자/요청자 데이터로 사용) |
| `service_request` / `request_log` / `request_attachment` | 업무 요청과 처리 이력·첨부 |
| `ip_address` / `ip_history` | PC(IP) 관리와 변경 이력 |
| `inventory_item` / `inventory_transaction` | 소모품 재고와 입출고 |
| `internet_work` | 인터넷 공사 |
| `program_install` | 프로그램 설치 |
| `supply_issue` | 소모품 지급 |
| `qr_location` | QR 구역 |

## 권한

KIMS 관리자 명단은 **공통코드 그룹 `KIMS_PERM`** 에서 관리한다 (공통코드 관리 화면 → KIMS 관리자 → 코드 추가,
`CODE` = 플랫폼 로그인 ID). 소방 `FIRE_PERM`, PS점검 `PS_INSP_AUTH` 과 같은 방식이다.

판정은 [`KimsPermission`](../../module-kims/src/main/java/com/company/module/kims/support/KimsPermission.java) 이 하고,
컨트롤러는 `@PreAuthorize` 로 이를 호출한다.

| 대상 | 표현식 | 통과 조건 |
|------|--------|-----------|
| 관리자 전용 (13곳) | `@kimsPerm.isAdmin(authentication)` | KIMS_PERM 등록자 **또는** 플랫폼 `ROLE_ADMIN` |
| 요청 처리·입력 (15곳) | `@kimsPerm.canWork(authentication)` | 위 관리자 **또는** 플랫폼 `ROLE_MANAGER` |

플랫폼 `ROLE_ADMIN` 을 항상 통과시키는 이유는 공통코드에서 실수로 전원을 지웠을 때 잠기지 않게 하기 위해서다.
목록은 30초간 캐시되므로 공통코드를 고친 뒤 최대 30초 후 반영된다.

`kims_user.ROLE` 은 더 이상 권한 판정에 쓰이지 않는다(화면의 담당자 구분 표시용으로만 남아 있음).

> 메뉴 노출은 여전히 역할 기반(`core_role_menu`: ROLE_ADMIN / ROLE_MANAGER)이다.
> KIMS_PERM 에만 넣고 플랫폼 역할이 ROLE_USER 인 사람은 API 는 되지만 좌측 메뉴가 보이지 않는다.

## 마이그레이션 이력

KIMS 단독 프로젝트 시절의 마이그레이션 SQL(01~29)은 이 폴더에서 뺐다.
현재 스키마가 `01_schema.sql` 에 이미 반영돼 있어 실행할 필요가 없고,
일부는 `DROP` 이나 데이터 재구축이라 운영 DB 에서 잘못 실행하면 위험하기 때문이다.
이력이 필요하면 `Kims/KIMS Ver 0.3.zip` 안의 `KIMS/sql/module-kims/` 를 참고한다.
