package com.company.module.kims.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.kims.dto.response.AttachmentResponse;
import com.company.module.kims.entity.RequestAttachment;
import com.company.module.kims.entity.ServiceRequest;
import com.company.module.kims.repository.RequestAttachmentRepository;
import com.company.module.kims.repository.ServiceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * 업무 요청 첨부파일 서비스.
 * <p>실제 파일은 업로드 디렉토리({@code kims.upload-dir})에 저장하고, DB 에는 메타데이터만 보관한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    private final RequestAttachmentRepository attachmentRepository;
    private final ServiceRequestRepository serviceRequestRepository;

    /** 업로드 디렉토리 (application.yml: kims.upload-dir) */
    @Value("${kims.upload-dir}")
    private String uploadDir;

    @Transactional
    public AttachmentResponse upload(Long requestId, MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 없습니다.");
        }
        ServiceRequest request = serviceRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("업무 요청을 찾을 수 없습니다. id=" + requestId));

        String original = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "file";
        String stored = UUID.randomUUID() + extension(original);

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(stored).toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 중 오류가 발생했습니다.", e);
        }

        RequestAttachment entity = RequestAttachment.builder()
                .serviceRequest(request)
                .originalName(original)
                .storedName(stored)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedBy(uploadedBy)
                .build();
        return AttachmentResponse.from(attachmentRepository.save(entity));
    }

    public List<AttachmentResponse> getList(Long requestId) {
        return attachmentRepository.findByServiceRequest_RequestIdOrderByCreatedAtAsc(requestId)
                .stream().map(AttachmentResponse::from).toList();
    }

    /** 다운로드용 파일 로드 (바이트 + 원본명 + 타입) */
    public DownloadFile loadForDownload(Long attachmentId) {
        RequestAttachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("첨부파일을 찾을 수 없습니다. id=" + attachmentId));
        try {
            byte[] data = Files.readAllBytes(Paths.get(uploadDir).resolve(a.getStoredName()));
            return new DownloadFile(data, a.getOriginalName(), a.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "저장된 파일을 읽을 수 없습니다. id=" + attachmentId);
        }
    }

    @Transactional
    public void delete(Long attachmentId) {
        RequestAttachment a = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("첨부파일을 찾을 수 없습니다. id=" + attachmentId));
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(a.getStoredName()));
        } catch (IOException ignored) {
            // 파일이 이미 없어도 메타데이터는 삭제 진행
        }
        attachmentRepository.delete(a);
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }

    /** 다운로드 파일 묶음 */
    public record DownloadFile(byte[] data, String originalName, String contentType) {
    }
}
