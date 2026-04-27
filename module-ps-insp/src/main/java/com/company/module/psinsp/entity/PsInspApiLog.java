package com.company.module.psinsp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * PS 지분검사 API 통신 로그 엔티티
 *
 * <p>Table: ps_insp_api_log
 *
 * <p>API명 (실제 메서드/엔드포인트명 기록):
 * <ul>
 *   <li>saveInspection - 검사 결과 저장</li>
 *   <li>getInspection - 검사 단건 조회</li>
 *   <li>listInspections - 검사 목록 조회</li>
 *   <li>searchInspections - 검사 검색</li>
 *   <li>checkExists - 중복 체크</li>
 *   <li>deleteInspection - 검사 삭제</li>
 *   <li>deleteAllInspections - 전체 삭제</li>
 *   <li>sendResult - MES 결과 전송 요청 (프론트→백엔드)</li>
 *   <li>saveDustInspectionResult - MES 외부 전송 (백엔드→MES서버)</li>
 *   <li>getPpmLimit - PPM 기준값 조회</li>
 *   <li>savePpmLimit - PPM 기준값 저장</li>
 *   <li>getPpmAdmins - 권한자 조회</li>
 *   <li>updatePpmAdmins - 권한자 수정</li>
 *   <li>getAllConfigs - 전체 설정 조회</li>
 * </ul>
 *
 * <p>COMSTAT 코드:
 * <ul>
 *   <li>S - Success (성공)</li>
 *   <li>E - Error (에러)</li>
 *   <li>P - Processing (처리 중)</li>
 * </ul>
 *
 * <p>INERRAT 내부 에러 코드:
 * <ul>
 *   <li>AUTH_FAIL - 인증 실패</li>
 *   <li>DB_ERROR - 데이터베이스 오류</li>
 *   <li>VALIDATION_ERROR - 입력값 검증 실패</li>
 *   <li>MES_TIMEOUT - MES 전송 타임아웃</li>
 *   <li>MES_CONN_FAIL - MES 연결 실패</li>
 *   <li>MES_RESP_ERROR - MES 응답 오류 (RS_CODE=E)</li>
 *   <li>MES_SESSION_EXPIRED - MES 세션 만료 (HTML 리다이렉트)</li>
 *   <li>MES_PARSE_ERROR - MES 응답 파싱 실패</li>
 *   <li>FILE_ERROR - 파일 처리 오류</li>
 *   <li>UNKNOWN_ERROR - 알 수 없는 오류</li>
 * </ul>
 */
@Entity
@Table(name = "ps_insp_api_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PsInspApiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOG_ID")
    private Long logId;

    /** API 유형 코드 (INSPECTION_SAVE, MES_SEND 등) */
    @Column(name = "API", nullable = false, length = 50)
    private String api;

    /** 클라이언트 IP */
    @Column(name = "IP", length = 100)
    private String ip;

    /** 통신 상태: S=Success, E=Error, P=Processing */
    @Column(name = "COMSTAT", nullable = false, length = 1)
    private String comstat;

    /** 에러 메시지 (COMSTAT=E 일 때) */
    @Column(name = "ERRTXT", length = 2000)
    private String errtxt;

    /** 생성 날짜 */
    @Column(name = "CREDAT", nullable = false)
    private LocalDate credat;

    /** 생성 시간 */
    @Column(name = "CRETIM", nullable = false)
    private LocalTime cretim;

    /** 생성자 (요청 사용자 ID) */
    @Column(name = "CREUSR", length = 200)
    private String creusr;

    /** 비고/메모 */
    @Column(name = "REMARK", length = 2000)
    private String remark;

    /** 내부 에러 코드 (AUTH_FAIL, DB_ERROR, MES_TIMEOUT 등) */
    @Column(name = "INERRAT", length = 50)
    private String inerrat;

    /** 내부 에러 상세 메시지 */
    @Column(name = "INERRTXT", length = 2000)
    private String inerrtxt;

    /** INPUT 파라미터 (요청 데이터, JSON) */
    @Column(name = "IN_PARAMETER", columnDefinition = "TEXT")
    private String inParameter;

    /** OUTPUT 파라미터 (응답 데이터, JSON) */
    @Column(name = "OUT_PARAMETER", columnDefinition = "TEXT")
    private String outParameter;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @PrePersist
    protected void onCreate() {
        if (this.credat == null) {
            this.credat = LocalDate.now(KST);
        }
        if (this.cretim == null) {
            this.cretim = LocalTime.now(KST);
        }
        if (this.comstat == null) {
            this.comstat = "S";
        }
    }
}
