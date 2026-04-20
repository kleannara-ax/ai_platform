package com.company.module.psinsp.repository;

import com.company.module.psinsp.entity.PsInspCodeDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 공통코드 상세(code_detail) 접근 Repository (PS-INSP 전용)
 *
 * <p>code_group.GROUP_CODE = 'PS_INSP_DEFAULT' 하위 코드만 조회합니다.
 */
public interface PsInspCodeDetailRepository extends JpaRepository<PsInspCodeDetail, Long> {

    /**
     * 그룹 코드 + 코드로 단건 조회
     * 예: groupCode='PS_INSP_DEFAULT', code='PPM_LIMIT'
     */
    @Query("SELECT d FROM PsInspCodeDetail d WHERE d.groupId = " +
           "(SELECT g.groupId FROM com.company.module.psinsp.entity.PsInspCodeGroup g WHERE g.groupCode = :groupCode) " +
           "AND d.code = :code AND d.isActive = true")
    Optional<PsInspCodeDetail> findByGroupCodeAndCode(
            @Param("groupCode") String groupCode,
            @Param("code") String code);

    /**
     * 그룹 코드로 활성 코드 목록 조회
     */
    @Query("SELECT d FROM PsInspCodeDetail d WHERE d.groupId = " +
           "(SELECT g.groupId FROM com.company.module.psinsp.entity.PsInspCodeGroup g WHERE g.groupCode = :groupCode) " +
           "AND d.isActive = true ORDER BY d.sortOrder ASC")
    List<PsInspCodeDetail> findAllByGroupCode(@Param("groupCode") String groupCode);
}
