package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.CellPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CellPermissionRepository extends JpaRepository<CellPermission, Long> {

    /** 사용자 ID로 활성 권한 목록 조회 */
    List<CellPermission> findByUserIdAndIsActiveTrue(Long userId);

    /** 사용자 + 표 코드로 활성 권한 조회 */
    List<CellPermission> findByUserIdAndTableCodeAndIsActiveTrue(Long userId, String tableCode);

    /** 표 코드별 모든 권한 조회 */
    List<CellPermission> findByTableCodeAndIsActiveTrue(String tableCode);

    /** 특정 사용자가 특정 표의 특정 셀에 대해 권한이 있는지 확인 */
    @Query("SELECT p FROM CellPermission p " +
           "WHERE p.userId = :userId " +
           "AND p.tableCode = :tableCode " +
           "AND p.isActive = true " +
           "AND :rowIndex BETWEEN p.rowStart AND p.rowEnd " +
           "AND :colIndex BETWEEN p.colStart AND p.colEnd")
    List<CellPermission> findPermissionsForCell(
            @Param("userId") Long userId,
            @Param("tableCode") String tableCode,
            @Param("rowIndex") int rowIndex,
            @Param("colIndex") int colIndex);

    /** 사용자 + 표 코드 존재 여부 */
    boolean existsByUserIdAndTableCodeAndIsActiveTrue(Long userId, String tableCode);
}
