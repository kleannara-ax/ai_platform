# 신규 모듈 추가 가이드

> 플랫폼 관리시스템에 새로운 업무 모듈을 추가하고 메뉴로 등록하는 방법

---

## 목차

1. [시스템 구조 이해](#1-시스템-구조-이해)
2. [신규 모듈 생성 (5단계)](#2-신규-모듈-생성-5단계)
3. [메뉴 등록 (2가지 방법)](#3-메뉴-등록-2가지-방법)
4. [접근 권한 설정](#4-접근-권한-설정)
5. [빌드 및 배포](#5-빌드-및-배포)
6. [실전 예제: 공지사항 모듈](#6-실전-예제-공지사항-모듈)
7. [체크리스트](#7-체크리스트)

---

## 1. 시스템 구조 이해

### 멀티모듈 구조

```
platform/
├── core/                    ← 공통 모듈 (인증, 메뉴, 권한, 보안)
│   └── com.company.core
├── module-common/           ← 공통 모듈 (사용자 프로필, 부서, 공통코드)
│   └── com.company.module.user, com.company.module.code
├── module-xxx/              ← 신규 모듈 (여기에 추가!)
│   └── com.company.module.xxx
└── app/                     ← 실행 모듈 (모든 모듈 조합)
    └── com.company.app
```

### 핵심 규칙

| 규칙 | 설명 |
|------|------|
| 패키지 | `com.company.module.{모듈명}` 하위에 생성 |
| 테이블 Prefix | `MOD_{모듈명}_` (예: `MOD_NOTICE_`) |
| 자동 스캔 | `com.company.module` 하위는 자동으로 Component/Entity/Repository 스캔됨 |
| Core 수정 불필요 | 신규 모듈 추가 시 Core 소스 코드 수정 없음 |

---

## 2. 신규 모듈 생성 (5단계)

> 예시: `module-notice` (공지사항 모듈)을 추가한다고 가정

### Step 1. 디렉토리 생성

```bash
cd /data/aiplatform/source

# 디렉토리 구조 생성
mkdir -p module-notice/src/main/java/com/company/module/notice/{config,controller,dto,entity,repository,service}
```

생성되는 구조:
```
module-notice/
└── src/main/java/com/company/module/notice/
    ├── config/           ← 모듈 설정
    ├── controller/       ← REST API 컨트롤러
    ├── dto/              ← 요청/응답 DTO
    ├── entity/           ← JPA 엔티티
    ├── repository/       ← JPA Repository
    └── service/          ← 비즈니스 로직
```

### Step 2. build.gradle 작성

`module-notice/build.gradle` 파일 생성:

```groovy
dependencies {
    // Core 모듈 의존 (인증, 공통 예외, 응답 형식 등)
    implementation project(':core')
}
```

### Step 3. settings.gradle에 등록

프로젝트 루트의 `settings.gradle`에 한 줄 추가:

```groovy
rootProject.name = 'platform'

include 'core'
include 'module-common'
include 'module-notice'    // ← 추가
include 'app'
```

### Step 4. app/build.gradle에 의존성 추가

`app/build.gradle`의 dependencies 블록에 추가:

```groovy
dependencies {
    implementation project(':core')
    implementation project(':module-common')
    implementation project(':module-notice')    // ← 추가

    runtimeOnly 'org.mariadb.jdbc:mariadb-java-client'
}
```

### Step 5. 모듈 Config 클래스 생성

`module-notice/src/main/java/com/company/module/notice/config/ModuleNoticeConfig.java`:

```java
package com.company.module.notice.config;

import org.springframework.context.annotation.Configuration;

/**
 * module-notice 모듈 설정
 *
 * - ComponentScan: App의 @ComponentScan(com.company.module)에 의해 자동 포함
 * - EntityScan: App의 @EntityScan(com.company.module)에 의해 자동 포함
 * - Repository: App의 @EnableJpaRepositories(com.company.module)에 의해 자동 포함
 */
@Configuration
public class ModuleNoticeConfig {
    // 모듈 초기화 설정이 필요한 경우 여기에 추가
}
```

> **중요**: `PlatformApplication.java`의 `@ComponentScan`, `@EntityScan`, `@EnableJpaRepositories`가
> 모두 `com.company.module`을 스캔하므로, **App 모듈 소스 수정은 필요 없습니다.**

---

## 3. 메뉴 등록 (2가지 방법)

### 방법 1: 관리 화면에서 등록 (권장)

1. 관리자 계정으로 로그인 (`https://aiplatform.kleannara.com`)
2. 좌측 메뉴 → **메뉴 관리** 클릭
3. 우측 상단 **+ 메뉴 추가** 클릭
4. 메뉴 정보 입력:

| 항목 | 입력값 (예시) | 설명 |
|------|-------------|------|
| 메뉴명 | `공지사항` | 사이드바에 표시될 이름 |
| 메뉴 코드 | `NOTICE_MGMT` | 유니크 코드 (영문 대문자) |
| 상위 메뉴 | `없음 (최상위)` | 최상위 또는 다른 메뉴 하위 |
| URL | `/notices` | 프론트엔드 라우트 경로 |
| 아이콘 | `notice` | 아이콘 식별자 |
| 유형 | `메뉴` | MENU, BUTTON, API 중 선택 |
| 정렬 순서 | `5` | 사이드바 표시 순서 |
| 설명 | `공지사항 관리` | 메뉴 설명 |

5. **등록** 클릭
6. 좌측 메뉴 → **접근 권한** 클릭
7. 추가한 메뉴의 역할별 접근 체크 → **변경사항 저장** 클릭

### 방법 2: SQL 직접 실행

```sql
-- 1. 메뉴 추가
INSERT INTO CORE_MENU (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES ('공지사항', 'NOTICE_MGMT', NULL, '/notices', 'notice', 5, 'MENU', 1, 1, '공지사항 관리');

-- 추가된 메뉴 ID 확인
SELECT MENU_ID FROM CORE_MENU WHERE MENU_CODE = 'NOTICE_MGMT';
-- 예: MENU_ID = 5

-- 2. 역할별 접근 권한 매핑
INSERT INTO CORE_ROLE_MENU (ROLE, MENU_ID) VALUES
    ('ROLE_ADMIN', 5),     -- 관리자: 접근 가능
    ('ROLE_MANAGER', 5),   -- 매니저: 접근 가능
    ('ROLE_USER', 5);      -- 일반사용자: 접근 가능

-- 3. (선택) 세부 권한 추가
INSERT INTO CORE_PERMISSION (PERM_CODE, PERM_NAME, DESCRIPTION, IS_ACTIVE) VALUES
    ('NOTICE_READ',  '공지사항 조회', '공지사항 목록/상세 조회', 1),
    ('NOTICE_WRITE', '공지사항 관리', '공지사항 등록/수정/삭제', 1);

-- 4. (선택) 역할-권한 매핑
INSERT INTO CORE_ROLE_PERMISSION (ROLE, PERM_ID) VALUES
    ('ROLE_ADMIN',   (SELECT PERM_ID FROM CORE_PERMISSION WHERE PERM_CODE='NOTICE_READ')),
    ('ROLE_ADMIN',   (SELECT PERM_ID FROM CORE_PERMISSION WHERE PERM_CODE='NOTICE_WRITE')),
    ('ROLE_MANAGER', (SELECT PERM_ID FROM CORE_PERMISSION WHERE PERM_CODE='NOTICE_READ')),
    ('ROLE_USER',    (SELECT PERM_ID FROM CORE_PERMISSION WHERE PERM_CODE='NOTICE_READ'));
```

---

## 4. 접근 권한 설정

### API 레벨 권한 제어

컨트롤러에서 `@PreAuthorize` 어노테이션으로 제어:

```java
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    // 모든 인증된 사용자 접근 가능
    @GetMapping
    public ResponseEntity<?> getNotices() { ... }

    // ADMIN, MANAGER만 접근 가능
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<?> createNotice() { ... }

    // ADMIN만 접근 가능
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteNotice() { ... }
}
```

### 역할 종류

| 역할 | 코드 | 설명 |
|------|------|------|
| 관리자 | `ROLE_ADMIN` | 모든 기능 접근 |
| 매니저 | `ROLE_MANAGER` | 조회 + 등록/수정 |
| 일반사용자 | `ROLE_USER` | 조회만 |

---

## 5. 빌드 및 배포

### 개발 환경 (로컬)

```bash
cd /data/aiplatform/source
./gradlew :app:clean :app:build -x test
```

### 운영 서버 배포

```bash
su - knaraadm
cd /data/aiplatform/source

# 1. 소스 업데이트
git pull origin main

# 2. 빌드
./gradlew :app:clean :app:build -x test

# 3. JAR 교체
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/

# 4. DB 테이블 생성 (신규 모듈의 테이블이 있는 경우)
mariadb -u appuser -p'비밀번호' aiplatform < sql/V1.1.0__notice_schema.sql

# 5. 서비스 재시작
sudo systemctl restart aiplatform

# 6. 확인
sudo systemctl status aiplatform
curl -s http://localhost:8080/api/health
```

---

## 6. 실전 예제: 공지사항 모듈

### 6-1. DB 테이블 생성

`sql/V1.1.0__notice_schema.sql`:

```sql
-- 공지사항 테이블
CREATE TABLE IF NOT EXISTS MOD_NOTICE (
    NOTICE_ID    BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '공지 ID (PK)',
    TITLE        VARCHAR(200) NOT NULL                 COMMENT '제목',
    CONTENT      TEXT         NOT NULL                 COMMENT '내용',
    NOTICE_TYPE  VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT '유형 (NORMAL, IMPORTANT, URGENT)',
    IS_PINNED    TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '상단 고정 여부',
    VIEW_COUNT   INT          NOT NULL DEFAULT 0       COMMENT '조회수',
    CREATED_BY   BIGINT       NOT NULL                 COMMENT '작성자 ID',
    CREATED_AT   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT   DATETIME     NULL     ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (NOTICE_ID),
    CONSTRAINT FK_MOD_NOTICE_USER FOREIGN KEY (CREATED_BY)
        REFERENCES CORE_USER (USER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT='공지사항';

CREATE INDEX IDX_MOD_NOTICE_TYPE ON MOD_NOTICE (NOTICE_TYPE);
CREATE INDEX IDX_MOD_NOTICE_CREATED ON MOD_NOTICE (CREATED_AT DESC);
```

### 6-2. Entity

`module-notice/src/main/java/com/company/module/notice/entity/Notice.java`:

```java
package com.company.module.notice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "MOD_NOTICE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "NOTICE_ID")
    private Long noticeId;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "CONTENT", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "NOTICE_TYPE", length = 20)
    @Builder.Default
    private String noticeType = "NORMAL";

    @Column(name = "IS_PINNED")
    @Builder.Default
    private Boolean isPinned = false;

    @Column(name = "VIEW_COUNT")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String title, String content, String noticeType, Boolean isPinned) {
        this.title = title;
        this.content = content;
        this.noticeType = noticeType;
        this.isPinned = isPinned;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }
}
```

### 6-3. Repository

`module-notice/src/main/java/com/company/module/notice/repository/NoticeRepository.java`:

```java
package com.company.module.notice.repository;

import com.company.module.notice.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Page<Notice> findByNoticeTypeOrderByCreatedAtDesc(String noticeType, Pageable pageable);

    Page<Notice> findAllByOrderByIsPinnedDescCreatedAtDesc(Pageable pageable);
}
```

### 6-4. DTO

`module-notice/src/main/java/com/company/module/notice/dto/NoticeRequest.java`:

```java
package com.company.module.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NoticeRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private String noticeType;  // NORMAL, IMPORTANT, URGENT
    private Boolean isPinned;
}
```

`module-notice/src/main/java/com/company/module/notice/dto/NoticeResponse.java`:

```java
package com.company.module.notice.dto;

import com.company.module.notice.entity.Notice;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Builder @AllArgsConstructor
public class NoticeResponse {
    private Long noticeId;
    private String title;
    private String content;
    private String noticeType;
    private Boolean isPinned;
    private Integer viewCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoticeResponse from(Notice notice) {
        return NoticeResponse.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .noticeType(notice.getNoticeType())
                .isPinned(notice.getIsPinned())
                .viewCount(notice.getViewCount())
                .createdBy(notice.getCreatedBy())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
```

### 6-5. Service

`module-notice/src/main/java/com/company/module/notice/service/NoticeService.java`:

```java
package com.company.module.notice.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.module.notice.dto.NoticeRequest;
import com.company.module.notice.dto.NoticeResponse;
import com.company.module.notice.entity.Notice;
import com.company.module.notice.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;

    public Page<NoticeResponse> getNotices(Pageable pageable) {
        return noticeRepository.findAllByOrderByIsPinnedDescCreatedAtDesc(pageable)
                .map(NoticeResponse::from);
    }

    public NoticeResponse getNotice(Long noticeId) {
        Notice notice = findById(noticeId);
        return NoticeResponse.from(notice);
    }

    @Transactional
    public NoticeResponse createNotice(NoticeRequest request, Long userId) {
        Notice notice = Notice.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .noticeType(request.getNoticeType() != null ? request.getNoticeType() : "NORMAL")
                .isPinned(request.getIsPinned() != null ? request.getIsPinned() : false)
                .createdBy(userId)
                .build();
        Notice saved = noticeRepository.save(notice);
        log.info("공지사항 등록: noticeId={}, title={}", saved.getNoticeId(), saved.getTitle());
        return NoticeResponse.from(saved);
    }

    @Transactional
    public NoticeResponse updateNotice(Long noticeId, NoticeRequest request) {
        Notice notice = findById(noticeId);
        notice.update(request.getTitle(), request.getContent(),
                request.getNoticeType(), request.getIsPinned());
        log.info("공지사항 수정: noticeId={}", noticeId);
        return NoticeResponse.from(notice);
    }

    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = findById(noticeId);
        noticeRepository.delete(notice);
        log.info("공지사항 삭제: noticeId={}", noticeId);
    }

    private Notice findById(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new EntityNotFoundException("Notice", noticeId));
    }
}
```

### 6-6. Controller

`module-notice/src/main/java/com/company/module/notice/controller/NoticeController.java`:

```java
package com.company.module.notice.controller;

import com.company.core.common.response.ApiResponse;
import com.company.core.common.response.PageResponse;
import com.company.core.security.CustomUserDetails;
import com.company.module.notice.dto.NoticeRequest;
import com.company.module.notice.dto.NoticeResponse;
import com.company.module.notice.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /** 공지사항 목록 조회 */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NoticeResponse>>> getNotices(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(noticeService.getNotices(pageable))));
    }

    /** 공지사항 상세 조회 */
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeResponse>> getNotice(@PathVariable Long noticeId) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.getNotice(noticeId)));
    }

    /** 공지사항 등록 (ADMIN, MANAGER만) */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<NoticeResponse>> createNotice(
            @Valid @RequestBody NoticeRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(noticeService.createNotice(request, user.getUserId())));
    }

    /** 공지사항 수정 (ADMIN, MANAGER만) */
    @PutMapping("/{noticeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<NoticeResponse>> updateNotice(
            @PathVariable Long noticeId, @Valid @RequestBody NoticeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(noticeService.updateNotice(noticeId, request)));
    }

    /** 공지사항 삭제 (ADMIN만) */
    @DeleteMapping("/{noticeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteNotice(@PathVariable Long noticeId) {
        noticeService.deleteNotice(noticeId);
        return ResponseEntity.ok(ApiResponse.success());
    }
}
```

### 6-7. 메뉴 등록 (관리 화면 또는 SQL)

```sql
-- 메뉴 등록
INSERT INTO CORE_MENU (MENU_NAME, MENU_CODE, PARENT_ID, MENU_URL, ICON, SORT_ORDER, MENU_TYPE, IS_VISIBLE, IS_ACTIVE, DESCRIPTION)
VALUES ('공지사항', 'NOTICE_MGMT', NULL, '/notices', 'notice', 5, 'MENU', 1, 1, '공지사항 관리');

-- 역할별 접근 권한 (MENU_ID는 위 INSERT 결과 확인)
INSERT INTO CORE_ROLE_MENU (ROLE, MENU_ID) VALUES
    ('ROLE_ADMIN',   LAST_INSERT_ID()),
    ('ROLE_MANAGER', LAST_INSERT_ID()),
    ('ROLE_USER',    LAST_INSERT_ID());
```

---

## 7. 체크리스트

### 소스 코드

- [ ] `module-xxx/build.gradle` 생성
- [ ] `settings.gradle`에 `include 'module-xxx'` 추가
- [ ] `app/build.gradle`에 `implementation project(':module-xxx')` 추가
- [ ] 패키지: `com.company.module.xxx` 하위에 생성
- [ ] Config 클래스 생성 (`@Configuration`)
- [ ] Entity, Repository, Service, Controller, DTO 작성
- [ ] 빌드 성공 확인: `./gradlew :app:clean :app:build -x test`

### 데이터베이스

- [ ] 테이블 생성 SQL 작성 (테이블명 Prefix: `MOD_{모듈명}_`)
- [ ] 서버에서 SQL 실행
- [ ] 테이블 생성 확인

### 메뉴 등록

- [ ] CORE_MENU에 메뉴 추가 (관리 화면 또는 SQL)
- [ ] CORE_ROLE_MENU에 역할별 접근 권한 매핑
- [ ] (선택) CORE_PERMISSION에 세부 권한 추가
- [ ] (선택) CORE_ROLE_PERMISSION에 역할-권한 매핑

### 배포

- [ ] GitHub 커밋/푸시
- [ ] 서버에서 `git pull origin main`
- [ ] `./gradlew :app:clean :app:build -x test`
- [ ] DB 테이블 생성 SQL 실행
- [ ] JAR 교체: `cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/`
- [ ] 서비스 재시작: `sudo systemctl restart aiplatform`
- [ ] 헬스체크: `curl -s http://localhost:8080/api/health`
- [ ] API 테스트

---

## 참고: 파일 수정이 필요한 곳 요약

| 파일 | 수정 내용 | 비고 |
|------|----------|------|
| `settings.gradle` | `include 'module-xxx'` 추가 | 필수 |
| `app/build.gradle` | `implementation project(':module-xxx')` 추가 | 필수 |
| `module-xxx/build.gradle` | 신규 생성 | 필수 |
| `module-xxx/src/main/java/...` | 모듈 소스 코드 | 필수 |
| `sql/Vx.x.x__xxx_schema.sql` | DB 테이블 생성 스크립트 | 테이블 있을 때 |
| **PlatformApplication.java** | **수정 불필요** | 자동 스캔 |
| **Core 모듈 소스** | **수정 불필요** | 독립 모듈 |
