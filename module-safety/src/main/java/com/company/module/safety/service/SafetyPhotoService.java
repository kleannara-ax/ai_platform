package com.company.module.safety.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.safety.dto.response.StepPhotoResponse;
import com.company.module.safety.entity.SafetyManualStep;
import com.company.module.safety.entity.SafetyManualStepPhoto;
import com.company.module.safety.repository.SafetyManualStepPhotoRepository;
import com.company.module.safety.repository.SafetyManualStepRepository;
import com.company.module.safety.support.SafetyExcelParser.ParsedPhoto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 매뉴얼 단계별 사진 서비스.
 * <p>실제 파일은 업로드 디렉토리({@code safety.upload-dir})에 저장하고, DB 에는 메타데이터만 보관한다.
 * (module-kims 의 AttachmentService 와 동일한 패턴)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyPhotoService {

    private final SafetyManualStepPhotoRepository photoRepository;
    private final SafetyManualStepRepository stepRepository;

    /** 업로드 디렉토리 (application.yml: safety.upload-dir) */
    @Value("${safety.upload-dir}")
    private String uploadDir;

    // ================================================================
    // 사진 업로드 (관리자, 매뉴얼 상세 화면에서 직접 첨부)
    // ================================================================
    @Transactional
    public StepPhotoResponse upload(Long stepId, MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "업로드할 사진이 없습니다.");
        }
        SafetyManualStep step = findActiveStep(stepId);

        String original = (file.getOriginalFilename() != null) ? file.getOriginalFilename() : "photo";
        String stored = UUID.randomUUID() + extension(original);

        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            file.transferTo(dir.resolve(stored).toAbsolutePath());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "사진 저장 중 오류가 발생했습니다.");
        }

        SafetyManualStepPhoto entity = SafetyManualStepPhoto.builder()
                .step(step)
                .originalName(original)
                .storedName(stored)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .sortOrder(nextSortOrder(stepId))
                .createdBy(uploadedBy)
                .build();
        return StepPhotoResponse.from(photoRepository.save(entity));
    }

    // ================================================================
    // 엑셀 일괄업로드에서 추출된 사진(byte[])을 디스크에 저장 (2단계: 확정 시에만 호출)
    // ================================================================
    @Transactional
    public StepPhotoResponse saveParsedPhoto(SafetyManualStep step, ParsedPhoto parsed, String createdBy) {
        String stored = UUID.randomUUID() + extension(parsed.fileName());
        try {
            Path dir = Paths.get(uploadDir);
            Files.createDirectories(dir);
            Files.write(dir.resolve(stored), parsed.data());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "사진 저장 중 오류가 발생했습니다.");
        }

        SafetyManualStepPhoto entity = SafetyManualStepPhoto.builder()
                .step(step)
                .originalName(parsed.fileName())
                .storedName(stored)
                .contentType(parsed.contentType())
                .fileSize(parsed.data() != null ? parsed.data().length : 0)
                .sortOrder(nextSortOrder(step.getStepId()))
                .createdBy(createdBy)
                .build();
        return StepPhotoResponse.from(photoRepository.save(entity));
    }

    // ================================================================
    // 사진 조회 (화면 <img> 표시용, 공개 — SafetySecurityConfig 참고)
    // ================================================================
    public ViewFile loadForView(Long photoId) {
        SafetyManualStepPhoto p = findActive(photoId);
        try {
            byte[] data = Files.readAllBytes(Paths.get(uploadDir).resolve(p.getStoredName()));
            return new ViewFile(data, p.getOriginalName(), p.getContentType());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "저장된 사진을 읽을 수 없습니다. id=" + photoId);
        }
    }

    // ================================================================
    // 사진 삭제 (관리자)
    // ================================================================
    @Transactional
    public void delete(Long photoId, String deletedBy) {
        SafetyManualStepPhoto p = findActive(photoId);
        try {
            Files.deleteIfExists(Paths.get(uploadDir).resolve(p.getStoredName()));
        } catch (IOException ignored) {
            // 파일이 이미 없어도 메타데이터는 삭제 진행
        }
        p.delete(deletedBy);
    }

    // ----------------------------------------------------------------
    // 내부 공통
    // ----------------------------------------------------------------

    private SafetyManualStep findActiveStep(Long stepId) {
        return stepRepository.findActiveById(stepId)
                .orElseThrow(() -> new EntityNotFoundException("단계를 찾을 수 없습니다. id=" + stepId));
    }

    private SafetyManualStepPhoto findActive(Long photoId) {
        return photoRepository.findActiveById(photoId)
                .orElseThrow(() -> new EntityNotFoundException("사진을 찾을 수 없습니다. id=" + photoId));
    }

    private int nextSortOrder(Long stepId) {
        return photoRepository.findByStepIdOrderBySortOrder(stepId).size();
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot) : "";
    }

    /** 조회/다운로드용 파일 묶음 */
    public record ViewFile(byte[] data, String originalName, String contentType) {
    }
}
