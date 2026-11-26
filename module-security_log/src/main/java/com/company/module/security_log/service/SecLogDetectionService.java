package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DetectionResponse;
import com.company.module.security_log.dto.DetectionReviewRequest;
import com.company.module.security_log.dto.RuleReviewRequest;
import com.company.module.security_log.dto.RuleReviewResponse;
import com.company.module.security_log.entity.ReviewStatus;
import com.company.module.security_log.entity.SecLogDetection;
import com.company.module.security_log.entity.SecLogUpload;
import com.company.module.security_log.entity.Severity;
import com.company.module.security_log.repository.SecLogDetectionRepository;
import com.company.module.security_log.repository.SecLogUploadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 탐지 결과 조회 / 검토
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogDetectionService {

    private final SecLogDetectionRepository detectionRepository;
    private final SecLogUploadRepository uploadRepository;

    public Page<DetectionResponse> getList(Long uploadId, Severity severity, ReviewStatus reviewStatus,
                                           String ruleCode, int page, int size) {
        findUpload(uploadId);
        int pageSize = size <= 0 ? 50 : Math.min(size, SecLogConstants.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), pageSize,
                Sort.by(Sort.Order.asc("lineNumber"), Sort.Order.asc("detectionId")));
        return detectionRepository.search(uploadId, severity, reviewStatus,
                        ruleCode == null || ruleCode.isBlank() ? null : ruleCode.trim(), pageable)
                .map(DetectionResponse::from);
    }

    public DetectionResponse getDetail(Long detectionId) {
        SecLogDetection detection = findDetection(detectionId);
        findUpload(detection.getUploadId());
        return DetectionResponse.from(detection);
    }

    /** 단건/다건 검토 처리 (모두 동일 업로드에 속해야 함) */
    @Transactional
    public List<DetectionResponse> review(Long uploadId, DetectionReviewRequest request, Long userId) {
        checkEditable(uploadId);
        List<Long> ids = List.copyOf(new LinkedHashSet<>(request.getDetectionIds()));
        List<SecLogDetection> detections = detectionRepository.findAllById(ids);

        if (detections.size() != ids.size()) {
            throw new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        for (SecLogDetection d : detections) {
            if (!uploadId.equals(d.getUploadId()) || !SecLogConstants.DELETED_N.equals(d.getDeletedYn())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "해당 업로드에 속하지 않은 탐지 ID가 포함되어 있습니다.");
            }
        }
        detections.forEach(d -> d.review(request.getReviewStatus(), request.getActionContent(), userId));
        return detections.stream().map(DetectionResponse::from).toList();
    }

    /**
     * 룰 단위 일괄 검토
     * - 해당 업로드에서 같은 룰로 탐지된 건을 한 번에 처리 (건수 제한 없음)
     * - onlyPending=true(기본)면 미검토 건만, false면 이미 검토한 건도 덮어씀
     */
    @Transactional
    public RuleReviewResponse reviewByRule(Long uploadId, RuleReviewRequest request, Long userId) {
        checkEditable(uploadId);
        String ruleCode = request.getRuleCode().trim();
        boolean onlyPending = !Boolean.FALSE.equals(request.getOnlyPending());
        List<SecLogDetection> targets = detectionRepository.findByRule(uploadId, ruleCode,
                onlyPending ? ReviewStatus.PENDING : null);
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    onlyPending ? "해당 룰에 미검토 탐지 건이 없습니다." : "해당 룰의 탐지 건이 없습니다.");
        }
        targets.forEach(d -> d.review(request.getReviewStatus(), request.getActionContent(), userId));
        return RuleReviewResponse.builder().ruleCode(ruleCode).updatedCount(targets.size()).build();
    }

    private void checkEditable(Long uploadId) {
        SecLogUpload upload = findUpload(uploadId);
        if (upload.isReviewed()) {
            // 업로드 검토완료 후에는 탐지 건 변경 불가 (검토취소 후 변경)
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "검토완료된 건은 변경할 수 없습니다. 검토취소 후 진행하세요.");
        }
    }

    private SecLogUpload findUpload(Long uploadId) {
        return uploadRepository.findByUploadIdAndDeletedYn(uploadId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private SecLogDetection findDetection(Long detectionId) {
        return detectionRepository.findByDetectionIdAndDeletedYn(detectionId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
