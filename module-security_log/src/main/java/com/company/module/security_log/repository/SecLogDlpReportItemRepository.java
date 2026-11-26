package com.company.module.security_log.repository;

import com.company.module.security_log.entity.SecLogDlpReportItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SecLogDlpReportItemRepository extends JpaRepository<SecLogDlpReportItem, Long> {

    List<SecLogDlpReportItem> findByTargetYmAndDeletedYn(String targetYm, String deletedYn);

    Optional<SecLogDlpReportItem> findByTargetYmAndSectionAndItemKey(String targetYm, String section, String itemKey);

    /** 그 달까지의 값 (최근 달 순) - 부서 인원처럼 다음 달에도 이어서 쓰는 값 */
    List<SecLogDlpReportItem> findBySectionAndTargetYmLessThanEqualAndDeletedYnOrderByTargetYmDesc(String section, String targetYm, String deletedYn);
}
