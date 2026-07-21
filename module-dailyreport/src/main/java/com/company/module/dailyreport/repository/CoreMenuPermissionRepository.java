package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.CoreMenuPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 사용자별 메뉴 접근 권한 리포지토리 (★ Phase 4 — 읽기 전용 참조)
 */
public interface CoreMenuPermissionRepository extends JpaRepository<CoreMenuPermission, Long> {

    /** 사용자 + 메뉴 ID로 권한 조회 */
    Optional<CoreMenuPermission> findByUserIdAndMenuId(Long userId, Long menuId);

    /** 사용자 + 메뉴 코드로 읽기 권한 확인 */
    @Query("SELECT mp FROM CoreMenuPermission mp " +
           "JOIN CoreMenu m ON m.menuId = mp.menuId " +
           "WHERE mp.userId = :userId AND m.menuCode = :menuCode AND mp.canRead = true")
    Optional<CoreMenuPermission> findReadPermissionByUserIdAndMenuCode(
            @Param("userId") Long userId,
            @Param("menuCode") String menuCode);

    /** 사용자 + 메뉴 코드로 쓰기 권한 확인 */
    @Query("SELECT mp FROM CoreMenuPermission mp " +
           "JOIN CoreMenu m ON m.menuId = mp.menuId " +
           "WHERE mp.userId = :userId AND m.menuCode = :menuCode AND mp.canWrite = true")
    Optional<CoreMenuPermission> findWritePermissionByUserIdAndMenuCode(
            @Param("userId") Long userId,
            @Param("menuCode") String menuCode);

    /** 사용자가 특정 메뉴에 읽기 권한이 있는지 */
    @Query("SELECT COUNT(mp) > 0 FROM CoreMenuPermission mp " +
           "JOIN CoreMenu m ON m.menuId = mp.menuId " +
           "WHERE mp.userId = :userId AND m.menuCode = :menuCode AND mp.canRead = true")
    boolean hasReadAccess(@Param("userId") Long userId, @Param("menuCode") String menuCode);

    /** 사용자가 특정 메뉴에 관리 권한이 있는지 */
    @Query("SELECT COUNT(mp) > 0 FROM CoreMenuPermission mp " +
           "JOIN CoreMenu m ON m.menuId = mp.menuId " +
           "WHERE mp.userId = :userId AND m.menuCode = :menuCode AND mp.canAdmin = true")
    boolean hasAdminAccess(@Param("userId") Long userId, @Param("menuCode") String menuCode);
}
