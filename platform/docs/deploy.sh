#!/bin/bash
# ============================================================
#  플랫폼 배포 스크립트 (Rocky Linux 9)
#  실행형 JAR 배포 방식
# ============================================================

set -e

# ── 환경 변수 ──
APP_NAME="platform"
APP_VERSION="1.0.0"
JAR_FILE="${APP_NAME}-${APP_VERSION}.jar"
APP_HOME="/opt/platform"
LOG_DIR="/var/log/platform"
PID_FILE="${APP_HOME}/${APP_NAME}.pid"
PROFILE="prod"

# ── 환경 변수 (DB, JWT 등) ──
export DB_HOST="localhost"
export DB_PORT="3306"
export DB_NAME="platform_db"
export DB_USERNAME="platform_user"
export DB_PASSWORD="your_secure_password"
export JWT_SECRET="your_base64_encoded_secret_key"

# ── 디렉토리 생성 ──
mkdir -p "${APP_HOME}"
mkdir -p "${LOG_DIR}"

# ── 함수: 시작 ──
start() {
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}")
        if ps -p "${PID}" > /dev/null 2>&1; then
            echo "[INFO] ${APP_NAME} is already running (PID: ${PID})"
            return 1
        fi
    fi

    echo "[INFO] Starting ${APP_NAME}..."

    nohup java \
        -Xms512m \
        -Xmx1024m \
        -XX:+UseG1GC \
        -Dspring.profiles.active=${PROFILE} \
        -Dfile.encoding=UTF-8 \
        -Duser.timezone=Asia/Seoul \
        -jar "${APP_HOME}/${JAR_FILE}" \
        > "${LOG_DIR}/stdout.log" 2>&1 &

    echo $! > "${PID_FILE}"
    echo "[INFO] ${APP_NAME} started (PID: $(cat ${PID_FILE}))"
}

# ── 함수: 중지 ──
stop() {
    if [ ! -f "${PID_FILE}" ]; then
        echo "[WARN] PID file not found. ${APP_NAME} may not be running."
        return 0
    fi

    PID=$(cat "${PID_FILE}")
    if ps -p "${PID}" > /dev/null 2>&1; then
        echo "[INFO] Stopping ${APP_NAME} (PID: ${PID})..."
        kill "${PID}"

        # Graceful shutdown 대기 (최대 30초)
        for i in $(seq 1 30); do
            if ! ps -p "${PID}" > /dev/null 2>&1; then
                break
            fi
            sleep 1
        done

        # 여전히 실행 중이면 강제 종료
        if ps -p "${PID}" > /dev/null 2>&1; then
            echo "[WARN] Force killing ${APP_NAME}..."
            kill -9 "${PID}"
        fi

        rm -f "${PID_FILE}"
        echo "[INFO] ${APP_NAME} stopped."
    else
        echo "[WARN] ${APP_NAME} is not running (stale PID file)."
        rm -f "${PID_FILE}"
    fi
}

# ── 함수: 재시작 ──
restart() {
    stop
    sleep 2
    start
}

# ── 함수: 상태 확인 ──
status() {
    if [ -f "${PID_FILE}" ]; then
        PID=$(cat "${PID_FILE}")
        if ps -p "${PID}" > /dev/null 2>&1; then
            echo "[INFO] ${APP_NAME} is running (PID: ${PID})"
            return 0
        fi
    fi
    echo "[INFO] ${APP_NAME} is NOT running."
    return 1
}

# ── 함수: 배포 ──
deploy() {
    echo "[INFO] Deploying ${APP_NAME}..."

    # 빌드 결과물 복사
    if [ -f "app/build/libs/${JAR_FILE}" ]; then
        cp "app/build/libs/${JAR_FILE}" "${APP_HOME}/"
        echo "[INFO] JAR copied to ${APP_HOME}/"
    else
        echo "[ERROR] JAR file not found: app/build/libs/${JAR_FILE}"
        echo "[INFO] Run './gradlew :app:bootJar' first."
        exit 1
    fi

    restart
    echo "[INFO] Deployment complete."
}

# ── 메인 ──
case "$1" in
    start)   start   ;;
    stop)    stop    ;;
    restart) restart ;;
    status)  status  ;;
    deploy)  deploy  ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|deploy}"
        exit 1
        ;;
esac
