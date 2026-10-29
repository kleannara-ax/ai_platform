package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.InternalIpResponse;
import com.company.module.security_log.dto.InternalIpSaveRequest;
import com.company.module.security_log.dto.InternalIpTestResponse;
import com.company.module.security_log.entity.SecLogInternalIp;
import com.company.module.security_log.repository.SecLogInternalIpRepository;
import com.company.module.security_log.support.IpRange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.function.Predicate;

/**
 * 관리 > 내부 IP 설정
 * - 사용 중인 대역에 속하지 않는 IPv4 를 접속 행위 분석에서 '외부 IP' 로 표시한다.
 * - 변경은 이후 업로드·재분석부터 적용된다 (이미 분석한 결과는 그대로).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogInternalIpService {

    /** 너무 넓은 대역(예: 0.0.0.0/0)을 내부로 지정하면 외부 IP 판정이 사라지므로 /8 보다 넓은 대역은 막는다 */
    static final int MIN_PREFIX = 8;

    private final SecLogInternalIpRepository repository;

    public List<InternalIpResponse> getList() {
        return repository.findByDeletedYnOrderBySortOrderAscStartIpNumAsc(SecLogConstants.DELETED_N)
                .stream().map(InternalIpResponse::from).toList();
    }

    /**
     * 분석용 내부 IP 판정기 (사용 중인 대역 스냅샷)
     * - IPv4 가 아니면(IPv6·형식 오류) 내부로 본다: 외부 IP 로 잘못 표시하지 않기 위함 (기존 동작과 같음)
     */
    public Predicate<String> internalMatcher() {
        List<long[]> ranges = repository
                .findByDeletedYnAndUseYnOrderBySortOrderAscStartIpNumAsc(SecLogConstants.DELETED_N, "Y")
                .stream().map(e -> new long[]{e.getStartIpNum(), e.getEndIpNum()}).toList();
        return ip -> {
            Long v = IpRange.toLong(ip);
            if (v == null) {
                return true;
            }
            for (long[] r : ranges) {
                if (v >= r[0] && v <= r[1]) {
                    return true;
                }
            }
            return false;
        };
    }

    /** IP 확인 (저장하지 않음) */
    public InternalIpTestResponse test(String ip) {
        String v = ip == null ? "" : ip.trim();
        Long n = IpRange.toLong(v);
        if (n == null) {
            return InternalIpTestResponse.builder().ip(v).valid(false).internal(false).matched(List.of())
                    .message("IPv4 형식이 아닙니다 (예: 203.0.113.10). IPv6 는 외부 IP 판정을 하지 않습니다.").build();
        }
        List<String> matched = repository
                .findByDeletedYnAndUseYnOrderBySortOrderAscStartIpNumAsc(SecLogConstants.DELETED_N, "Y").stream()
                .filter(e -> e.contains(n)).map(e -> e.getIpName() + " (" + e.getIpRange() + ")").toList();
        boolean internal = !matched.isEmpty();
        return InternalIpTestResponse.builder().ip(v).valid(true).internal(internal).matched(matched)
                .message(internal ? "내부 IP 입니다. 외부 IP 로 표시하지 않습니다."
                        : "등록된 내부 대역에 없습니다. 접속 행위 분석에서 '외부 IP' 로 표시합니다.").build();
    }

    @Transactional
    public InternalIpResponse create(InternalIpSaveRequest r, Long userId) {
        IpRange range = parse(r.getIpRange());
        SecLogInternalIp existing = repository.findByIpRange(range.getCidr()).orElse(null);
        if (existing != null) {
            if (SecLogConstants.DELETED_N.equals(existing.getDeletedYn())) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 등록된 IP 대역입니다: " + range.getCidr());
            }
            // 삭제했던 대역을 다시 등록 → 복구 후 새 값으로 갱신 (UK 충돌 방지)
            existing.restore(userId);
            existing.update(range.getCidr(), range.getStart(), range.getEnd(), r.getIpName().trim(),
                    trim(r.getDescription()), r.getUseYn(), r.getSortOrder(), userId);
            return InternalIpResponse.from(existing);
        }
        SecLogInternalIp e = SecLogInternalIp.builder()
                .ipRange(range.getCidr()).startIpNum(range.getStart()).endIpNum(range.getEnd())
                .ipName(r.getIpName().trim()).description(trim(r.getDescription()))
                .useYn(r.getUseYn()).sortOrder(r.getSortOrder()).createdBy(userId).build();
        return InternalIpResponse.from(repository.save(e));
    }

    @Transactional
    public InternalIpResponse update(Long id, InternalIpSaveRequest r, Long userId) {
        SecLogInternalIp e = find(id);
        IpRange range = parse(r.getIpRange());
        repository.findByIpRange(range.getCidr()).ifPresent(o -> {
            if (!o.getInternalIpId().equals(id)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 등록된(또는 삭제된) IP 대역입니다: " + range.getCidr());
            }
        });
        e.update(range.getCidr(), range.getStart(), range.getEnd(), r.getIpName().trim(),
                trim(r.getDescription()), r.getUseYn(), r.getSortOrder(), userId);
        return InternalIpResponse.from(e);
    }

    @Transactional
    public InternalIpResponse changeUseYn(Long id, String useYn, Long userId) {
        SecLogInternalIp e = find(id);
        e.changeUseYn(useYn, userId);
        return InternalIpResponse.from(e);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        find(id).delete(userId);
    }

    private SecLogInternalIp find(Long id) {
        return repository.findByInternalIpIdAndDeletedYn(id, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static IpRange parse(String text) {
        IpRange range = IpRange.parse(text);
        if (range == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "IP 대역 형식이 올바르지 않습니다. 예: 10.0.0.0/8, 192.168.10.0/24, 203.0.113.10 (IPv4 만 가능)");
        }
        if (range.getPrefix() < MIN_PREFIX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "대역이 너무 넓습니다. /" + MIN_PREFIX + " 이상으로 입력하세요 (모든 IP 를 내부로 보면 외부 IP 판정을 할 수 없습니다).");
        }
        return range;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
