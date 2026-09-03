package com.company.module.dailyreport.repository;

import com.company.module.dailyreport.entity.DailyBatchJob;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 게시판 재업로드 요청 큐 Repository
 * - 이 애플리케이션은 요청 INSERT(save)만 수행한다.
 * - 실제 처리(폴링/UPDATE)는 별도 PC의 배치 시스템이 담당하므로
 *   이 애플리케이션에는 추가 조회 메서드가 필요 없다.
 */
public interface DailyBatchJobRepository extends JpaRepository<DailyBatchJob, Long> {
}
