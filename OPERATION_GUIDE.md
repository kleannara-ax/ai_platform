# 플랫폼 관리시스템 - 운영 매뉴얼

> 서비스 종료 / 시작 / 재시작 절차 및 GitHub 소스 업데이트 배포 가이드

---

## 시스템 개요

| 항목 | 내용 |
|------|------|
| **서비스명** | aiplatform (systemd 서비스) |
| **운영 사용자** | knaraadm |
| **서비스 URL** | https://aiplatform.kleannara.com |
| **GitHub 저장소** | https://github.com/kleannara-ax/ai_platform |
| **기본 브랜치** | main |
| **소스 경로** | /data/aiplatform/source |
| **JAR 경로** | /data/aiplatform/bin/platform-1.0.0.jar |
| **설정 파일** | /data/aiplatform/config/application-prod.yml |
| **환경변수 파일** | /data/aiplatform/config/platform.env |
| **애플리케이션 로그** | /data/aiplatform/logs/ |
| **Nginx 설정** | /etc/nginx/conf.d/aiplatform.conf |
| **systemd 서비스 파일** | /etc/systemd/system/aiplatform.service |

### 디렉토리 구조

```
/data/aiplatform/
  ├── source/          # Git 소스 코드 (git clone 위치)
  ├── bin/             # 실행용 JAR 파일 (platform-1.0.0.jar)
  ├── config/          # 설정 파일 (application-prod.yml, platform.env)
  ├── logs/            # 애플리케이션 로그
  ├── sql/             # SQL 스크립트
  └── backup/          # DB 백업 파일
```

### 관련 서비스 구성

```
[클라이언트] → [AWS ALB/SSL] → [Nginx :80] → [Spring Boot :8080] → [MariaDB :3306]
```

- **AWS**: SSL 인증서 처리 (HTTPS → HTTP로 변환하여 서버에 전달)
- **Nginx**: 리버스 프록시 (80 포트 → 8080 포트로 전달)
- **Spring Boot**: 애플리케이션 서버 (8080 포트)
- **MariaDB**: 데이터베이스 (3306 포트)

---

## 1. 서비스 종료

### 1-1. 표준 종료 절차

```bash
# 서비스 종료
sudo systemctl stop aiplatform
```

### 1-2. 종료 확인

```bash
# 상태 확인 - Active: inactive (dead) 가 나오면 정상 종료
sudo systemctl status aiplatform
```

정상 종료 시 출력 예시:
```
● aiplatform.service - Platform Management System
   Loaded: loaded (/etc/systemd/system/aiplatform.service; enabled)
   Active: inactive (dead) since ...
```

### 1-3. 종료 확인 (추가 검증)

```bash
# 8080 포트가 사용 중이지 않은지 확인 (출력 없으면 정상 종료)
sudo ss -tlnp | grep 8080

# Java 프로세스 확인 (출력 없으면 정상 종료)
ps -ef | grep platform-1.0.0 | grep -v grep
```

### 1-4. 강제 종료 (서비스가 종료되지 않을 때)

```bash
# 1) systemctl stop이 안 될 경우 - 강제 kill
sudo systemctl kill -s SIGKILL aiplatform

# 2) 그래도 안 될 경우 - 프로세스 직접 kill
ps -ef | grep platform-1.0.0 | grep -v grep
# PID 확인 후
sudo kill -9 <PID>
```

> **주의사항**
> - 종료 시 현재 처리 중인 요청이 중단될 수 있습니다.
> - Spring Boot의 Graceful Shutdown이 설정되어 있으면 진행 중인 요청 완료 후 종료됩니다.
> - 강제 종료(`kill -9`)는 데이터 유실 가능성이 있으므로 최후의 수단으로 사용하세요.

---

## 2. 서비스 시작

### 2-1. 시작 전 사전 확인

