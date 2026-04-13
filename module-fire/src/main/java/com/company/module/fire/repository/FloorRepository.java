package com.company.module.fire.repository;

import com.company.module.fire.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {
    List<Floor> findAllByOrderBySortOrderAsc();

    /** QR 페이지: 전체 층 목록 (정렬순+이름순) */
    List<Floor> findAllByOrderBySortOrderAscFloorNameAsc();

    /** 이름으로 층 조회 */
    Optional<Floor> findFirstByFloorNameContainingIgnoreCase(String keyword);
}
