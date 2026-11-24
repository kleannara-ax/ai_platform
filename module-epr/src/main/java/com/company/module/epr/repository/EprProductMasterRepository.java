package com.company.module.epr.repository;

import com.company.module.epr.entity.EprProductMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 기본 조회는 삭제되지 않은 행만 본다 (엔티티의 @SQLRestriction). */
public interface EprProductMasterRepository extends JpaRepository<EprProductMaster, Long> {

    List<EprProductMaster> findAllByOrderByProductCodeAsc();

    Optional<EprProductMaster> findByProductCode(String productCode);

    List<EprProductMaster> findAllByProductCodeIn(Collection<String> productCodes);

    long countByPartPrefix(String partPrefix);

    /**
     * 제품코드·제품명 검색(대소문자 무시)과 파트 필터. 빈 문자열이면 그 조건을 쓰지 않는다.
     * keyword 는 소문자로 넘긴다.
     */
    @Query("""
            SELECT p FROM EprProductMaster p
             WHERE (:keyword = '' OR LOWER(p.productCode) LIKE CONCAT('%', :keyword, '%')
                                  OR LOWER(p.productName) LIKE CONCAT('%', :keyword, '%'))
               AND (:partPrefix = '' OR p.partPrefix = :partPrefix)
             ORDER BY p.partPrefix ASC, p.productCode ASC""")
    List<EprProductMaster> search(@Param("keyword") String keyword, @Param("partPrefix") String partPrefix);

    /** 파트코드별 등록 SKU 수 */
    @Query("SELECT p.partPrefix AS partPrefix, COUNT(p) AS skuCount FROM EprProductMaster p GROUP BY p.partPrefix")
    List<PartSkuCount> countSkuByPartPrefix();

    /** 삭제된 행까지 포함해 찾는다 — 같은 제품코드를 다시 등록할 때 UNIQUE 충돌 없이 되살리기 위해. */
    @Query(value = "SELECT * FROM epr_product_master WHERE PRODUCT_CODE IN (:productCodes)", nativeQuery = true)
    List<EprProductMaster> findAllAnyByProductCodeIn(@Param("productCodes") Collection<String> productCodes);

    interface PartSkuCount {
        String getPartPrefix();
        long getSkuCount();
    }
}
