package com.company.module.code.service;

import com.company.core.common.exception.BusinessException;
import com.company.core.common.exception.ErrorCode;
import com.company.module.code.dto.*;
import com.company.module.code.entity.CodeDetail;
import com.company.module.code.entity.CodeGroup;
import com.company.module.code.repository.CodeDetailRepository;
import com.company.module.code.repository.CodeGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeGroupRepository groupRepo;
    private final CodeDetailRepository detailRepo;

    // ══════════════════════════════════════
    //  코드 그룹 CRUD
    // ══════════════════════════════════════

    /** 전체 그룹 목록 (코드 수 포함, 상세 미포함) */
    public List<CodeGroupResponse> getGroups() {
        return groupRepo.findAllByOrderBySortOrderAscGroupCodeAsc().stream()
                .map(CodeGroupResponse::summary)
                .toList();
    }

    /** 그룹 상세 (하위 코드 포함) */
    public CodeGroupResponse getGroup(Long groupId) {
        CodeGroup group = findGroupById(groupId);
        return CodeGroupResponse.from(group);
    }

    /** 그룹 코드로 조회 (하위 코드 포함) */
    public CodeGroupResponse getGroupByCode(String groupCode) {
        CodeGroup group = groupRepo.findByGroupCode(groupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "코드 그룹을 찾을 수 없습니다: " + groupCode));
        return CodeGroupResponse.from(group);
    }

    /** 그룹 생성 */
    @Transactional
    public CodeGroupResponse createGroup(CodeGroupRequest request) {
        if (groupRepo.existsByGroupCode(request.getGroupCode())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "이미 존재하는 그룹 코드입니다: " + request.getGroupCode());
        }
        CodeGroup group = CodeGroup.builder()
                .groupCode(request.getGroupCode().toUpperCase())
                .groupName(request.getGroupName())
                .description(request.getDescription())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        CodeGroup saved = groupRepo.save(group);
        log.info("공통코드 그룹 생성: {} ({})", saved.getGroupName(), saved.getGroupCode());
        return CodeGroupResponse.summary(saved);
    }

    /** 그룹 수정 */
    @Transactional
    public CodeGroupResponse updateGroup(Long groupId, CodeGroupRequest request) {
        CodeGroup group = findGroupById(groupId);
        group.update(request.getGroupName(), request.getDescription(),
                request.getIsActive(), request.getSortOrder());
        log.info("공통코드 그룹 수정: {} ({})", group.getGroupName(), group.getGroupCode());
        return CodeGroupResponse.summary(group);
    }

    /** 그룹 삭제 (하위 코드 포함 삭제) */
    @Transactional
    public void deleteGroup(Long groupId) {
        CodeGroup group = findGroupById(groupId);
        log.info("공통코드 그룹 삭제: {} ({}), 하위코드 {}건", group.getGroupName(), group.getGroupCode(), group.getDetails().size());
        groupRepo.delete(group);
    }

    // ══════════════════════════════════════
    //  코드 상세 CRUD
    // ══════════════════════════════════════

    /** 그룹 하위 코드 목록 */
    public List<CodeDetailResponse> getDetails(Long groupId) {
        return detailRepo.findByGroup_GroupIdOrderBySortOrderAsc(groupId).stream()
                .map(CodeDetailResponse::from)
                .toList();
    }

    /** 그룹 코드(문자열)로 활성 코드 목록 조회 (드롭다운용) */
    public List<CodeDetailResponse> getDetailsByGroupCode(String groupCode) {
        return detailRepo.findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc(groupCode).stream()
                .map(CodeDetailResponse::from)
                .toList();
    }

    /** 코드 상세 단건 */
    public CodeDetailResponse getDetail(Long codeId) {
        CodeDetail detail = findDetailById(codeId);
        return CodeDetailResponse.from(detail);
    }

    /** 코드 추가 */
    @Transactional
    public CodeDetailResponse createDetail(Long groupId, CodeDetailRequest request) {
        CodeGroup group = findGroupById(groupId);
        if (detailRepo.existsByGroup_GroupIdAndCode(groupId, request.getCode())) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "이미 존재하는 코드입니다: " + request.getCode());
        }
        CodeDetail detail = CodeDetail.builder()
                .group(group)
                .code(request.getCode().toUpperCase())
                .codeName(request.getCodeName())
                .description(request.getDescription())
                .extraValue1(request.getExtraValue1())
                .extraValue2(request.getExtraValue2())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        CodeDetail saved = detailRepo.save(detail);
        log.info("공통코드 추가: {}.{} ({})", group.getGroupCode(), saved.getCode(), saved.getCodeName());
        return CodeDetailResponse.from(saved);
    }

    /** 코드 수정 */
    @Transactional
    public CodeDetailResponse updateDetail(Long codeId, CodeDetailRequest request) {
        CodeDetail detail = findDetailById(codeId);
        // 코드 중복 체크 (자기 자신 제외)
        if (detailRepo.existsByGroup_GroupIdAndCodeAndCodeIdNot(detail.getGroup().getGroupId(), request.getCode(), codeId)) {
            throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS, "이미 존재하는 코드입니다: " + request.getCode());
        }
        detail.update(request.getCode().toUpperCase(), request.getCodeName(), request.getDescription(),
                request.getExtraValue1(), request.getExtraValue2(),
                request.getIsActive(), request.getSortOrder());
        log.info("공통코드 수정: {}.{} ({})", detail.getGroup().getGroupCode(), detail.getCode(), detail.getCodeName());
        return CodeDetailResponse.from(detail);
    }

    /** 코드 삭제 */
    @Transactional
    public void deleteDetail(Long codeId) {
        CodeDetail detail = findDetailById(codeId);
        log.info("공통코드 삭제: {}.{} ({})", detail.getGroup().getGroupCode(), detail.getCode(), detail.getCodeName());
        detailRepo.delete(detail);
    }

    // ══ 헬퍼 ══

    private CodeGroup findGroupById(Long groupId) {
        return groupRepo.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "코드 그룹을 찾을 수 없습니다."));
    }

    private CodeDetail findDetailById(Long codeId) {
        return detailRepo.findById(codeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "코드를 찾을 수 없습니다."));
    }
}
