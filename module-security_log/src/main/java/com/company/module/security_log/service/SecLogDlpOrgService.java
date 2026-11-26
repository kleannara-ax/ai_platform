package com.company.module.security_log.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.EntityNotFoundException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.security_log.dto.DlpOrgResponse;
import com.company.module.security_log.dto.DlpOrgSaveRequest;
import com.company.module.security_log.entity.SecLogDlpKakaoApproval;
import com.company.module.security_log.entity.SecLogDlpOrg;
import com.company.module.security_log.repository.SecLogDlpKakaoApprovalRepository;
import com.company.module.security_log.repository.SecLogDlpOrgRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DLP 관리 &gt; 사업부·부서 관리 - 카카오톡 승인 대장 입력 드롭다운 목록
 * <ul>
 *   <li>사업부 아래 부서 (2단계), 같은 사업부 안에서 이름 중복 안 됨</li>
 *   <li>삭제 대신 '사용 안 함' - 드롭다운에서만 빠지고 기존 대장 값은 그대로</li>
 *   <li>이름을 바꿔도 이미 등록된 대장 값은 바꾸지 않음 (DLP 유출 로그 부서 이름과 맞춰야 하므로 담당자가 확인)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SecLogDlpOrgService {

    private static final int MAX_ORGS = 1000;

    private final SecLogDlpOrgRepository orgRepository;
    private final SecLogDlpKakaoApprovalRepository approvalRepository;

    /** 사업부(이름순)마다 아래 부서(이름순), 사용 안 함 포함 */
    @Transactional(readOnly = true)
    public List<DlpOrgResponse> getList() {
        List<SecLogDlpOrg> all = orgRepository.findByDeletedYnOrderByOrgNameAsc(SecLogConstants.DELETED_N);
        Map<String, Long> divUsed = new HashMap<>();
        Map<String, Long> deptUsed = new HashMap<>();
        for (SecLogDlpKakaoApproval a : approvalRepository.findByDeletedYnOrderByEndDateDescKakaoApprovalIdDesc(SecLogConstants.DELETED_N)) {
            String div = trim(a.getDivisionName());
            divUsed.merge(div, 1L, Long::sum);
            deptUsed.merge(div + "|" + trim(a.getDeptName()), 1L, Long::sum);
        }
        List<DlpOrgResponse> result = new ArrayList<>();
        for (SecLogDlpOrg d : all) {
            if (!d.isDivision()) {
                continue;
            }
            List<DlpOrgResponse> depts = new ArrayList<>();
            for (SecLogDlpOrg t : all) {
                if (d.getOrgId().equals(t.getParentOrgId())) {
                    depts.add(DlpOrgResponse.builder().orgId(t.getOrgId()).orgName(t.getOrgName()).useYn(t.getUseYn())
                            .usedCount(deptUsed.getOrDefault(d.getOrgName() + "|" + t.getOrgName(), 0L)).build());
                }
            }
            result.add(DlpOrgResponse.builder().orgId(d.getOrgId()).orgName(d.getOrgName()).useYn(d.getUseYn())
                    .usedCount(divUsed.getOrDefault(d.getOrgName(), 0L)).depts(depts).build());
        }
        return result;
    }

    @Transactional
    public Long create(DlpOrgSaveRequest q, Long userId) {
        if (orgRepository.count() >= MAX_ORGS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사업부·부서는 " + MAX_ORGS + "개까지 등록할 수 있습니다.");
        }
        long parent = SecLogDlpOrg.DIVISION_PARENT;
        if (q.getParentOrgId() != null && q.getParentOrgId() != SecLogDlpOrg.DIVISION_PARENT) {
            SecLogDlpOrg div = org(q.getParentOrgId());
            if (!div.isDivision()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "부서는 사업부 아래에만 추가할 수 있습니다.");
            }
            parent = div.getOrgId();
        }
        String name = name(q.getOrgName());
        checkDuplicate(parent, name, null);
        return orgRepository.save(SecLogDlpOrg.builder().parentOrgId(parent).orgName(name).createdBy(userId).build()).getOrgId();
    }

    @Transactional
    public void update(Long id, DlpOrgSaveRequest q, Long userId) {
        SecLogDlpOrg o = org(id);
        String name = name(q.getOrgName());
        checkDuplicate(o.getParentOrgId(), name, id);
        o.update(name, q.getUseYn() == null ? o.getUseYn() : q.getUseYn(), userId);
    }

    private void checkDuplicate(Long parent, String name, Long selfId) {
        orgRepository.findByParentOrgIdAndOrgName(parent, name)
                .filter(x -> selfId == null || !x.getOrgId().equals(selfId))
                .ifPresent(x -> {
                    throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                            (parent == SecLogDlpOrg.DIVISION_PARENT ? "같은 이름의 사업부" : "이 사업부에 같은 이름의 부서") + "가 이미 있습니다: " + name);
                });
    }

    private SecLogDlpOrg org(Long id) {
        return orgRepository.findByOrgIdAndDeletedYn(id, SecLogConstants.DELETED_N)
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private static String name(String v) {
        String n = v == null ? "" : v.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이름을 입력해 주세요.");
        }
        return n;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
