package com.company.module.safety.repository;

import com.company.module.safety.entity.SafetyNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SafetyNoticeRepository extends JpaRepository<SafetyNotice, Long> {

    /** 활성 공지 목록. 상단 고정 글이 먼저, 그 다음 최신순. */
    @Query("""
            SELECT n FROM SafetyNotice n
            WHERE n.deletedYn = 'N'
            ORDER BY n.pinnedYn DESC, n.createdAt DESC, n.noticeId DESC
            """)
    List<SafetyNotice> findAllActive();

    @Query("SELECT n FROM SafetyNotice n WHERE n.noticeId = :id AND n.deletedYn = 'N'")
    Optional<SafetyNotice> findActiveById(@Param("id") Long id);
}
