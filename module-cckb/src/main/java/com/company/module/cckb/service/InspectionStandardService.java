package com.company.module.cckb.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.cckb.dto.request.InspectionStandardUpdateRequest;
import com.company.module.cckb.dto.response.InspectionStandardResponse;
import com.company.module.cckb.entity.InspectionStandard;
import com.company.module.cckb.entity.StandardAuditLog;
import com.company.module.cckb.repository.InspectionStandardRepository;
import com.company.module.cckb.repository.StandardAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * "⚙ 기준값 설정" 탭 비즈니스 로직.
 * <p>원본 V4 HTML 은 localStorage 에 기준 L* / a* / b*(REF_LAB_KEY) + 임계값(THRESHOLDS_KEY) 을
 * 단일 레코드로 저장했으나, 플랫폼 이관에서는 {@link InspectionStandard} 이력 테이블로 승격하고
 * 수정 시마다 {@link StandardAuditLog} 에 변경 필드별 1건씩 감사 로그를 남긴다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionStandardService {

    /** 원본 V4 HTML DEFAULT_REF_LAB = {L:59.2, a:9.0, b:21.3} */
    private static final BigDecimal DEFAULT_REF_L = new BigDecimal("59.2");
    private static final BigDecimal DEFAULT_REF_A = new BigDecimal("9.0");
    private static final BigDecimal DEFAULT_REF_B = new BigDecimal("21.3");
    /** 원본 V4 HTML DEFAULT_THRESHOLDS */
    private static final BigDecimal DEFAULT_NORMAL_DE = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_NORMAL_L = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_NORMAL_A = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_NORMAL_B = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_DEFECT_DE = new BigDecimal("4.0");
    private static final BigDecimal DEFAULT_DEFECT_L = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_DEFECT_A = new BigDecimal("2.0");
    private static final BigDecimal DEFAULT_DEFECT_B = new BigDecimal("2.0");

    private final InspectionStandardRepository standardRepository;
    private final StandardAuditLogRepository auditLogRepository;

    /**
     * 현재 활성 기준값 조회. 아직 한 건도 없으면(최초 기동) 원본 HTML 의 기본값으로
     * 1건을 자동 생성한다 — localStorage 버전의 "기본값 폴백" 동작을 DB 버전에서도 재현.
     */
    @Transactional
    public InspectionStandardResponse getActiveStandard() {
        InspectionStandard standard = standardRepository
                .findFirstByIsActiveAndDeletedYnOrderByStandardIdDesc("Y", "N")
                .orElseGet(this::createDefaultStandard);
        return InspectionStandardResponse.from(standard);
    }

    /** 엔티티 그대로 필요로 하는 내부 서비스(InspectionService)용 */
    @Transactional
    public InspectionStandard getActiveStandardEntity() {
        return standardRepository
                .findFirstByIsActiveAndDeletedYnOrderByStandardIdDesc("Y", "N")
                .orElseGet(this::createDefaultStandard);
    }

    private InspectionStandard createDefaultStandard() {
        InspectionStandard standard = InspectionStandard.builder()
                .standardVersion("v1.0")
                .refL(DEFAULT_REF_L).refA(DEFAULT_REF_A).refB(DEFAULT_REF_B)
                .normalDe00Max(DEFAULT_NORMAL_DE).normalDlMax(DEFAULT_NORMAL_L)
                .normalDaMax(DEFAULT_NORMAL_A).normalDbMax(DEFAULT_NORMAL_B)
                .defectDe00Min(DEFAULT_DEFECT_DE).defectDlMax(DEFAULT_DEFECT_L)
                .defectDaMax(DEFAULT_DEFECT_A).defectDbMax(DEFAULT_DEFECT_B)
                .effectiveFrom(LocalDate.now())
                .remarks("시스템 기본값 자동 생성")
                .createdBy(null)
                .build();
        return standardRepository.save(standard);
    }

    /**
     * 기준값 수정 (관리자 전용 — 컨트롤러에서 CckbPermission 으로 사전 검증).
     * 변경된 필드마다 {@link StandardAuditLog} 1건씩 기록한다.
     */
    @Transactional
    public InspectionStandardResponse update(InspectionStandardUpdateRequest req, Long userId) {
        InspectionStandard standard = getActiveStandardEntity();

        recordDiff(standard, "ref_l", standard.getRefL(), req.getRefL(), userId, req.getRemarks());
        recordDiff(standard, "ref_a", standard.getRefA(), req.getRefA(), userId, req.getRemarks());
        recordDiff(standard, "ref_b", standard.getRefB(), req.getRefB(), userId, req.getRemarks());
        recordDiff(standard, "normal_de00_max", standard.getNormalDe00Max(), req.getNormalDe00Max(), userId, req.getRemarks());
        recordDiff(standard, "normal_dl_max", standard.getNormalDlMax(), req.getNormalDlMax(), userId, req.getRemarks());
        recordDiff(standard, "normal_da_max", standard.getNormalDaMax(), req.getNormalDaMax(), userId, req.getRemarks());
        recordDiff(standard, "normal_db_max", standard.getNormalDbMax(), req.getNormalDbMax(), userId, req.getRemarks());
        recordDiff(standard, "defect_de00_min", standard.getDefectDe00Min(), req.getDefectDe00Min(), userId, req.getRemarks());
        recordDiff(standard, "defect_dl_max", standard.getDefectDlMax(), req.getDefectDlMax(), userId, req.getRemarks());
        recordDiff(standard, "defect_da_max", standard.getDefectDaMax(), req.getDefectDaMax(), userId, req.getRemarks());
        recordDiff(standard, "defect_db_max", standard.getDefectDbMax(), req.getDefectDbMax(), userId, req.getRemarks());

        standard.update(
                req.getRefL(), req.getRefA(), req.getRefB(),
                req.getNormalDe00Max(), req.getNormalDlMax(), req.getNormalDaMax(), req.getNormalDbMax(),
                req.getDefectDe00Min(), req.getDefectDlMax(), req.getDefectDaMax(), req.getDefectDbMax(),
                req.getRemarks(), userId
        );
        return InspectionStandardResponse.from(standard);
    }

    private void recordDiff(InspectionStandard standard, String field, BigDecimal oldVal, BigDecimal newVal,
                             Long userId, String reason) {
        if (newVal == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, field + " 값은 필수입니다.");
        }
        if (oldVal != null && oldVal.compareTo(newVal) == 0) {
            return;
        }
        StandardAuditLog log = StandardAuditLog.builder()
                .standard(standard)
                .changedField(field)
                .oldValue(oldVal != null ? oldVal.toPlainString() : null)
                .newValue(newVal.toPlainString())
                .changedBy(userId)
                .changeReason(reason)
                .build();
        auditLogRepository.save(log);
    }

    public InspectionStandard findByIdOrThrow(Long standardId) {
        return standardRepository.findById(standardId)
                .orElseThrow(() -> new EntityNotFoundException("기준값", standardId));
    }
}
