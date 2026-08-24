package com.company.module.kims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

/**
 * 업무 요청 첨부파일.
 * <p>실제 파일은 디스크(업로드 디렉토리)에 저장하고, 이 엔티티에는 메타데이터만 보관한다.
 */
@Entity
@Table(name = "request_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestAttachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ATTACHMENT_ID")
    private Long attachmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "REQUEST_ID", nullable = false)
    private ServiceRequest serviceRequest;

    /** 사용자가 올린 원본 파일명 */
    @Column(name = "ORIGINAL_NAME", nullable = false, length = 255)
    private String originalName;

    /** 디스크에 저장된 파일명 (UUID 기반, 충돌 방지) */
    @Column(name = "STORED_NAME", nullable = false, length = 255)
    private String storedName;

    @Column(name = "CONTENT_TYPE", length = 150)
    private String contentType;

    /** 파일 크기 (byte) */
    @Column(name = "FILE_SIZE", nullable = false)
    private long fileSize;

    @Column(name = "UPLOADED_BY", length = 50)
    private String uploadedBy;

    @Builder
    private RequestAttachment(ServiceRequest serviceRequest, String originalName, String storedName,
                              String contentType, long fileSize, String uploadedBy) {
        this.serviceRequest = serviceRequest;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedBy = uploadedBy;
    }
}
