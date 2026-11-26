package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * DLP 모니터링 보고서 자료 (회사 보고서 양식 8장 기준)
 * - 유출 로그로 계산한 값 + 사람이 적은 값(승인여부·검토 결과·사전승인·사유·조치·부서 인원·보안 위험도·표지 정보)
 * - 화면 [보고서 정보 입력] 과 PPT 가 같은 자료를 사용
 */
@Getter
@Builder
public class DlpReportDataResponse {

    private String targetYm;
    /** 분석 기준 유출 로그 (없으면 null → PPT 불가) */
    private DlpLeakUploadResponse upload;
    private Cover cover;
    private Usb usb;
    private Kakao kakao;
    private PrivacyiList reinstall;
    private PrivacyiList offline;
    private Ai ai;
    /** 생성형 AI 판정에 쓰는 서비스 목록 수 (안내용) */
    private int aiCatalogSize;

    /** 1. 표지 (보고 기간은 연월로 계산) */
    @Getter
    @Builder
    public static class Cover {
        private String reportPeriod;
        private String reportType;
        private String targetSystem;
        private String writerDept;
        /** 회사 식별 단어 (쉼표 구분) - 보안 위험도 '상' 자동 제안 */
        private String companyKeywords;
        /** 이 달 값이 없어 이전 달 값을 이어서 보여 줌 */
        private boolean carriedOver;
    }

    /** 3. Removable Drive (외장하드 및 USB) */
    @Getter
    @Builder
    public static class Usb {
        private long blocked;
        private long allowedRegistered;
        private long allowedUnregistered;
        private long total;
        /** 등록 안 된 외장하드로 업로드한 인원 */
        private List<PersonRow> unregUsers;
        private long unregTotalCount;
    }

    /** 4. PC 카카오톡 */
    @Getter
    @Builder
    public static class Kakao {
        /** 사용 부서 현황 = 카카오톡 사용자 현황(승인 대장) 기준, basisDate 날짜 기준 사용 중 인원 */
        private List<DeptRow> depts;
        private long userTotal;
        /** 사용 부서 현황 기준일 (보고서를 만든 날 = 최신 대장) */
        private java.time.LocalDate basisDate;
        /** 부서 인원 합계 (입력한 부서만) */
        private long headcountTotal;
        /** 카카오톡 사용자 전체 (사전승인·사유 입력 대상) */
        private List<PersonRow> users;
        /** 미승인 사용자 수 (사전승인 = 승인 이 아닌 사람) */
        private long unapprovedCount;
        private long eventCount;
    }

    /** 5·6. Privacy-I 재설치 / 장기 미접속 */
    @Getter
    @Builder
    public static class PrivacyiList {
        /** 올린 목록 파일명 (없으면 null) */
        private String fileName;
        private List<PiRow> rows;
        /** 장기 미접속 분석: 7일 이상 / 조치완료 / 휴직 및 퇴사 / 미조치 및 보류 */
        private long longOffline;
        private long actionDone;
        private long leaveOrQuit;
        private long pending;
    }

    /** 7·8. 생성형 AI */
    @Getter
    @Builder
    public static class Ai {
        private long eventCount;
        private List<CountRow> services;
        private List<AiDeptRow> depts;
        /** 업로드 빈도 상위 10명 */
        private List<AiUserRow> users;
    }

    /** 사람 1명 (외장하드 미등록 / 카카오톡) */
    @Getter
    @Builder
    public static class PersonRow {
        /** 저장 대상 키 (사용자 아이디, 없으면 이름) */
        private String itemKey;
        private String deptName;
        private String userName;
        private String userId;
        private long count;
        /** 승인여부 / 사전승인 (승인·미승인) */
        private String value1;
        /** 검토 결과 / 사유 */
        private String value2;
        /** 직접 입력해 저장한 값 (없으면 null) */
        private String manual1;
        private String manual2;
        /** 카카오톡: 승인 대장 자동 판정 (승인 / 미승인 / null = 확인 필요) */
        private String autoApproval;
        private String autoReason;
        /** 카카오톡: 판정 근거 한 줄 */
        private String autoNote;
        /** 카카오톡: MANUAL 직접 입력 / LEDGER 대장 자동 / CHECK 확인 필요 */
        private String source;
        /** 카카오톡: 담당자 확인 필요 (승인 기간 밖 사용 / 같은 이름이 다른 부서에만 있음) */
        private boolean needConfirm;
    }

    @Getter
    @Builder
    public static class DeptRow {
        private String deptName;
        private long userCount;
        /** 부서 인원 (입력값, 이전 달 값 이어서 사용) */
        private Integer headcount;
    }

    @Getter
    @Builder
    public static class PiRow {
        private String itemKey;
        private int rank;
        private int rowNo;
        private String deptName;
        private String userName;
        private String userId;
        /** 설치 일시 / 최종 오프라인 일시 */
        private String eventAt;
        private Integer offlineDays;
        /** 조치 구분 (장기 미접속: 조치완료 / 휴직 및 퇴사 / 미조치 및 보류) */
        private String value1;
        /** 검토내용 / 조치 내용 */
        private String value2;
    }

    @Getter
    @Builder
    public static class CountRow {
        private int rank;
        private String name;
        private long count;
    }

    @Getter
    @Builder
    public static class AiDeptRow {
        private int rank;
        private String deptName;
        private long count;
        private String mainService;
    }

    @Getter
    @Builder
    public static class AiUserRow {
        private String itemKey;
        private String deptName;
        private String userName;
        private String userId;
        private long count;
        /** 보안 위험도 (입력값, 없으면 자동 제안값) 상 / 중 / 하 */
        private String risk;
        /** 자동 제안 (회사 식별 단어 → 상, 업무 파일 → 중, 그 외 → 하) */
        private String riskSuggested;
        private String riskReason;
        /** 사람이 직접 정한 값인지 */
        private boolean riskManual;
        private List<String> files;
        private List<String> services;
    }
}
