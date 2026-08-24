package com.company.module.kims.service;

import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.response.PageResponse;
import com.company.module.kims.dto.request.QrLocationCreateRequest;
import com.company.module.kims.dto.request.QrLocationUpdateRequest;
import com.company.module.kims.dto.response.QrLocationResponse;
import com.company.module.kims.entity.QrLocation;
import com.company.module.kims.repository.QrLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * QR 구역 관리 비즈니스 로직.
 * <p>관리자가 위치/부서를 입력해 QR 구역을 생성하면 고유 토큰이 발급되고,
 * 그 토큰 기반 URL 을 QR 이미지로 제공한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QrLocationService {

    /** QR 이 가리키는 요청 페이지 경로 (휴대폰 스캔 시 열림) */
    private static final String REQUEST_PAGE = "/qr-request.html";
    /** QR 이미지 한 변 픽셀 */
    private static final int QR_SIZE = 320;

    private final QrLocationRepository qrLocationRepository;
    private final QrCodeGenerator qrCodeGenerator;

    @Transactional
    public QrLocationResponse create(QrLocationCreateRequest req) {
        QrLocation entity = QrLocation.builder()
                .token(UUID.randomUUID().toString())
                .name(req.getName())
                .location(req.getLocation())
                .department(req.getDepartment())
                .active(req.getActive())
                .remark(req.getRemark())
                .build();
        return QrLocationResponse.from(qrLocationRepository.save(entity));
    }

    public PageResponse<QrLocationResponse> getList(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<QrLocationResponse> result = qrLocationRepository
                .search(emptyToNull(keyword), pageable)
                .map(QrLocationResponse::from);
        return PageResponse.of(result);
    }

    public QrLocationResponse getDetail(Long qrId) {
        return QrLocationResponse.from(findQr(qrId));
    }

    /** 토큰으로 구역 정보 조회 (공개 페이지용, 비활성 구역은 예외) */
    public QrLocationResponse getByToken(String token) {
        QrLocation qr = qrLocationRepository.findByTokenAndDeletedYn(token, "N")
                .orElseThrow(() -> new EntityNotFoundException("유효하지 않은 QR 입니다."));
        if (!qr.isActive()) {
            throw new EntityNotFoundException("비활성화된 QR 입니다.");
        }
        return QrLocationResponse.from(qr);
    }

    @Transactional
    public QrLocationResponse update(Long qrId, QrLocationUpdateRequest req) {
        QrLocation qr = findQr(qrId);
        qr.update(req.getName(), req.getLocation(), req.getDepartment(), req.isActive(), req.getRemark());
        return QrLocationResponse.from(qr);
    }

    @Transactional
    public void delete(Long qrId, String deletedBy) {
        findQr(qrId).delete(deletedBy);
    }

    /**
     * QR 이미지(PNG) 생성.
     * @param baseUrl 서버 외부 접속 베이스 URL (예: https://kims.example.com). 끝 슬래시 없음.
     */
    public byte[] generatePng(Long qrId, String baseUrl) {
        QrLocation qr = findQr(qrId);
        String content = baseUrl + REQUEST_PAGE + "?token=" + qr.getToken();
        return qrCodeGenerator.pngOf(content, QR_SIZE);
    }

    /** 해당 구역의 QR 이 인코딩하는 URL */
    public String buildUrl(Long qrId, String baseUrl) {
        QrLocation qr = findQr(qrId);
        return baseUrl + REQUEST_PAGE + "?token=" + qr.getToken();
    }

    private QrLocation findQr(Long qrId) {
        QrLocation qr = qrLocationRepository.findById(qrId)
                .orElseThrow(() -> new EntityNotFoundException("QR 구역을 찾을 수 없습니다. id=" + qrId));
        if (qr.isDeleted()) {
            throw new EntityNotFoundException("QR 구역을 찾을 수 없습니다. id=" + qrId);
        }
        return qr;
    }

    private String emptyToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
