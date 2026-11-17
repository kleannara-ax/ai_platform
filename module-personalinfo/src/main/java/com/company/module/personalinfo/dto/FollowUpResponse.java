package com.company.module.personalinfo.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 보안팀 후속 관리 상태 (계약 1건).
 * <ul>
 *   <li>만료 서류: 종료했거나 만료 알림 기간(만료임박·만료)에 들어간 계약은 만료 점검 체크리스트·확약서·파기 확인서 3종을 받아야 한다</li>
 *   <li>조치 지연: 점검 결과 '조치 필요' 중 조치 예정일이 지났는데 조치 완료가 아닌 점검</li>
 *   <li>재위탁 동의서 누락: 재위탁 Y 인데 재위탁 동의서 파일이 없음</li>
 *   <li>파기 지연: 파기 확인서의 파기 완료일이 계약 종료 기준일 + 허용 기간보다 늦음</li>
 * </ul>
 */
@Getter
@Builder
public class FollowUpResponse {

    /** 만료 서류를 받아야 하는 계약인지 (종료 / 만료임박 / 만료) */
    private boolean expiryDocsNeeded;
    /** 받은 만료 서류 수 (0~3) */
    private int expiryDocsReceived;
    /** 받아야 하는데 아직 못 받은 서류 이름 */
    private List<String> missingDocs;
    /** 만료 서류 미수취 (expiryDocsNeeded 이고 3종 중 빠진 것이 있음) */
    private boolean expiryDocsMissing;

    /** 조치 지연 점검 건수 */
    private int actionOverdueCount;
    /** 조치 지연 점검 (상세 화면에서만 채움) */
    private List<OverdueInspection> overdueInspections;

    /** 재위탁 동의서 누락 */
    private boolean consentMissing;

    /** 파기 완료일이 허용 기한보다 늦음 */
    private boolean destructionLate;
    /** 파기 확인서의 파기 완료일 (최근 파일) */
    private LocalDate destructionDate;
    /** 파기 허용 기한 (계약 종료 기준일 + 허용 일수) */
    private LocalDate destructionDeadline;
    /** 받았지만 서류 일자(서명일·파기 완료일)를 입력하지 않은 서류 */
    private List<String> documentDateMissing;

    @Getter
    @Builder
    public static class OverdueInspection {
        private Long inspectionId;
        private LocalDate inspectionDate;
        private LocalDate actionDueDate;
        private long overdueDays;
        private int nCount;
    }
}
