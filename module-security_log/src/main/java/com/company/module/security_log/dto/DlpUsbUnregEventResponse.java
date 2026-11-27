package com.company.module.security_log.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 보고서 01 '등록 안 된 외장하드로 업로드' 카드의 근거 로그 목록.
 * 카드 숫자와 같은 기준(외장하드·USB 채널 + 차단 아님 + 등록 여부 '등록 안 됨')으로 고른다.
 */
@Getter
@Builder
public class DlpUsbUnregEventResponse {

    /** 대상 연월 YYYYMM */
    private String targetYm;
    /** 기준 유출 로그 파일 이름 (없으면 null) */
    private String fileName;
    /** 전체 건수 (= 카드 숫자) */
    private int total;
    /** 화면에 보내는 최대 건수 */
    private int limit;
    /** 최근 발생 순 목록 (최대 limit 건) */
    private List<Row> rows;

    @Getter
    @Builder
    public static class Row {
        /** 원본 엑셀 행 번호 */
        private Integer rowNo;
        private LocalDateTime occurredAt;
        /** 원본에 적힌 발생 일시 글자 (날짜로 못 읽었을 때 표시용) */
        private String occurredAtText;
        private String deptName;
        private String userName;
        private String userId;
        private String computerName;
        private String fileList;
        private String fileSizeText;
        private String subChannel;
    }
}
