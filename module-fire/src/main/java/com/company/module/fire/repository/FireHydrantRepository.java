package com.company.module.fire.repository;

import com.company.module.fire.entity.FireHydrant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 소화전 Repository
 * <p>
 * NOTE: LEFT JOIN FETCH + Pageable 조합은 반드시 countQuery를 분리해야
 *       HHH90003004 경고 및 페이징 결과 오류를 방지할 수 있다.
 */
public interface FireHydrantRepository extends JpaRepository<FireHydrant, Long> {

    Optional<FireHydrant> findBySerialNumber(String serialNumber);

    Optional<FireHydrant> findByQrKey(String qrKey);

    /**
     * 소화전 목록 검색 (페이징)
     * - countQuery 분리: LEFT JOIN FETCH + Pageable 사용 시 필수
     */
    @Query(value =
           "SELECT h FROM FireHydrant h " +
           "LEFT JOIN FETCH h.building b " +
           "LEFT JOIN FETCH h.floor f " +
           "WHERE h.active = true " +
           "AND (:buildingId IS NULL OR b.buildingId = :buildingId) " +
           "AND (:floorId IS NULL OR f.floorId = :floorId) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(h.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(h.hydrantType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(h.locationDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))",
           countQuery =
           "SELECT COUNT(h) FROM FireHydrant h " +
           "LEFT JOIN h.building b " +
           "LEFT JOIN h.floor f " +
           "WHERE h.active = true " +
           "AND (:buildingId IS NULL OR b.buildingId = :buildingId) " +
           "AND (:floorId IS NULL OR f.floorId = :floorId) " +
           "AND (:keyword IS NULL OR " +
           "     LOWER(h.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(b.buildingName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(f.floorName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(h.hydrantType) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "     LOWER(h.locationDescription) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<FireHydrant> searchHydrants(
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId,
            @Param("keyword") String keyword,
            Pageable pageable);

    /** 도면용: 특정 건물/층의 활성 소화전 좌표 조회 */
    @Query("SELECT h FROM FireHydrant h " +
           "WHERE h.active = true " +
           "AND h.x IS NOT NULL AND h.y IS NOT NULL " +
           "AND h.hydrantType = :hydrantType " +
           "AND h.building.buildingId = :buildingId " +
           "AND h.floor.floorId = :floorId")
    List<FireHydrant> findForMap(
            @Param("hydrantType") String hydrantType,
            @Param("buildingId") Long buildingId,
            @Param("floorId") Long floorId);

    /** 다음 일련번호 계산용 */
    @Query("SELECT h.serialNumber FROM FireHydrant h WHERE h.serialNumber LIKE 'HYD-%'")
    List<String> findAllSerialNumbers();

    /** QR 목록: 건물+층 필터 */
    List<FireHydrant> findByBuilding_BuildingIdAndFloor_FloorId(Long buildingId, Long floorId);

    /** QR 목록: 건물 필터 */
    List<FireHydrant> findByBuilding_BuildingId(Long buildingId);

    /** QR 목록: 층 필터 */
    List<FireHydrant> findByFloor_FloorId(Long floorId);

    /** 시리얼 번호 존재 여부 */
    boolean existsBySerialNumber(String serialNumber);

    boolean existsByQrKey(String qrKey);
}
