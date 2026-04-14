package com.company.module.psinsp.repository;

import com.company.module.psinsp.entity.PsInspection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * PS 지분 검사 결과 Repository
 *
 * <p>Table: ps_inspection
 */
public interface PsInspectionRepository extends JpaRepository<PsInspection, Long> {

    /** 최신순 페이징 */
    Page<PsInspection> findAllByOrderByInspectedAtDesc(Pageable pageable);

    /** 바코드 LIKE 검색 */
    @Query("SELECT i FROM PsInspection i WHERE i.indBcd LIKE %:keyword% ORDER BY i.inspectedAt DESC")
    Page<PsInspection> searchByIndBcd(@Param("keyword") String keyword, Pageable pageable);

    /** LOT 번호 LIKE 검색 */
    @Query("SELECT i FROM PsInspection i WHERE i.lotnr LIKE %:keyword% ORDER BY i.inspectedAt DESC")
    Page<PsInspection> searchByLotnr(@Param("keyword") String keyword, Pageable pageable);

    /** 자재코드 LIKE 검색 */
    @Query("SELECT i FROM PsInspection i WHERE i.matnr LIKE %:keyword% ORDER BY i.inspectedAt DESC")
    Page<PsInspection> searchByMatnr(@Param("keyword") String keyword, Pageable pageable);

    /** 기간 조회 */
    @Query("SELECT i FROM PsInspection i WHERE i.inspectedAt BETWEEN :from AND :to ORDER BY i.inspectedAt DESC")
    Page<PsInspection> findByDateRange(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** 바코드 + 기간 복합 검색 */
    @Query("SELECT i FROM PsInspection i WHERE i.indBcd LIKE %:keyword% AND i.inspectedAt BETWEEN :from AND :to ORDER BY i.inspectedAt DESC")
    Page<PsInspection> searchByIndBcdAndDateRange(
            @Param("keyword") String keyword,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /** 같은 바코드의 최대 seq 조회 */
    @Query("SELECT MAX(i.seq) FROM PsInspection i WHERE i.indBcd = :indBcd")
    Optional<Integer> findMaxSeqByIndBcd(@Param("indBcd") String indBcd);

    /** 같은 바코드의 검사 차수 카운트 */
    @Query("SELECT COUNT(i) FROM PsInspection i WHERE i.indBcd = :indBcd")
    long countByIndBcd(@Param("indBcd") String indBcd);

    /** 동일 자재 + LOT + 개별바코드 조합으로 기존 레코드 조회 (재검사 UPDATE 용) */
    Optional<PsInspection> findByMatnrAndLotnrAndIndBcd(String matnr, String lotnr, String indBcd);
}