```bash
# 1) MariaDB가 실행 중인지 확인 (필수 - DB 없으면 서비스 시작 실패)
sudo systemctl status mariadb
# Active: active (running) 인지 확인

# 2) Nginx가 실행 중인지 확인
sudo systemctl status nginx
# Active: active (running) 인지 확인

# 3) JAR 파일 존재 확인
ls -lh /data/aiplatform/bin/platform-1.0.0.jar

# 4) 설정 파일 존재 확인
ls -l /data/aiplatform/config/application-prod.yml
ls -l /data/aiplatform/config/platform.env

# 5) 포트 충돌 확인 (8080 포트가 비어있어야 함)
sudo ss -tlnp | grep 8080
```

### 2-2. 서비스 시작

```bash
# 서비스 시작
sudo systemctl start aiplatform
```

### 2-3. 시작 확인

```bash
# 1) 상태 확인 - Active: active (running) 이 나와야 정상
sudo systemctl status aiplatform

# 2) 헬스 체크 (Spring Boot 기동까지 30초~1분 대기 후 실행)
#    정상 응답 예: {"success":true,"code":200,"data":{"status":"UP"}}
sleep 30 && curl -s http://localhost:8080/api/health

# 3) 외부 접근 확인 (Nginx를 통한 접근)
curl -s -H "Host: aiplatform.kleannara.com" http://localhost/api/health
```

### 2-4. 시작 실패 시 로그 확인

```bash
# systemd 로그 확인 (최근 200줄)
sudo journalctl -u aiplatform -n 200 --no-pager

# 또는 실시간 로그 보기 (Ctrl+C로 종료)
sudo journalctl -u aiplatform -f

# 애플리케이션 로그 확인
tail -200 /data/aiplatform/logs/platform.log
tail -100 /data/aiplatform/logs/platform-error.log
```

### 2-5. 시작 실패 주요 원인 및 해결

| 증상 | 원인 | 해결 방법 |
|------|------|----------|
| `Connection refused` (DB) | MariaDB 미실행 | `sudo systemctl start mariadb` |
| `Access denied` (DB) | DB 비밀번호 오류 | `/data/aiplatform/config/platform.env` 확인 |
| `Port already in use: 8080` | 포트 충돌 | `sudo ss -tlnp \| grep 8080` 으로 확인 후 해당 프로세스 종료 |
| `UnsupportedClassVersionError` | Java 버전 불일치 | `java -version` 으로 17 확인 |
| `FileNotFoundException` (JAR) | JAR 파일 누락 | `/data/aiplatform/bin/` 에 JAR 파일 배치 |
| `Permission denied` | 권한 문제 | `sudo chown knaraadm:knaraadm /data/aiplatform/bin/*.jar` |

---

## 3. 서비스 재시작

### 3-1. 표준 재시작

```bash
# 서비스 재시작 (종료 후 자동으로 시작)
sudo systemctl restart aiplatform
```

### 3-2. 재시작 확인

```bash
# 1) 상태 확인
sudo systemctl status aiplatform

# 2) 헬스 체크 (30초~1분 대기)
sleep 30 && curl -s http://localhost:8080/api/health

# 3) 로그에 에러 없는지 확인
sudo journalctl -u aiplatform -n 50 --no-pager | grep -i error
```

### 3-3. 설정 변경 후 재시작

systemd 서비스 파일(`/etc/systemd/system/aiplatform.service`)을 수정한 경우:

```bash
# 1) 서비스 파일 수정 후 데몬 리로드 (필수)
sudo systemctl daemon-reload

# 2) 서비스 재시작
sudo systemctl restart aiplatform

# 3) 상태 확인
sudo systemctl status aiplatform
```

> **참고**: `application-prod.yml` 또는 `platform.env` 만 수정한 경우에는 `daemon-reload` 없이 `restart`만 하면 됩니다.

---

## 4. GitHub 소스 업데이트 및 배포

> **개발 PC에서 코드를 수정하고 GitHub에 push한 후**, 서버에 반영하는 절차입니다.

### 4-1. 전체 배포 흐름

