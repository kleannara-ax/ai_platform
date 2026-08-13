package com.company.module.dailyreport.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 게시판(공장일보/세부공장일보) 재업로드 요청 큐 엔티티 (★ 신규, 2026-08)
 *
 * [배경]
 * 이 AI 플랫폼 화면에 입력하는 값 중,
 *   - 특이사항 + 표2(제지 재공품 및 야적현황) 값 → "공장일보" 게시글에 반영
 *   - 표 1/2/3/4 값 전체 → "세부공장일보" 게시글에 반영
 * 매일 오전 8:05 이전 저장은 아직 게시판 배치가 게시하기 전이므로 별도 조치가
 * 필요 없지만, 오전 8:05 이후 값을 고치면 이미 게시된 게시글이 최신 값과
 * 달라지므로 별도 PC에서 동작하는 배치 시스템이 그 게시글을 다시 게시(재업로드)
 * 해야 한다. 이 엔티티는 그 배치 시스템에 "재업로드가 필요하다"는 요청을
 * 전달하는 큐 역할만 한다 — 실제 게시판 갱신 로직은 이 애플리케이션의 책임이
 * 아니며, 배치 시스템이 이 테이블을 5초 주기로 폴링하여 처리한다.
 *
 * BATCH_TYPE 값:
 *   '1' = 공장일보 재업로드   (특이사항 / 표2 제지 재공품 항목 수정)
 *   '2' = 세부공장일보 재업로드 (표 1, 2, 3, 4 값 수정)
 *   '3' = 모두 재업로드       (공장일보 + 세부공장일보 둘 다)
 *
 * ※ 이 애플리케이션은 요청 행을 INSERT만 하며, CREATE_YN/RESULT_VALUE 등
 *   처리 결과 필드는 배치 시스템이 채운다(이 애플리케이션은 갱신하지 않음).
 */
@Entity
@Table(name = "daily_batchjob")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyBatchJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SEQ_NO")
    private Long seqNo;

    /** 일자 (YYYYMMDD 문자열 — 배치 시스템 규격에 맞춰 varchar(8) 그대로 사용) */
    @Column(name = "BATCH_DATE", nullable = false, length = 8)
    private String batchDate;

    /** 구분: 1=공장일보, 2=세부공장일보, 3=모두 */
    @Column(name = "BATCH_TYPE", nullable = false, length = 1)
    private String batchType;

    /** 생성여부 — 배치가 처리 완료 시 'Y'로 갱신 (이 앱은 항상 'N'으로 INSERT) */
    @Column(name = "CREATE_YN", nullable = false, length = 1)
    private String createYn;

    /** 요청일시 */
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 요청자 (core_user FK) */
    @Column(name = "CREATED_BY", nullable = false)
    private Long createdBy;

    /** 수정일시 (배치 처리 시 갱신) */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /** 수정자 (배치 처리 주체 식별용) */
    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    /** 성공여부 (배치가 처리 후 기록) */
    @Column(name = "RESULT_VALUE", length = 1)
    private String resultValue;

    @Column(name = "REMARKS1", length = 100)
    private String remarks1;

    @Column(name = "REMARKS2", length = 100)
    private String remarks2;

    @Column(name = "REMARKS3", length = 100)
    private String remarks3;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.createYn == null) {
            this.createYn = "N";
        }
    }

    @Builder
    public DailyBatchJob(String batchDate, String batchType, Long createdBy, String remarks1) {
        this.batchDate = batchDate;
        this.batchType = batchType;
        this.createYn = "N";
        this.createdBy = createdBy;
        this.remarks1 = remarks1;
    }
}
