package com.company.module.personalinfo.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.core.security.CustomUserDetails;
import com.company.module.personalinfo.dto.AccessLogResponse;
import com.company.module.personalinfo.entity.AccessActionType;
import com.company.module.personalinfo.entity.PiAccessLog;
import com.company.module.personalinfo.entity.PiContract;
import com.company.module.personalinfo.repository.PiAccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 접근 기록 (조회·다운로드).
 *
 * <p>계약 상세·점검 상세 조회와 계약서·점검 결과·엑셀 다운로드를 누가·언제·어디서·무엇을(엑셀은 사유까지) 했는지 남긴다.
 * 조회 트랜잭션(readOnly)과 상관없이 남도록 별도 트랜잭션으로 저장하고, 기록 저장이 실패해도 업무 화면은 막지 않는다(오류 로그).
 * 기록은 추가만 하고 수정·삭제 API 는 없다.
 */
@Slf4j
@Service
public class PiAccessLogService {

    public static final int MIN_REASON_LENGTH = 2;
    private static final int MAX_PAGE_SIZE = 500;

    private final PiAccessLogRepository repository;
    private final TransactionTemplate newTx;

    public PiAccessLogService(PiAccessLogRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(AccessActionType action, PiContract c, Long targetId, String targetName) {
        record(action, c, targetId, targetName, null, null);
    }

    public void record(AccessActionType action, PiContract c, Long targetId, String targetName,
                       Integer recordCount, String reason) {
        record(action, c == null ? null : c.getContractId(), c == null ? null : c.getVendorName(),
                c == null ? null : c.getContractName(), targetId, targetName, recordCount, reason);
    }

    public void record(AccessActionType action, Long contractId, String vendorName, String contractName,
                       Long targetId, String targetName, Integer recordCount, String reason) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            CustomUserDetails user = auth != null && auth.getPrincipal() instanceof CustomUserDetails u ? u : null;
            HttpServletRequest req = currentRequest();
            PiAccessLog row = PiAccessLog.builder()
                    .actionType(action)
                    .contractId(contractId)
                    .vendorName(vendorName)
                    .contractName(contractName)
                    .targetId(targetId)
                    .targetName(targetName)
                    .recordCount(recordCount)
                    .reason(reason)
                    .userId(user == null ? null : user.getUserId())
                    .userLoginId(user == null ? (auth == null ? null : auth.getName()) : user.getLoginId())
                    .userName(user == null ? null : user.getUserDisplayName())
                    .clientIp(clientIp(req))
                    .userAgent(req == null ? null : req.getHeader("User-Agent"))
                    .build();
            newTx.executeWithoutResult(s -> repository.save(row));
        } catch (RuntimeException e) {
            log.error("[personalinfo] 접근 기록 저장 실패 action={} contractId={}", action, contractId, e);
        }
    }

    /** 다운로드 사유 확인 (엑셀 다운로드는 사유 필수) */
    public static String requireReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() < MIN_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "다운로드 사유를 입력하세요. (개인정보가 포함된 목록을 내려받는 사유를 기록합니다)");
        }
        return r.length() > 500 ? r.substring(0, 500) : r;
    }

    /** 접근 기록 조회 (관리자). 기간 기본값: 최근 30일 */
    public Page<AccessLogResponse> search(LocalDate from, LocalDate to, String actionType, Long contractId,
                                          String user, String vendor, int page, int size) {
        LocalDate t = to == null ? LocalDate.now() : to;
        LocalDate f = from == null ? t.minusDays(30) : from;
        if (f.isAfter(t)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "조회 시작일이 종료일보다 늦습니다.");
        }
        AccessActionType type = null;
        if (StringUtils.hasText(actionType)) {
            try {
                type = AccessActionType.valueOf(actionType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "행위 구분 값이 올바르지 않습니다: " + actionType);
            }
        }
        return repository.search(f.atStartOfDay(), t.plusDays(1).atStartOfDay(), type == null ? 0 : 1, type, contractId,
                        user == null ? "" : user.trim(), vendor == null ? "" : vendor.trim(),
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE)))
                .map(AccessLogResponse::from);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a ? a.getRequest() : null;
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String real = req.getHeader("X-Real-IP");
        return StringUtils.hasText(real) ? real.trim() : req.getRemoteAddr();
    }
}
