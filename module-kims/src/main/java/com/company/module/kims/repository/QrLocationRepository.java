package com.company.module.kims.repository;

import com.company.module.kims.entity.QrLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QrLocationRepository extends JpaRepository<QrLocation, Long> {

    /** 토큰으로 조회 (삭제되지 않은 것만) */
    Optional<QrLocation> findByTokenAndDeletedYn(String token, String deletedYn);

    /** 구역명/위치/부서 부분일치 검색 (소프트 삭제된 건은 제외) */
    @Query("""
            SELECT q FROM QrLocation q
            WHERE q.deletedYn = 'N'
              AND (:keyword IS NULL OR q.name LIKE CONCAT('%', :keyword, '%')
                                    OR q.location LIKE CONCAT('%', :keyword, '%')
                                    OR q.department LIKE CONCAT('%', :keyword, '%'))
            ORDER BY q.qrId DESC
            """)
    Page<QrLocation> search(@Param("keyword") String keyword, Pageable pageable);
}
