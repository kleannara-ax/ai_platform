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

    @Builder
    private PiContractFile(Long contractId, ContractFileType fileType, String originalName, String storedPath,
                           String contentType, Long fileSize, String fileHash, String description, Long createdBy) {
        this.contractId = contractId;
        this.fileType = fileType == null ? ContractFileType.CONTRACT : fileType;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.description = description;
        markCreatedBy(createdBy);
    }

    public void delete(Long deletedBy) {
        softDelete(deletedBy);
    }

    public void restore(Long updatedBy) {
        restoreDeleted(updatedBy);
    }
}
