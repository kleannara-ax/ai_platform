package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.CellAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 셀 단위 접근 권한 리포지토리 (★ Phase 4 신규)
 */
public interface CellAuthRepository extends JpaRepository<CellAuth, Long> {

    /** 사용자별 활성 권한 전체 조회 */
    List<CellAuth> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * 사용자 + 표 코드로 활성 권한 조회 (단건)
     * ⚠ Deprecated: 한 사용자가 같은 표에 여러 CellAuth(주기별)를 가질 수 있으므로
     * 이 메서드는 여러 개 중 하나만 반환할 위험이 있다. 신규 코드는
     * {@link #findAllByUserIdAndTableCodeAndIsActiveTrue}를 사용할 것.
     */
    @Deprecated
    Optional<CellAuth> findByUserIdAndTableCodeAndIsActiveTrue(Long userId, String tableCode);

    /** 사용자 + 표 코드로 활성 권한 전체 조회 (한 사용자가 같은 표에서 여러 주기를 담당할 수 있음) */
    List<CellAuth> findAllByUserIdAndTableCodeAndIsActiveTrue(Long userId, String tableCode);

    /** 사용자 + 표 코드로 전체 조회 (활성/비활성 무관, 여러 건) */
    List<CellAuth> findAllByUserIdAndTableCode(Long userId, String tableCode);

    /** 표 코드별 모든 활성 권한 조회 */
    List<CellAuth> findByTableCodeAndIsActiveTrue(String tableCode);

    /** 전체 활성 권한 조회 (관리자 페이지용) */
    List<CellAuth> findByIsActiveTrue();

    /** 전체 조회 (비활성 포함, 관리자 페이지용) */
    List<CellAuth> findAllByOrderByTableCodeAscUserIdAsc();

    /** 사용자 + 표 코드 존재 여부 */
    boolean existsByUserIdAndTableCodeAndIsActiveTrue(Long userId, String tableCode);

    /** 사용자 + 표 코드 (활성/비활성 무관) */
    Optional<CellAuth> findByUserIdAndTableCode(Long userId, String tableCode);

    /** 사용자에게 활성 셀 권한이 1개라도 있는지 확인 (메뉴 접근 판단용) */
    boolean existsByUserIdAndIsActiveTrue(Long userId);
}
