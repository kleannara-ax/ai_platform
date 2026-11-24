package com.company.module.cckb.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.common.response.PageResponse;
import com.company.module.cckb.dto.response.CckbImageResponse;
import com.company.module.cckb.dto.response.InspectionRecordResponse;
import com.company.module.cckb.entity.CckbImage;
import com.company.module.cckb.entity.InspectionRecord;
import com.company.module.cckb.entity.InspectionStandard;
import com.company.module.cckb.entity.JudgmentCode;
import com.company.module.cckb.repository.CckbImageRepository;
import com.company.module.cckb.repository.InspectionRecordRepository;
import com.company.module.cckb.repository.JudgmentCodeRepository;
import com.company.module.cckb.support.CckbFileStorageService;
import com.company.module.cckb.support.ColorMathUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 색상 검사 실행 + 이력 조회 핵심 서비스.
 * <p>원본 V4 HTML 의 analyze() 함수가 하던 일을 전부 이관:
 * <ol>
 *   <li>업로드된 비교 이미지에서 평균 Lab 추출 (ColorMathUtil.averageLab)</li>
 *   <li>기준 Lab 대비 ΔL/Δa/Δb, ΔE00(CIEDE2000) 계산</li>
 *   <li>현재 활성 기준값의 임계값으로 판정(classify) — 정상/경계/불량</li>
 *   <li>검사ID 채번(CckbSequenceService) 후 InspectionRecord 저장</li>
 *   <li>업로드 이미지 파일을 디스크에 저장하고 CckbImage 메타를 검사 레코드에 연결
 *       (dailyreport ImageController 패턴 — 파일 저장은 CckbFileStorageService 위임)</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService {

    private final InspectionRecordRepository inspectionRecordRepository;
    private final CckbImageRepository cckbImageRepository;
    private final JudgmentCodeRepository judgmentCodeRepository;
    private final InspectionStandardService standardService;
    private final CckbSequenceService sequenceService;
    private final CckbFileStorageService fileStorageService;

    /**
     * 색상 검사 실행.
     *
     * @param file                 비교(CMP) 이미지 파일
     * @param originalInspectionId 재검사인 경우 원본 검사 ID (아니면 null)
     * @param userId               검사를 실행한 사용자 ID
     */
    @Transactional
    public InspectionRecordResponse analyze(MultipartFile file, String originalInspectionId, Long userId) {
        fileStorageService.validate(file);

        // 재검사 지정 시 원본 검사가 실제 존재하는지 확인 (원본 HTML 의 startReinspect 플로우)
        if (originalInspectionId != null && !originalInspectionId.isBlank()) {
            inspectionRecordRepository.findByInspectionIdAndDeletedYn(originalInspectionId, "N")
                    .orElseThrow(() -> new EntityNotFoundException("원본 검사 이력", originalInspectionId));
        }

        BufferedImage image;
        try {
            image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일을 읽을 수 없습니다.");
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미지 파일을 읽는 중 오류가 발생했습니다.");
        }

        InspectionStandard standard = standardService.getActiveStandardEntity();
        double[] refLab = {standard.getRefL().doubleValue(), standard.getRefA().doubleValue(), standard.getRefB().doubleValue()};
        double[] cmpLab = ColorMathUtil.averageLab(image);

        double dL = cmpLab[0] - refLab[0];
        double dA = cmpLab[1] - refLab[1];
        double dB = cmpLab[2] - refLab[2];
        double dE00 = ColorMathUtil.deltaE2000(refLab, cmpLab);

        ColorMathUtil.Thresholds thresholds = new ColorMathUtil.Thresholds(
                standard.getNormalDe00Max().doubleValue(), standard.getNormalDlMax().doubleValue(),
                standard.getNormalDaMax().doubleValue(), standard.getNormalDbMax().doubleValue(),
                standard.getDefectDe00Min().doubleValue(), standard.getDefectDlMax().doubleValue(),
                standard.getDefectDaMax().doubleValue(), standard.getDefectDbMax().doubleValue()
        );
        ColorMathUtil.JudgmentResult judgment = ColorMathUtil.classify(dE00, dL, dA, dB, thresholds);

        String inspectionId = sequenceService.generateInspectionId();

        InspectionRecord record = InspectionRecord.builder()
                .inspectionId(inspectionId)
                .standard(standard)
                .measuredL(round2(cmpLab[0])).measuredA(round2(cmpLab[1])).measuredB(round2(cmpLab[2]))
                .deltaL(round2(dL)).deltaA(round2(dA)).deltaB(round2(dB))
                .deltaE00(round2(dE00))
                .judgmentCode(judgment.code())
                .judgmentReason(judgment.reason())
                .originalInspectionId((originalInspectionId != null && !originalInspectionId.isBlank()) ? originalInspectionId : null)
                .createdBy(userId)
                .build();
        inspectionRecordRepository.save(record);

        // 이미지 파일 저장 (dailyreport 패턴: 물리 저장 → API 경로를 DB 메타로)
        CckbFileStorageService.StoredFile stored = fileStorageService.save(file);
        String fileUrl = "/cckb-api/images/files/" + stored.storedFilename();
        CckbImage cckbImage = CckbImage.builder()
                .inspectionRecord(record)
                .imageType(CckbImage.TYPE_CMP)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : stored.storedFilename())
                .fileUrl(fileUrl)
                .widthPx(image.getWidth())
                .heightPx(image.getHeight())
                .fileSizeKb((int) Math.ceil(file.getSize() / 1024.0))
                .uploadedBy(userId)
                .build();
        cckbImageRepository.save(cckbImage);

        return toResponse(record);
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 이력 탭 검색/페이지네이션.
     * 원본 V4 HTML 의 getFilteredHistory() + HISTORY_PAGE_SIZE=10 페이지네이션 이관.
     */
    public PageResponse<InspectionRecordResponse> search(String keyword, LocalDateTime from, LocalDateTime to, int page, int size) {
        Page<InspectionRecord> result = inspectionRecordRepository.search(
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "inspectionDatetime"))
        );
        Page<InspectionRecordResponse> mapped = result.map(this::toResponse);
        return PageResponse.of(mapped);
    }

    public InspectionRecordResponse getById(String inspectionId) {
        InspectionRecord record = inspectionRecordRepository.findByInspectionIdAndDeletedYn(inspectionId, "N")
                .orElseThrow(() -> new EntityNotFoundException("검사 이력", inspectionId));
        return toResponse(record);
    }

    private InspectionRecordResponse toResponse(InspectionRecord record) {
        JudgmentCode code = judgmentCodeRepository.findById(record.getJudgmentCode()).orElse(null);
        List<CckbImageResponse> images = cckbImageRepository
                .findByInspectionRecord_InspectionIdOrderByImageIdAsc(record.getInspectionId())
                .stream().map(CckbImageResponse::from).toList();
        return InspectionRecordResponse.from(
                record,
                code != null ? code.getJudgmentNameKr() : record.getJudgmentCode(),
                code != null ? code.getColorHex() : null,
                images
        );
    }
}
