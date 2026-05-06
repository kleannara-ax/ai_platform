package com.company.core.auth.service;

import com.company.core.auth.dto.EncDataRequest;
import com.company.core.auth.dto.EncDataResponse;
import com.company.core.auth.dto.SsoValidateRequest;
import com.company.core.auth.dto.SsoValidateResponse;
import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.security.CustomUserDetails;
import com.company.core.security.JwtTokenProvider;
import com.company.core.user.entity.CoreUser;
import com.company.core.user.repository.CoreUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 암호화 데이터 처리 서비스
 * POST /api/login/sendEncData 요청의 비즈니스 로직 담당
 *
 * 처리 흐름:
 * 1. encData 수신
 * 2. SSO 서버(https://sso.kleannara.com/rest/security/encValidateProduct)에
 *    productId(PRO_000643) + encData를 POST로 전달하여 검증
 * 3. 응답의 head.returnCode == "0" 이면 body.sproId 추출
 * 4. sproId와 동일한 loginId를 가진 사용자 조회
 * 5. 해당 사용자로 JWT 토큰 발급 (로그인 처리)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncDataService {

    private final RestTemplate restTemplate;
    private final CoreUserRepository coreUserRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${sso.validate-url:https://sso.kleannara.com/rest/security/encValidateProduct}")
    private String ssoValidateUrl;

    @Value("${sso.product-id:PRO_000643}")
    private String productId;

    @Value("${jwt.access-token-expiration:3600000}")
    private long accessTokenExpiration;

    /**
     * encData 수신 → SSO 검증 → 로그인 처리
     *
     * @param request encData가 포함된 요청 DTO
     * @return JWT 토큰이 포함된 응답 DTO
     */
    public EncDataResponse processEncData(EncDataRequest request) {
        String encData = request.getEncData();
        log.info("encData 수신 완료: length={}", encData.length());

        // ── 1. SSO 서버에 encData 검증 요청 ──
        SsoValidateResponse ssoResponse = callSsoValidate(encData);

        // ── 2. SSO 응답 검증 (returnCode == "0") ──
        if (!ssoResponse.isSuccess()) {
            String returnCode = ssoResponse.getHead() != null
                    ? ssoResponse.getHead().getReturnCode() : "null";
            String returnMessage = ssoResponse.getHead() != null
                    ? ssoResponse.getHead().getReturnMessage() : "응답 없음";
            log.warn("SSO 검증 실패: returnCode={}, returnMessage={}", returnCode, returnMessage);
            throw new BusinessException(ErrorCode.SSO_VALIDATION_FAILED);
        }

        // ── 3. sproId 추출 ──
        String sproId = ssoResponse.getSproId();
        if (sproId == null || sproId.isBlank()) {
            log.warn("SSO 검증 성공이나 sproId가 비어있습니다.");
            throw new BusinessException(ErrorCode.SSO_VALIDATION_FAILED);
        }
        log.info("SSO 검증 성공: sproId={}", sproId);

        // ── 4. sproId(=loginId)로 사용자 조회 ──
        CoreUser user = coreUserRepository.findByLoginId(sproId)
                .orElseThrow(() -> {
                    log.warn("SSO 인증된 사용자를 찾을 수 없습니다: sproId={}", sproId);
                    return new BusinessException(ErrorCode.SSO_USER_NOT_FOUND);
                });

        if (!user.getEnabled()) {
            log.warn("SSO 인증된 사용자가 비활성화 상태입니다: sproId={}", sproId);
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }

        // ── 5. JWT 토큰 발급 (로그인 처리) ──
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, "", userDetails.getAuthorities());

        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        log.info("SSO 로그인 처리 완료: sproId={}, userId={}", sproId, user.getUserId());

        return EncDataResponse.of(
                accessToken,
                refreshToken,
                accessTokenExpiration / 1000,
                sproId,
                "SSO 인증 및 로그인 처리 완료"
        );
    }

    /**
     * SSO 서버에 encData 검증 요청
     *
     * @param encData 암호화된 데이터
     * @return SSO 검증 응답
     */
    private SsoValidateResponse callSsoValidate(String encData) {
        try {
            // 요청 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 요청 바디 설정
            SsoValidateRequest ssoRequest = SsoValidateRequest.builder()
                    .productId(productId)
                    .encData(encData)
                    .build();

            HttpEntity<SsoValidateRequest> httpEntity = new HttpEntity<>(ssoRequest, headers);

            log.info("SSO 검증 요청: url={}, productId={}", ssoValidateUrl, productId);
            log.debug("SSO 검증 요청 body: {}", ssoRequest);

            // SSO API 호출
            ResponseEntity<SsoValidateResponse> responseEntity = restTemplate.exchange(
                    ssoValidateUrl,
                    HttpMethod.POST,
                    httpEntity,
                    SsoValidateResponse.class
            );

            SsoValidateResponse response = responseEntity.getBody();
            if (response == null) {
                log.error("SSO 서버 응답이 비어있습니다.");
                throw new BusinessException(ErrorCode.SSO_SERVER_ERROR);
            }

            log.info("SSO 검증 응답: {}", response);
            return response;

        } catch (RestClientException e) {
            log.error("SSO 서버 연동 오류: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SSO_SERVER_ERROR);
        }
    }
}
