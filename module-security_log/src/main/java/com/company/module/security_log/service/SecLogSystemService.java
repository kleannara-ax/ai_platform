package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.SystemResponse;
import com.company.module.security_log.dto.SystemSaveRequest;
import com.company.module.security_log.entity.LogType;
import com.company.module.security_log.entity.SecLogSystem;
import com.company.module.security_log.repository.SecLogSystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 점검 대상 시스템 마스터 관리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecLogSystemService {

    private final SecLogSystemRepository systemRepository;

    public List<SystemResponse> getList(boolean activeOnly) {
        List<SecLogSystem> list = activeOnly
                ? systemRepository.findByDeletedYnAndUseYnOrderBySortOrderAscSystemIdAsc(SecLogConstants.DELETED_N, "Y")
                : systemRepository.findByDeletedYnOrderBySortOrderAscSystemIdAsc(SecLogConstants.DELETED_N);
        return list.stream().map(SystemResponse::from).toList();
    }

    public SecLogSystem findSystem(Long systemId) {
        return systemRepository.findBySystemIdAndDeletedYn(systemId, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException("시스템", systemId));
    }

    @Transactional
    public SystemResponse create(SystemSaveRequest r, Long userId) {
        if (!StringUtils.hasText(r.getSystemCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시스템 코드는 필수입니다.");
        }
        if (systemRepository.existsBySystemCode(r.getSystemCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 사용 중인 시스템 코드입니다: " + r.getSystemCode());
        }
        validateLogType(r.getDefaultLogType());
        SecLogSystem s = SecLogSystem.builder()
                .systemCode(r.getSystemCode()).systemName(r.getSystemName().trim()).category(r.getCategory().trim())
                .defaultLogType(r.getDefaultLogType()).managerName(r.getManagerName())
                .description(r.getDescription()).useYn(r.getUseYn()).sortOrder(r.getSortOrder()).createdBy(userId)
                .build();
        return SystemResponse.from(systemRepository.save(s));
    }

    @Transactional
    public SystemResponse update(Long systemId, SystemSaveRequest r, Long userId) {
        validateLogType(r.getDefaultLogType());
        SecLogSystem s = findSystem(systemId);
        s.update(r.getSystemName().trim(), r.getCategory().trim(), r.getDefaultLogType(), r.getManagerName(),
                r.getDescription(), r.getUseYn(), r.getSortOrder(), userId);
        return SystemResponse.from(s);
    }

    @Transactional
    public SystemResponse changeUseYn(Long systemId, String useYn, Long userId) {
        SecLogSystem s = findSystem(systemId);
        s.changeUseYn(useYn, userId);
        return SystemResponse.from(s);
    }

    @Transactional
    public void delete(Long systemId, Long userId) {
        findSystem(systemId).delete(userId);
    }

    private static void validateLogType(LogType t) {
        if (t == LogType.ALL) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "시스템 기본 로그 유형에는 ALL 을 사용할 수 없습니다.");
        }
    }
}
