package com.company.module.kims.repository;

import com.company.module.kims.entity.QrLocation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QrLocationRepository extends JpaRepository<QrLocation, Long> {

    Optional<QrLocation> findByToken(String token);

    /** 구역명/위치/부서 부분일치 검색 */
    @Query("""
            SELECT q FROM QrLocation q
            WHERE (:keyword IS NULL OR q.name LIKE CONCAT('%', :keyword, '%')
                                    OR q.location LIKE CONCAT('%', :keyword, '%')
                                    OR q.department LIKE CONCAT('%', :keyword, '%'))
            ORDER BY q.qrId DESC
            """)
    Page<QrLocation> search(@Param("keyword") String keyword, Pageable pageable);
}
