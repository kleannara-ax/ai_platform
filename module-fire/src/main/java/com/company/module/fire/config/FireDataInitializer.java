package com.company.module.fire.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소방 모듈 필수 마스터 데이터 보장.
 * <p>
 * 앱 시작 시 building / floor 테이블에 필수 레코드가 없으면
 * INSERT IGNORE 로 자동 삽입한다.
 * ddl-auto=none 환경이므로 JPA 가 아닌 네이티브 SQL 사용.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class FireDataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[FireDataInitializer] 필수 건물/층 마스터 데이터 확인 시작");

        // ── 건물(building) ──
        // id=99 '옥외' 건물은 옥외 소화전 등에서 반드시 필요
        insertIgnoreBuilding(99, "옥외");

        // ── 층(floor) ──
        // 지하1층 ~ 4층 + 옥외(id=99)
        insertIgnoreFloor(1, "지하1층(B1)", 0);
        insertIgnoreFloor(2, "1층",         1);
        insertIgnoreFloor(3, "2층",         2);
        insertIgnoreFloor(4, "3층",         3);
        insertIgnoreFloor(5, "4층",         4);
        insertIgnoreFloor(99, "옥외",       99);

        log.info("[FireDataInitializer] 필수 건물/층 마스터 데이터 확인 완료");

        // ── 데이터 보정: IS_ACTIVE=0 인 수신기/소방펌프 활성화 ──
        // QR 미등록 등록 시 isActive 누락 버그로 IS_ACTIVE=0 저장된 데이터 보정
        fixInactiveEquipment();
    }

    private void fixInactiveEquipment() {
        int receiverFixed = jdbc.update(
                "UPDATE fire_receiver SET IS_ACTIVE = 1 WHERE IS_ACTIVE = 0");
        if (receiverFixed > 0) {
            log.info("[FireDataInitializer] 비활성 수신기 {}건 활성화 완료", receiverFixed);
        }

        int pumpFixed = jdbc.update(
                "UPDATE fire_pump SET IS_ACTIVE = 1 WHERE IS_ACTIVE = 0");
        if (pumpFixed > 0) {
            log.info("[FireDataInitializer] 비활성 소방펌프 {}건 활성화 완료", pumpFixed);
        }
    }

    // ----------------------------------------------------------------
    //  INSERT IGNORE: 해당 PK 가 이미 존재하면 무시
    // ----------------------------------------------------------------

    private void insertIgnoreBuilding(long id, String name) {
        int affected = jdbc.update(
                "INSERT IGNORE INTO building (BUILDING_ID, BUILDING_NAME, IS_ACTIVE) VALUES (?, ?, 1)",
                id, name);
        if (affected > 0) {
            log.info("[FireDataInitializer] building 추가: id={}, name={}", id, name);
        }
    }

    private void insertIgnoreFloor(long id, String name, int sortOrder) {
        int affected = jdbc.update(
                "INSERT IGNORE INTO floor (FLOOR_ID, FLOOR_NAME, SORT_ORDER) VALUES (?, ?, ?)",
                id, name, sortOrder);
        if (affected > 0) {
            log.info("[FireDataInitializer] floor 추가: id={}, name={}, sortOrder={}", id, name, sortOrder);
        }
    }
}