```
[개발 PC]                              [운영 서버]
 코드 수정                              
   ↓                                   
 git commit                            
   ↓                                   
 git push origin main                  
   ↓                                   
   ════════════════════════════════════→ git pull origin main
                                          ↓
                                        ./gradlew :app:clean :app:build -x test
                                          ↓
                                        cp JAR → /data/aiplatform/bin/
                                          ↓
                                        sudo systemctl restart aiplatform
                                          ↓
                                        헬스 체크 확인
```

### 4-2. 운영 서버 배포 절차 (단계별)

```bash
# ── Step 1. knaraadm 사용자로 전환 ──
su - knaraadm

# ── Step 2. 소스 디렉토리로 이동 ──
cd /data/aiplatform/source

# ── Step 3. 현재 상태 확인 ──
git status
# 로컬 변경사항이 없는 것을 확인 (clean working tree)
# 만약 로컬 변경이 있으면 Step 3-1 참고

# ── Step 4. 최신 소스 가져오기 ──
git pull origin main
# Already up to date. 또는 변경된 파일 목록이 표시됨

# ── Step 5. 빌드 ──
./gradlew :app:clean :app:build -x test
# BUILD SUCCESSFUL 이 나와야 정상

# ── Step 6. 빌드 결과물 확인 ──
ls -lh app/build/libs/platform-1.0.0.jar
# 파일 크기와 타임스탬프 확인

# ── Step 7. JAR 파일 교체 ──
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/

# ── Step 8. 서비스 재시작 ──
sudo systemctl restart aiplatform

# ── Step 9. 정상 동작 확인 ──
sudo systemctl status aiplatform
sleep 30 && curl -s http://localhost:8080/api/health
```

### 4-3. 빠른 배포 (한 줄 명령어)

```bash
# knaraadm 사용자로 전환 후 한 번에 실행
su - knaraadm
cd /data/aiplatform/source && \
git pull origin main && \
./gradlew :app:clean :app:build -x test && \
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/ && \
sudo systemctl restart aiplatform && \
sleep 30 && \
curl -s http://localhost:8080/api/health
```

### 4-4. SQL 변경이 포함된 경우

소스 업데이트에 DB 스키마 변경(테이블 추가/수정)이 포함된 경우:

```bash
# Step 1~6: 위와 동일 (git pull → 빌드)

# ── Step 6-1. 서비스 종료 (DB 변경 전 안전을 위해) ──
sudo systemctl stop aiplatform

# ── Step 6-2. DB 백업 (안전을 위해 권장) ──
mysqldump -u appuser -p'비밀번호' aiplatform --single-transaction | gzip > /data/aiplatform/backup/aiplatform_$(date +%Y%m%d_%H%M%S).sql.gz

# ── Step 6-3. SQL 스크립트 실행 ──
# 새로운 SQL 파일 확인
ls -lt sql/
# 해당 SQL 실행
mariadb -u appuser -p'비밀번호' aiplatform < sql/V1.x.x__변경내용.sql

# Step 7~9: 위와 동일 (JAR 교체 → 재시작 → 확인)
```

### 4-5. 로컬 변경사항이 있을 때 (Step 3-1)

서버에서 직접 파일을 수정한 경우, `git pull`이 실패할 수 있습니다:

```bash
# 방법 1: 로컬 변경사항 무시하고 원격 코드로 덮어쓰기
git stash                    # 로컬 변경사항 임시 저장
git pull origin main         # 최신 소스 가져오기
# git stash pop              # 필요 시 로컬 변경사항 복원

# 방법 2: 로컬 변경사항 완전 폐기 (주의!)
git checkout .               # 모든 로컬 변경 되돌리기
git pull origin main         # 최신 소스 가져오기
```

### 4-6. 빌드 실패 시 대처

```bash
# 1) 에러 메시지 확인
./gradlew :app:clean :app:build -x test 2>&1 | tail -50

# 2) Gradle 캐시 초기화 후 재시도
./gradlew clean
./gradlew :app:clean :app:build -x test

# 3) 그래도 안 되면 Gradle 캐시 완전 삭제
rm -rf ~/.gradle/caches
./gradlew :app:clean :app:build -x test

# 4) Java 버전 확인 (17 필수)
java -version

# 5) Gradle Wrapper 실행 권한 확인
chmod +x ./gradlew
```

