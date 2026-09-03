package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyManualCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyManualCategoryRepository extends JpaRepository<SafetyManualCategory, Long> {

    /** 전체 활성 분류 (트리 구성은 서비스에서 parent 관계로 조립) */
    @Query("SELECT c FROM SafetyManualCategory c WHERE c.deletedYn = 'N' ORDER BY c.sortOrder ASC, c.categoryId ASC")
    List<SafetyManualCategory> findAllActive();

    /** 최상위 분류만 */
    @Query("SELECT c FROM SafetyManualCategory c WHERE c.parent IS NULL AND c.deletedYn = 'N' ORDER BY c.sortOrder ASC")
    List<SafetyManualCategory> findRootCategories();

    /** 특정 부모의 하위 분류 */
    @Query("SELECT c FROM SafetyManualCategory c WHERE c.parent.categoryId = :parentId AND c.deletedYn = 'N' ORDER BY c.sortOrder ASC")
    List<SafetyManualCategory> findByParentId(@Param("parentId") Long parentId);

    @Query("SELECT c FROM SafetyManualCategory c WHERE c.categoryId = :id AND c.deletedYn = 'N'")
    Optional<SafetyManualCategory> findActiveById(@Param("id") Long id);

    /** 같은 부모 아래 이름 중복 확인 (분류명 유일성은 형제 단위) */
    @Query("""
            SELECT COUNT(c) > 0 FROM SafetyManualCategory c
            WHERE c.name = :name AND c.deletedYn = 'N'
              AND ((:parentId IS NULL AND c.parent IS NULL) OR c.parent.categoryId = :parentId)
            """)
    boolean existsByNameAndParent(@Param("name") String name, @Param("parentId") Long parentId);
}
