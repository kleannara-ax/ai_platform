package com.company.module.security_log.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 업로드 원본 로그 파일 (GZIP 압축 저장)
 * - ISMS 증적 보존 및 룰 변경 후 재분석 용도
 * - 대용량 컬럼이므로 업로드 목록 조회 시 로딩되지 않도록 별도 테이블로 분리
 */
@Entity
@Table(name = "sec_log_upload_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogUploadFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UPLOAD_FILE_ID")
    private Long uploadFileId;

    @Column(name = "UPLOAD_ID", nullable = false, updatable = false)
    private Long uploadId;

    @Column(name = "COMPRESS_TYPE", nullable = false, length = 10)
    private String compressType;

    @Column(name = "COMPRESSED_SIZE", nullable = false)
    private Long compressedSize;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "FILE_DATA", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] fileData;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "CREATED_BY", updatable = false)
    private Long createdBy;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "UPDATED_BY")
    private Long updatedBy;

    @Column(name = "DELETED_YN", nullable = false, length = 1)
    private String deletedYn;

    @Column(name = "DELETED_AT")
    private LocalDateTime deletedAt;

    @Column(name = "DELETED_BY")
    private Long deletedBy;

    @Builder
    private SecLogUploadFile(Long uploadId, byte[] fileData, Long createdBy) {
        this.uploadId = uploadId;
        this.compressType = "GZIP";
        this.fileData = fileData;
        this.compressedSize = fileData == null ? 0L : (long) fileData.length;
        this.createdBy = createdBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore(Long updatedBy) {
        this.deletedYn = "N";
        this.deletedBy = null;
        this.deletedAt = null;
        this.updatedBy = updatedBy;
    }
}