### 4-7. 배포 후 롤백 (이전 버전으로 되돌리기)

문제가 발생하여 이전 버전으로 복구해야 할 때:

```bash
# 1) 서비스 종료
sudo systemctl stop aiplatform

# 2) Git에서 이전 커밋으로 되돌리기
cd /data/aiplatform/source
git log --oneline -10           # 최근 커밋 이력 확인
git checkout <이전커밋해시>      # 원하는 버전으로 이동

# 3) 다시 빌드
./gradlew :app:clean :app:build -x test

# 4) JAR 교체 및 재시작
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/
sudo systemctl restart aiplatform

# 5) 확인
curl -s http://localhost:8080/api/health

# 6) 롤백 후 main 브랜치로 복귀
git checkout main
```

---

## 5. 개발 PC에서 GitHub 업데이트 방법

### 5-1. 최초 저장소 클론

```bash
# 저장소 클론
git clone https://github.com/kleannara-ax/ai_platform.git
cd ai_platform
```

### 5-2. 코드 수정 → 커밋 → 푸시

```bash
# 1) 최신 소스 동기화
git pull origin main

# 2) 코드 수정 (IDE 또는 에디터에서 작업)

# 3) 변경사항 확인
git status
git diff

# 4) 스테이징 (변경된 파일 추가)
git add .                        # 모든 변경 파일 추가
# 또는
git add <특정파일경로>             # 특정 파일만 추가

# 5) 커밋
git commit -m "fix: 사용자 관리 이메일 검증 수정"

# 6) GitHub에 푸시
git push origin main
```

### 5-3. 커밋 메시지 규칙 (권장)

```
<타입>: <설명>

# 타입 종류
feat:     새로운 기능 추가
fix:      버그 수정
docs:     문서 수정
refactor: 코드 리팩토링
style:    코드 포맷 변경 (기능 변경 없음)
test:     테스트 추가/수정
chore:    빌드 설정, 패키지 등 기타 변경

# 예시
feat: 공지사항 모듈 추가
fix: 로그인 시 공백 제거 처리
docs: 운영 매뉴얼 업데이트
```

### 5-4. 브랜치 전략 (선택사항)

기능 개발 시 별도 브랜치에서 작업하고 main에 병합하는 방식:

```bash
# 1) 기능 브랜치 생성
git checkout -b feature/notice-module

# 2) 개발 및 커밋
git add .
git commit -m "feat: 공지사항 모듈 추가"

# 3) main 브랜치로 전환
git checkout main

# 4) 병합
git merge feature/notice-module

# 5) 푸시
git push origin main

# 6) 기능 브랜치 삭제 (선택)
git branch -d feature/notice-module
```

---

## 6. Nginx 관리

### 6-1. Nginx 기본 명령어

```bash
# 상태 확인
sudo systemctl status nginx

# 설정 검증 (문법 오류 확인 - 반드시 재시작 전 실행)
sudo nginx -t

# Nginx 재시작
sudo systemctl restart nginx

# Nginx 설정 리로드 (서비스 중단 없이 설정 반영)
sudo systemctl reload nginx
```

### 6-2. Nginx 로그 확인

```bash
# 에러 로그
sudo tail -50 /var/log/nginx/error.log

# 접근 로그
sudo tail -50 /var/log/nginx/access.log
```

### 6-3. Nginx 설정 수정 시 절차

```bash
# 1) 설정 파일 백업
sudo cp /etc/nginx/conf.d/aiplatform.conf /etc/nginx/conf.d/aiplatform.conf.bak

# 2) 설정 수정
sudo vi /etc/nginx/conf.d/aiplatform.conf

# 3) 문법 검증 (필수)
sudo nginx -t

# 4) 검증 통과 시 리로드
sudo systemctl reload nginx

# 5) 문제 발생 시 백업에서 복원
sudo cp /etc/nginx/conf.d/aiplatform.conf.bak /etc/nginx/conf.d/aiplatform.conf
sudo systemctl reload nginx
```

