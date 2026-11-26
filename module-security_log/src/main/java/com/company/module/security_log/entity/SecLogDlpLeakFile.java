package com.company.module.security_log.entity;

import com.company.module.security_log.support.SecLogTime;
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
 * DLP 모니터링 - 유출 로그 원본 파일 (GZIP, 증적·재분석용)
 */
@Entity
@Table(name = "sec_log_dlp_leak_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecLogDlpLeakFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LEAK_FILE_ID")
    private Long leakFileId;

    @Column(name = "LEAK_UPLOAD_ID", nullable = false, updatable = false)
    private Long leakUploadId;

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
    private SecLogDlpLeakFile(Long leakUploadId, byte[] fileData, Long createdBy) {
        this.leakUploadId = leakUploadId;
        this.compressType = "GZIP";
        this.fileData = fileData;
        this.compressedSize = fileData == null ? 0L : (long) fileData.length;
        this.createdBy = createdBy;
        this.deletedYn = "N";
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = SecLogTime.now();
        if (this.deletedYn == null) {
            this.deletedYn = "N";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = SecLogTime.now();
    }

    public void delete(Long deletedBy) {
        this.deletedYn = "Y";
        this.deletedBy = deletedBy;
        this.deletedAt = SecLogTime.now();
    }
}
