# 플랫폼 관리시스템 - 운영 매뉴얼

## 서비스 정보

| 항목 | 내용 |
|------|------|
| **서비스명** | aiplatform.service |
| **운영 사용자** | knaraadm |
| **URL** | https://aiplatform.kleannara.com |
| **소스 경로** | /data/aiplatform/source |
| **JAR 경로** | /data/aiplatform/bin/platform-1.0.0.jar |
| **설정 파일** | /data/aiplatform/config/application-prod.yml |
| **환경변수** | /data/aiplatform/config/platform.env |
| **로그** | /data/aiplatform/logs/ |
| **GitHub** | https://github.com/kleannara-ax/ai_platform |

---

## 1. 서비스 시작

```bash
sudo systemctl start aiplatform
```

시작 확인:
```bash
# 상태 확인 (Active: active (running) 이 나와야 정상)
sudo systemctl status aiplatform

# 헬스 체크 (Spring Boot 기동까지 30초~1분 대기)
curl -s http://localhost:8080/api/health
```

---

## 2. 서비스 종료

```bash
sudo systemctl stop aiplatform
```

종료 확인:
```bash
sudo systemctl status aiplatform
# Active: inactive (dead) 가 나오면 정상 종료
```

---

## 3. 서비스 재시작

```bash
sudo systemctl restart aiplatform
```

재시작 확인:
```bash
sudo systemctl status aiplatform
curl -s http://localhost:8080/api/health
```

---

## 4. GitHub 업데이트 반영 (소스 수정 후 배포)

```bash
# 1. knaraadm 사용자로 전환
su - knaraadm

# 2. 소스 디렉토리로 이동
cd /data/aiplatform/source

# 3. 최신 소스 가져오기
git pull origin main

# 4. 빌드
./gradlew :app:clean :app:build -x test

# 5. JAR 파일 교체
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/

# 6. 서비스 재시작
sudo systemctl restart aiplatform

# 7. 정상 확인
sudo systemctl status aiplatform
curl -s http://localhost:8080/api/health
```

> **참고**: SQL 변경이 있는 경우 `cp sql/*.sql /data/aiplatform/sql/` 후 DB에 수동 반영

---

## 5. 로그 확인

```bash
# 실시간 로그 (Ctrl+C로 종료)
sudo journalctl -u aiplatform -f

# 최근 100줄
sudo journalctl -u aiplatform -n 100 --no-pager

# 애플리케이션 로그 파일
tail -100 /data/aiplatform/logs/platform.log

# 에러 로그만
tail -100 /data/aiplatform/logs/platform-error.log
```

---

## 6. Nginx 관리

```bash
# Nginx 재시작
sudo systemctl restart nginx

# Nginx 설정 검증
sudo nginx -t

# Nginx 상태 확인
sudo systemctl status nginx

# Nginx 에러 로그
sudo tail -20 /var/log/nginx/error.log
```

---

## 7. 문제 해결

### 서비스가 시작 후 바로 죽는 경우

```bash
# 에러 로그 확인
sudo journalctl -u aiplatform -n 200 --no-pager

# 또는 직접 수동 실행하여 에러 확인
su - knaraadm
java \
  -jar /data/aiplatform/bin/platform-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/data/aiplatform/config/
```

### 504 Gateway Timeout (Nginx → Spring Boot 연결 실패)

```bash
# 1. Spring Boot가 실행 중인지 확인
sudo ss -tlnp | grep 8080

# 2. SELinux 허용 (Rocky Linux)
sudo setsebool -P httpd_can_network_connect 1

# 3. 서비스 재시작
sudo systemctl restart aiplatform
```

### DB 연결 실패

```bash
# MariaDB 상태 확인
sudo systemctl status mariadb

# DB 접속 테스트
mariadb -u appuser -p'Kleannara12#' aiplatform -e "SELECT 1;"

# platform.env 설정 확인
cat /data/aiplatform/config/platform.env
```

### 포트 충돌

```bash
# 8080 포트 사용 중인 프로세스 확인
sudo ss -tlnp | grep 8080
```

---

## 8. 관리자 비밀번호 초기화

로그인이 안 될 때 DB에서 직접 비밀번호를 초기화합니다:

```bash
# admin 비밀번호를 admin123! 로 초기화
mariadb -u appuser -p'Kleannara12#' aiplatform -e "UPDATE CORE_USER SET PASSWORD='\$2a\$10\$GKQBHCZW3fUd/bnINzscKuHAsHFU4YaH/oCqvhlzWWiMRkT5H8WqW' WHERE LOGIN_ID='admin';"
```

로그인: `admin` / `admin123!`

---

**작성일**: 2026-04-03
**시스템 버전**: Platform Management System v1.0.0