> **주의**: `nginx.conf`에 기본 server 블록(`server_name _;`)이 있으면 `aiplatform.conf`보다 우선 처리되어 서비스가 안 될 수 있습니다. 기본 server 블록을 주석 처리하거나 삭제하세요.

---

## 7. MariaDB 관리

### 7-1. MariaDB 기본 명령어

```bash
# 상태 확인
sudo systemctl status mariadb

# 시작 / 종료 / 재시작
sudo systemctl start mariadb
sudo systemctl stop mariadb
sudo systemctl restart mariadb
```

### 7-2. DB 접속 및 확인

```bash
# DB 접속
mariadb -u appuser -p'비밀번호' aiplatform

# 테이블 목록 확인
SHOW TABLES;

# 사용자 수 확인
SELECT COUNT(*) FROM CORE_USER;

# DB 연결 상태 확인
SHOW PROCESSLIST;
```

### 7-3. DB 백업

```bash
# 수동 백업
mysqldump -u appuser -p'비밀번호' aiplatform \
  --single-transaction \
  --routines \
  --triggers \
  | gzip > /data/aiplatform/backup/aiplatform_$(date +%Y%m%d_%H%M%S).sql.gz

# 백업 파일 확인
ls -lh /data/aiplatform/backup/
```

### 7-4. DB 복원

```bash
# 1) 서비스 종료
sudo systemctl stop aiplatform

# 2) 백업 파일에서 복원
gunzip < /data/aiplatform/backup/aiplatform_20260403_030000.sql.gz | mariadb -u appuser -p'비밀번호' aiplatform

# 3) 서비스 시작
sudo systemctl start aiplatform
```

---

## 8. 로그 확인

### 8-1. 로그 종류

| 로그 | 경로 | 용도 |
|------|------|------|
| systemd 로그 | `journalctl -u aiplatform` | 서비스 시작/종료/에러 |
| 애플리케이션 로그 | `/data/aiplatform/logs/platform.log` | 비즈니스 로그 (INFO) |
| 에러 로그 | `/data/aiplatform/logs/platform-error.log` | 에러 전용 |
| Nginx 접근 로그 | `/var/log/nginx/access.log` | HTTP 요청 기록 |
| Nginx 에러 로그 | `/var/log/nginx/error.log` | Nginx 오류 |

### 8-2. 로그 확인 명령어

```bash
# 실시간 애플리케이션 로그 (Ctrl+C로 종료)
tail -f /data/aiplatform/logs/platform.log

# 실시간 에러 로그
tail -f /data/aiplatform/logs/platform-error.log

# systemd 실시간 로그
sudo journalctl -u aiplatform -f

# 최근 로그에서 에러만 찾기
grep -i "error\|exception" /data/aiplatform/logs/platform.log | tail -30

# 특정 시간대 로그 검색 (systemd)
sudo journalctl -u aiplatform --since "2026-04-03 18:00:00" --until "2026-04-03 19:00:00"
```

---

## 9. 관리자 비밀번호 초기화

로그인이 안 될 때 DB에서 직접 비밀번호를 초기화합니다:

```bash
# admin 비밀번호를 admin123! 로 초기화
mariadb -u appuser -p'비밀번호' aiplatform -e \
  "UPDATE CORE_USER SET PASSWORD='\$2a\$10\$GKQBHCZW3fUd/bnINzscKuHAsHFU4YaH/oCqvhlzWWiMRkT5H8WqW' WHERE LOGIN_ID='admin';"
```

초기화 후 로그인: **admin** / **admin123!**

> **보안 주의**: 초기화 후 반드시 비밀번호를 변경하세요.

---

## 10. 전체 시스템 재시작 순서

서버 재부팅 또는 전체 시스템을 다시 시작해야 할 때:

### 10-1. 시작 순서 (의존성 순서)

