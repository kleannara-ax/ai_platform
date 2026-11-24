package com.company.module.personalinfo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 계약 첨부파일 (위수탁 계약서, 재위탁 동의서, 확약서 등).
 * 실제 파일은 업로드 디렉토리에 저장하고 DB 에는 메타데이터만 보관한다.
 */
@Entity
@Table(name = "pi_contract_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiContractFile extends PiBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FILE_ID")
    private Long fileId;

    @Column(name = "CONTRACT_ID", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "FILE_TYPE", nullable = false, length = 30)
    private ContractFileType fileType;

    @Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
    private String originalName;

    @Column(name = "STORED_PATH", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "CONTENT_TYPE", length = 150)
    private String contentType;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    /** 계약서상 체결일 (위수탁 계약서만 사용. 체결일 표기가 없으면 계약기간 시작일) */
    @Column(name = "SIGNED_DATE")
    private LocalDate signedDate;

    /** 받은 서류의 일자 (정보보호 및 비밀유지 확약서 서명일 / 개인정보 파기 확인서 파기 완료일) */
    @Column(name = "DOCUMENT_DATE")
    private LocalDate documentDate;

    /** 서류 일자 관련 메모 (파기 확인서: 파기 기한보다 늦게 파기한 사유) */
    @Column(name = "DOCUMENT_NOTE", length = 500)
    private String documentNote;

    /** 정기 파기 확인서 대상 기간 (예: 2026-07 / 2026-Q3 / 2026-H2). null 이면 계약 종료 시 받는 파기 확인서(만료 서류) */
    @Column(name = "DESTRUCTION_PERIOD", length = 10)
    private String destructionPeriod;

    /** 업로드 내용 확인 결과: AUTO 자동 확인 통과 / MANUAL 담당자가 내용 직접 확인 (이전 파일은 null) */
    @Column(name = "CONTENT_CHECK", length = 10)
    private String contentCheck;

    @Builder
    private PiContractFile(Long contractId, ContractFileType fileType, String originalName, String storedPath,
                           String contentType, Long fileSize, String fileHash, String description, LocalDate signedDate,
                           LocalDate documentDate, String documentNote, String destructionPeriod, String contentCheck,
                           Long createdBy) {
        this.contractId = contractId;
        this.fileType = fileType == null ? ContractFileType.CONTRACT : fileType;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.description = description;
        this.signedDate = this.fileType == ContractFileType.CONTRACT ? signedDate : null;
        this.documentDate = this.fileType.hasDocumentDate() ? documentDate : null;
        this.documentNote = this.fileType == ContractFileType.DESTRUCTION_CONFIRM ? documentNote : null;
        this.destructionPeriod = this.fileType == ContractFileType.DESTRUCTION_CONFIRM ? destructionPeriod : null;
        this.contentCheck = contentCheck;
        markCreatedBy(createdBy);
    }

    /** 계약서상 체결일 변경 (위수탁 계약서만 가능, null 이면 비움) */
    public void changeSignedDate(LocalDate signedDate, Long updatedBy) {
        this.signedDate = signedDate;
        markUpdatedBy(updatedBy);
    }

    /** 서류 일자 변경 (확약서 서명일 / 파기 확인서 파기 완료일, null 이면 비움) */
    public void changeDocumentDate(LocalDate documentDate, Long updatedBy) {
        this.documentDate = documentDate;
        markUpdatedBy(updatedBy);
    }

    /** 서류 일자 + 메모 변경 (파기 확인서의 파기 지연 사유) */
    public void changeDocumentDate(LocalDate documentDate, String documentNote, Long updatedBy) {
        this.documentDate = documentDate;
        this.documentNote = this.fileType == ContractFileType.DESTRUCTION_CONFIRM ? documentNote : null;
        markUpdatedBy(updatedBy);
    }

    /** 정기 파기 확인서 대상 기간 변경 (null 이면 계약 종료 파기 확인서로) */
    public void changeDestructionPeriod(String destructionPeriod, Long updatedBy) {
        this.destructionPeriod = this.fileType == ContractFileType.DESTRUCTION_CONFIRM ? destructionPeriod : null;
        markUpdatedBy(updatedBy);
    }

    /** 정기 파기 확인서인지 (대상 기간 있음) */
    public boolean isPeriodicDestruction() {
        return fileType == ContractFileType.DESTRUCTION_CONFIRM && destructionPeriod != null;
    }

    /** 계약 종료 시 받는 파기 확인서인지 (만료 서류) */
    public boolean isTerminationDestruction() {
        return fileType == ContractFileType.DESTRUCTION_CONFIRM && destructionPeriod == null;
    }

    public boolean isContractDoc() {
        return fileType == ContractFileType.CONTRACT;
    }

    public void delete(Long deletedBy) {
        softDelete(deletedBy);
    }

    public void restore(Long updatedBy) {
        restoreDeleted(updatedBy);
    }
}
