package com.company.module.security_log.service;

/**
 * security_log 모듈 상수
 * - 운영 설정 파일(application.yml)을 생성하지 않기 위해 모듈 내부 상수로 관리한다.
 * - 값 변경이 필요하면 README 의 "플랫폼 app 설정에 추가 필요" 항목을 참고한다.
 */
public final class SecLogConstants {

    private SecLogConstants() {
    }

    public static final String DELETED_N = "N";
    public static final String DELETED_Y = "Y";

    /** 업로드 허용 최대 파일 크기 (50MB) - 플랫폼 multipart 설정도 이 값 이상이어야 함 */
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 분석 최대 라인 수 */
    public static final int MAX_LINE_COUNT = 2_000_000;

    /** 정규식 매칭 시 사용할 라인 최대 길이 (ReDoS / 과도한 연산 방지) */
    public static final int MAX_MATCH_LINE_LENGTH = 4_000;

    /** 탐지 결과에 저장할 로그 원문 최대 길이 (LOG_CONTENT 컬럼 길이) */
    public static final int MAX_LOG_CONTENT_LENGTH = 2_000;

    /** 정규식 1회 매칭 최대 허용 시간(ms) */
    public static final long REGEX_TIMEOUT_MS = 200L;

    /** 업로드 1건, 룰 1개당 저장할 최대 탐지 건수 */
    public static final int MAX_DETECTION_PER_RULE = 1_000;

    /** 업로드 1건당 저장할 최대 탐지 건수 */
    public static final int MAX_DETECTION_PER_UPLOAD = 20_000;

    /** 룰 테스트 시 반환할 최대 매칭 라인 수 */
    public static final int MAX_TEST_RESULT_LINES = 200;

    /** 페이지 최대 크기 */
    public static final int MAX_PAGE_SIZE = 500;

    /** 업로드 허용 확장자 (텍스트 로그만 허용) */
    public static final String[] ALLOWED_EXTENSIONS = {"log", "txt", "csv", "json", "out"};
}
