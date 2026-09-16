package com.company.module.steamenergy.repository;

import com.company.module.steamenergy.entity.SteamPrice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 시설별 단가 Repository
 *
 * <p>Table: steam_price
 */
public interface SteamPriceRepository extends JpaRepository<SteamPrice, Long> {

    /** 업서트 키: 연도 + 월 + 항목코드 */
    Optional<SteamPrice> findByYearNoAndMonthNoAndItemCode(Integer yearNo, Integer monthNo, String itemCode);

    /** 연도 전체 (관리자 그리드 로드용) */
    List<SteamPrice> findByYearNoOrderByFacilityAscItemCodeAscMonthNoAsc(Integer yearNo);

    /** 연/월 페이징 */
    Page<SteamPrice> findByYearNoAndMonthNoOrderByFacilityAscItemCodeAsc(Integer yearNo, Integer monthNo, Pageable pageable);

    /** 연도 페이징 */
    Page<SteamPrice> findByYearNoOrderByMonthNoAscFacilityAscItemCodeAsc(Integer yearNo, Pageable pageable);
}
