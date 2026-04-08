# 플랫폼 관리시스템 - 외부 서버 배포 가이드

## 목차
1. [시스템 요구사항](#1-시스템-요구사항)
2. [서버 환경 구축](#2-서버-환경-구축)
3. [데이터베이스 설치 및 설정](#3-데이터베이스-설치-및-설정)
4. [애플리케이션 빌드](#4-애플리케이션-빌드)
5. [애플리케이션 배포 및 실행](#5-애플리케이션-배포-및-실행)
6. [리버스 프록시 설정 (Nginx)](#6-리버스-프록시-설정-nginx)
7. [서비스 등록 (systemd)](#7-서비스-등록-systemd)
8. [방화벽 설정](#8-방화벽-설정)
9. [SSL 인증서 설정 (HTTPS)](#9-ssl-인증서-설정-https)
10. [운영 환경 체크리스트](#10-운영-환경-체크리스트)
11. [모니터링 및 로그 관리](#11-모니터링-및-로그-관리)
12. [문제 해결 가이드](#12-문제-해결-가이드)

---

## 1. 시스템 요구사항

### 하드웨어 최소 사양
| 항목 | 최소 | 권장 |
|------|------|------|
| CPU | 2 Core | 4 Core 이상 |
| RAM | 2 GB | 4 GB 이상 |
| 디스크 | 20 GB | 50 GB 이상 (SSD 권장) |

### 소프트웨어 요구사항
| 소프트웨어 | 버전 | 용도 |
|------------|------|------|
| **OS** | CentOS 7+, Rocky Linux 8+, Ubuntu 20.04+ | 서버 운영체제 |
| **Java (JDK)** | **17** (필수) | 애플리케이션 런타임 |
| **MariaDB** | **10.11** 이상 (10.11.15 권장) | 데이터베이스 |
| **Nginx** | 1.18+ | 리버스 프록시 (선택) |
| **Gradle** | 8.7 (Wrapper 자동 설치, 별도 설치 불필요) | 빌드 도구 |
| **curl, unzip** | - | Gradle Wrapper 실행 시 필요 |

### 프로젝트 기술 스택
- **프레임워크**: Spring Boot 3.2.5
- **언어**: Java 17
- **ORM**: Spring Data JPA + Hibernate
- **인증**: JWT (JSON Web Token)
- **보안**: Spring Security + BCrypt
- **DB 드라이버**: MariaDB JDBC Connector
- **빌드 도구**: Gradle 8.7
- **빌드 결과물**: `platform-1.0.0.jar` (약 53MB, 실행형 Fat JAR)

---

## 2. 서버 환경 구축

### 2.1 Java 17 설치

#### Ubuntu / Debian
```bash
# 패키지 업데이트
sudo apt update && sudo apt upgrade -y

# OpenJDK 17 설치
sudo apt install -y openjdk-17-jdk

# 설치 확인
java -version
# 출력 예: openjdk version "17.0.x" ...
```

#### CentOS / Rocky Linux / RHEL
```bash
# EPEL 저장소 추가
sudo dnf install -y epel-release

# OpenJDK 17 설치
sudo dnf install -y java-17-openjdk java-17-openjdk-devel

# 설치 확인
java -version
```

#### JAVA_HOME 환경변수 설정
```bash
# Java 설치 경로 확인
sudo update-alternatives --config java

# /etc/profile.d/java.sh 생성
sudo tee /etc/profile.d/java.sh > /dev/null << 'EOF'
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
EOF

# 환경변수 적용
source /etc/profile.d/java.sh

# 확인
echo $JAVA_HOME
```

> **참고**: CentOS/Rocky의 경우 경로가 `/usr/lib/jvm/java-17-openjdk`일 수 있습니다.

---

### 2.2 서비스 운영 사용자 (knaraadm)

서비스 실행 및 파일 관리 전용 사용자는 `knaraadm`을 사용합니다.

```bash
# knaraadm 사용자가 없는 경우 생성
sudo useradd -m -s /bin/bash knaraadm
sudo passwd knaraadm

# /data 디렉토리 생성 (없는 경우)
sudo mkdir -p /data

# 애플리케이션 디렉토리 구조 생성
sudo mkdir -p /data/aiplatform/{source,bin,config,logs,backup,sql}

# 디렉토리 소유권 설정
sudo chown -R knaraadm:knaraadm /data/aiplatform
```

디렉토리 구조:
```
/data/aiplatform/
  ├── source/      # 프로젝트 소스 코드 (git clone 위치)
  ├── bin/         # 실행용 JAR 파일
  ├── config/      # 외부 설정 파일 (application-prod.yml, platform.env)
  ├── logs/        # 애플리케이션 로그
  ├── sql/         # SQL 스크립트 (스키마, 초기 데이터)
  └── backup/      # DB 백업 파일
```

---

## 3. 데이터베이스 설치 및 설정

### 3.1 MariaDB 설치

#### Ubuntu / Debian
```bash
# MariaDB 공식 저장소 추가 (10.11)
sudo apt install -y software-properties-common
curl -LsS https://downloads.mariadb.com/MariaDB/mariadb_repo_setup | sudo bash -s -- --mariadb-server-version=10.11

# MariaDB 설치
sudo apt install -y mariadb-server mariadb-client

# 서비스 시작 및 부팅 시 자동 시작
sudo systemctl start mariadb
sudo systemctl enable mariadb
```

#### CentOS / Rocky Linux / RHEL
```bash
# MariaDB 공식 저장소 추가
sudo tee /etc/yum.repos.d/MariaDB.repo > /dev/null << 'EOF'
[mariadb]
name = MariaDB
baseurl = https://mirror.mariadb.org/yum/10.11/centos/$releasever/$basearch
gpgkey = https://mirror.mariadb.org/yum/RPM-GPG-KEY-MariaDB
gpgcheck = 1
EOF

# MariaDB 설치
sudo dnf install -y MariaDB-server MariaDB-client

# 서비스 시작 및 부팅 시 자동 시작
sudo systemctl start mariadb
sudo systemctl enable mariadb
```

### 3.2 MariaDB 초기 보안 설정
```bash
# 보안 설정 마법사 실행
sudo mysql_secure_installation

# 질문에 대한 권장 답변:
# - Enter current password for root: (Enter - 초기 비밀번호 없음)
# - Switch to unix_socket authentication: Y
# - Change the root password: Y (강력한 비밀번호 설정)
# - Remove anonymous users: Y
# - Disallow root login remotely: Y
# - Remove test database: Y
# - Reload privilege tables: Y
```

### 3.3 MariaDB 인코딩 설정 (중요!)

```bash
sudo tee /etc/mysql/mariadb.conf.d/99-platform.cnf > /dev/null << 'EOF'
# /etc/mysql/mariadb.conf.d/99-platform.cnf
# CentOS인 경우: /etc/my.cnf.d/platform.cnf

[mysqld]
# 문자 인코딩 (한글 완전 지원)
character-set-server = utf8mb4
collation-server = utf8mb4_general_ci

# InnoDB 설정
innodb_buffer_pool_size = 512M
innodb_log_file_size = 128M
innodb_flush_log_at_trx_commit = 1

# 연결 설정
max_connections = 200
wait_timeout = 600
interactive_timeout = 600

# 타임존
default-time-zone = '+09:00'

[client]
default-character-set = utf8mb4

[mysql]
default-character-set = utf8mb4
EOF
```

```bash
# MariaDB 재시작
sudo systemctl restart mariadb
```

### 3.4 데이터베이스 및 사용자 생성
```bash
# MariaDB 접속
sudo mariadb -u root -p
```

```sql
-- 데이터베이스 생성
CREATE DATABASE platform_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

-- 전용 사용자 생성 (비밀번호는 반드시 변경!)
CREATE USER 'platform_user'@'localhost' IDENTIFIED BY '여기에_강력한_비밀번호_입력';

-- 권한 부여
GRANT ALL PRIVILEGES ON platform_db.* TO 'platform_user'@'localhost';
FLUSH PRIVILEGES;

-- 확인
SHOW DATABASES;
SELECT User, Host FROM mysql.user WHERE User = 'platform_user';

EXIT;
```

### 3.5 스키마 및 초기 데이터 입력

프로젝트의 `sql/` 디렉토리에 있는 SQL 파일을 순서대로 실행합니다:

```bash
# 1) 스키마 생성 (테이블, 인덱스, FK 등)
mariadb -u platform_user -p platform_db < /data/aiplatform/sql/V1.0.0__init_schema.sql

# 2) 초기 데이터 입력 (관리자 계정, 메뉴, 부서 등)
mariadb -u platform_user -p platform_db < /data/aiplatform/sql/V1.0.1__init_data.sql
```

생성되는 테이블 목록:
| 테이블명 | 설명 |
|----------|------|
| `CORE_USER` | 사용자 계정 |
| `CORE_MENU` | 메뉴 |
| `CORE_PERMISSION` | 권한 |
| `CORE_ROLE_MENU` | 역할-메뉴 매핑 |
| `CORE_ROLE_PERMISSION` | 역할-권한 매핑 |
| `MOD_USER_DEPARTMENT` | 부서 |
| `user_profile` | 사용자 프로필 |

초기 데이터:
- **관리자 계정**: `admin` / `admin123!` (ROLE_ADMIN)
- **기본 부서**: 본사, 경영지원본부, IT개발본부, 인사팀, 재무팀, 개발1팀, 개발2팀, 인프라팀
- **기본 메뉴**: 대시보드, 사용자 관리, 메뉴 관리, 접근 권한

```bash
# 데이터 입력 확인
mariadb -u platform_user -p platform_db -e "
    SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'platform_db';
"
```

---

## 4. 애플리케이션 빌드

> **Gradle을 별도로 설치할 필요 없습니다.**
> 프로젝트에 포함된 Gradle Wrapper(`./gradlew`)가 필요한 Gradle 8.7을 자동 다운로드합니다.

### 4.1 사전 요구사항 확인

빌드 전 필요한 것:
- **Java 17** (JDK) - 위 2.1절에서 설치 완료
- **curl, unzip** - Gradle Wrapper가 Gradle 배포판을 다운로드/압축해제할 때 필요

```bash
# 사전 패키지 설치 (없는 경우)
# Ubuntu / Debian
sudo apt install -y curl unzip

# CentOS / Rocky Linux
sudo dnf install -y curl unzip

# Java 17 설치 확인
java -version
# 출력 예: openjdk version "17.0.x" ...
```

### 4.2 서버에서 직접 빌드하는 경우

`knaraadm` 사용자로 로그인한 후 진행합니다:

```bash
# knaraadm 사용자로 로그인
su - knaraadm

# 최초 1회: 소스 코드 받기
cd /data/aiplatform
git clone <저장소_URL> source
# → /data/aiplatform/source/ 에 소스가 배치됩니다

# 이후 업데이트 시:
# cd /data/aiplatform/source && git pull

# 소스 디렉토리로 이동
cd /data/aiplatform/source

# Gradle Wrapper 실행 권한 부여
chmod +x ./gradlew

# 빌드 (테스트 제외) - Gradle 8.7 자동 다운로드
./gradlew :app:clean :app:build -x test

# 빌드 결과물 확인
ls -lh app/build/libs/platform-1.0.0.jar
# 약 53MB 실행형 JAR 파일이 생성됩니다

# JAR 파일을 실행 디렉토리로 복사
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/

# SQL 파일도 복사 (최초 1회 또는 변경 시)
cp sql/*.sql /data/aiplatform/sql/
```

> **에러 발생 시 확인사항:**
> - `Permission denied` → `chmod +x ./gradlew` 실행
> - `JAVA_HOME is not set` → [2.1 Java 설치](#21-java-17-설치) 확인
> - `Connection refused` (Gradle 다운로드 실패) → 프록시/방화벽 환경에서 `services.gradle.org` 접근 허용 필요
> - `unzip: command not found` → `sudo apt install unzip` 또는 `sudo dnf install unzip`

### 4.3 로컬 PC에서 빌드 후 서버로 전송

운영 서버에 소스 코드를 올리지 않고, **로컬 PC에서 빌드 → JAR만 전송**하는 방식입니다:

```bash
# --- 로컬 PC에서 ---
cd /path/to/platform
chmod +x ./gradlew
./gradlew :app:clean :app:build -x test

# SCP로 서버 전송 (knaraadm 사용자)
scp app/build/libs/platform-1.0.0.jar knaraadm@서버IP:/data/aiplatform/bin/
scp sql/*.sql knaraadm@서버IP:/data/aiplatform/sql/

# 설정 파일 전송 (최초 배포 시만)
scp app/src/main/resources/application-prod.yml knaraadm@서버IP:/data/aiplatform/config/
```

---

## 5. 애플리케이션 배포 및 실행

### 5.1 JAR 파일 배치

> 4.2에서 서버에서 직접 빌드한 경우 이미 `/data/aiplatform/bin/`에 복사되어 있습니다.
> 4.3에서 로컬 PC에서 SCP로 전송한 경우에도 이미 `/data/aiplatform/bin/`에 도착해 있습니다.

`knaraadm` 사용자로 로그인한 상태에서 진행합니다:

```bash
# knaraadm 사용자로 전환 (다른 사용자로 접속한 경우)
su - knaraadm

# 파일 확인
ls -lh /data/aiplatform/bin/platform-1.0.0.jar
ls -lh /data/aiplatform/sql/

# 실행 권한 설정
chmod 500 /data/aiplatform/bin/platform-1.0.0.jar
```

### 5.2 운영용 설정 파일 생성

JAR 외부에 `application-prod.yml`을 배치하면 JAR 내부 설정을 덮어씁니다:

```bash
# knaraadm 사용자로 설정 파일 생성
tee /data/aiplatform/config/application-prod.yml > /dev/null << 'EOF'
# ============================================================
#  운영 환경 설정
# ============================================================

server:
  port: 8080

spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/platform_db?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Seoul
    username: platform_user
    password: 여기에_DB_비밀번호_입력
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10

  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        use_sql_comments: false

jwt:
  # JWT 시크릿 키 (설정하지 않으면 서버 시작 시 자동 생성)
  # 자동 생성 시 서버 재시작하면 기존 로그인 토큰이 무효화됩니다
  # 고정 키를 사용하려면: openssl rand -base64 64 실행 후 결과값 입력
  # secret: 여기에_고정_키_입력 (선택사항)
  access-token-expiration: 3600000     # 1시간
  refresh-token-expiration: 604800000  # 7일

logging:
  level:
    root: WARN
    com.company: INFO
    org.hibernate.SQL: WARN
  file:
    path: /data/aiplatform/logs
EOF
```

### 5.3 JWT 시크릿 키 (선택사항)

> **JWT 시크릿 키는 별도로 설정하지 않아도 됩니다.**
> 설정하지 않으면 서버 시작 시 자동 생성됩니다.
> 단, 서버 재시작 시 기존 로그인 토큰이 무효화됩니다.

고정 키를 사용하고 싶다면:
```bash
# Base64 인코딩된 키 생성
openssl rand -base64 64
# 출력된 문자열을 platform.env의 JWT_SECRET에 입력 (선택)
```

### 5.4 환경변수로 민감 정보 관리 (권장)

설정 파일에 비밀번호를 직접 쓰는 대신 환경변수를 사용할 수 있습니다:

```bash
# knaraadm 사용자로 환경변수 파일 생성
tee /data/aiplatform/config/platform.env > /dev/null << 'EOF'
DB_HOST=localhost
DB_PORT=3306
DB_NAME=platform_db
DB_USERNAME=platform_user
DB_PASSWORD=여기에_DB_비밀번호
# JWT_SECRET=  (선택사항 - 미설정 시 서버 시작할 때 자동 생성)
EOF

# 보안: 본인만 읽기 가능
chmod 600 /data/aiplatform/config/platform.env
```

이 경우 `application-prod.yml`에서 플레이스홀더를 사용합니다:
```yaml
spring:
  datasource:
    url: jdbc:mariadb://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:platform_db}?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Seoul
    username: ${DB_USERNAME:platform_user}
    password: ${DB_PASSWORD}

jwt:
  secret: ${JWT_SECRET:}  # 비어있으면 서버 시작 시 자동 생성
```

### 5.5 수동 실행 테스트
```bash
# knaraadm 사용자로 수동 실행하여 정상 동작 확인
java \
  -jar /data/aiplatform/bin/platform-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/data/aiplatform/config/

# 별도 터미널에서 헬스 체크
curl -s http://localhost:8080/api/health
# 정상 응답: {"status":"UP"} 또는 유사한 JSON
```

> **Ctrl+C**로 종료 후, systemd 서비스로 등록합니다.

---

## 6. 리버스 프록시 설정 (Nginx)

외부에서 80/443 포트로 접근하고, Nginx가 내부 8080 포트로 프록시합니다.

### 6.1 Nginx 설치

#### Ubuntu / Debian
```bash
sudo apt install -y nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

#### CentOS / Rocky Linux
```bash
sudo dnf install -y nginx
sudo systemctl start nginx
sudo systemctl enable nginx
```

### 6.2 Nginx 설정

```bash
sudo tee /etc/nginx/conf.d/platform.conf > /dev/null << 'EOF'
# /etc/nginx/conf.d/platform.conf

upstream platform_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name your-domain.com;    # 실제 도메인으로 변경

    # (SSL 설정 후 HTTPS 리다이렉트)
    # return 301 https://$host$request_uri;

    # 요청 본문 크기 제한
    client_max_body_size 10M;

    # 정적 파일 캐시 (SPA 에셋)
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        proxy_pass http://platform_backend;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # API 및 SPA 라우팅
    location / {
        proxy_pass http://platform_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port $server_port;

        # WebSocket 지원 (필요한 경우)
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # 타임아웃 설정
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # 헬스 체크 엔드포인트 (외부 노출 차단)
    location /api/health {
        allow 127.0.0.1;
        deny all;
        proxy_pass http://platform_backend;
    }

    # Actuator 엔드포인트 보호
    location /actuator {
        allow 127.0.0.1;
        deny all;
        proxy_pass http://platform_backend;
    }
}
EOF
```

```bash
# 설정 검증
sudo nginx -t

# Nginx 재시작
sudo systemctl reload nginx
```

### 6.3 Apache httpd를 사용하는 경우

```bash
sudo tee /etc/httpd/conf.d/platform.conf > /dev/null << 'EOF'
<VirtualHost *:80>
    ServerName your-domain.com

    ProxyPreserveHost On
    ProxyPass / http://127.0.0.1:8080/
    ProxyPassReverse / http://127.0.0.1:8080/

    RequestHeader set X-Forwarded-Proto "http"
    RequestHeader set X-Forwarded-Port "80"
</VirtualHost>
EOF

# 필요 모듈 활성화
sudo a2enmod proxy proxy_http headers  # Ubuntu
# sudo dnf install -y mod_proxy_html    # CentOS

sudo systemctl restart httpd
```

> 이 애플리케이션은 이미 `forward-headers-strategy: framework`과 `X-Forwarded-*` 헤더를 처리하도록 설정되어 있으므로 리버스 프록시와 자연스럽게 호환됩니다.

---

## 7. 서비스 등록 (systemd)

서버 부팅 시 자동 시작되도록 systemd 서비스로 등록합니다.

### 7.1 서비스 파일 생성

```bash
sudo tee /etc/systemd/system/platform.service > /dev/null << 'EOF'
[Unit]
Description=Platform Management System
Documentation=https://github.com/company/platform
After=network.target mariadb.service
Requires=mariadb.service

[Service]
Type=simple
User=knaraadm
Group=knaraadm

# 환경변수 파일 로드
EnvironmentFile=/data/aiplatform/config/platform.env

# 실행 명령
ExecStart=/usr/bin/java \
    -Xms512m \
    -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=Asia/Seoul \
    -jar /data/aiplatform/bin/platform-1.0.0.jar \
    --spring.profiles.active=prod \
    --spring.config.additional-location=file:/data/aiplatform/config/

# 작업 디렉토리
WorkingDirectory=/data/aiplatform

# 프로세스 관리
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

# 로그 (systemd journal로 기록)
StandardOutput=journal
StandardError=journal
SyslogIdentifier=platform

# 보안 강화
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/data/aiplatform

[Install]
WantedBy=multi-user.target
EOF
```

### 7.2 서비스 등록 및 시작

```bash
# systemd 데몬 리로드
sudo systemctl daemon-reload

# 서비스 시작
sudo systemctl start platform

# 부팅 시 자동 시작 등록
sudo systemctl enable platform

# 상태 확인
sudo systemctl status platform

# 로그 확인
sudo journalctl -u platform -f --no-pager
```

### 7.3 서비스 관리 명령어
```bash
# 서비스 시작/중지/재시작
sudo systemctl start platform
sudo systemctl stop platform
sudo systemctl restart platform

# 상태 확인
sudo systemctl status platform

# 실시간 로그 확인
sudo journalctl -u platform -f

# 최근 100줄 로그
sudo journalctl -u platform -n 100 --no-pager
```

---

## 8. 방화벽 설정

### Ubuntu (UFW)
```bash
# 방화벽 활성화 (SSH 먼저!)
sudo ufw allow ssh
sudo ufw allow 80/tcp      # HTTP
sudo ufw allow 443/tcp     # HTTPS
sudo ufw enable

# 8080 포트는 외부 차단 (Nginx가 프록시하므로 외부 노출 불필요)
# 확인
sudo ufw status verbose
```

### CentOS / Rocky Linux (firewalld)
```bash
# HTTP, HTTPS 허용
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https

# 8080은 외부에서 직접 접근 불필요 (Nginx 프록시)
# 만약 Nginx 없이 직접 접근하려면:
# sudo firewall-cmd --permanent --add-port=8080/tcp

# 반영
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

> **보안 주의**: MariaDB 포트(3306)는 외부에 열지 마세요. localhost에서만 접근 가능해야 합니다.

---

## 9. SSL 인증서 설정 (HTTPS)

### Let's Encrypt (무료 SSL - 권장)

```bash
# Certbot 설치
# Ubuntu
sudo apt install -y certbot python3-certbot-nginx

# CentOS
sudo dnf install -y certbot python3-certbot-nginx

# 인증서 발급 (Nginx 자동 설정)
sudo certbot --nginx -d your-domain.com

# 자동 갱신 테스트
sudo certbot renew --dry-run

# 자동 갱신 크론 등록 (보통 certbot이 자동으로 등록)
sudo systemctl enable certbot-renew.timer
```

### 인증서 발급 후 Nginx HTTPS 설정 (자동 설정이 안 된 경우)

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    # ... (위의 location 설정과 동일)
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}
```

---

## 10. 운영 환경 체크리스트

배포 전 반드시 확인해야 할 항목입니다:

### 보안 설정
- [ ] **관리자 비밀번호 변경**: 기본 `admin123!`는 즉시 변경
- [ ] **JWT 시크릿 키**: 미설정 시 자동 생성됨 (고정 키 사용 시 `openssl rand -base64 64` 결과를 platform.env에 설정)
- [ ] **DB 비밀번호**: 강력한 비밀번호 설정
- [ ] **MariaDB 외부 접근 차단**: `bind-address = 127.0.0.1` 확인
- [ ] **8080 포트 외부 차단**: Nginx 프록시 사용 시 외부 노출 불필요
- [ ] **SSL 인증서**: HTTPS 적용 완료

### 성능 설정
- [ ] **JVM 메모리**: `-Xms512m -Xmx1024m` (서버 사양에 맞게 조정)
- [ ] **DB Connection Pool**: `maximum-pool-size: 30` (동시 사용자 수에 맞게)
- [ ] **MariaDB `innodb_buffer_pool_size`**: 전체 메모리의 50~70%

### 운영 설정
- [ ] **`ddl-auto: none`** 확인 (운영에서 절대 `update`나 `create` 사용 금지)
- [ ] **`show-sql: false`** 확인 (운영에서 SQL 로깅 비활성화)
- [ ] **로그 경로**: `/data/aiplatform/logs` 존재 및 쓰기 권한 확인
- [ ] **타임존**: `Asia/Seoul` 설정 확인
- [ ] **systemd 서비스**: 부팅 시 자동 시작 (`enabled`) 확인

### 배포 후 검증
- [ ] 헬스 체크: `curl http://localhost:8080/api/health`
- [ ] 로그인 테스트: admin 계정으로 로그인
- [ ] 메뉴 접근: 대시보드, 사용자 관리, 메뉴 관리, 접근 권한
- [ ] 사용자 CRUD: 등록, 조회, 수정, 비활성화
- [ ] 프로필 통합: 사용자 등록/수정 시 프로필 정보 입력 가능 확인

---

## 11. 모니터링 및 로그 관리

### 11.1 로그 파일 위치
```
/data/aiplatform/logs/
  ├── spring.log          # 메인 애플리케이션 로그
  └── spring.log.*.gz     # 롤링된 이전 로그
```

### 11.2 로그 로테이션 설정

Spring Boot의 기본 Logback이 자동 로테이션하지만, logrotate를 추가로 설정할 수 있습니다:

```bash
sudo tee /etc/logrotate.d/platform > /dev/null << 'EOF'
/data/aiplatform/logs/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    dateext
    dateformat -%Y%m%d
}
EOF
```

### 11.3 헬스 체크 스크립트
```bash
tee /data/aiplatform/bin/healthcheck.sh > /dev/null << 'SCRIPT'
#!/bin/bash
# 플랫폼 헬스 체크 스크립트

HEALTH_URL="http://localhost:8080/api/health"
TIMEOUT=5

response=$(curl -s --max-time $TIMEOUT -o /dev/null -w "%{http_code}" $HEALTH_URL)

if [ "$response" = "200" ]; then
    echo "$(date '+%Y-%m-%d %H:%M:%S') [OK] Platform is healthy"
    exit 0
else
    echo "$(date '+%Y-%m-%d %H:%M:%S') [ERROR] Platform health check failed (HTTP $response)"
    # 선택: 서비스 자동 재시작
    # sudo systemctl restart platform
    exit 1
fi
SCRIPT

chmod +x /data/aiplatform/bin/healthcheck.sh
```

```bash
# knaraadm 사용자 크론 등록 (5분마다 헬스 체크)
(crontab -l 2>/dev/null; echo "*/5 * * * * /data/aiplatform/bin/healthcheck.sh >> /data/aiplatform/logs/healthcheck.log 2>&1") | crontab -
```

### 11.4 DB 백업 스크립트
```bash
tee /data/aiplatform/bin/db-backup.sh > /dev/null << 'SCRIPT'
#!/bin/bash
# MariaDB 백업 스크립트

BACKUP_DIR="/data/aiplatform/backup"
DB_NAME="platform_db"
DB_USER="platform_user"
DB_PASS="여기에_DB_비밀번호"
DATE=$(date +%Y%m%d_%H%M%S)
KEEP_DAYS=30

# 백업 실행
mysqldump -u${DB_USER} -p${DB_PASS} ${DB_NAME} \
    --single-transaction \
    --routines \
    --triggers \
    --quick \
    | gzip > "${BACKUP_DIR}/${DB_NAME}_${DATE}.sql.gz"

# 오래된 백업 삭제
find ${BACKUP_DIR} -name "*.sql.gz" -mtime +${KEEP_DAYS} -delete

echo "$(date '+%Y-%m-%d %H:%M:%S') Backup completed: ${DB_NAME}_${DATE}.sql.gz"
SCRIPT

chmod +x /data/aiplatform/bin/db-backup.sh
```

```bash
# knaraadm 사용자 크론 등록 (매일 새벽 3시 백업)
(crontab -l 2>/dev/null; echo "0 3 * * * /data/aiplatform/bin/db-backup.sh >> /data/aiplatform/logs/backup.log 2>&1") | crontab -
```

---

## 12. 문제 해결 가이드

### 서비스 시작 안 됨
```bash
# 상세 로그 확인
sudo journalctl -u platform -n 200 --no-pager

# 자주 발생하는 원인:
# 1. DB 연결 실패 -> MariaDB 실행 확인, 비밀번호 확인
# 2. 포트 충돌 -> sudo ss -tlnp | grep 8080
# 3. Java 버전 불일치 -> java -version (17 필수)
# 4. 권한 문제 -> JAR 파일, 로그 디렉토리 권한 확인
```

### DB 연결 실패
```bash
# MariaDB 상태 확인
sudo systemctl status mariadb

# 접속 테스트
mariadb -u platform_user -p -h localhost platform_db -e "SELECT 1;"

# 인코딩 확인
mariadb -u platform_user -p -e "SHOW VARIABLES LIKE '%character%';"
```

### 한글 깨짐
```bash
# DB 인코딩 확인 (모두 utf8mb4여야 함)
mariadb -u platform_user -p -e "
    SHOW VARIABLES LIKE 'character_set%';
    SHOW VARIABLES LIKE 'collation%';
"

# JDBC URL에 인코딩 파라미터 확인
# ?useUnicode=true&characterEncoding=utf8mb4
```

### Out of Memory
```bash
# JVM 메모리 사용량 확인
jstat -gc $(pgrep -f platform-1.0.0) 1000

# Heap 설정 조정 (systemd 서비스 파일에서)
# -Xms1024m -Xmx2048m (서버 사양에 맞게)

# systemd 서비스 파일 수정 후:
sudo systemctl daemon-reload
sudo systemctl restart platform
```

### 포트 충돌
```bash
# 8080 포트 사용 확인
sudo ss -tlnp | grep 8080

# 다른 포트로 변경 (application-prod.yml)
# server:
#   port: 9090
```

---

## 빠른 배포 요약 (Quick Start)

전체 과정을 요약하면:

```bash
# 1. Java 17 + 필수 도구 설치
sudo apt install -y openjdk-17-jdk curl unzip    # Ubuntu
# sudo dnf install -y java-17-openjdk curl unzip # CentOS

# 2. MariaDB 설치 및 설정
sudo apt install -y mariadb-server                # Ubuntu
sudo mysql_secure_installation
mariadb -u root -p -e "
    CREATE DATABASE platform_db DEFAULT CHARACTER SET utf8mb4;
    CREATE USER 'platform_user'@'localhost' IDENTIFIED BY '강력한비밀번호';
    GRANT ALL PRIVILEGES ON platform_db.* TO 'platform_user'@'localhost';
    FLUSH PRIVILEGES;
"

# 3. 스키마 및 데이터 초기화
mariadb -u platform_user -p platform_db < V1.0.0__init_schema.sql
mariadb -u platform_user -p platform_db < V1.0.1__init_data.sql

# 4. knaraadm 사용자 및 디렉토리 준비
sudo useradd -m -s /bin/bash knaraadm && sudo passwd knaraadm
sudo mkdir -p /data/aiplatform/{source,bin,config,logs,sql,backup}
sudo chown -R knaraadm:knaraadm /data/aiplatform

# 5. 소스 받기 + 빌드 + 배치 (knaraadm으로 전환)
su - knaraadm
cd /data/aiplatform
git clone <저장소_URL> source
cd source
chmod +x ./gradlew
./gradlew :app:clean :app:build -x test
cp app/build/libs/platform-1.0.0.jar /data/aiplatform/bin/
cp sql/*.sql /data/aiplatform/sql/
# application-prod.yml과 platform.env 설정 (위 5.2, 5.4절 참고)

# 6. systemd 서비스 등록 (위 7절 참고)
sudo systemctl daemon-reload
sudo systemctl start platform
sudo systemctl enable platform

# 7. Nginx 리버스 프록시 (위 6절 참고)
sudo apt install -y nginx
# platform.conf 설정 후
sudo systemctl restart nginx

# 8. 확인
curl http://localhost:8080/api/health
```

---

## 참고: 프로젝트 구조

```
platform/
  ├── app/                    # 실행 모듈 (main class, 통합 컨트롤러)
  │   └── build/libs/
  │       └── platform-1.0.0.jar   # 실행형 Fat JAR (53MB)
  ├── core/                   # 핵심 모듈 (인증, 메뉴, 권한, 사용자 CRUD)
  ├── module-common/           # 공통 모듈 (사용자 프로필, 부서, 공통코드)
  ├── sql/
  │   ├── V1.0.0__init_schema.sql  # 테이블 생성 스크립트
  │   └── V1.0.1__init_data.sql    # 초기 데이터
  ├── build.gradle            # 루트 빌드 (Spring Boot 3.2.5, Java 17)
  └── settings.gradle         # 모듈 정의
```

---

**작성일**: 2026-04-03  
**시스템 버전**: Platform Management System v1.0.0  
**Spring Boot**: 3.2.5 | **Java**: 17 | **MariaDB**: 10.11+