```bash
# 1) MariaDB 시작 (가장 먼저)
sudo systemctl start mariadb
sudo systemctl status mariadb    # active 확인

# 2) Spring Boot 애플리케이션 시작
sudo systemctl start aiplatform
sleep 30
curl -s http://localhost:8080/api/health    # 헬스 체크

# 3) Nginx 시작 (가장 마지막)
sudo systemctl start nginx
sudo systemctl status nginx      # active 확인
```

### 10-2. 종료 순서 (역순)

```bash
# 1) Nginx 종료
sudo systemctl stop nginx

# 2) Spring Boot 애플리케이션 종료
sudo systemctl stop aiplatform

# 3) MariaDB 종료 (가장 마지막)
sudo systemctl stop mariadb
```

### 10-3. 부팅 시 자동 시작 설정 확인

```bash
# 자동 시작 상태 확인
sudo systemctl is-enabled mariadb      # enabled 확인
sudo systemctl is-enabled aiplatform   # enabled 확인
sudo systemctl is-enabled nginx        # enabled 확인

# 자동 시작 등록 (disabled인 경우)
sudo systemctl enable mariadb
sudo systemctl enable aiplatform
sudo systemctl enable nginx
```

---

## 11. 긴급 상황 대응

### 11-1. 서비스 접속 불가

```bash
# 1) 서비스 상태 확인
sudo systemctl status aiplatform
sudo systemctl status nginx
sudo systemctl status mariadb

# 2) 포트 확인
sudo ss -tlnp | grep -E "80|8080|3306"

# 3) 헬스 체크
curl -s http://localhost:8080/api/health           # Spring Boot 직접
curl -s -H "Host: aiplatform.kleannara.com" http://localhost/api/health  # Nginx 경유

# 4) 로그 확인
sudo journalctl -u aiplatform -n 100 --no-pager | tail -30
sudo tail -20 /var/log/nginx/error.log
```

### 11-2. 서비스는 실행 중인데 접속 안 되는 경우

| 상태 | 원인 | 해결 |
|------|------|------|
| `curl localhost:8080` 성공, `curl localhost` 실패 | Nginx 문제 | `sudo nginx -t && sudo systemctl restart nginx` |
| `curl localhost:8080` 실패 | Spring Boot 문제 | `sudo systemctl restart aiplatform` |
| `curl localhost:8080` → DB 관련 에러 | MariaDB 문제 | `sudo systemctl restart mariadb` → `sudo systemctl restart aiplatform` |
| 외부에서 접속 불가, 서버 내부는 정상 | 방화벽/보안그룹 | AWS 보안그룹, `firewall-cmd` 확인 |

### 11-3. Out of Memory (메모리 부족)

```bash
# 메모리 사용량 확인
free -h

# Java 프로세스 메모리 확인
ps -eo pid,rss,comm | grep java | awk '{printf "PID: %s, RSS: %.0f MB\n", $1, $2/1024}'

# 서비스 재시작으로 메모리 해제
sudo systemctl restart aiplatform
```

---

## 12. 요약 - 자주 사용하는 명령어

### 서비스 관리

```bash
sudo systemctl start aiplatform       # 시작
sudo systemctl stop aiplatform        # 종료
sudo systemctl restart aiplatform     # 재시작
sudo systemctl status aiplatform      # 상태 확인
```

### 헬스 체크

```bash
curl -s http://localhost:8080/api/health
```

### GitHub 업데이트 배포

```bash
su - knaraadm
cd /data/aiplatform/source
git pull origin main
./gradlew :app:clean :app:build -x test
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/
sudo systemctl restart aiplatform
curl -s http://localhost:8080/api/health
```

### 로그 확인

```bash
sudo journalctl -u aiplatform -f                    # 실시간 systemd 로그
tail -f /data/aiplatform/logs/platform.log          # 실시간 애플리케이션 로그
tail -f /data/aiplatform/logs/platform-error.log    # 실시간 에러 로그
```

---

**작성일**: 2026-04-06
**시스템 버전**: Platform Management System v1.0.0
**Spring Boot**: 3.2.5 | **Java**: 17 | **MariaDB**: 10.11+
