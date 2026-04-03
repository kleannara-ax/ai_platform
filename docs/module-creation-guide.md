# 신규 업무 모듈 추가 가이드

## 1. 모듈 생성 절차

새로운 업무 모듈(예: `module-board`)을 추가하는 절차를 설명합니다.
**Core 소스 코드 수정 없이** 아래 단계만 수행하면 됩니다.

### Step 1: 디렉토리 생성

```bash
mkdir -p module-board/src/main/java/com/company/module/board/{config,controller,service,repository,entity,dto}
mkdir -p module-board/src/main/resources
mkdir -p module-board/src/test/java/com/company/module/board
```

### Step 2: settings.gradle에 모듈 등록

```groovy
// settings.gradle
rootProject.name = 'platform'

include 'core'
include 'module-user'
include 'module-board'    // ← 추가
include 'app'
```

### Step 3: module-board/build.gradle 작성

```groovy
dependencies {
    implementation project(':core')
}
```

### Step 4: app/build.gradle에 의존성 추가

```groovy
dependencies {
    implementation project(':core')
    implementation project(':module-user')
    implementation project(':module-board')    // ← 추가
}
```

### Step 5: 모듈 설정 클래스 생성

```java
package com.company.module.board.config;

@Configuration
@ComponentScan(basePackages = "com.company.module.board")
@EnableJpaRepositories(basePackages = "com.company.module.board.repository")
public class ModuleBoardConfig {
}
```

### Step 6: 엔티티, Repository, Service, Controller 구현

- **패키지**: `com.company.module.board.*`
- **테이블 Prefix**: `MOD_BOARD_`
- **API Prefix**: `/api/module-board/**`

## 2. 네이밍 규칙

| 항목 | 규칙 | 예시 |
|------|------|------|
| Gradle 모듈명 | `module-{기능명}` | `module-board` |
| 패키지 | `com.company.module.{기능명}` | `com.company.module.board` |
| DB 테이블 Prefix | `MOD_{기능명}_` | `MOD_BOARD_` |
| API URL Prefix | `/api/module-{기능명}/` | `/api/module-board/` |
| Config 클래스 | `Module{기능명}Config` | `ModuleBoardConfig` |

## 3. Bean 충돌 방지 체크리스트

- [ ] 모듈 Config에 `@ComponentScan(basePackages = ...)` 명시
- [ ] `@EnableJpaRepositories(basePackages = ...)` 명시
- [ ] 엔티티 클래스명에 모듈 prefix 고려 (동일 이름 방지)
- [ ] 패키지가 `com.company.module.{기능명}` 하위인지 확인

## 4. Core 모듈 활용

업무 모듈에서 Core의 기능을 아래와 같이 사용합니다:

```java
// 공통 응답 포맷
import com.company.core.common.response.ApiResponse;
return ResponseEntity.ok(ApiResponse.success(data));

// 공통 예외 처리 (GlobalExceptionHandler가 자동 처리)
throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
throw new EntityNotFoundException("Board", boardId);

// 보안 (Spring Security @PreAuthorize)
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminOnly() { ... }

// 트랜잭션
@Service
@Transactional(readOnly = true)
public class BoardService {
    @Transactional
    public Board create(...) { ... }
}
```

## 5. SQL DDL 규칙

```sql
-- 테이블명: MOD_{모듈명}_{테이블명}
CREATE TABLE MOD_BOARD_POST (
    POST_ID    BIGINT NOT NULL AUTO_INCREMENT COMMENT 'PK',
    ...
    CREATED_AT DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (POST_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- sql/ 디렉토리에 버전별 파일로 관리
-- 예: sql/V1.1.0__add_board_module.sql
```
