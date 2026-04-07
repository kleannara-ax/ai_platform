package com.company.app.user.service;

import com.company.app.user.dto.IntegratedUserResponse;
import com.company.core.user.dto.UserCreateRequest;
import com.company.core.user.dto.UserUpdateRequest;
import com.company.core.user.entity.CoreUser;
import com.company.core.user.service.CoreUserService;
import com.company.module.code.entity.CodeDetail;
import com.company.module.code.repository.CodeDetailRepository;
import com.company.module.user.entity.UserProfile;
import com.company.module.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용자 + 프로필 통합 서비스
 * app 모듈에서 core와 module-common(사용자 프로필 + 공통코드)을 조합한다.
 * 부서는 공통코드(DEPT 그룹)로 관리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IntegratedUserService {

    private final CoreUserService coreUserService;
    private final UserProfileRepository profileRepo;
    private final CodeDetailRepository codeDetailRepo;

    /**
     * 사용자 목록 조회 (프로필 포함)
     */
    public Page<IntegratedUserResponse> getUsers(Pageable pageable) {
        Page<CoreUser> users = coreUserService.getUserEntities(pageable);
        List<Long> userIds = users.getContent().stream().map(CoreUser::getUserId).toList();

        Map<Long, UserProfile> profileMap = profileRepo.findAll().stream()
                .filter(p -> userIds.contains(p.getUserId()))
                .collect(Collectors.toMap(UserProfile::getUserId, p -> p, (a, b) -> a));

        // 공통코드 DEPT 그룹에서 부서명 맵 구성: CODE -> CODE_NAME
        Map<String, String> deptNameMap = codeDetailRepo
                .findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc("DEPT").stream()
                .collect(Collectors.toMap(CodeDetail::getCode, CodeDetail::getCodeName, (a, b) -> a));

        return users.map(user -> {
            UserProfile profile = profileMap.get(user.getUserId());
            String deptName = resolveDeptName(profile, deptNameMap);
            return IntegratedUserResponse.from(user, profile, deptName);
        });
    }

    /**
     * 사용자 단건 조회 (프로필 포함)
     */
    public IntegratedUserResponse getUser(Long userId) {
        CoreUser user = coreUserService.getUserEntity(userId);
        UserProfile profile = profileRepo.findByUserId(userId).orElse(null);
        String deptName = resolveDeptName(profile);
        return IntegratedUserResponse.from(user, profile, deptName);
    }

    /**
     * 사용자 생성 (프로필 포함)
     */
    @Transactional
    public IntegratedUserResponse createUser(UserCreateRequest request) {
        CoreUser saved = coreUserService.createCoreUser(request);

        UserProfile profile = null;
        if (hasProfileData(request.getDeptCode(), request.getPosition(), request.getJobTitle(),
                request.getEmployeeNo(), request.getJoinDate(), request.getOfficePhone(), request.getInternalExt())) {
            profile = UserProfile.builder()
                    .userId(saved.getUserId())
                    .deptCode(request.getDeptCode())
                    .position(request.getPosition())
                    .jobTitle(request.getJobTitle())
                    .employeeNo(request.getEmployeeNo())
                    .joinDate(request.getJoinDate())
                    .officePhone(request.getOfficePhone())
                    .internalExt(request.getInternalExt())
                    .build();
            profileRepo.save(profile);
        }

        String deptName = resolveDeptName(profile);
        return IntegratedUserResponse.from(saved, profile, deptName);
    }

    /**
     * 사용자 수정 (프로필 포함)
     */
    @Transactional
    public IntegratedUserResponse updateUser(Long userId, UserUpdateRequest request) {
        CoreUser user = coreUserService.updateCoreUser(userId, request);

        UserProfile profile = profileRepo.findByUserId(userId).orElse(null);
        if (profile != null) {
            profile.updateProfile(request.getDeptCode(), request.getPosition(),
                    request.getJobTitle(), request.getOfficePhone(), request.getInternalExt());
            profile.updateExtended(request.getEmployeeNo(), request.getJoinDate());
        } else if (hasProfileData(request.getDeptCode(), request.getPosition(), request.getJobTitle(),
                request.getEmployeeNo(), request.getJoinDate(), request.getOfficePhone(), request.getInternalExt())) {
            profile = UserProfile.builder()
                    .userId(userId)
                    .deptCode(request.getDeptCode())
                    .position(request.getPosition())
                    .jobTitle(request.getJobTitle())
                    .employeeNo(request.getEmployeeNo())
                    .joinDate(request.getJoinDate())
                    .officePhone(request.getOfficePhone())
                    .internalExt(request.getInternalExt())
                    .build();
            profileRepo.save(profile);
        }

        String deptName = resolveDeptName(profile);
        return IntegratedUserResponse.from(user, profile, deptName);
    }

    /**
     * 역할 변경
     */
    @Transactional
    public IntegratedUserResponse changeRole(Long userId, String role) {
        coreUserService.changeRole(userId, role);
        return getUser(userId);
    }

    // ── 헬퍼 ──

    /**
     * 부서명 조회 (공통코드 DEPT 그룹에서 deptCode → codeName 변환)
     */
    private String resolveDeptName(UserProfile profile) {
        if (profile == null || profile.getDeptCode() == null || profile.getDeptCode().isBlank()) return null;
        return codeDetailRepo.findByGroup_GroupCodeAndIsActiveTrueOrderBySortOrderAsc("DEPT").stream()
                .filter(d -> d.getCode().equals(profile.getDeptCode()))
                .map(CodeDetail::getCodeName)
                .findFirst().orElse(profile.getDeptCode());
    }

    private String resolveDeptName(UserProfile profile, Map<String, String> deptNameMap) {
        if (profile == null || profile.getDeptCode() == null || profile.getDeptCode().isBlank()) return null;
        return deptNameMap.getOrDefault(profile.getDeptCode(), profile.getDeptCode());
    }

    private boolean hasProfileData(Object... fields) {
        for (Object f : fields) {
            if (f != null) {
                if (f instanceof String && ((String) f).isBlank()) continue;
                return true;
            }
        }
        return false;
    }
}
