package com.company.module.kims.repository;

import com.company.module.kims.entity.ProgramInstall;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProgramInstallRepository extends JpaRepository<ProgramInstall, Long> {

    /** 특정 요청에 연결된 설치 내역 (요청 상세에서 "관련 프로그램 설치 내역" 표시용) */
    List<ProgramInstall> findByServiceRequest_RequestIdOrderByCreatedAtAsc(Long requestId);

    /** 기간 내 설치 내역 (월말 결산용, 설치일 기준) */
    List<ProgramInstall> findByInstalledAtBetweenOrderByInstalledAtDesc(LocalDate from, LocalDate to);

    /**
     * 설치 내역 검색 (프로그램/요청자/대상PC 부분일치, 부서, 담당자, 설치일 기간 — 모두 선택적).
     * <p>{@code Pageable.unpaged()} 로 전체(엑셀용) 조회 가능.
     */
    @Query("""
            SELECT p FROM ProgramInstall p
            WHERE (:keyword IS NULL OR p.programName LIKE CONCAT('%', :keyword, '%')
                                    OR p.requesterName LIKE CONCAT('%', :keyword, '%')
                                    OR p.targetPc LIKE CONCAT('%', :keyword, '%'))
              AND (:department IS NULL OR p.department = :department)
              AND (:installedBy IS NULL OR p.installedBy = :installedBy)
              AND (:from IS NULL OR p.installedAt >= :from)
              AND (:to   IS NULL OR p.installedAt <= :to)
            ORDER BY p.installedAt DESC, p.installId DESC
            """)
    Page<ProgramInstall> search(@Param("keyword") String keyword,
                                @Param("department") String department,
                                @Param("installedBy") String installedBy,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to,
                                Pageable pageable);
}
