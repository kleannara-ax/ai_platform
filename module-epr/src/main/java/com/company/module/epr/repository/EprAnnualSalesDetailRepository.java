package com.company.module.epr.repository;

import com.company.module.epr.entity.EprAnnualSalesDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 기본 조회는 삭제되지 않은 행만 본다 (엔티티의 @SQLRestriction). */
public interface EprAnnualSalesDetailRepository extends JpaRepository<EprAnnualSalesDetail, Long> {

    List<EprAnnualSalesDetail> findAllByAnnualSalesIdOrderByPartPrefixAscProductCodeAsc(Long annualSalesId);

    /**
     * 같은 연도를 다시 올릴 때 기존 SKU 상세를 한 번에 소프트 삭제한다 (물리 삭제하지 않는다).
     * SKU 가 수천 건이라 엔티티를 하나씩 읽어 delete() 하지 않고 한 문장으로 처리한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE EprAnnualSalesDetail d
               SET d.deletedYn = 'Y', d.deletedAt = :deletedAt, d.deletedBy = :deletedBy
             WHERE d.annualSalesId = :annualSalesId AND d.deletedYn = 'N'""")
    int softDeleteAllByAnnualSalesId(@Param("annualSalesId") Long annualSalesId,
                                     @Param("deletedBy") Long deletedBy,
                                     @Param("deletedAt") LocalDateTime deletedAt);
}
