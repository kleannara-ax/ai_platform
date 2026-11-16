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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 양식 버전 (보안팀 양식 관리).
 *
 * <p>종류별로 사용 중(ACTIVE_YN = 'Y') 버전은 최대 1건이며, 없으면 모듈 내장 기본 양식을 쓴다.
 * 이전 버전은 지우지 않고 남겨 두어 되돌리기와 심사 증적에 쓴다.
 */
@Entity
@Table(name = "pi_form_template")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PiFormTemplate extends PiBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FORM_TEMPLATE_ID")
    private Long formTemplateId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "FORM_TYPE", nullable = false, length = 30)
    private FormTemplateType formType;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
    private String originalName;

    @Column(name = "STORED_PATH", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "CONTENT_TYPE", length = 150)
    private String contentType;

    @Column(name = "FILE_EXT", nullable = false, length = 10)
    private String fileExt;

    @Column(name = "FILE_SIZE", nullable = false)
    private Long fileSize;

    @Column(name = "FILE_HASH", length = 64)
    private String fileHash;

    @Column(name = "CHANGE_NOTE", length = 1000)
    private String changeNote;

    @Column(name = "VALIDATION_NOTE", length = 2000)
    private String validationNote;

    @Column(name = "ACTIVE_YN", nullable = false, length = 1, columnDefinition = "CHAR(1)")
    private String activeYn = "N";

    @Column(name = "ACTIVATED_AT")
    private LocalDateTime activatedAt;

    @Column(name = "ACTIVATED_BY")
    private Long activatedBy;

    @Column(name = "ACTIVATED_BY_NAME", length = 100)
    private String activatedByName;

    @Column(name = "CREATED_BY_NAME", length = 100)
    private String createdByName;

    @Builder
    private PiFormTemplate(FormTemplateType formType, Integer versionNo, String originalName, String storedPath,
                           String contentType, String fileExt, Long fileSize, String fileHash, String changeNote,
                           String validationNote, Long createdBy, String createdByName) {
        this.formType = formType;
        this.versionNo = versionNo;
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.fileExt = fileExt;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.changeNote = changeNote;
        this.validationNote = validationNote;
        this.activeYn = "N";
        this.createdByName = createdByName;
        markCreatedBy(createdBy);
    }

    public boolean isActive() {
        return "Y".equals(activeYn);
    }

    public String getVersionLabel() {
        return "v" + versionNo;
    }

    /** 사용 중 양식으로 지정 */
    public void activate(Long userId, String userName) {
        this.activeYn = "Y";
        this.activatedAt = LocalDateTime.now();
        this.activatedBy = userId;
        this.activatedByName = userName;
        markUpdatedBy(userId);
    }

    /** 사용 중 해제 (다른 버전 또는 기본 양식을 쓰게 됨) */
    public void deactivate(Long userId) {
        this.activeYn = "N";
        markUpdatedBy(userId);
    }

    public void delete(Long userId) {
        softDelete(userId);
    }
}
