package com.company.module.epr.repository;

import com.company.module.epr.entity.EprPartCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 기본 조회는 삭제되지 않은 행만 본다 (엔티티의 @SQLRestriction). */
public interface EprPartCategoryRepository extends JpaRepository<EprPartCategory, Long> {

    List<EprPartCategory> findAllByOrderByPartPrefixAsc();

    Optional<EprPartCategory> findByPartPrefix(String partPrefix);

    List<EprPartCategory> findAllByPartPrefixIn(Collection<String> partPrefixes);

    /** 삭제된 행까지 포함해 찾는다 — 같은 파트코드를 다시 등록할 때 UNIQUE 충돌 없이 되살리기 위해. */
    @Query(value = "SELECT * FROM epr_part_category WHERE PART_PREFIX = :partPrefix", nativeQuery = true)
    Optional<EprPartCategory> findAnyByPartPrefix(@Param("partPrefix") String partPrefix);
}
