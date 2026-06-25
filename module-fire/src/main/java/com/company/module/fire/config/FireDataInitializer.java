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
        insertIgnoreBuilding(1,  "복지관");
        insertIgnoreBuilding(2,  "관리동");
        insertIgnoreBuilding(3,  "제지1,2호기");
        insertIgnoreBuilding(4,  "제지3호기");
        insertIgnoreBuilding(5,  "밀롤창고");
        insertIgnoreBuilding(6,  "기관실");
        insertIgnoreBuilding(7,  "화장지 3,6호기");
        insertIgnoreBuilding(8,  "화장지 4,5호기");
        insertIgnoreBuilding(9,  "전기현장");
        insertIgnoreBuilding(10, "주차타워");
        insertIgnoreBuilding(11, "보일러 조정동");
        insertIgnoreBuilding(12, "복합보일러");
        insertIgnoreBuilding(13, "전기공무");
        insertIgnoreBuilding(14, "패드동");
        insertIgnoreBuilding(15, "심면펄퍼");
        insertIgnoreBuilding(16, "수출창고");
        insertIgnoreBuilding(17, "중문창고");
        insertIgnoreBuilding(18, "기저귀동");
        insertIgnoreBuilding(19, "화장지 원단창고");
        insertIgnoreBuilding(20, "화장지 천막창고");
        insertIgnoreBuilding(21, "원료장");
        insertIgnoreBuilding(99, "옥외");

        // ── 층(floor) ──
        insertIgnoreFloor(1,  "지하1층(B1)", 0);
        insertIgnoreFloor(2,  "1층",         1);
        insertIgnoreFloor(3,  "2층",         2);
        insertIgnoreFloor(4,  "3층",         3);
        insertIgnoreFloor(5,  "4층",         4);
        insertIgnoreFloor(6,  "옥상",        5);
        insertIgnoreFloor(99, "옥외",        99);

        log.info("[FireDataInitializer] 필수 건물/층 마스터 데이터 확인 완료");

        // ── 건물명 보정: 잘못된 이름 수정 ──
        fixBuildingNames();

        // ── 데이터 보정: IS_ACTIVE=0 인 수신기/소방펌프 활성화 ──
        // QR 미등록 등록 시 isActive 누락 버그로 IS_ACTIVE=0 저장된 데이터 보정
        fixInactiveEquipment();
    }

    private void fixBuildingNames() {
        String[][] corrections = {
                // "저장" → "제지"
                {"저장 1,2호기", "제지1,2호기"},
                {"저장1,2호기", "제지1,2호기"},
                {"저장 3호기", "제지3호기"},
                {"저장3호기", "제지3호기"},
                {"저장12호기", "제지1,2호기"},
                {"저장 12호기", "제지1,2호기"},
                // "현장저장" → "화장지"
                {"현장저장 3,6호기", "화장지 3,6호기"},
                {"현장저장3,6호기", "화장지 3,6호기"},
                {"현장저장 4,5호기", "화장지 4,5호기"},
                {"현장저장4,5호기", "화장지 4,5호기"},
        };
        for (String[] pair : corrections) {
            int affected = jdbc.update(
                    "UPDATE building SET BUILDING_NAME = ? WHERE BUILDING_NAME = ?",
                    pair[1], pair[0]);
            if (affected > 0) {
                log.info("[FireDataInitializer] 건물명 보정: '{}' → '{}'", pair[0], pair[1]);
            }
        }
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
