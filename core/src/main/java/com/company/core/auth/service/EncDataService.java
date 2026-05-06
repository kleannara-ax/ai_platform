package com.company.core.auth.service;

import com.company.core.auth.dto.EncDataRequest;
import com.company.core.auth.dto.EncDataResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 암호화 데이터 처리 서비스
 * POST /api/login/sendEncData 요청의 비즈니스 로직 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncDataService {

    /**
     * encData 수신 및 처리
     *
     * @param request encData가 포함된 요청 DTO
     * @return 처리 결과 응답 DTO
     */
    public EncDataResponse processEncData(EncDataRequest request) {
        String encData = request.getEncData();
        log.info("encData 수신 완료: length={}", encData.length());
        log.debug("encData 내용: {}", encData);

        // ──────────────────────────────────────────────
        //  TODO: 실제 암호화 데이터 처리 로직 구현
        //  예) 복호화, 검증, 외부 시스템 연동 등
        // ──────────────────────────────────────────────

        return EncDataResponse.of(encData, "encData 수신 및 처리 완료");
    }
}
