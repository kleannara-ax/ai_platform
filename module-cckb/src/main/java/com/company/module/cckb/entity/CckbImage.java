package com.company.module.cckb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 색상검사 이미지(기준사진/비교사진) 첨부 엔티티.
 *
 * <p>※ 엔티티명은 {@code CckbImage} 이지만 실제 테이블명은 엑셀 스키마 원안 그대로
 * {@code image} 를 사용한다 (다른 모듈과 이름이 겹치지 않도록 모듈별 스키마 범위에서만 유효).
 *
 * <p>※ 이미지 저장/서빙 방식은 module-dailyreport 의 {@code DailyReportImage}/{@code ImageController}
 * 패턴을 그대로 따른다 (사용자 명시 지시):
 * <ul>
 *   <li>실제 파일은 디스크에 UUID 파일명으로 저장</li>
 *   <li>{@link #fileUrl} 에는 물리 경로가 아니라 다운로드 API 경로(예:
 *       {@code /cckb-api/images/files/{filename}})를 저장한다
 *       (엑셀 설계서 원안의 "R2/오브젝트 스토리지 경로" 코멘트는 dailyreport 방식 채택으로
 *       "인증 API 경로"로 대체됨 — 아래 FILE_URL 컬럼 설명 참고)</li>
 *   <li>조회는 인증(Bearer) 필요 — permitAll 예외 없음. 프론트엔드는 fetch()+blob 사용.</li>
 * </ul>
 *
 * <p>※ 엑셀 원본 스키마에는 {@code DELETED_YN} 컬럼이 누락되어 있었다(다른 4개 테이블은
 * 모두 보유). 다른 테이블과의 일관성 및 소프트 삭제 표준(CckbBaseEntity)을 위해
 * 이 테이블에도 DELETED_YN 을 추가했다 — 사용자에게 보고 필요한 스키마 보정 사항.
 */
@Entity
@Table(name = "image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CckbImage extends CckbBaseEntity {

    public static final String TYPE_REF = "REF";
    public static final String TYPE_CMP = "CMP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IMAGE_ID")
    private Long imageId;

    /** 기준사진(REF)은 특정 검사에 종속되지 않을 수 있어 NULL 허용 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "INSPECTION_ID", referencedColumnName = "INSPECTION_ID",
            foreignKey = @ForeignKey(name = "FK_IMAGE_INSPECTION"))
    private InspectionRecord inspectionRecord;

    /** REF(기준) / CMP(비교) */
    @Column(name = "IMAGE_TYPE", nullable = false, length = 10)
    private String imageType;

    @Column(name = "FILE_NAME", nullable = false, length = 200)
    private String fileName;

    /** 다운로드 API 경로 (물리 경로 아님) — dailyreport ImageController 패턴 */
    @Column(name = "FILE_URL", nullable = false, length = 300)
    private String fileUrl;

    @Column(name = "WIDTH_PX")
    private Integer widthPx;

    @Column(name = "HEIGHT_PX")
    private Integer heightPx;

    @Column(name = "FILE_SIZE_KB")
    private Integer fileSizeKb;

    /** 업로드자 (core 사용자 ID, FK 아님) */
    @Column(name = "UPLOADED_BY")
    private Long uploadedBy;

    @Column(name = "UPLOADED_AT", nullable = false)
    private LocalDateTime uploadedAt;

    @Builder
    private CckbImage(InspectionRecord inspectionRecord, String imageType, String fileName, String fileUrl,
                       Integer widthPx, Integer heightPx, Integer fileSizeKb, Long uploadedBy) {
        this.inspectionRecord = inspectionRecord;
        this.imageType = imageType;
        this.fileName = fileName;
        this.fileUrl = fileUrl;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.fileSizeKb = fileSizeKb;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = LocalDateTime.now();
        markCreatedBy(uploadedBy);
    }

    /** 검사 완료 후 비교사진을 해당 검사 레코드에 연결한다 */
    void assignInspection(InspectionRecord inspectionRecord) {
        this.inspectionRecord = inspectionRecord;
    }

    public void delete(Long deletedBy) {
        markDeleted(deletedBy);
    }
}
